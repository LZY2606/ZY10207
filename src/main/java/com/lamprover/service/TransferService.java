package com.lamprover.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamprover.domain.Stackup;
import com.lamprover.persistence.RunRepository;
import com.lamprover.persistence.StackupRepository;
import com.lamprover.persistence.StoredRun;
import com.lamprover.persistence.StoredVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行记录导出/导入。bundle 同时包含叠层版本载荷与运行结果，
 * 导入时重算 SHA-256 并与记录的 inputHash 比对，支持“清空数据库后重新导入复核”。
 */
@Service
public class TransferService {

    public static final String BUNDLE_KIND = "laminate-prover-export";
    public static final int BUNDLE_VERSION = 1;

    private final StackupRepository stackups;
    private final RunRepository runs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TransferService(StackupRepository stackups, RunRepository runs,
                           JdbcTemplate jdbc, ObjectMapper mapper) {
        this.stackups = stackups;
        this.runs = runs;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> exportBundle() {
        List<Map<String, Object>> versions = stackups.loadAllVersions().stream()
                .map(this::versionToMap).toList();
        List<Map<String, Object>> runList = runs.loadAll().stream()
                .map(this::runToMap).toList();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("kind", BUNDLE_KIND);
        bundle.put("bundleVersion", BUNDLE_VERSION);
        bundle.put("exportedAt", Instant.now().toString());
        bundle.put("versions", versions);
        bundle.put("runs", runList);
        return bundle;
    }

    private Map<String, Object> versionToMap(StoredVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("boardCode", v.boardCode());
        m.put("version", v.version());
        m.put("materialRevision", v.materialRevision());
        m.put("payload", v.stackup());
        m.put("payloadHash", v.payloadHash());
        m.put("createdAt", v.createdAt().toString());
        return m;
    }

    private Map<String, Object> runToMap(StoredRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("boardCode", r.boardCode());
        m.put("version", r.version());
        m.put("seed", r.seed());
        m.put("sampleCount", r.sampleCount());
        m.put("passed", r.passed());
        m.put("cornerPolicy", r.cornerPolicy());
        m.put("inputHash", r.inputHash());
        m.put("createdAt", r.createdAt().toString());
        try {
            m.put("result", mapper.readValue(r.resultJson(), new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception e) {
            m.put("resultRaw", r.resultJson());
        }
        return m;
    }

    public record ImportReport(int versionsImported, int runsImported,
                               int hashMismatches, List<String> notes) {
    }

    @Transactional
    public ImportReport importBundle(Map<String, Object> bundle, boolean replaceAll) {
        if (!BUNDLE_KIND.equals(bundle.get("kind"))) {
            throw new IllegalArgumentException("不是层压证明器导出的 bundle（kind=" + bundle.get("kind") + "）");
        }
        if (replaceAll) {
            runs.deleteAll();
        }
        int vCount = 0;
        int rCount = 0;
        int mismatch = 0;
        List<String> notes = new ArrayList<>();

        for (Map<String, Object> vm : castList(bundle.get("versions"))) {
            String boardCode = (String) vm.get("boardCode");
            int version = asInt(vm.get("version"));
            Object payload = vm.get("payload");
            String json = writeJson(payload);
            String actualHash = StackupRepository.sha256(json);
            String expectedHash = (String) vm.get("payloadHash");
            if (!actualHash.equals(expectedHash)) {
                mismatch++;
                notes.add("版本 " + boardCode + " v" + version + " 载荷哈希与导出时不一致，已拒绝");
                continue;
            }
            Stackup stackup = mapper.convertValue(payload, Stackup.class);
            List<String> validationErrors = stackup.validationErrors();
            if (!validationErrors.isEmpty()) {
                mismatch++;
                notes.add("版本 " + boardCode + " v" + version + " 校验失败: " + String.join("; ", validationErrors));
                continue;
            }
            if (stackups.load(boardCode, version) == null) {
                insertVersion(boardCode, version, stackup, expectedHash,
                        (String) vm.getOrDefault("createdAt", Instant.now().toString()));
            }
            vCount++;
        }

        for (Map<String, Object> rm : castList(bundle.get("runs"))) {
            String boardCode = (String) rm.get("boardCode");
            int version = asInt(rm.get("version"));
            String inputHash = (String) rm.get("inputHash");
            StoredVersion stored = stackups.load(boardCode, version);
            if (stored == null) {
                notes.add("运行记录缺少对应版本，已跳过: " + boardCode + " v" + version);
                continue;
            }
            if (!stored.payloadHash().equals(inputHash)) {
                mismatch++;
                notes.add("运行记录输入哈希与版本载荷不一致，已跳过: " + boardCode + " v" + version);
                continue;
            }
            Object result = rm.get("result");
            runs.insert(boardCode, version,
                    asLong(rm.getOrDefault("seed", 0L)),
                    asInt(rm.getOrDefault("sampleCount", 0)),
                    Boolean.TRUE.equals(rm.get("passed")),
                    (String) rm.getOrDefault("cornerPolicy", AnalysisService.POLICY_CORRELATED),
                    result != null ? writeJson(result) : "{}", inputHash);
            rCount++;
        }
        return new ImportReport(vCount, rCount, mismatch, notes);
    }

    private void insertVersion(String boardCode, int version, Stackup stackup,
                               String hash, String createdAt) {
        jdbc.update("""
                        INSERT INTO stackups(board_code,name,created_at,current_version)
                        VALUES(?,?,?,?)
                        ON CONFLICT(board_code) DO UPDATE SET
                          name=excluded.name,
                          current_version=MAX(current_version, excluded.current_version)""",
                boardCode, stackup.name(), createdAt, version);
        jdbc.update("""
                        INSERT INTO stackup_versions(board_code,version,material_revision,payload,payload_hash,created_at)
                        VALUES(?,?,?,?,?,?)""",
                boardCode, version, stackup.materialRevision(),
                stackups.writeJson(stackup), hash, createdAt);
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    private static long asLong(Object o) {
        return ((Number) o).longValue();
    }
}

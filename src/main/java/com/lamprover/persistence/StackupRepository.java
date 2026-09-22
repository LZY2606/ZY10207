package com.lamprover.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamprover.domain.Stackup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Repository
public class StackupRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public StackupRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record VersionSummary(String boardCode, String name, int version,
                                 String materialRevision, String payloadHash,
                                 Instant createdAt) {
    }

    /**
     * 保存叠层：若 boardCode 不存在则创建 v1；存在则追加新版本，
     * 旧版本与旧运行结论保持不变。
     */
    public StoredVersion saveNewVersion(Stackup stackup) {
        String code = stackup.boardCode();
        String json = writeJson(stackup);
        String hash = sha256(json);
        Instant now = Instant.now();
        Integer max = jdbc.query("SELECT COALESCE(MAX(version),0) FROM stackup_versions WHERE board_code=?",
                (rs, i) -> rs.getInt(1), code).stream().findFirst().orElse(0);
        int version = max + 1;
        int updated = jdbc.update("""
                        INSERT INTO stackups(board_code,name,created_at,current_version)
                        VALUES(?,?,?,?)
                        ON CONFLICT(board_code) DO UPDATE SET
                          name=excluded.name, current_version=excluded.current_version""",
                code, stackup.name(), now.toString(), version);
        jdbc.update("""
                        INSERT INTO stackup_versions(board_code,version,material_revision,payload,payload_hash,created_at)
                        VALUES(?,?,?,?,?,?)""",
                code, version, stackup.materialRevision(), json, hash, now.toString());
        return new StoredVersion(code, version, stackup.materialRevision(), stackup, hash, now);
    }

    public StoredVersion load(String boardCode, Integer version) {
        int v = version == null ? currentVersion(boardCode) : version;
        List<StoredVersion> rows = jdbc.query("""
                        SELECT payload, payload_hash, created_at, material_revision, version
                        FROM stackup_versions WHERE board_code=? AND version=?""",
                (rs, i) -> new StoredVersion(boardCode, rs.getInt("version"),
                        rs.getString("material_revision"),
                        readJson(rs.getString("payload")),
                        rs.getString("payload_hash"),
                        Instant.parse(rs.getString("created_at"))),
                boardCode, v);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    public int currentVersion(String boardCode) {
        Integer v = jdbc.query("SELECT current_version FROM stackups WHERE board_code=?",
                (rs, i) -> rs.getInt(1), boardCode).stream().findFirst().orElse(null);
        return v == null ? 0 : v;
    }

    public List<VersionSummary> listVersions(String boardCode) {
        return jdbc.query("""
                        SELECT s.board_code, s.name, v.version, v.material_revision, v.payload_hash, v.created_at
                        FROM stackups s JOIN stackup_versions v ON s.board_code = v.board_code
                        WHERE s.board_code=? ORDER BY v.version""",
                (rs, i) -> new VersionSummary(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5), Instant.parse(rs.getString(6))),
                boardCode);
    }

    public List<VersionSummary> listAll() {
        return jdbc.query("""
                        SELECT s.board_code, s.name, v.version, v.material_revision, v.payload_hash, v.created_at
                        FROM stackups s JOIN stackup_versions v
                          ON s.board_code = v.board_code AND s.current_version = v.version
                        ORDER BY s.board_code""",
                (rs, i) -> new VersionSummary(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5), Instant.parse(rs.getString(6))));
    }

    public List<StoredVersion> loadAllVersions() {
        return jdbc.query("""
                        SELECT board_code, version, material_revision, payload, payload_hash, created_at
                        FROM stackup_versions ORDER BY board_code, version""",
                (rs, i) -> new StoredVersion(rs.getString(1), rs.getInt(2), rs.getString(3),
                        readJson(rs.getString(4)), rs.getString(5),
                        Instant.parse(rs.getString(6))));
    }

    public String writeJson(Stackup stackup) {
        try {
            return mapper.writeValueAsString(stackup);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("叠层序列化失败: " + e.getMessage(), e);
        }
    }

    public Stackup readJson(String json) {
        try {
            return mapper.readValue(json, Stackup.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("叠层反序列化失败: " + e.getMessage(), e);
        }
    }

    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

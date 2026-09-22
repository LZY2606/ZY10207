package com.lamprover.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamprover.core.Analyzer;
import com.lamprover.core.LotVariable;
import com.lamprover.core.ToleranceMachine;
import com.lamprover.persistence.RunRepository;
import com.lamprover.persistence.StoredRun;
import com.lamprover.persistence.StoredVersion;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {

    public static final String POLICY_CORRELATED = "correlated-press-batches";
    public static final String POLICY_INDEPENDENT = "independent-extrema";

    private final StackupService stackupService;
    private final RunRepository runs;
    private final ObjectMapper mapper;

    public AnalysisService(StackupService stackupService, RunRepository runs, ObjectMapper mapper) {
        this.stackupService = stackupService;
        this.runs = runs;
        this.mapper = mapper;
    }

    public record RunEnvelope(StoredRun run, Map<String, Object> result) {
    }

    /**
     * 执行分析并持久化运行记录。
     *
     * @param cornerPolicy 角落策略；{@code independent-extrema} 会被明确拒绝（422），
     *                     因为同压合批次的量不允许独立取极值。
     */
    public RunEnvelope runAnalysis(String boardCode, Integer version, Long seed,
                                   Integer sampleCount, String cornerPolicy, boolean persist) {
        if (POLICY_INDEPENDENT.equals(cornerPolicy)) {
            throw new RejectedPolicyException(
                    "拒绝策略 independent-extrema：介质厚度与铜厚属于同一压合批次，"
                            + "独立取极值会构造物理不可能的组合。请使用 " + POLICY_CORRELATED + "。");
        }
        StoredVersion stored = stackupService.require(boardCode, version);
        long actualSeed = seed == null ? Fixture.fixedSeed() : seed;
        int actualSamples = sampleCount == null ? Fixture.fixedSampleCount() : sampleCount;
        Analyzer.AnalysisResult result = Analyzer.analyze(stored.stackup(), actualSeed, actualSamples);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("boardCode", boardCode);
        envelope.put("version", stored.version());
        envelope.put("materialRevision", stored.materialRevision());
        envelope.put("inputHash", stored.payloadHash());
        envelope.put("seed", actualSeed);
        envelope.put("sampleCount", actualSamples);
        envelope.put("cornerPolicy", POLICY_CORRELATED);
        envelope.put("ranAt", Instant.now().toString());
        envelope.put("passed", result.passed());
        envelope.put("traces", result.traces());

        String json = toJson(envelope);
        if (!persist) {
            return new RunEnvelope(null, envelope);
        }
        StoredRun saved = runs.insert(boardCode, stored.version(), actualSeed, actualSamples,
                result.passed(), POLICY_CORRELATED, json, stored.payloadHash());
        return new RunEnvelope(saved, envelope);
    }

    /**
     * 显式构造“逐变量独立取极值”的赋值，仅用于验收对照：
     * 同 lot 内方向冲突时返回诊断，拒绝输出该角落。
     */
    public Map<String, Object> diagnoseIndependentExtrema(String boardCode, Integer version,
                                                          Map<String, Integer> requestedSigns) {
        StoredVersion stored = stackupService.require(boardCode, version);
        Map<String, Object> response = new LinkedHashMap<>();
        boolean anyConflict = false;
        for (com.lamprover.domain.Trace trace : stored.stackup().traces()) {
            List<LotVariable> vars = Analyzer.buildVariables(stored.stackup(), trace);
            String conflict = ToleranceMachine.independentExtremaConflict(vars, requestedSigns);
            Map<String, Object> perTrace = new LinkedHashMap<>();
            perTrace.put("policy", POLICY_INDEPENDENT);
            perTrace.put("rejected", conflict != null);
            perTrace.put("reason", conflict);
            if (conflict != null) {
                anyConflict = true;
            }
            response.put(trace.name(), perTrace);
        }
        response.put("rejected", anyConflict);
        response.put("requiredPolicy", POLICY_CORRELATED);
        return response;
    }

    public List<StoredRun> listRuns(String boardCode, Integer version) {
        return runs.list(boardCode, version);
    }

    public StoredRun getRun(long id) {
        StoredRun r = runs.load(id);
        if (r == null) {
            throw new StackupService.NotFoundException("运行记录不存在: #" + id);
        }
        return r;
    }

    private String toJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("分析结果序列化失败", e);
        }
    }

    public static class RejectedPolicyException extends RuntimeException {
        public RejectedPolicyException(String message) {
            super(message);
        }
    }
}

package com.lamprover.core;

import com.lamprover.domain.CopperRole;
import com.lamprover.domain.Layer;
import com.lamprover.domain.Stackup;
import com.lamprover.domain.ToleranceSpec;
import com.lamprover.domain.Trace;
import com.lamprover.domain.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阻抗容差分析器。
 *
 * <p>分析一条走线时，只有“影响该走线阻抗”的几何与材料量进入变量表：
 * 走线层与最近参考平面之间介质（微带）或两平面之间介质（带状线）、
 * 走线层铜厚、线宽、差分线距、外侧阻焊。蛇形容差只影响长度/相位，明确不参与。</p>
 */
public final class Analyzer {

    private Analyzer() {
    }

    public record PointEvaluation(
            String pointId,
            String kind,
            boolean inDomain,
            Double zOhm,
            boolean withinBand,
            Map<String, String> lotDirections,
            Map<String, Double> lotDraws,
            Map<String, String> traceability,
            List<ImpedanceResult.Violation> violations,
            String formulaId,
            String formulaName,
            String topologyReason,
            GeometryInputs geometrySi) {
    }

    public record TraceReport(
            String traceName,
            boolean passed,
            boolean allInDomain,
            Double zMin,
            Double zMax,
            PointEvaluation nominal,
            List<PointEvaluation> corners,
            List<PointEvaluation> samples,
            List<LotInfo> lots,
            int cornerFailCount,
            int sampleFailCount,
            int outOfDomainCount) {
    }

    public record LotInfo(String lotId, String process, List<String> variables) {
    }

    public record AnalysisResult(
            boolean passed,
            long seed,
            int sampleCount,
            List<TraceReport> traces,
            String cornerPolicy) {
    }

    public static final String CORRELATED_POLICY = "correlated-press-batches";

    public static AnalysisResult analyze(Stackup stackup, long seed, int sampleCount) {
        List<TraceReport> reports = new ArrayList<>();
        for (Trace trace : stackup.traces()) {
            reports.add(analyzeTrace(stackup, trace, seed, sampleCount));
        }
        boolean passed = reports.stream().allMatch(TraceReport::passed);
        return new AnalysisResult(passed, seed, sampleCount, reports, CORRELATED_POLICY);
    }

    public static TraceReport analyzeTrace(Stackup stackup, Trace trace, long seed, int sampleCount) {
        List<LotVariable> vars = buildVariables(stackup, trace);
        Map<String, LotVariable> byKey = new LinkedHashMap<>();
        vars.forEach(v -> byKey.put(v.key(), v));

        PointEvaluation nominal = evaluate(stackup, trace, nominalValues(vars),
                "NOMINAL", "nominal", Map.of(), Map.of());

        List<ToleranceMachine.Corner> corners = ToleranceMachine.correlatedCorners(vars);
        List<PointEvaluation> cornerEvals = new ArrayList<>();
        for (ToleranceMachine.Corner c : corners) {
            Map<String, String> traceMap = traceForCorner(vars, c);
            cornerEvals.add(evaluate(stackup, trace, c.values(), c.id(), "corner",
                    c.lotDirections(), traceMap));
        }

        List<ToleranceMachine.Sample> samples = ToleranceMachine.samples(vars, seed, sampleCount);
        List<PointEvaluation> sampleEvals = new ArrayList<>();
        for (ToleranceMachine.Sample s : samples) {
            sampleEvals.add(evaluate(stackup, trace, s.values(), s.id(), "sample",
                    Map.of(), s.trace()));
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean allInDomain = true;
        int fail = 0;
        int sFail = 0;
        int ood = 0;
        List<PointEvaluation> all = new ArrayList<>();
        all.add(nominal);
        all.addAll(cornerEvals);
        all.addAll(sampleEvals);
        for (PointEvaluation point : all) {
            if (!point.inDomain()) {
                allInDomain = false;
                ood++;
                if (point.kind().equals("corner")) {
                    fail++;
                } else if (point.kind().equals("sample")) {
                    sFail++;
                }
                continue;
            }
            min = Math.min(min, point.zOhm());
            max = Math.max(max, point.zOhm());
            if (!point.withinBand()) {
                if (point.kind().equals("corner")) {
                    fail++;
                } else if (point.kind().equals("sample")) {
                    sFail++;
                }
            }
        }
        boolean nominalOk = nominal.inDomain() && nominal.withinBand();
        boolean passed = nominalOk && fail == 0 && sFail == 0;
        List<LotInfo> lots = lotInfo(vars);
        return new TraceReport(trace.name(), passed, allInDomain,
                Double.isInfinite(min) ? null : min,
                Double.isInfinite(max) ? null : max,
                nominal, cornerEvals, sampleEvals, lots, fail, sFail, ood);
    }

    private static PointEvaluation evaluate(Stackup stackup, Trace trace,
                                            Map<String, Double> values, String pointId, String kind,
                                            Map<String, String> lotDirections,
                                            Map<String, String> traceability) {
        StackupResolver.Resolved resolved = StackupResolver.resolve(stackup, trace, values);
        if (!resolved.structurallyValid()) {
            return new PointEvaluation(pointId, kind, false, null, false,
                    lotDirections, Map.of(), traceability,
                    resolved.structuralViolations(), null, null,
                    resolved.topologyReason(), null);
        }
        boolean differential = trace.type() == com.lamprover.domain.TraceType.DIFFERENTIAL;
        ImpedanceResult result = ImpedanceFormulas.compute(resolved.inputs(), differential);
        boolean within = result.inDomain() && trace.target().contains(result.zOhm());
        List<ImpedanceResult.Violation> violations = new ArrayList<>(result.violations());
        if (result.inDomain() && !within) {
            violations.add(new ImpedanceResult.Violation("Z",
                    String.format(java.util.Locale.ROOT,
                            "阻抗 %.2f Ω 落在目标带 [%.2f, %.2f] Ω 之外",
                            result.zOhm(), trace.target().lower(), trace.target().upper()),
                    result.zOhm(), trace.target().upper()));
        }
        return new PointEvaluation(pointId, kind, result.inDomain(),
                result.inDomain() ? round(result.zOhm()) : null,
                within, lotDirections,
                extractLotDraws(traceability), traceability,
                violations, result.formulaId(), result.formulaName(),
                resolved.topologyReason(), resolved.inputs());
    }

    private static Map<String, Double> extractLotDraws(Map<String, String> trace) {
        Map<String, Double> draws = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : trace.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("lot=([^ ]+).*u=([0-9.]+)").matcher(e.getValue());
            if (m.find() && draws.putIfAbsent(m.group(1), Double.parseDouble(m.group(2))) == null) {
                // first occurrence per lot is enough
            }
        }
        return draws;
    }

    private static Map<String, String> traceForCorner(List<LotVariable> vars,
                                                      ToleranceMachine.Corner c) {
        Map<String, String> out = new LinkedHashMap<>();
        for (LotVariable v : vars) {
            String dir = c.lotDirections().get(v.lotId());
            double value = c.values().get(v.key());
            out.put(v.key(), dir == null
                    ? "lot=" + safe(v.lotId()) + " nominal"
                    : "lot=" + safe(v.lotId()) + " corner " + dir
                    + " -> " + v.role() + "=" + String.format(java.util.Locale.ROOT, "%.6g", value)
                    + (v.spec().unitLabel() == null ? "" : " " + v.spec().unitLabel()));
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "independent" : s;
    }

    private static List<LotInfo> lotInfo(List<LotVariable> vars) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        Map<String, String> process = new LinkedHashMap<>();
        for (LotVariable v : vars) {
            String lot = v.lotId() == null ? "independent" : v.lotId();
            grouped.computeIfAbsent(lot, k -> new ArrayList<>())
                    .add(v.role() + " [" + v.key() + "]");
            process.putIfAbsent(lot, v.spec().processHint() == null
                    ? (lot.startsWith("independent:") ? "独立量（无关联）" : "关联压合/材料批次")
                    : v.spec().processHint());
        }
        List<LotInfo> out = new ArrayList<>();
        grouped.forEach((lot, vs) -> out.add(new LotInfo(lot, process.get(lot), vs)));
        return out;
    }

    private static Map<String, Double> nominalValues(List<LotVariable> vars) {
        Map<String, Double> m = new LinkedHashMap<>();
        vars.forEach(v -> m.put(v.key(), v.nominal()));
        return m;
    }

    /**
     * 构建走线相关变量。为可重放，批次顺序固定为：
     * 层从上到下（厚度后 Dk），再走线宽度、线距。
     */
    public static List<LotVariable> buildVariables(Stackup stackup, Trace trace) {
        List<LotVariable> vars = new ArrayList<>();
        List<Layer> layers = stackup.layers();
        int sigIdx = indexOfSignal(layers, trace.signalLayerName());
        Integer up = nearestPlane(layers, sigIdx, -1);
        Integer down = nearestPlane(layers, sigIdx, +1);

        int lo, hi;
        if (up != null && down != null) {
            lo = Math.min(up, down) + 1;
            hi = Math.max(up, down);
        } else {
            Integer p = up != null ? up : down;
            if (p == null) {
                lo = sigIdx + 1;
                hi = sigIdx;
            } else {
                lo = Math.min(sigIdx, p) + 1;
                hi = Math.max(sigIdx, p);
            }
        }
        for (int i = lo; i < hi; i++) {
            Layer l = layers.get(i);
            if (l.isDielectric() || l.isMask()) {
                addLayerVars(l, vars);
            }
        }
        Layer signal = layers.get(sigIdx);
        ToleranceSpec copperTol = trace.copperThicknessTol() != null
                ? trace.copperThicknessTol() : signal.thicknessTol();
        vars.add(new LotVariable(Keys.copperThickness(signal.name()),
                copperTol == null ? null : copperTol.lotId(),
                signal.name() + " 铜厚", signal.thickness().si(),
                withUnit(copperTol, signal.thickness().unit()),
                signal.thickness().unit()));
        vars.add(new LotVariable(Keys.traceWidth(trace.name()),
                trace.widthTol() == null ? null : trace.widthTol().lotId(),
                trace.name() + " 线宽", trace.width().si(),
                withUnit(trace.widthTol(), trace.width().unit()),
                trace.width().unit()));
        if (trace.type() == com.lamprover.domain.TraceType.DIFFERENTIAL) {
            vars.add(new LotVariable(Keys.traceSpacing(trace.name()),
                    trace.spacingTol() == null ? null : trace.spacingTol().lotId(),
                    trace.name() + " 线距", trace.spacing().si(),
                    withUnit(trace.spacingTol(), trace.spacing().unit()),
                    trace.spacing().unit()));
        }
        return vars;
    }

    private static void addLayerVars(Layer l, List<LotVariable> vars) {
        vars.add(new LotVariable(Keys.layerThickness(l.name()),
                l.thicknessTol() == null ? null : l.thicknessTol().lotId(),
                l.name() + " 厚度", l.thickness().si(),
                withUnit(l.thicknessTol(), l.thickness().unit()),
                l.thickness().unit()));
        if (l.dk() != null) {
            vars.add(new LotVariable(Keys.layerDk(l.name()),
                    l.dkTol() == null ? null : l.dkTol().lotId(),
                    l.name() + " Dk", l.dk().si(),
                    dkTol(l.dkTol()), com.lamprover.domain.Unit.ONE));
        }
    }

    private static ToleranceSpec withUnit(ToleranceSpec spec, Unit unit) {
        if (spec == null) {
            return null;
        }
        return spec.withUnitLabel(unit == Unit.ONE ? null : unit.name());
    }

    private static ToleranceSpec dkTol(ToleranceSpec spec) {
        return spec;
    }

    private static int indexOfSignal(List<Layer> layers, String name) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).isCopper() && layers.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("信号层不存在: " + name);
    }

    private static Integer nearestPlane(List<Layer> layers, int from, int dir) {
        for (int i = from + dir; i >= 0 && i < layers.size(); i += dir) {
            Layer l = layers.get(i);
            if (l.isCopper()) {
                return l.copperRole() == CopperRole.REFERENCE_PLANE ? i : null;
            }
        }
        return null;
    }

    private static double round(double z) {
        return Math.round(z * 1000.0) / 1000.0;
    }
}

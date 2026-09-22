package com.lamprover.core;

import com.lamprover.domain.CopperRole;
import com.lamprover.domain.Layer;
import com.lamprover.domain.Stackup;
import com.lamprover.domain.Trace;

import java.util.List;

/**
 * 把一版叠层、一条走线和一组容差取值（SI）解析为公式输入。
 *
 * <p>识别参考平面：走线铜层向上、向下找到的第一层 REFERENCE_PLANE 铜层。</p>
 * <ul>
 *   <li>单侧有平面 → 微带；仅当走线是最外信号铜层且其外侧存在阻焊时计入阻焊修正。</li>
 *   <li>双侧有平面 → 带状线；b 为两平面之间全部介质厚度之和，Dk 按厚度加权；
 *       h 为到较近平面的距离，居中度不足时由公式适用域给出诊断（不计算偏移带状线）。</li>
 *   <li>无参考平面 → 无法定义阻抗，返回诊断（不输出数字）。</li>
 * </ul>
 */
public final class StackupResolver {

    private StackupResolver() {
    }

    public record Resolved(GeometryInputs inputs, String topologyReason,
                           List<ImpedanceResult.Violation> structuralViolations) {
        public boolean structurallyValid() {
            return structuralViolations.isEmpty();
        }
    }

    public static Resolved resolve(Stackup stackup, Trace trace, java.util.Map<String, Double> values) {
        List<Layer> layers = stackup.layers();
        int sigIdx = -1;
        for (int i = 0; i < layers.size(); i++) {
            Layer l = layers.get(i);
            if (l.isCopper() && l.name().equals(trace.signalLayerName())) {
                sigIdx = i;
                break;
            }
        }
        if (sigIdx < 0) {
            return new Resolved(null, "信号层 " + trace.signalLayerName() + " 不在叠层中",
                    List.of(new ImpedanceResult.Violation("signalLayer",
                            "信号层不在叠层中", Double.NaN, Double.NaN)));
        }
        Layer signalLayer = layers.get(sigIdx);

        Integer abovePlane = nearestPlane(layers, sigIdx, -1);
        Integer belowPlane = nearestPlane(layers, sigIdx, +1);

        double t = get(values, Keys.copperThickness(signalLayer.name()), signalLayer.thickness().si());

        double w = get(values, Keys.traceWidth(trace.name()), trace.width().si());
        double s = trace.type() == com.lamprover.domain.TraceType.DIFFERENTIAL
                ? get(values, Keys.traceSpacing(trace.name()), trace.spacing().si()) : 0.0;

        if (abovePlane != null && belowPlane != null) {
            int hi = Math.min(abovePlane, belowPlane);
            int lo = Math.max(abovePlane, belowPlane);
            double sum = 0;
            double weightedDk = 0;
            for (int i = hi + 1; i < lo; i++) {
                Layer d = layers.get(i);
                if (!d.isDielectric()) {
                    continue;
                }
                double th = get(values, Keys.layerThickness(d.name()), d.thickness().si());
                double dk = get(values, Keys.layerDk(d.name()), d.dk().si());
                sum += th;
                weightedDk += th * dk;
            }
            double b = sum;
            double er = b > 0 ? weightedDk / b : Double.NaN;
            double hUp = dielectricSpan(layers, sigIdx, abovePlane, values);
            double hDown = dielectricSpan(layers, sigIdx, belowPlane, values);
            double h = Math.min(hUp, hDown);
            GeometryInputs g = new GeometryInputs(GeometryInputs.Topology.STRIPLINE,
                    w, t, h, b, er, s, 0, 0);
            String reason = "带状线：上下最近参考平面为 " + layers.get(abovePlane).name()
                    + " 与 " + layers.get(belowPlane).name() + "，b=" + fmt(b) + "m";
            return new Resolved(g, reason, List.of());
        }

        int dir;
        int planeIdx;
        if (belowPlane != null) {
            dir = +1;
            planeIdx = belowPlane;
        } else if (abovePlane != null) {
            dir = -1;
            planeIdx = abovePlane;
        } else {
            return new Resolved(null, "走线 " + trace.name() + " 上下均无完整参考平面，阻抗无定义",
                    List.of(new ImpedanceResult.Violation("referencePlane",
                            "走线上下均无完整参考平面", Double.NaN, Double.NaN)));
        }
        double h = dielectricSpan(layers, sigIdx, planeIdx, values);
        Layer dielectric = firstDielectric(layers, sigIdx, dir);
        double er = dielectric != null
                ? get(values, Keys.layerDk(dielectric.name()), dielectric.dk().si())
                : Double.NaN;
        double hm = 0;
        double erm = 0;
        Layer mask = outerMask(layers, sigIdx, -dir);
        if (mask != null) {
            hm = get(values, Keys.layerThickness(mask.name()), mask.thickness().si());
            erm = get(values, Keys.layerDk(mask.name()), mask.dk().si());
        }
        GeometryInputs g = new GeometryInputs(GeometryInputs.Topology.MICROSTRIP,
                w, t, h, 0, er, s, hm, erm);
        String reason = "微带：参考平面 " + layers.get(planeIdx).name()
                + "，h=" + fmt(h) + "m" + (mask != null ? "，外侧有阻焊 " + mask.name() : "，无外侧阻焊");
        return new Resolved(g, reason, List.of());
    }

    private static Integer nearestPlane(List<Layer> layers, int from, int dir) {
        for (int i = from + dir; i >= 0 && i < layers.size(); i += dir) {
            Layer l = layers.get(i);
            if (l.isCopper()) {
                if (l.copperRole() == CopperRole.REFERENCE_PLANE) {
                    return i;
                }
                return null;
            }
        }
        return null;
    }

    private static double dielectricSpan(List<Layer> layers, int from, int to,
                                         java.util.Map<String, Double> values) {
        int lo = Math.min(from, to);
        int hi = Math.max(from, to);
        double span = 0;
        for (int i = lo + 1; i < hi; i++) {
            Layer l = layers.get(i);
            if (l.isDielectric() || l.isMask()) {
                span += get(values, Keys.layerThickness(l.name()), l.thickness().si());
            }
        }
        return span;
    }

    private static Layer firstDielectric(List<Layer> layers, int from, int dir) {
        for (int i = from + dir; i >= 0 && i < layers.size(); i += dir) {
            Layer l = layers.get(i);
            if (l.isCopper()) {
                return null;
            }
            if (l.isDielectric()) {
                return l;
            }
        }
        return null;
    }

    /**
     * 朝板边方向（dir）查找外侧阻焊：方向上不能先遇到其它铜层，
     * 找到第一层阻焊即返回。
     */
    private static Layer outerMask(List<Layer> layers, int sigIdx, int dir) {
        for (int i = sigIdx + dir; i >= 0 && i < layers.size(); i += dir) {
            Layer l = layers.get(i);
            if (l.isCopper()) {
                return null;
            }
            if (l.isMask()) {
                return l;
            }
        }
        return null;
    }

    private static double get(java.util.Map<String, Double> values, String key, double fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v;
    }

    private static String fmt(double m) {
        return String.format(java.util.Locale.ROOT, "%.3g", m);
    }
}

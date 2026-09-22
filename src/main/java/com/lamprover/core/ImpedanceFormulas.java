package com.lamprover.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 阻抗公式集合（工程经验公式，来源 IPC-2141 / IPC-D-330，单位长度无关，只要 w,t,h,b,s,hm 同单位）。
 *
 * <p>每个公式都带显式适用域；越界返回 {@link ImpedanceResult#out}，保留输入与越界指标，
 * 不输出阻抗数字。</p>
 *
 * <p>公式：</p>
 * <ol>
 *   <li>IPC-2141 表面微带（零铜厚）：
 *       Z0 = 87 / sqrt(er+1.41) · ln(5.98h / (0.8w+t))；本实现以有效宽度 we=w+Δw 纳入铜厚，
 *       Δw = (t/π)·ln(1 + 4e / (t·coth^2(√(6.517·w/h))))（Hammerstad-Jensen 厚度修正）。</li>
 *   <li>IPC 带状线（居中）：Z0 = 60/√er · ln(1.9·(2b+t)/(0.8w+t))。</li>
 *   <li>微带差分（IPC-D-330 形式）：Zd = 2·Z0/(1 + 0.48·exp(-0.96·s/h))。</li>
 *   <li>带状线差分：Zd = 2·Z0/(1 + 0.347·exp(-2.9·s/b))。</li>
 *   <li>阻焊一阶修正（Bogatin/TI 经验式，单面覆盖）：Zm = Z0·(1 - 0.5/(hm·er/(hm+h))^1.45)，
 *       适用 hm/h ≤ 0.3。</li>
 * </ol>
 */
public final class ImpedanceFormulas {

    private ImpedanceFormulas() {
    }

    static final double MIN_ER = 1.05;
    static final double MAX_ER = 12.0;
    static final double MIN_WH = 0.05;
    static final double MAX_WH = 8.0;
    static final double MIN_TW = 0.0;
    static final double MAX_TW = 0.5;
    static final double MIN_TH = 0.0;
    static final double MAX_TH = 0.5;
    static final double MIN_CENTER_RATIO = 0.9;
    static final double MIN_SH = 0.05;
    static final double MAX_SH = 8.0;
    static final double MAX_HM_H = 0.3;
    static final double MIN_MASK_ER = 2.0;
    static final double MAX_MASK_ER = 6.0;

    public static ImpedanceResult compute(GeometryInputs g, boolean differential) {
        return differential ? differential(g) : singleEnded(g);
    }

    public static ImpedanceResult singleEnded(GeometryInputs g) {
        return switch (g.topology()) {
            case MICROSTRIP -> microstripSe(g);
            case STRIPLINE -> striplineSe(g);
        };
    }

    public static ImpedanceResult differential(GeometryInputs g) {
        ImpedanceResult se = singleEnded(g);
        List<ImpedanceResult.Violation> violations = new ArrayList<>(se.violations());
        if (g.s() <= 0) {
            violations.add(new ImpedanceResult.Violation("s", "差分必须给 s>0", g.s(), 0));
        }
        double ratio = g.topology() == GeometryInputs.Topology.STRIPLINE ? g.s() / g.b() : g.s() / g.h();
        if (!inRange(ratio, MIN_SH, MAX_SH)) {
            violations.add(new ImpedanceResult.Violation(
                    g.topology() == GeometryInputs.Topology.STRIPLINE ? "s/b" : "s/h",
                    "差分耦合公式适用 " + MIN_SH + ".." + MAX_SH, ratio, MAX_SH));
        }
        if (!violations.isEmpty()) {
            return ImpedanceResult.out(g, formulaId(g, true), formulaName(g, true),
                    diffExpression(g), violations);
        }
        double coupling = g.topology() == GeometryInputs.Topology.STRIPLINE
                ? 0.347 * Math.exp(-2.9 * g.s() / g.b())
                : 0.48 * Math.exp(-0.96 * g.s() / g.h());
        double zd = 2 * se.zOhm() / (1 + coupling);
        return ImpedanceResult.ok(g, zd, formulaId(g, true), formulaName(g, true), diffExpression(g));
    }

    private static ImpedanceResult microstripSe(GeometryInputs g) {
        String id = "IPC2141-MICROSTRIP-SE";
        String name = "IPC-2141 表面微带单端（Hammerstad 铜厚修正）";
        String expr = "Z0 = 87/sqrt(er+1.41) * ln(5.98h/(0.8*we)); 阻焊: Zm=Z0/(1+(Z0/87)*(hm/h)*(erm-1)/er*2.5)";
        List<ImpedanceResult.Violation> v = commonChecks(g, id, name);
        double hmH = g.h() == 0 ? Double.POSITIVE_INFINITY : g.hm() / g.h();
        if (g.hm() > 0) {
            if (!inRange(hmH, 0.0, MAX_HM_H)) {
                v.add(new ImpedanceResult.Violation("hm/h", "阻焊修正适用 hm/h ≤ " + MAX_HM_H,
                        hmH, MAX_HM_H));
            }
            if (!inRange(g.erm(), MIN_MASK_ER, MAX_MASK_ER)) {
                v.add(new ImpedanceResult.Violation("er_mask",
                        "阻焊 Dk 适用 " + MIN_MASK_ER + ".." + MAX_MASK_ER, g.erm(), MAX_MASK_ER));
            }
        }
        if (!v.isEmpty()) {
            return ImpedanceResult.out(g, id, name, expr, v);
        }
        double we = effectiveWidth(g.w(), g.t(), g.h());
        double z0 = 87.0 / Math.sqrt(g.er() + 1.41)
                * Math.log(5.98 * g.h() / (0.8 * we));
        if (g.hm() > 0) {
            z0 = applyMaskCorrection(z0, g);
        }
        if (!(z0 > 0 && Double.isFinite(z0))) {
            v.add(new ImpedanceResult.Violation("z0", "公式返回非正/非有限值，请检查几何比例",
                    z0, 0));
            return ImpedanceResult.out(g, id, name, expr, v);
        }
        return ImpedanceResult.ok(g, z0, id, name, expr);
    }

    private static ImpedanceResult striplineSe(GeometryInputs g) {
        String id = "IPC-STRIPLINE-SE";
        String name = "IPC 居中带状线单端";
        String expr = "Z0 = 60/sqrt(er) * ln(1.9*(2b+t)/(0.8w+t))";
        List<ImpedanceResult.Violation> v = commonChecks(g, id, name);
        if (g.b() <= 2 * g.h()) {
            v.add(new ImpedanceResult.Violation("b", "带状线需为双参考平面结构 b>2h", g.b(), 2 * g.h()));
        }
        double centered = 1.0 - Math.abs(g.h() - (g.b() - g.h())) / g.b();
        if (g.b() > 0 && centered < MIN_CENTER_RATIO) {
            v.add(new ImpedanceResult.Violation("centering",
                    "居中带状线公式要求走线位于两平面间 ±" + ((1 - MIN_CENTER_RATIO) * 100 / 2)
                            + "% 内（本版本不实现偏移带状线）", centered, MIN_CENTER_RATIO));
        }
        if (!v.isEmpty()) {
            return ImpedanceResult.out(g, id, name, expr, v);
        }
        double z0 = 60.0 / Math.sqrt(g.er())
                * Math.log(1.9 * (2 * g.b() + g.t()) / (0.8 * g.w() + g.t()));
        if (!(z0 > 0 && Double.isFinite(z0))) {
            v.add(new ImpedanceResult.Violation("z0", "公式返回非正/非有限值，请检查几何比例",
                    z0, 0));
            return ImpedanceResult.out(g, id, name, expr, v);
        }
        return ImpedanceResult.ok(g, z0, id, name, expr);
    }

    private static List<ImpedanceResult.Violation> commonChecks(GeometryInputs g, String id, String name) {
        List<ImpedanceResult.Violation> v = new ArrayList<>();
        if (!(g.w() > 0)) {
            v.add(new ImpedanceResult.Violation("w", "线宽必须为正", g.w(), 0));
        }
        if (!(g.t() >= 0)) {
            v.add(new ImpedanceResult.Violation("t", "铜厚必须非负", g.t(), 0));
        }
        if (!(g.h() > 0)) {
            v.add(new ImpedanceResult.Violation("h", "到参考平面距离必须为正", g.h(), 0));
        }
        if (g.w() > 0 && g.h() > 0) {
            double wh = g.w() / g.h();
            if (!inRange(wh, MIN_WH, MAX_WH)) {
                v.add(new ImpedanceResult.Violation("w/h",
                        "公式适用域 w/h ∈ [" + MIN_WH + ", " + MAX_WH + "]", wh, MAX_WH));
            }
            if (g.t() > 0) {
                double tw = g.t() / g.w();
                double th = g.t() / g.h();
                if (!inRange(tw, MIN_TW, MAX_TW)) {
                    v.add(new ImpedanceResult.Violation("t/w",
                            "铜厚修正适用 t/w ≤ " + MAX_TW, tw, MAX_TW));
                }
                if (!inRange(th, MIN_TH, MAX_TH)) {
                    v.add(new ImpedanceResult.Violation("t/h",
                            "铜厚修正适用 t/h ≤ " + MAX_TH, th, MAX_TH));
                }
            }
        }
        if (!inRange(g.er(), MIN_ER, MAX_ER)) {
            v.add(new ImpedanceResult.Violation("er",
                    "公式适用域 Dk ∈ [" + MIN_ER + ", " + MAX_ER + "]", g.er(), MAX_ER));
        }
        return v;
    }

    /** Hammerstad-Jensen 有效宽度。 */
    static double effectiveWidth(double w, double t, double h) {
        if (t <= 0) {
            return w;
        }
        double x = Math.sqrt(6.517 * w / h);
        double coth = Math.cosh(x) / Math.sinh(x);
        double inner = t * coth * coth;
        double dw = (t / Math.PI) * Math.log(1 + 4 * Math.E / inner);
        return w + (dw > 0 ? dw : 0);
    }

    /**
     * 阻焊一阶降额（Bogatin《Signal and Power Integrity》经验式的工程形式）：
     * <pre>
     * Zm = Z0 / (1 + delta)
     * delta = (Z0/87) * (hm/h) * (erm-1)/er * 2.5
     * </pre>
     * 系数 2.5 对常见 0.4..0.7mil、Dk≈3.3..4.0 阻焊给出约 3%..6% 降额；
     * 仅在 hm/h ≤ 0.3 且阻焊 Dk∈[2,6] 适用。
     */
    private static double applyMaskCorrection(double z0, GeometryInputs g) {
        double delta = (z0 / 87.0) * (g.hm() / g.h()) * (g.erm() - 1.0) / g.er() * 2.5;
        return z0 / (1.0 + Math.max(delta, 0.0));
    }

    private static boolean inRange(double x, double lo, double hi) {
        return Double.isFinite(x) && x >= lo && x <= hi;
    }

    private static String formulaId(GeometryInputs g, boolean diff) {
        String base = g.topology() == GeometryInputs.Topology.STRIPLINE
                ? "IPC-STRIPLINE" : "IPC2141-MICROSTRIP";
        return base + (diff ? "-DIFF" : "-SE");
    }

    private static String formulaName(GeometryInputs g, boolean diff) {
        String topo = g.topology() == GeometryInputs.Topology.STRIPLINE ? "带状线" : "微带";
        return "IPC " + topo + (diff ? "差分" : "单端");
    }

    private static String diffExpression(GeometryInputs g) {
        return g.topology() == GeometryInputs.Topology.STRIPLINE
                ? "Zd = 2*Z0/(1+0.347*exp(-2.9*s/b))"
                : "Zd = 2*Z0/(1+0.48*exp(-0.96*s/h))";
    }

    public record FormulaDoc(String id, String name, String expression,
                             String domain, String reference, String topology, String traceType) {
    }

    public static List<FormulaDoc> documentation() {
        return List.of(
                new FormulaDoc("IPC2141-MICROSTRIP-SE",
                        "IPC-2141 表面微带单端",
                        "Z0 = 87/sqrt(er+1.41) * ln(5.98h/(0.8*we))，we 为 Hammerstad 有效宽度",
                        "w/h∈[0.05,8], t/w≤0.5, t/h≤0.5, Dk∈[1.05,12], hm/h≤0.3, Dk_mask∈[2,6]",
                        "IPC-2141 (1996) 表面微带式；铜厚修正 Hammerstad & Jensen 1980",
                        "MICROSTRIP", "SINGLE_ENDED"),
                new FormulaDoc("IPC-STRIPLINE-SE",
                        "IPC 居中带状线单端",
                        "Z0 = 60/sqrt(er) * ln(1.9*(2b+t)/(0.8w+t))",
                        "w/h∈[0.05,8], t/w≤0.5, t/h≤0.5, Dk∈[1.05,12], 走线居中 ±5%",
                        "IPC-D-330 / IPC-2141 带状线式",
                        "STRIPLINE", "SINGLE_ENDED"),
                new FormulaDoc("IPC2141-MICROSTRIP-DIFF",
                        "IPC 微带差分",
                        "Zd = 2*Z0/(1+0.48*exp(-0.96*s/h))",
                        "同微带单端，且 s/h∈[0.05,8]",
                        "IPC-D-330 边缘耦合微带经验式",
                        "MICROSTRIP", "DIFFERENTIAL"),
                new FormulaDoc("IPC-STRIPLINE-DIFF",
                        "IPC 带状线差分",
                        "Zd = 2*Z0/(1+0.347*exp(-2.9*s/b))",
                        "同带状线单端，且 s/b∈[0.05,8]",
                        "IPC 边缘耦合带状线经验式",
                        "STRIPLINE", "DIFFERENTIAL"));
    }
}

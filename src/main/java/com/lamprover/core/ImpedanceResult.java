package com.lamprover.core;

import java.util.List;

/**
 * 一次阻抗计算的结果。
 *
 * <p>适用范围失效时 {@code inDomain=false} 且 {@code zOhm} 为 NaN：
 * 只保留输入与越界指标，绝不给伪精确数字。</p>
 */
public record ImpedanceResult(
        boolean inDomain,
        double zOhm,
        List<Violation> violations,
        String formulaId,
        String formulaName,
        String zExpression,
        GeometryInputs inputs) {

    public record Violation(String parameter, String rule, double actual, double limit) {
    }

    public static ImpedanceResult out(GeometryInputs in, String formulaId, String formulaName,
                                      String expr, List<Violation> violations) {
        return new ImpedanceResult(false, Double.NaN, violations, formulaId, formulaName, expr, in);
    }

    public static ImpedanceResult ok(GeometryInputs in, double z, String formulaId, String name,
                                     String expr) {
        return new ImpedanceResult(true, z, List.of(), formulaId, name, expr, in);
    }
}

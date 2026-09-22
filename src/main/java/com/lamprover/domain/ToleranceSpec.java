package com.lamprover.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个标称量的容差。
 *
 * @param lowerAbs 下界绝对量（与标称同单位，可空）
 * @param upperAbs 上界绝对量（与标称同单位，可空）
 * @param lowerPct 下界百分比（正数，如 10 表示 -10%，可空）
 * @param upperPct 上界百分比（正数，可空）
 * @param distribution 分布形式（uniform / normal，界对 normal 按 3σ 解释）
 * @param lotId 关联批次/压合过程标识；同 lot 的多个量共享一个偏移方向
 * @param role 该量在批次中的角色（溯源显示）
 * @param processHint 工艺过程说明（溯源显示）
 * @param unitLabel 绝对容差的工程单位标签（运行期由解析器注入，JSON 通常省略）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToleranceSpec(
        Double lowerAbs,
        Double upperAbs,
        Double lowerPct,
        Double upperPct,
        String distribution,
        String lotId,
        String role,
        String processHint,
        String unitLabel) {

    public ToleranceSpec(Double lowerAbs, Double upperAbs, Double lowerPct, Double upperPct,
                         String distribution, String lotId, String role) {
        this(lowerAbs, upperAbs, lowerPct, upperPct, distribution, lotId, role, null, null);
    }

    public ToleranceSpec(Double lowerAbs, Double upperAbs, Double lowerPct, Double upperPct,
                         String distribution, String lotId, String role, String processHint) {
        this(lowerAbs, upperAbs, lowerPct, upperPct, distribution, lotId, role, processHint, null);
    }

    public boolean hasTolerance() {
        return lowerAbs != null || upperAbs != null || lowerPct != null || upperPct != null;
    }

    public Distribution dist() {
        return Distribution.parse(distribution);
    }

    public double lowerFraction() {
        return lowerPct == null ? 0.0 : lowerPct / 100.0;
    }

    public double upperFraction() {
        return upperPct == null ? 0.0 : upperPct / 100.0;
    }

    public ToleranceSpec withUnitLabel(String label) {
        return new ToleranceSpec(lowerAbs, upperAbs, lowerPct, upperPct,
                distribution, lotId, role, processHint, label);
    }
}

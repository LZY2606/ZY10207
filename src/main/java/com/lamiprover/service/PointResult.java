package com.lamiprover.service;

import com.lamiprover.impedance.Violation;
import java.util.List;
import java.util.Map;

/**
 * 单个最坏角落 / 抽样点的评估结果。
 *
 * @param label            点标识（如角落 #3 或 sample #17）
 * @param pass             是否通过（在适用域内且阻抗在目标带内）
 * @param impedanceOhm     阻抗（Ω）；越界时为 null，禁止展示伪精度
 * @param deviationPercent 相对目标阻抗偏差（%）；越界时为 null
 * @param inDomain         公式适用域是否成立
 * @param violations       越界指标（保留输入与允许范围）
 * @param effectiveDk      该点等效 Dk；越界时为 null
 * @param values           本点各因子实际取值（SI）
 * @param traces           每个因子 -> {group, direction(±1/0), r, sourceLot, label}
 */
public record PointResult(
    String label,
    boolean pass,
    Double impedanceOhm,
    Double deviationPercent,
    boolean inDomain,
    List<Violation> violations,
    Double effectiveDk,
    Map<String, Double> values,
    Map<String, FactorTrace> traces,
    String failureReason
) {

  public static PointResult domain(String label, List<Violation> violations,
                                   Map<String, Double> values,
                                   Map<String, FactorTrace> traces) {
    return new PointResult(label, false, null, null, false, violations, null,
        values, traces, "FORMULA_DOMAIN");
  }

  public static PointResult spec(String label, double z, double dev, double effDk,
                                 Map<String, Double> values,
                                 Map<String, FactorTrace> traces) {
    boolean pass = false;
    return new PointResult(label, pass, z, dev, true, List.of(), effDk, values, traces,
        "IMPEDANCE_OUT_OF_SPEC");
  }
}

package com.lamiprover.impedance;

import java.util.List;

/**
 * 公式求值结果。越界时 {@code inDomain=false}、{@code impedanceOhm=NaN}，
 * 只保留越界指标；调用方不得展示阻抗数值。
 */
public record ImpedanceResult(
    boolean inDomain,
    double impedanceOhm,
    String formulaId,
    String formulaName,
    List<Violation> violations,
    double effectiveDk
) {

  public static ImpedanceResult ok(String id, String name, double z, double effEr) {
    return new ImpedanceResult(true, z, id, name, List.of(), effEr);
  }

  public static ImpedanceResult out(String id, String name, List<Violation> violations) {
    return new ImpedanceResult(false, Double.NaN, id, name, List.copyOf(violations), Double.NaN);
  }
}

package com.lamiprover.impedance;

/** 适用域越界指标：保留实际量与允许范围，不产生任何“伪精确”阻抗数字。 */
public record Violation(String check, double actual, double min, double max,
                        String detail, String rendered) {

  public Violation(String check, double actual, double min, double max, String detail) {
    this(check, actual, min, max, detail,
        detail + "（实际 " + fmt(actual) + "，允许 [" + fmt(min) + ", " + fmt(max) + "]）");
  }

  private static String fmt(double v) {
    if (Double.isNaN(v)) {
      return "—";
    }
    return String.format("%.4g", v);
  }
}

package com.lamiprover.model;

/** 走线/横截面类型。STACKUP 仅支持对称叠层（参考平面上下等距 h）。 */
public enum TraceType {
  /** 外层表面微带线，单端。h = 走线到其下参考平面的介质高度。 */
  MICROSTRIP_SE("表层微带线·单端"),
  /** 外层表面微带线，差分对（边缘耦合）。 */
  MICROSTRIP_DIFF("表层微带线·差分"),
  /** 内层带状线，单端（上下参考平面等距，走线居中）。 */
  STRIPLINE_SE("带状线·单端"),
  /** 内层带状线，差分对（边缘耦合，走线居中，上下平面等距）。 */
  STRIPLINE_DIFF("带状线·差分");

  private final String label;

  TraceType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public boolean differential() {
    return this == MICROSTRIP_DIFF || this == STRIPLINE_DIFF;
  }

  public boolean stripline() {
    return this == STRIPLINE_SE || this == STRIPLINE_DIFF;
  }
}

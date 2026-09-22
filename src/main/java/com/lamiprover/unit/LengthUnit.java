package com.lamiprover.unit;

import java.util.Locale;

/**
 * 显式工程长度单位。所有长度在进入核心计算前必须经 {@code toMetres} 归一化为 SI（米），
 * 禁止凭数值大小猜测单位。
 */
public enum LengthUnit {
  M("m", 1.0),
  MM("mm", 1e-3),
  UM("um", 1e-6),
  MIL("mil", 25.4e-6);

  private final String symbol;
  private final double metres;

  LengthUnit(String symbol, double metres) {
    this.symbol = symbol;
    this.metres = metres;
  }

  public double toMetres(double value) {
    return value * metres;
  }

  public double fromMetres(double valueInMetres) {
    return valueInMetres / metres;
  }

  public String symbol() {
    return symbol;
  }

  public static LengthUnit fromSymbol(String s) {
    if (s == null) {
      throw new IllegalArgumentException("长度单位不能为空");
    }
    String key = s.trim().toLowerCase(Locale.ROOT)
        .replace("μ", "u")
        .replace("µ", "u");
    for (LengthUnit u : values()) {
      if (u.symbol.equals(key) || u.name().equalsIgnoreCase(key)) {
        return u;
      }
    }
    throw new IllegalArgumentException("未知长度单位: " + s + "（允许 m / mm / um / mil）");
  }
}

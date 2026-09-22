package com.lamiprover.unit;

/**
 * 带显式单位的长度量。归一化 ({@link #metres()}) 是进入阻抗计算前的唯一入口。
 */
public record Length(double value, LengthUnit unit) {

  public Length {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException("长度值不是有限数: " + value);
    }
    if (unit == null) {
      throw new IllegalArgumentException("长度必须带显式单位（m/mm/um/mil），禁止按数值猜测");
    }
  }

  public static Length of(double value, String unitSymbol) {
    return new Length(value, LengthUnit.fromSymbol(unitSymbol));
  }

  /** SI 口径：米。 */
  public double metres() {
    return unit.toMetres(value);
  }

  public Length to(LengthUnit target) {
    return new Length(target.fromMetres(metres()), target);
  }

  @Override
  public String toString() {
    return value + " " + unit.symbol();
  }
}

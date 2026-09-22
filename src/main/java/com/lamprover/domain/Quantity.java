package com.lamprover.domain;

/**
 * 带显式工程单位的量。{@link #si()} 是进入核心计算的唯一口径，
 * mil / 微米 / 毫米绝不能按数值大小猜测。
 */
public record Quantity(double value, Unit unit) {

    public Quantity {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("数值必须有限");
        }
        if (unit == null) {
            throw new IllegalArgumentException("单位不能为空");
        }
    }

    public double si() {
        return unit.toSi(value);
    }

    public static Quantity of(double value, String unit) {
        return new Quantity(value, Unit.parse(unit));
    }
}

package com.lamprover.domain;

import java.util.Locale;

/**
 * 支持的工程单位。进入核心计算前必须显式换算为 SI，
 * 禁止依据数值大小猜测单位。
 */
public enum Unit {
    UM(1e-6),
    MM(1e-3),
    MIL(2.54e-5),
    /** 无量纲（如相对介电常数）。 */
    ONE(1.0),
    /** 欧姆。 */
    OHM(1.0);

    private final double toSi;

    Unit(double toSi) {
        this.toSi = toSi;
    }

    public double toSi(double value) {
        return value * toSi;
    }

    public double fromSi(double siValue) {
        return siValue / toSi;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static Unit parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("单位不能为空");
        }
        String t = raw.trim();
        String s = t.toLowerCase(Locale.ROOT).replace("μ", "u").replace("µ", "u").replace("ω", "ohm");
        return switch (s) {
            case "um", "micron" -> UM;
            case "mm" -> MM;
            case "mil", "thou" -> MIL;
            case "1", "", "none", "dimensionless" -> ONE;
            case "ohm", "r" -> OHM;
            default -> {
                try {
                    yield valueOf(t.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("不支持的单位: " + raw);
                }
            }
        };
    }
}

package com.lamprover.domain;

/** 制板厂给出的容差分布形式。 */
public enum Distribution {
    /** 均匀分布：样本在上下界之间等概率取值。 */
    UNIFORM,
    /** 正态分布：以标称值为均值，界按 3 个标准差解释。 */
    NORMAL;

    public static Distribution parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNIFORM;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "uniform", "u" -> UNIFORM;
            case "normal", "gaussian", "n" -> NORMAL;
            default -> throw new IllegalArgumentException("不支持的分布形式: " + raw);
        };
    }
}

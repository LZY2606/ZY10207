package com.lamprover.domain;

/** 走线类型：单端或差分。 */
public enum TraceType {
    SINGLE_ENDED,
    DIFFERENTIAL;

    public static TraceType parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("走线类型不能为空");
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "se", "single", "single-ended", "single_ended", "单端" -> SINGLE_ENDED;
            case "diff", "differential", "差分" -> DIFFERENTIAL;
            default -> throw new IllegalArgumentException("不支持的走线类型: " + raw);
        };
    }
}

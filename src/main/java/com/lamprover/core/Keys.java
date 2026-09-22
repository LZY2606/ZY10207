package com.lamprover.core;

/** 容差变量键命名约定，贯穿赋值映射、抽样溯源与导出。 */
public final class Keys {

    private Keys() {
    }

    public static String layerThickness(String layer) {
        return "layer:" + layer + ":thickness";
    }

    public static String layerDk(String layer) {
        return "layer:" + layer + ":dk";
    }

    public static String copperThickness(String layer) {
        return "layer:" + layer + ":thickness";
    }

    public static String traceWidth(String trace) {
        return "trace:" + trace + ":width";
    }

    public static String traceSpacing(String trace) {
        return "trace:" + trace + ":spacing";
    }
}

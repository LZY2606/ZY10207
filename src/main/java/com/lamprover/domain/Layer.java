package com.lamprover.domain;

import java.util.List;

/**
 * 叠层中的一层，按“从上到下”的顺序存储。
 *
 * <p>铜层厚度容差可与相邻介质厚度容差共享同一个 {@code lotId}（同一压合批次），
 * 角落分析会强制它们取同一个压合方向。</p>
 */
public record Layer(
        String name,
        LayerKind kind,
        Quantity thickness,
        ToleranceSpec thicknessTol,
        /** 介质层相对介电常数（仅介质层非空）。 */
        Quantity dk,
        ToleranceSpec dkTol,
        /** 介质材料版本标识，仅用于展示与溯源。 */
        String material,
        /** 铜层角色。 */
        CopperRole copperRole) {

    public boolean isDielectric() {
        return kind == LayerKind.DIELECTRIC;
    }

    public boolean isCopper() {
        return kind == LayerKind.COPPER;
    }

    public boolean isMask() {
        return kind == LayerKind.SOLDER_MASK;
    }

    public static Layer dielectric(String name, Quantity thickness, ToleranceSpec thicknessTol,
                                   Quantity dk, ToleranceSpec dkTol, String material) {
        return new Layer(name, LayerKind.DIELECTRIC, thickness, thicknessTol,
                dk, dkTol, material, null);
    }

    public static Layer copper(String name, CopperRole role, Quantity thickness, ToleranceSpec tol) {
        return new Layer(name, LayerKind.COPPER, thickness, tol, null, null, null, role);
    }

    public static Layer mask(String name, Quantity thickness, ToleranceSpec tol,
                             Quantity dk, ToleranceSpec dkTol) {
        return new Layer(name, LayerKind.SOLDER_MASK, thickness, tol, dk, dkTol, "solder-mask", null);
    }

    public List<String> validationErrors() {
        java.util.ArrayList<String> errs = new java.util.ArrayList<>();
        if (name == null || name.isBlank()) {
            errs.add("层名称不能为空");
        }
        if (kind == null) {
            errs.add((name == null ? "?" : name) + ": 层类型不能为空");
            return errs;
        }
        if (thickness == null || thickness.si() <= 0) {
            errs.add(name + ": 厚度必须为正");
        }
        if (isDielectric()) {
            if (dk == null || dk.value() <= 1.0) {
                errs.add(name + ": 介质 Dk 必须大于 1");
            }
        }
        if (isCopper() && copperRole == null) {
            errs.add(name + ": 铜层必须指定角色（信号/参考平面）");
        }
        return errs;
    }
}

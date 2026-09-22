package com.lamprover.core;

import com.lamprover.domain.ToleranceSpec;
import com.lamprover.domain.Unit;

/**
 * 进入容差分析的一个变量（具体几何/材料量），全部使用 SI。
 *
 * @param key 变量标识，如 layer:L1:thickness
 * @param lotId 关联批次标识；同 lot 变量在角落分析里共享同一方向（同一次压合）
 * @param role 批次内角色（溯源显示）
 * @param nominal 标称值（SI）
 * @param spec 容差（绝对界按 {@code unit} 换算到 SI）
 * @param unit 标称与绝对容差的工程单位；Dk 用 {@link Unit#ONE}
 */
public record LotVariable(
        String key,
        String lotId,
        String role,
        double nominal,
        ToleranceSpec spec,
        Unit unit) {

    public LotVariable(String key, String lotId, String role, double nominal, ToleranceSpec spec) {
        this(key, lotId, role, nominal, spec, Unit.ONE);
    }

    public double lowerSi() {
        double d = fractionBound(spec.lowerAbs(), spec.lowerFraction());
        return nominal - d;
    }

    public double upperSi() {
        double d = fractionBound(spec.upperAbs(), spec.upperFraction());
        return nominal + d;
    }

    private double fractionBound(Double abs, double pctFraction) {
        double d = 0.0;
        if (abs != null) {
            d = Math.max(d, abs * unit.toSi(1.0));
        }
        d = Math.max(d, nominal * pctFraction);
        return d;
    }
}

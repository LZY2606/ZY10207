package com.lamiprover.service;

/**
 * 单个因子在某个样本点的可回查轨迹。
 *
 * @param group     关联容差组
 * @param sourceLot 材料批次 / 工艺批
 * @param direction 最坏角落方向（-1/+1/0）；抽样模式为 null
 * @param r         抽样归一化位置 [-1,1]（单侧为 [0,1]）；最坏角落为 null
 * @param label     因子中文名
 */
public record FactorTrace(
    String group,
    String sourceLot,
    Integer direction,
    Double r,
    String label
) {
}

package com.lamprover.service;

import com.lamprover.domain.CopperRole;
import com.lamprover.domain.ImpedanceTarget;
import com.lamprover.domain.Layer;
import com.lamprover.domain.MeanderTolerance;
import com.lamprover.domain.Quantity;
import com.lamprover.domain.Stackup;
import com.lamprover.domain.ToleranceSpec;
import com.lamprover.domain.Trace;
import com.lamprover.domain.TraceType;

import java.util.List;

/**
 * 固定演示叠层（4 层对称板，单位刻意混用 mil / µm / mm）。
 *
 * <p>关联批次设计（同一压合过程不允许独立取极值）：</p>
 * <ul>
 *   <li>{@code press-top}：L1 铜厚 + 半固化片厚度（同一次压合，方向一致）；</li>
 *   <li>{@code press-bottom}：L4 铜厚 + 底部阻焊厚度；</li>
 *   <li>{@code core-A}：L2/L3 铜厚 + 芯板厚度；</li>
 *   <li>{@code dk-pp} / {@code dk-core}：两类介质各自的材料批次 Dk；</li>
 *   <li>{@code etch-A}：外层线宽（蚀刻批次）。</li>
 * </ul>
 */
public final class Fixture {

    private Fixture() {
    }

    public static Stackup demoStackup() {
        Layer maskTop = Layer.mask("SM-top",
                Quantity.of(0.5, "mil"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "mask-top", "顶部阻焊厚度", "阻焊涂布批次"),
                Quantity.of(3.8, "1"),
                new ToleranceSpec(null, null, 5.0, 5.0, "uniform",
                        "dk-mask", "阻焊 Dk", "阻焊油墨批次"));

        Layer l1 = Layer.copper("L1", CopperRole.SIGNAL,
                Quantity.of(35.0, "um"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "press-top", "L1 铜箔厚度", "顶部压合批次（铜箔+半固化片同涨落）"));

        Layer pp = Layer.dielectric("PP-7628",
                Quantity.of(0.25, "mm"),
                new ToleranceSpec(null, null, 7.0, 7.0, "normal",
                        "press-top", "半固化片介质厚度", "顶部压合批次"),
                Quantity.of(4.2, "1"),
                new ToleranceSpec(null, null, 3.0, 3.0, "normal",
                        "dk-pp", "半固化片 Dk@1GHz", "材料版本 PP-7628/4.2 批次"),
                "PP-7628/4.2");

        Layer l2 = Layer.copper("L2-GND", CopperRole.REFERENCE_PLANE,
                Quantity.of(1.4, "mil"),
                new ToleranceSpec(null, null, 8.0, 8.0, "uniform",
                        "core-A", "L2 铜箔厚度", "芯板压合批次"));

        Layer core = Layer.dielectric("CORE-FR4",
                Quantity.of(47.0, "mil"),
                new ToleranceSpec(null, null, 5.0, 5.0, "normal",
                        "core-A", "芯板介质厚度", "芯板压合批次（含两侧铜箔）"),
                Quantity.of(4.4, "1"),
                new ToleranceSpec(null, null, 3.0, 3.0, "normal",
                        "dk-core", "芯板 Dk@1GHz", "材料版本 CORE-FR4/4.4 批次"),
                "CORE-FR4/4.4");

        Layer l3 = Layer.copper("L3-PWR", CopperRole.REFERENCE_PLANE,
                Quantity.of(1.4, "mil"),
                new ToleranceSpec(null, null, 8.0, 8.0, "uniform",
                        "core-A", "L3 铜箔厚度", "芯板压合批次"));

        Layer ppBottom = Layer.dielectric("PP-7628-B",
                Quantity.of(250.0, "um"),
                new ToleranceSpec(null, null, 7.0, 7.0, "normal",
                        "press-bottom", "底部半固化片厚度", "底部压合批次"),
                Quantity.of(4.2, "1"),
                new ToleranceSpec(null, null, 3.0, 3.0, "normal",
                        "dk-pp", "半固化片 Dk@1GHz", "材料版本 PP-7628/4.2 批次"),
                "PP-7628/4.2");

        Layer l4 = Layer.copper("L4", CopperRole.SIGNAL,
                Quantity.of(35.0, "um"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "press-bottom", "L4 铜箔厚度", "底部压合批次"));

        Layer maskBottom = Layer.mask("SM-bottom",
                Quantity.of(0.5, "mil"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "mask-top", "底部阻焊厚度", "阻焊涂布批次"),
                Quantity.of(3.8, "1"),
                new ToleranceSpec(null, null, 5.0, 5.0, "uniform",
                        "dk-mask", "阻焊 Dk", "阻焊油墨批次"));

        Trace se50 = new Trace(
                "SE-50-L1", "L1", TraceType.SINGLE_ENDED,
                Quantity.of(0.30, "mm"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "etch-A", "L1 线宽", "外层蚀刻批次"),
                null, null, null,
                new MeanderTolerance(
                        Quantity.of(0.30, "mm"),
                        new ToleranceSpec(null, null, 8.0, 8.0, "uniform",
                                "meander-A", "蛇形幅度", "CAM/绕线工序"),
                        Quantity.of(1.0, "mm"), null,
                        Quantity.of(12.0, "mm")),
                ImpedanceTarget.of(50.0, 45.0, 55.0));

        Trace diff100 = new Trace(
                "USB-DP-L1", "L1", TraceType.DIFFERENTIAL,
                Quantity.of(0.22, "mm"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "etch-A", "差分线宽", "外层蚀刻批次"),
                Quantity.of(0.25, "mm"),
                new ToleranceSpec(null, null, 12.0, 12.0, "uniform",
                        "etch-A", "差分线距", "外层蚀刻批次"),
                null, null,
                ImpedanceTarget.of(95.0, 90.0, 110.0));

        Trace tooWide = new Trace(
                "WIDE-OOD-L1", "L1", TraceType.SINGLE_ENDED,
                Quantity.of(3.0, "mm"),
                new ToleranceSpec(null, null, 10.0, 10.0, "uniform",
                        "etch-A", "宽线线宽", "外层蚀刻批次"),
                null, null, null, null,
                ImpedanceTarget.of(50.0, 45.0, 55.0));

        return new Stackup(
                "演示四层阻抗板",
                "DEMO-4L-001",
                "FR4-7628@revC",
                List.of(maskTop, l1, pp, l2, core, l3, ppBottom, l4, maskBottom),
                List.of(se50, diff100, tooWide),
                true,
                "混合单位：阻焊 mil、铜厚 µm、介质 mm/mil/µm；介质厚度与铜厚共享压合批次。");
    }

    public static long fixedSeed() {
        return 20260922L;
    }

    public static int fixedSampleCount() {
        return 200;
    }
}

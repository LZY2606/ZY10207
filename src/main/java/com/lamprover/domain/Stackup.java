package com.lamprover.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对称叠层快照：层列表按从上到下存储，工程层应对称（含上下阻焊）。
 * 快照本身不可变；保存时生成新版本，旧版本结论不自动重算。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Stackup(
        String name,
        String boardCode,
        String materialRevision,
        List<Layer> layers,
        List<Trace> traces,
        boolean symmetric,
        String notes) {

    public List<String> validationErrors() {
        List<String> errs = new ArrayList<>();
        if (layers == null || layers.isEmpty()) {
            errs.add("叠层至少需要一层");
            return errs;
        }
        Set<String> names = new HashSet<>();
        for (Layer l : layers) {
            errs.addAll(l.validationErrors());
            if (l.name() != null && !names.add(l.name())) {
                errs.add("层名称重复: " + l.name());
            }
        }
        List<String> signalLayers = layers.stream()
                .filter(l -> l.isCopper() && l.copperRole() == CopperRole.SIGNAL)
                .map(Layer::name).toList();
        for (Trace t : traces == null ? List.<Trace>of() : traces) {
            errs.addAll(t.validationErrors());
            if (t.signalLayerName() != null && !names.contains(t.signalLayerName())) {
                errs.add(t.name() + ": 信号层 " + t.signalLayerName() + " 不存在于叠层");
            }
        }
        if (symmetric && !checkSymmetry(layers)) {
            errs.add("叠层标记为对称，但层的类型/材料分布不关于中心对称（厚度可不同）");
        }
        return errs;
    }

    /** 对称只约束种类与材料角色镜像，厚度由各自容差表达。 */
    public static boolean checkSymmetry(List<Layer> ls) {
        int n = ls.size();
        for (int i = 0; i < n / 2; i++) {
            Layer a = ls.get(i);
            Layer b = ls.get(n - 1 - i);
            if (a.kind() != b.kind()) {
                return false;
            }
            if (a.copperRole() != b.copperRole()) {
                return false;
            }
            String ma = a.material() == null ? "" : a.material();
            String mb = b.material() == null ? "" : b.material();
            if (!ma.equals(mb)) {
                return false;
            }
        }
        return true;
    }
}

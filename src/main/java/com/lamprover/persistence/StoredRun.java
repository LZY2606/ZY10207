package com.lamprover.persistence;

import java.time.Instant;

/**
 * 一条不可变运行记录。旧版本叠层的 run 永远保留原值，
 * 叠层再编辑只产生新版本，不回写旧结论。
 */
public record StoredRun(
        long id,
        String boardCode,
        int version,
        long seed,
        int sampleCount,
        boolean passed,
        String cornerPolicy,
        String resultJson,
        String inputHash,
        Instant createdAt) {
}

package com.lamprover.persistence;

import com.lamprover.domain.Stackup;

import java.time.Instant;

public record StoredVersion(
        String boardCode,
        int version,
        String materialRevision,
        Stackup stackup,
        String payloadHash,
        Instant createdAt) {
}

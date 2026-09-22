package com.lamiprover.db;

import java.time.Instant;

/** stackups 表一行。 */
public record StackupRow(
    String id,
    String name,
    int version,
    String parentVersionId,
    String materialVersion,
    Instant createdAt
) {
}

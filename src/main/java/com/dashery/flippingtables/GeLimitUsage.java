package com.dashery.flippingtables;

import lombok.Data;

@Data
public class GeLimitUsage {
    private final int limitUsed;
    private final long limitRefreshTimestamp;
}

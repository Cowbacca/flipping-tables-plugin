package com.dashery.flippingtables;

import lombok.Data;

import java.time.temporal.ChronoUnit;

@Data
public class SerializableDuration {
    private final ChronoUnit unit;
    private final long value;
}

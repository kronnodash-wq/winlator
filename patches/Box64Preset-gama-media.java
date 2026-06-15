package com.winlator.box64;

import androidx.annotation.NonNull;

public class Box64Preset {
    public static final String STABILITY = "STABILITY";
    public static final String CONSERVATIVE = "CONSERVATIVE";
    public static final String INTERMEDIATE = "INTERMEDIATE";
    public static final String PERFORMANCE = "PERFORMANCE";
    public static final String CUSTOM = "CUSTOM";
    // PERFORMANCE preset: max JIT throughput (BIGBLOCK=3, FORWARD=512, CALLRET=1)
    // Ideal for Skullgirls 2nd Encore on Exynos 1580 (Galaxy A56) targeting 60-70 FPS
    public static final String DEFAULT = PERFORMANCE;
    public final String id;
    public final String name;

    public Box64Preset(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean isCustom() {
        return id.startsWith(CUSTOM);
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}

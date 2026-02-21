package com.kailas.settlementengine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Random;

@Service
public class SettlementOutcomeDecider {

    private final SettlementOutcomeMode mode;
    private final Random random;

    @Autowired
    public SettlementOutcomeDecider(
            @Value("${settlement.outcome.mode:RANDOM}") String configuredMode,
            @Value("${settlement.outcome.random-seed:}") String configuredSeed
    ) {
        this(parseMode(configuredMode), parseSeed(configuredSeed));
    }

    SettlementOutcomeDecider(SettlementOutcomeMode mode, Long randomSeed) {
        this.mode = mode;
        this.random = randomSeed == null ? new Random() : new Random(randomSeed);
    }

    public boolean shouldSucceed() {
        return switch (mode) {
            case ALWAYS_SUCCESS -> true;
            case ALWAYS_FAIL -> false;
            case RANDOM -> random.nextBoolean();
        };
    }

    public SettlementOutcomeMode getMode() {
        return mode;
    }

    private static SettlementOutcomeMode parseMode(String configuredMode) {
        if (configuredMode == null || configuredMode.isBlank()) {
            return SettlementOutcomeMode.RANDOM;
        }

        try {
            return SettlementOutcomeMode.valueOf(
                    configuredMode.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid settlement.outcome.mode: " + configuredMode
                            + ". Allowed values: RANDOM, ALWAYS_SUCCESS, ALWAYS_FAIL",
                    e
            );
        }
    }

    private static Long parseSeed(String configuredSeed) {
        if (configuredSeed == null || configuredSeed.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(configuredSeed.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid settlement.outcome.random-seed: " + configuredSeed
                            + ". Expected a numeric value.",
                    e
            );
        }
    }
}

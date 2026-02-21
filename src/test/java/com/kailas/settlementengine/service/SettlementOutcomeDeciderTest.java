package com.kailas.settlementengine.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettlementOutcomeDeciderTest {

    @Test
    void alwaysSuccessModeShouldAlwaysSucceed() {
        SettlementOutcomeDecider decider =
                new SettlementOutcomeDecider(SettlementOutcomeMode.ALWAYS_SUCCESS, null);

        for (int i = 0; i < 20; i++) {
            assertTrue(decider.shouldSucceed());
        }
    }

    @Test
    void alwaysFailModeShouldAlwaysFail() {
        SettlementOutcomeDecider decider =
                new SettlementOutcomeDecider(SettlementOutcomeMode.ALWAYS_FAIL, null);

        for (int i = 0; i < 20; i++) {
            assertFalse(decider.shouldSucceed());
        }
    }

    @Test
    void randomModeWithSameSeedShouldBeDeterministic() {
        SettlementOutcomeDecider a =
                new SettlementOutcomeDecider(SettlementOutcomeMode.RANDOM, 42L);
        SettlementOutcomeDecider b =
                new SettlementOutcomeDecider(SettlementOutcomeMode.RANDOM, 42L);

        for (int i = 0; i < 50; i++) {
            assertEquals(a.shouldSucceed(), b.shouldSucceed());
        }
    }

    @Test
    void invalidModeShouldThrowClearError() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new SettlementOutcomeDecider("INVALID_MODE", "")
        );

        assertTrue(ex.getMessage().contains("Invalid settlement.outcome.mode"));
    }

    @Test
    void invalidSeedShouldThrowClearError() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new SettlementOutcomeDecider("RANDOM", "not_a_number")
        );

        assertTrue(ex.getMessage().contains("Invalid settlement.outcome.random-seed"));
    }
}

package cn.elvis.monaco.testkit.fault;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultPlanTest {

    @Test
    void triggersOnceAlwaysAndNthInvocationRules() {
        FaultPlan plan = FaultPlan.builder()
                .failOnce(FaultPoint.AUTHENTICATE, new TestException("first"))
                .failAlways(FaultPoint.AUTHENTICATE, () -> new TestException("always"))
                .failOnInvocation(FaultPoint.AUTHORIZE, 2, new TestException("second"))
                .build();

        StepVerifier.create(plan.trigger(FaultPoint.AUTHENTICATE))
                .expectErrorMessage("first")
                .verify();
        StepVerifier.create(plan.trigger(FaultPoint.AUTHENTICATE))
                .expectErrorMessage("always")
                .verify();
        StepVerifier.create(plan.trigger(FaultPoint.AUTHORIZE)).verifyComplete();
        StepVerifier.create(plan.trigger(FaultPoint.AUTHORIZE))
                .expectErrorMessage("second")
                .verify();

        assertEquals(2, plan.invocationCount(FaultPoint.AUTHENTICATE));
        assertEquals(2, plan.invocationCount(FaultPoint.AUTHORIZE));
    }

    @Test
    void pausesUntilGateIsReleased() {
        Gate gate = new Gate("store commit");
        FaultPlan plan = FaultPlan.builder()
                .pauseOnce(FaultPoint.STORE_BEFORE_COMMIT, gate)
                .build();

        StepVerifier.create(plan.trigger(FaultPoint.STORE_BEFORE_COMMIT))
                .then(() -> {
                    assertEquals(1, gate.awaitArrivals(1, Duration.ofSeconds(1)).size());
                    assertTrue(gate.release());
                    assertFalse(gate.release());
                })
                .verifyComplete();

        StepVerifier.create(plan.trigger(FaultPoint.STORE_BEFORE_COMMIT)).verifyComplete();
    }

    private static final class TestException extends RuntimeException {

        private TestException(String message) {
            super(message);
        }
    }
}

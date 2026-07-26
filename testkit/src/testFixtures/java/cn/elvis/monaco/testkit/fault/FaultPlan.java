package cn.elvis.monaco.testkit.fault;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

public final class FaultPlan {

    private final Map<FaultPoint, List<Rule>> rules;
    private final Map<FaultPoint, AtomicLong> invocations = new EnumMap<>(FaultPoint.class);

    private FaultPlan(Map<FaultPoint, List<Rule>> rules) {
        this.rules = rules;
        for (FaultPoint point : FaultPoint.values()) {
            invocations.put(point, new AtomicLong());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Mono<Void> trigger(FaultPoint point) {
        Objects.requireNonNull(point, "point");
        return Mono.defer(() -> {
            long invocation = invocations.get(point).incrementAndGet();
            for (Rule rule : rules.getOrDefault(point, List.of())) {
                if (rule.matches().test(invocation)) {
                    return Objects.requireNonNull(rule.action().get(), "fault action returned null");
                }
            }
            return Mono.empty();
        });
    }

    public long invocationCount(FaultPoint point) {
        Objects.requireNonNull(point, "point");
        return invocations.get(point).get();
    }

    public static final class Builder {

        private final Map<FaultPoint, List<Rule>> rules = new EnumMap<>(FaultPoint.class);

        public Builder failOnce(FaultPoint point, Throwable error) {
            return failOnInvocation(point, 1, error);
        }

        public Builder failAlways(FaultPoint point, Supplier<? extends Throwable> errorSupplier) {
            Objects.requireNonNull(errorSupplier, "errorSupplier");
            return add(point, ignored -> true,
                    () -> Mono.error(Objects.requireNonNull(errorSupplier.get(), "errorSupplier returned null")));
        }

        public Builder failOnInvocation(FaultPoint point, long invocation, Throwable error) {
            if (invocation < 1) {
                throw new IllegalArgumentException("invocation must be positive");
            }
            Objects.requireNonNull(error, "error");
            return add(point, value -> value == invocation, () -> Mono.error(error));
        }

        public Builder pauseOnce(FaultPoint point, Gate gate) {
            Objects.requireNonNull(gate, "gate");
            return add(point, value -> value == 1, gate::await);
        }

        public FaultPlan build() {
            Map<FaultPoint, List<Rule>> snapshot = new EnumMap<>(FaultPoint.class);
            rules.forEach((point, pointRules) -> snapshot.put(point, List.copyOf(pointRules)));
            return new FaultPlan(Map.copyOf(snapshot));
        }

        private Builder add(
                FaultPoint point,
                LongPredicate matches,
                Supplier<? extends Mono<Void>> action) {
            Objects.requireNonNull(point, "point");
            rules.computeIfAbsent(point, ignored -> new ArrayList<>())
                    .add(new Rule(matches, action));
            return this;
        }
    }

    private record Rule(LongPredicate matches, Supplier<? extends Mono<Void>> action) {
    }
}

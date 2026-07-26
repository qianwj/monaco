package cn.elvis.monaco.testkit.fault;

import cn.elvis.monaco.testkit.probe.EventProbe;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public final class Gate {

    private final AtomicInteger arrivals = new AtomicInteger();
    private final EventProbe<Integer> arrivalProbe;
    private final Sinks.One<Void> completion = Sinks.one();

    public Gate(String name) {
        arrivalProbe = new EventProbe<>(Objects.requireNonNull(name, "name") + " arrivals");
    }

    public Mono<Void> await() {
        return Mono.defer(() -> {
            int arrival = arrivals.incrementAndGet();
            arrivalProbe.record(arrival);
            return completion.asMono();
        });
    }

    public List<Integer> awaitArrivals(int count, Duration timeout) {
        return arrivalProbe.awaitCount(count, timeout);
    }

    public int arrivalCount() {
        return arrivals.get();
    }

    public boolean release() {
        return completion.tryEmitEmpty().isSuccess();
    }

    public boolean fail(Throwable error) {
        return completion.tryEmitError(Objects.requireNonNull(error, "error")).isSuccess();
    }

    public boolean cancel() {
        return fail(new CancellationException("gate cancelled"));
    }
}

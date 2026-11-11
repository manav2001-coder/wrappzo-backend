package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** A rush of uploads must not wait for each other one by one: the workers run side by side, up to the limit. */
class ImportJobDispatcherTest {

    private static ImportJobStore.Claimed job() {
        return new ImportJobStore.Claimed(UUID.randomUUID(), "recipients", UUID.randomUUID(), UUID.randomUUID(), "[]");
    }

    @Test
    void runsUpToTheWorkerLimitAtOnceAndNoMore() throws Exception {
        ImportJobStore store = mock(ImportJobStore.class);
        ImportJobService service = mock(ImportJobService.class);
        AtomicInteger waiting = new AtomicInteger(5);
        when(store.claimNext(anyInt(), any())).thenAnswer(call -> waiting.getAndDecrement() > 0 ? Optional.of(job()) : Optional.empty());
        CountDownLatch threeRunning = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        doAnswer(call -> {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
            threeRunning.countDown();
            release.await(5, TimeUnit.SECONDS);
            running.decrementAndGet();
            done.incrementAndGet();
            return null;
        }).when(service).execute(any());

        ImportJobDispatcher dispatcher = new ImportJobDispatcher(store, service, 3);
        dispatcher.poll();

        assertThat(threeRunning.await(5, TimeUnit.SECONDS)).as("three imports running at once").isTrue();
        release.countDown();
        long deadline = System.currentTimeMillis() + 5000;
        while (done.get() < 5 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(done.get()).as("every waiting import ran").isEqualTo(5);
        assertThat(mostAtOnce.get()).isEqualTo(3);
        dispatcher.stop();
    }
}

/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.flags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FeatureExposureQueueTest {

    private static final long FLUSH_INTERVAL_MS = FlagConstants.ExposureQueue.FLUSH_INTERVAL_MS;
    private static final int BATCH_SIZE = FlagConstants.ExposureQueue.BATCH_SIZE;

    private FlushRecorder flushRecorder;
    private ExposureQueueTestSupport.ManualFlushTimer manualFlushTimer;
    private FeatureExposureQueue queue;

    @Before
    public void setUp() {
        flushRecorder = new FlushRecorder();
        manualFlushTimer = new ExposureQueueTestSupport.ManualFlushTimer();
        queue =
                ExposureQueueTestSupport.createQueue(
                        flushRecorder,
                        TestCallerThreadExecutorService.INSTANCE,
                        manualFlushTimer,
                        FLUSH_INTERVAL_MS,
                        BATCH_SIZE);
    }

    @After
    public void tearDown() {
        queue.shutdown();
    }

    @Test
    public void enqueue_firstEvent_schedulesTimerWithConfiguredInterval() {
        enqueueUnique(1, 100L);

        assertTrue(manualFlushTimer.isScheduled());
        assertEquals(FLUSH_INTERVAL_MS, manualFlushTimer.getDelayMs());
        assertTrue(flushRecorder.flushes.isEmpty());
    }

    @Test
    public void timerFlush_dispatchesAggregatedPendingEvents() {
        enqueueUnique(1, 100L);
        enqueueUnique(2, 200L);
        enqueueUnique(3, 300L);

        manualFlushTimer.fire();

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(3, flushRecorder.flushes.get(0).size());
        assertFalse(
                "Timer should stop when the queue drains to idle", manualFlushTimer.isScheduled());
    }

    @Test
    public void batchSizeReached_flushesImmediatelyBeforeTimer() {
        for (int index = 1; index <= BATCH_SIZE; index++) {
            enqueueUnique(index, index * 10L);
        }

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(BATCH_SIZE, flushRecorder.flushes.get(0).size());
        assertFalse(
                "Timer should stop when the queue drains to idle", manualFlushTimer.isScheduled());
    }

    @Test
    public void twentyFiveUniqueAggregationKeys_flushesTwentyThenFive() {
        for (int index = 1; index <= 25; index++) {
            enqueueUnique(index, index * 10L);
        }

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(BATCH_SIZE, flushRecorder.flushes.get(0).size());

        queue.flush();

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(5, flushRecorder.flushes.get(1).size());
    }

    @Test
    public void duplicateAggregationKey_aggregatesDisplayCountAndLastTimestamp() {
        FeatureResult feature = standaloneFeature(173226, "checkout-flag", "10283012");
        String aggregationKey = "F-173226-10283012";

        for (int index = 1; index <= 40; index++) {
            queue.enqueue(aggregationKey, feature, index * 100L);
        }

        queue.flush();

        assertEquals(1, flushRecorder.flushes.size());
        AggregatedExposureEvent event = flushRecorder.flushes.get(0).get(0);
        assertEquals(aggregationKey, event.getAggregationKey());
        assertEquals(40, event.getDisplayCount());
        assertEquals(4000L, event.getLastEvaluatedAtMillis());
    }

    @Test
    public void featureGroupDistinctFeatures_sameVariant_aggregateSeparately() {
        FeatureResult featureA = featureGroupFeature(23261, 1, "feature-a", "10283012");
        FeatureResult featureB = featureGroupFeature(23261, 2, "feature-b", "10283012");

        queue.enqueue("FG-23261-1-10283012", featureA, 100L);
        queue.enqueue("FG-23261-2-10283012", featureB, 200L);
        queue.enqueue("FG-23261-1-10283012", featureA, 300L);
        queue.flush();

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(2, flushRecorder.flushes.get(0).size());

        AggregatedExposureEvent first = flushRecorder.flushes.get(0).get(0);
        AggregatedExposureEvent second = flushRecorder.flushes.get(0).get(1);

        if ("FG-23261-2-10283012".equals(first.getAggregationKey())) {
            AggregatedExposureEvent swap = first;
            first = second;
            second = swap;
        }

        assertEquals("FG-23261-1-10283012", first.getAggregationKey());
        assertEquals(2, first.getDisplayCount());
        assertEquals("feature-a", first.getFeature().getKey());
        assertEquals("FG-23261-2-10283012", second.getAggregationKey());
        assertEquals(1, second.getDisplayCount());
        assertEquals("feature-b", second.getFeature().getKey());
    }

    @Test
    public void repeatedEnqueue_sameAggregationKey_coalescesInboundBeforeDrain()
            throws InterruptedException {
        final java.util.concurrent.atomic.AtomicInteger executeCount =
                new java.util.concurrent.atomic.AtomicInteger();
        final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();
        final ExecutorService countingExecutor =
                new ForwardingExecutorService(asyncExecutor, executeCount);
        final FlushRecorder coalescingRecorder = new FlushRecorder();
        final FeatureExposureQueue coalescingQueue =
                ExposureQueueTestSupport.createQueue(
                        coalescingRecorder,
                        countingExecutor,
                        new ExposureQueueTestSupport.ManualFlushTimer(),
                        FLUSH_INTERVAL_MS,
                        BATCH_SIZE);
        try {
            final FeatureResult feature = standaloneFeature(173226, "checkout-flag", "10283012");
            final String aggregationKey = "F-173226-10283012";

            for (int index = 1; index <= 1000; index++) {
                coalescingQueue.enqueue(aggregationKey, feature, index * 100L);
            }

            assertTrue(
                    "Repeated enqueues should not post one executor task per evaluation",
                    executeCount.get() < 100);

            final CountDownLatch flushLatch = new CountDownLatch(1);
            coalescingRecorder.completionLatch = flushLatch;
            coalescingQueue.flush();
            assertTrue(flushLatch.await(2, TimeUnit.SECONDS));

            assertEquals(1, coalescingRecorder.flushes.size());
            assertEquals(1000, coalescingRecorder.flushes.get(0).get(0).getDisplayCount());
        } finally {
            coalescingQueue.shutdown();
            asyncExecutor.shutdown();
            assertTrue(asyncExecutor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void explicitFlushWithEmptyQueue_doesNotInvokeCallback() {
        queue.flush();

        assertTrue(flushRecorder.flushes.isEmpty());
    }

    @Test
    public void emptyAggregationKey_isIgnored() {
        FeatureResult feature = standaloneFeature(1, "feature-a", "10283012");

        queue.enqueue("", feature, 100L);
        queue.flush();

        assertTrue(flushRecorder.flushes.isEmpty());
    }

    @Test
    public void flushRequestedWhileFlushInProgress_coalescesIntoChainedFlush() {
        FeatureResult extraFeature = standaloneFeature(999, "extra-flag", "10283012");

        flushRecorder.onFlushAction =
                () -> {
                    if (flushRecorder.flushes.size() == 1) {
                        queue.enqueue("F-999-10283012", extraFeature, 999L);
                        queue.flush();
                    }
                };

        for (int index = 1; index <= BATCH_SIZE; index++) {
            enqueueUnique(index, index * 10L);
        }

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(BATCH_SIZE, flushRecorder.flushes.get(0).size());
        assertEquals(1, flushRecorder.flushes.get(1).size());
        assertEquals("F-999-10283012", flushRecorder.flushes.get(1).get(0).getAggregationKey());
    }

    @Test
    public void enqueueDuringFlush_isIncludedInNextFlushBatch() {
        FeatureResult duringFlushFeature = standaloneFeature(999, "during-flush", "10283012");

        flushRecorder.onFlushAction =
                () -> {
                    if (flushRecorder.flushes.size() == 1) {
                        queue.enqueue("F-999-10283012", duringFlushFeature, 999L);
                    }
                };

        enqueueUnique(1, 100L);
        queue.flush();

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(1, flushRecorder.flushes.get(0).size());
        assertEquals(1, flushRecorder.flushes.get(1).size());
        assertEquals("F-999-10283012", flushRecorder.flushes.get(1).get(0).getAggregationKey());
    }

    @Test
    public void afterFlush_sameAggregationKeyCanBeEnqueuedAgain() {
        FeatureResult feature = standaloneFeature(173226, "checkout-flag", "10283012");
        String aggregationKey = "F-173226-10283012";

        queue.enqueue(aggregationKey, feature, 100L);
        queue.flush();
        queue.enqueue(aggregationKey, feature, 200L);
        queue.flush();

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(1, flushRecorder.flushes.get(0).get(0).getDisplayCount());
        assertEquals(100L, flushRecorder.flushes.get(0).get(0).getLastEvaluatedAtMillis());
        assertEquals(1, flushRecorder.flushes.get(1).get(0).getDisplayCount());
        assertEquals(200L, flushRecorder.flushes.get(1).get(0).getLastEvaluatedAtMillis());
    }

    @Test
    public void batchFlush_doesNotKeepTimerRunningWhenQueueDrains() {
        enqueueUnique(1, 100L);
        assertTrue(manualFlushTimer.isScheduled());

        for (int index = 2; index <= BATCH_SIZE; index++) {
            enqueueUnique(index, index * 10L);
        }

        assertFalse(
                "Timer should stop when the queue drains to idle", manualFlushTimer.isScheduled());
    }

    @Test
    public void shutdown_returnsImmediatelyAndFlushesPendingEventsAsynchronously()
            throws InterruptedException {
        ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch flushLatch = new CountDownLatch(1);
        FlushRecorder asyncRecorder = new FlushRecorder();
        asyncRecorder.completionLatch = flushLatch;

        FeatureExposureQueue asyncQueue =
                ExposureQueueTestSupport.createQueue(
                        asyncRecorder,
                        backgroundExecutor,
                        new ExposureQueueTestSupport.NoOpFlushTimer(),
                        FLUSH_INTERVAL_MS,
                        BATCH_SIZE);

        asyncQueue.enqueue("F-1-10283012", standaloneFeature(1, "feature-a", "10283012"), 100L);

        long startNanos = System.nanoTime();
        asyncQueue.shutdown();
        long elapsedNanos = System.nanoTime() - startNanos;

        assertTrue(
                "shutdown() must not block the caller",
                elapsedNanos < TimeUnit.MILLISECONDS.toNanos(50));
        assertTrue(flushLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, asyncRecorder.flushes.size());
        assertEquals(1, asyncRecorder.flushes.get(0).size());

        backgroundExecutor.shutdown();
        assertTrue(backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    public void shutdown_rejectsSubsequentEnqueues() {
        queue.enqueue("F-1-10283012", standaloneFeature(1, "feature-a", "10283012"), 100L);
        queue.shutdown();
        queue.enqueue("F-2-10283012", standaloneFeature(2, "feature-b", "10283012"), 200L);
        queue.flush();

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(1, flushRecorder.flushes.get(0).size());
        assertEquals("F-1-10283012", flushRecorder.flushes.get(0).get(0).getAggregationKey());
    }

    @Test
    public void shutdown_isIdempotent_doubleCloseDoesNotThrow() throws Exception {
        final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
        final FeatureExposureQueue asyncQueue =
                ExposureQueueTestSupport.createQueue(
                        new FlushRecorder(),
                        backgroundExecutor,
                        new ExposureQueueTestSupport.NoOpFlushTimer(),
                        FLUSH_INTERVAL_MS,
                        BATCH_SIZE);

        asyncQueue.shutdown();
        asyncQueue.awaitShutdown(2, TimeUnit.SECONDS);

        try {
            asyncQueue.shutdown();
        } catch (Exception e) {
            fail("shutdown() must be idempotent but threw on second call: " + e);
        }
    }

    @Test
    public void flush_afterShutdown_isNoOp() {
        enqueueUnique(1, 100L);
        queue.shutdown();
        // flushes once during shutdown
        final int flushesDuringShutdown = flushRecorder.flushes.size();

        queue.flush();

        assertEquals(
                "flush() after shutdown must not trigger an additional flush",
                flushesDuringShutdown,
                flushRecorder.flushes.size());
    }

    @Test
    public void pausePeriodicFlush_afterShutdown_isNoOp() {
        queue.shutdown();
        try {
            queue.pausePeriodicFlush();
        } catch (Exception e) {
            fail("pausePeriodicFlush() after shutdown must not throw: " + e);
        }
    }

    @Test
    public void resumePeriodicFlush_afterShutdown_isNoOp() {
        queue.shutdown();
        try {
            queue.resumePeriodicFlush();
        } catch (Exception e) {
            fail("resumePeriodicFlush() after shutdown must not throw: " + e);
        }
    }

    /**
     * Exercises the queue's serialization guarantee with a real async executor and multiple
     * concurrent caller threads. With {@link TestCallerThreadExecutorService} the executor runs
     * tasks inline on the calling thread, so the single-thread invariant is never actually tested.
     * Here we use a genuine {@link java.util.concurrent.Executors#newSingleThreadExecutor()} and
     * race N threads, each enqueueing the same aggregation key multiple times. The displayCount on
     * the flushed event must equal the total number of enqueue calls — any lost update would be
     * caught immediately.
     */
    @Test
    public void concurrentEnqueues_withRealSingleThreadExecutor_noLostUpdates() throws Exception {
        final int threadCount = 4;
        final int enqueuesPerThread = 50;
        final CountDownLatch flushLatch = new CountDownLatch(1);
        final FlushRecorder asyncRecorder = new FlushRecorder();
        asyncRecorder.completionLatch = flushLatch;

        final ExecutorService singleThreadQueue = Executors.newSingleThreadExecutor();
        final FeatureExposureQueue asyncQueue =
                ExposureQueueTestSupport.createQueue(
                        asyncRecorder,
                        singleThreadQueue,
                        new ExposureQueueTestSupport.NoOpFlushTimer(),
                        FLUSH_INTERVAL_MS,
                        BATCH_SIZE);
        try {
            final FeatureResult feature = standaloneFeature(1, "feature-a", "10283012");
            final ExecutorService callerPool = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch startGate = new CountDownLatch(1);
            final CountDownLatch allEnqueued = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                callerPool.submit(
                        () -> {
                            try {
                                startGate.await();
                                for (int i = 0; i < enqueuesPerThread; i++) {
                                    asyncQueue.enqueue("F-1-10283012", feature, 100L);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                allEnqueued.countDown();
                            }
                        });
            }

            startGate.countDown();
            assertTrue("All enqueues must complete", allEnqueued.await(5, TimeUnit.SECONDS));
            callerPool.shutdown();

            asyncQueue.flush();

            assertTrue(
                    "Flush must complete after concurrent enqueues",
                    flushLatch.await(5, TimeUnit.SECONDS));

            // All threadCount × enqueuesPerThread display increments must survive serialization.
            assertEquals(1, asyncRecorder.flushes.size());
            assertEquals(1, asyncRecorder.flushes.get(0).size());
            assertEquals(
                    threadCount * enqueuesPerThread,
                    asyncRecorder.flushes.get(0).get(0).getDisplayCount());
        } finally {
            asyncQueue.shutdown();
            asyncQueue.awaitShutdown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void flushCallbackException_stillAllowsSubsequentFlush() {
        flushRecorder.failFirstFlush = true;

        enqueueUnique(1, 100L);
        queue.flush();
        enqueueUnique(2, 200L);
        queue.flush();

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals("F-2-10283012", flushRecorder.flushes.get(0).get(0).getAggregationKey());
    }

    @Test
    public void scheduledTimerFlush_firesAfterConfiguredDelay_withoutManualFlush()
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final FlushRecorder scheduledRecorder = new FlushRecorder();
        scheduledRecorder.completionLatch = latch;

        final FeatureExposureQueue scheduledQueue =
                ExposureQueueTestSupport.createWithScheduledTimer(
                        scheduledRecorder, 100L, BATCH_SIZE);
        try {
            enqueueOn(
                    scheduledQueue,
                    1,
                    100L,
                    "F-1-10283012",
                    standaloneFeature(1, "feature-1", "10283012"));
            enqueueOn(
                    scheduledQueue,
                    2,
                    200L,
                    "F-2-10283012",
                    standaloneFeature(2, "feature-2", "10283012"));

            assertTrue(
                    "Scheduled timer should flush pending events",
                    latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduledRecorder.flushes.size());
            assertEquals(2, scheduledRecorder.flushes.get(0).size());
        } finally {
            scheduledQueue.shutdown();
        }
    }

    @Test
    public void scheduledTimerFlush_waitsUntilIntervalWhenBatchNotFull() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final FlushRecorder scheduledRecorder = new FlushRecorder();
        scheduledRecorder.completionLatch = latch;

        final FeatureExposureQueue scheduledQueue =
                ExposureQueueTestSupport.createWithScheduledTimer(
                        scheduledRecorder, 150L, BATCH_SIZE);
        try {
            for (int index = 1; index <= 19; index++) {
                enqueueUniqueOn(scheduledQueue, index, index * 10L);
            }

            assertFalse(
                    "Batch threshold should not be reached with fewer than 20 unique aggregation"
                            + " keys IDs",
                    latch.await(50, TimeUnit.MILLISECONDS));
            assertTrue(
                    "Timer should flush the partial batch after the configured interval",
                    latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduledRecorder.flushes.size());
            assertEquals(19, scheduledRecorder.flushes.get(0).size());
        } finally {
            scheduledQueue.shutdown();
        }
    }

    @Test
    public void batchSizeReachedBeforeScheduledTimer_firesImmediately() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final FlushRecorder scheduledRecorder = new FlushRecorder();
        scheduledRecorder.completionLatch = latch;

        final FeatureExposureQueue scheduledQueue =
                ExposureQueueTestSupport.createWithScheduledTimer(
                        scheduledRecorder, 500L, BATCH_SIZE);
        try {
            for (int index = 1; index <= BATCH_SIZE; index++) {
                enqueueUniqueOn(scheduledQueue, index, index * 10L);
            }

            assertTrue(
                    "Batch-size flush should not wait for the scheduled timer",
                    latch.await(200, TimeUnit.MILLISECONDS));
            assertEquals(1, scheduledRecorder.flushes.size());
            assertEquals(BATCH_SIZE, scheduledRecorder.flushes.get(0).size());
        } finally {
            scheduledQueue.shutdown();
        }
    }

    @Test
    public void periodicTimer_restartsOnNewEnqueueAfterIdleFlush() {
        enqueueUnique(1, 100L);
        enqueueUnique(2, 200L);
        manualFlushTimer.fire();

        assertEquals(1, flushRecorder.flushes.size());
        assertEquals(2, flushRecorder.flushes.get(0).size());
        assertFalse(manualFlushTimer.isScheduled());

        enqueueUnique(3, 300L);
        assertTrue(manualFlushTimer.isScheduled());
        manualFlushTimer.fire();

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(1, flushRecorder.flushes.get(1).size());
        assertFalse(manualFlushTimer.isScheduled());
    }

    @Test
    public void idleTimerDoesNotRescheduleWithoutPendingWork() {
        enqueueUnique(1, 100L);
        manualFlushTimer.fire();

        assertEquals(1, flushRecorder.flushes.size());
        assertFalse(manualFlushTimer.isScheduled());

        manualFlushTimer.fire();

        assertEquals(1, flushRecorder.flushes.size());
        assertFalse(manualFlushTimer.isScheduled());

        enqueueUnique(2, 200L);
        assertTrue(manualFlushTimer.isScheduled());
        manualFlushTimer.fire();

        assertEquals(2, flushRecorder.flushes.size());
        assertEquals(1, flushRecorder.flushes.get(1).size());
    }

    @Test
    public void pausePeriodicFlush_cancelsScheduledTimer() {
        enqueueUnique(1, 100L);
        assertTrue(manualFlushTimer.isScheduled());

        queue.pausePeriodicFlush();

        assertFalse(manualFlushTimer.isScheduled());
    }

    @Test
    public void resumePeriodicFlush_restartsTimerWhenPendingWorkRemains() {
        enqueueUnique(1, 100L);
        queue.pausePeriodicFlush();
        assertFalse(manualFlushTimer.isScheduled());

        queue.resumePeriodicFlush();

        assertTrue(manualFlushTimer.isScheduled());
    }

    @Test
    public void resumePeriodicFlush_doesNotScheduleTimerWhenQueueIsIdle() {
        enqueueUnique(1, 100L);
        queue.flush();
        queue.pausePeriodicFlush();

        queue.resumePeriodicFlush();

        assertFalse(manualFlushTimer.isScheduled());
    }

    @Test
    public void scheduledPeriodicFlush_firesSecondWindowAfterReschedule() throws Exception {
        final FlushRecorder scheduledRecorder = new FlushRecorder();
        scheduledRecorder.completionLatch = new CountDownLatch(1);

        final FeatureExposureQueue scheduledQueue =
                ExposureQueueTestSupport.createWithScheduledTimer(
                        scheduledRecorder, 100L, BATCH_SIZE);
        try {
            enqueueOn(
                    scheduledQueue,
                    1,
                    100L,
                    "F-1-10283012",
                    standaloneFeature(1, "feature-1", "10283012"));

            assertTrue(
                    "First periodic flush should fire",
                    scheduledRecorder.completionLatch.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduledRecorder.flushes.size());

            scheduledRecorder.completionLatch = new CountDownLatch(1);
            enqueueOn(
                    scheduledQueue,
                    2,
                    200L,
                    "F-2-10283012",
                    standaloneFeature(2, "feature-2", "10283012"));

            assertTrue(
                    "Second periodic flush should fire after a new enqueue restarts the timer",
                    scheduledRecorder.completionLatch.await(2, TimeUnit.SECONDS));
            assertEquals(2, scheduledRecorder.flushes.size());
            assertEquals(1, scheduledRecorder.flushes.get(1).size());
        } finally {
            scheduledQueue.shutdown();
        }
    }

    @Test
    public void constructor_invalidBatchSize_rejects() {
        try {
            ExposureQueueTestSupport.createQueue(
                    new FlushRecorder(),
                    TestCallerThreadExecutorService.INSTANCE,
                    new ExposureQueueTestSupport.ManualFlushTimer(),
                    FLUSH_INTERVAL_MS,
                    0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalStateException e) {
            assertTrue(hasCause(e, IllegalArgumentException.class));
        }
    }

    @Test
    public void constructor_invalidFlushInterval_rejects() {
        try {
            ExposureQueueTestSupport.createQueue(
                    new FlushRecorder(),
                    TestCallerThreadExecutorService.INSTANCE,
                    new ExposureQueueTestSupport.ManualFlushTimer(),
                    0L,
                    BATCH_SIZE);
            fail("Expected IllegalArgumentException");
        } catch (IllegalStateException e) {
            assertTrue(hasCause(e, IllegalArgumentException.class));
        }
    }

    private static boolean hasCause(
            @NonNull final Throwable throwable, @NonNull final Class<?> causeType) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (causeType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private void enqueueUnique(final int featureId, final long evaluatedAtMillis) {
        queue.enqueue(
                "F-" + featureId + "-10283012",
                standaloneFeature(featureId, "feature-" + featureId, "10283012"),
                evaluatedAtMillis);
    }

    private static void enqueueUniqueOn(
            final FeatureExposureQueue targetQueue,
            final int featureId,
            final long evaluatedAtMillis) {
        enqueueOn(
                targetQueue,
                featureId,
                evaluatedAtMillis,
                "F-" + featureId + "-10283012",
                standaloneFeature(featureId, "feature-" + featureId, "10283012"));
    }

    private static void enqueueOn(
            final FeatureExposureQueue targetQueue,
            final int featureId,
            final long evaluatedAtMillis,
            final String aggregationKey,
            final FeatureResult feature) {
        targetQueue.enqueue(aggregationKey, feature, evaluatedAtMillis);
    }

    private static FeatureResult featureGroupFeature(
            final int featureGroupId,
            final int featureId,
            final String featureKey,
            final String variantId) {
        AnalyticsParam analytics =
                new AnalyticsParam(featureGroupId, featureId, featureKey, variantId);
        return new FeatureResult(featureId, featureKey, "fg-group", null, null, analytics);
    }

    private static FeatureResult standaloneFeature(
            final int featureId, final String featureKey, final String variantId) {
        AnalyticsParam analytics = new AnalyticsParam(-1, featureId, featureKey, variantId);
        return new FeatureResult(
                featureId,
                featureKey,
                FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY,
                null,
                null,
                analytics);
    }

    private static final class FlushRecorder implements ExposureFlushCallback {

        final List<List<AggregatedExposureEvent>> flushes = new ArrayList<>();

        Runnable onFlushAction;

        CountDownLatch completionLatch;

        boolean failFirstFlush;
        boolean failedOnce;

        @Override
        public void onFlush(final List<AggregatedExposureEvent> events) {
            if (failFirstFlush && !failedOnce) {
                failedOnce = true;
                throw new RuntimeException("simulated flush failure");
            }

            flushes.add(new ArrayList<>(events));
            if (onFlushAction != null) {
                onFlushAction.run();
            }
            if (completionLatch != null) {
                completionLatch.countDown();
            }
        }
    }

    private static final class ForwardingExecutorService
            extends java.util.concurrent.AbstractExecutorService {

        @NonNull private final ExecutorService delegate;

        @NonNull private final java.util.concurrent.atomic.AtomicInteger executeCount;

        ForwardingExecutorService(
                @NonNull final ExecutorService delegate,
                @NonNull final java.util.concurrent.atomic.AtomicInteger executeCount) {
            this.delegate = delegate;
            this.executeCount = executeCount;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit)
                throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(final Runnable command) {
            executeCount.incrementAndGet();
            delegate.execute(command);
        }
    }
}

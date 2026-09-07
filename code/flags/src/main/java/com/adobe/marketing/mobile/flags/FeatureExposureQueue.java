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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.services.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Batches feature exposure events by {@code aggregationKey} and flushes on batch size, periodic
 * interval, explicit request, lifecycle pause, or shutdown.
 *
 * <p>A one-shot {@code flushIntervalMs} timer starts when the first event lands in a non-empty
 * pending window and is cancelled when the queue drains to idle. The timer does not reschedule on
 * empty ticks, so the process is not woken periodically while idle. The next enqueue restarts the
 * clock. While the app is backgrounded, the timer is paused and restarted on foreground when work
 * remains pending.
 *
 * <p>All mutable state is owned by a single background thread. Callers coalesce duplicate {@code
 * aggregationKey} values on an inbound map before posting drain work, so high evaluation volume
 * does not grow the executor task queue without bound.
 *
 * <p><strong>Executor task queue depth:</strong> {@link #scheduleDrain()} is CAS-protected, so at
 * most one drain task is pending at any time. Lifecycle calls ({@link #flush}, {@link
 * #pausePeriodicFlush}, {@link #resumePeriodicFlush}) do not apply the same guard and assume
 * single-caller semantics (one lifecycle event at a time from the MobileCore dispatcher). Callers
 * must not issue those methods in tight loops.
 */
final class FeatureExposureQueue {

    private static final String SELF_TAG = "FeatureExposureQueue";

    @NonNull private final ExposureFlushCallback flushCallback;

    @NonNull private final ExecutorService queueExecutor;

    @NonNull private final FlushTimer flushTimer;

    private final long flushIntervalMs;

    private final int batchSize;

    /**
     * Caller-thread staging area keyed by {@code aggregationKey}. Drained onto {@link #pending} by
     * the queue executor.
     */
    @NonNull private final ConcurrentHashMap<String, AggregatedEntry> inbound = new ConcurrentHashMap<>();

    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

    /** Preserves insertion order for deterministic flush ordering. */
    @NonNull private final LinkedHashMap<String, AggregatedEntry> pending = new LinkedHashMap<>();

    private volatile boolean flushing;

    private volatile boolean flushPending;

    private volatile boolean shuttingDown;

    /**
     * Set to {@code true} while the app is backgrounded. Prevents the flush timer from being
     * re-armed (e.g. by {@link #drainInbound()} or a completed flush) while the device should not
     * be doing background work. Cleared and the timer restarted when the app returns to foreground
     * via {@link #resumePeriodicFlush()}.
     */
    private volatile boolean timerPaused;

    FeatureExposureQueue(@NonNull final ExposureFlushCallback flushCallback) {
        this(
                flushCallback,
                createDefaultExecutor(),
                FlagConstants.ExposureQueue.FLUSH_INTERVAL_MS,
                FlagConstants.ExposureQueue.BATCH_SIZE);
    }

    private FeatureExposureQueue(
            @NonNull final ExposureFlushCallback flushCallback,
            @NonNull final ScheduledExecutorService scheduledExecutor,
            final long flushIntervalMs,
            final int batchSize) {
        this(
                flushCallback,
                scheduledExecutor,
                new ScheduledFlushTimer(scheduledExecutor),
                flushIntervalMs,
                batchSize);
    }

    @NonNull private static ScheduledExecutorService createDefaultExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                r -> {
                    final Thread thread = new Thread(r, "FlagExposure");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private FeatureExposureQueue(
            @NonNull final ExposureFlushCallback flushCallback,
            @NonNull final ExecutorService queueExecutor,
            @NonNull final FlushTimer flushTimer,
            final long flushIntervalMs,
            final int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        if (flushIntervalMs <= 0) {
            throw new IllegalArgumentException("flushIntervalMs must be > 0");
        }

        this.flushCallback = flushCallback;
        this.queueExecutor = queueExecutor;
        this.flushTimer = flushTimer;
        this.flushIntervalMs = flushIntervalMs;
        this.batchSize = batchSize;
    }

    /**
     * Queues a feature exposure for batched Edge dispatch.
     *
     * <p>Duplicate {@code aggregationKey} values within the same flush window increment {@code
     * displayCount} and refresh {@code evaluatedAtMillis} to the latest evaluation time.
     *
     * @param aggregationKey deduplication key; must not be null or empty
     * @param feature evaluated feature snapshot
     * @param evaluatedAtMillis evaluation timestamp in epoch milliseconds
     */
    void enqueue(
            @NonNull final String aggregationKey,
            @NonNull final FeatureResult feature,
            final long evaluatedAtMillis) {
        if (aggregationKey.isEmpty() || shuttingDown) {
            return;
        }

        mergeInbound(aggregationKey, feature, evaluatedAtMillis);

        scheduleDrain();
    }

    private void mergeInbound(
            @NonNull final String aggregationKey,
            @NonNull final FeatureResult feature,
            final long evaluatedAtMillis) {
        while (true) {
            final AggregatedEntry existing = inbound.get(aggregationKey);
            if (existing != null) {
                synchronized (existing) {
                    if (inbound.get(aggregationKey) == existing) {
                        existing.displayCount++;
                        existing.lastEvaluatedAtMillis = evaluatedAtMillis;
                        return;
                    }
                }
                continue;
            }

            final AggregatedEntry entry = new AggregatedEntry();
            entry.aggregationKey = aggregationKey;
            entry.feature = feature;
            entry.displayCount = 1;
            entry.lastEvaluatedAtMillis = evaluatedAtMillis;

            final AggregatedEntry prior = inbound.putIfAbsent(aggregationKey, entry);
            if (prior == null) {
                return;
            }
        }
    }

    /** Requests an immediate flush if the queue has pending events. */
    void flush() {
        if (shuttingDown) {
            return;
        }
        try {
            queueExecutor.execute(
                    () -> {
                        drainInboundToPending();
                        doFlush();
                    });
        } catch (RejectedExecutionException ignored) {
            // Executor terminated concurrently with this call; queue is shutting down.
        }
    }

    /** Cancels the periodic flush timer while the app is backgrounded. */
    void pausePeriodicFlush() {
        if (shuttingDown) {
            return;
        }
        try {
            queueExecutor.execute(
                    () -> {
                        timerPaused = true;
                        flushTimer.cancel();
                    });
        } catch (RejectedExecutionException ignored) {
            // Executor terminated concurrently with this call; queue is shutting down.
        }
    }

    /**
     * Restarts the periodic flush timer on foreground when pending work remains and the batch
     * threshold has not been reached.
     */
    void resumePeriodicFlush() {
        if (shuttingDown) {
            return;
        }
        try {
            queueExecutor.execute(
                    () -> {
                        timerPaused = false;
                        drainInboundToPending();
                        flushEventsIfRequired();
                    });
        } catch (RejectedExecutionException ignored) {
            // Executor terminated concurrently with this call; queue is shutting down.
        }
    }

    /**
     * Shuts down the queue asynchronously without blocking the caller.
     *
     * <p>Sets {@code shuttingDown} immediately on the calling thread so subsequent {@link #enqueue}
     * and public lifecycle calls are rejected without reaching the executor. Then posts the final
     * drain-and-flush task; if the executor was already terminated (e.g. double-close), the {@link
     * java.util.concurrent.RejectedExecutionException} is silently ignored because there is nothing
     * left to flush.
     */
    void shutdown() {
        shuttingDown = true;
        try {
            queueExecutor.execute(this::doShutdown);
        } catch (RejectedExecutionException ignored) {
            // Executor already terminated; nothing left to flush.
        }
    }

    /**
     * Blocks until the queue executor has finished the terminal shutdown flush, or the timeout
     * elapses.
     */
    boolean awaitShutdown(final long timeout, @NonNull final TimeUnit unit)
            throws InterruptedException {
        return queueExecutor.awaitTermination(timeout, unit);
    }

    private void scheduleDrain() {
        if (shuttingDown) {
            return;
        }

        if (drainScheduled.compareAndSet(false, true)) {
            try {
                queueExecutor.execute(this::drainInbound);
            } catch (RejectedExecutionException ignored) {
                // Executor terminated between the shuttingDown check and this execute call.
                drainScheduled.set(false);
            }
        }
    }

    private void drainInbound() {
        try {
            if (!shuttingDown) {
                drainInboundToPending();
                flushEventsIfRequired();
            }
        } finally {
            drainScheduled.set(false);
            if (!shuttingDown && !inbound.isEmpty() && drainScheduled.compareAndSet(false, true)) {
                queueExecutor.execute(this::drainInbound);
            }
        }
    }

    private void drainInboundToPending() {
        for (final String aggregationKey : new ArrayList<>(inbound.keySet())) {
            final AggregatedEntry inboundEntry = inbound.remove(aggregationKey);
            if (inboundEntry != null) {
                mergeIntoPending(inboundEntry);
            }
        }
    }

    private void mergeIntoPending(@NonNull final AggregatedEntry inboundEntry) {
        final AggregatedEntry existing = pending.get(inboundEntry.aggregationKey);
        if (existing != null) {
            existing.displayCount += inboundEntry.displayCount;
            existing.lastEvaluatedAtMillis = inboundEntry.lastEvaluatedAtMillis;
            return;
        }

        pending.put(inboundEntry.aggregationKey, inboundEntry);
    }

    private void flushEventsIfRequired() {
        if (pending.isEmpty()) {
            return;
        }

        if (pending.size() >= batchSize) {
            doFlush();
        } else if (!flushing && !timerPaused && !flushTimer.isScheduled()) {
            scheduleTimer();
        }
    }

    private void scheduleTimer() {
        if (timerPaused || shuttingDown || pending.isEmpty()) {
            return;
        }

        flushTimer.schedule(
                flushIntervalMs,
                () -> {
                    if (!shuttingDown) {
                        drainInboundToPending();
                        doFlush();
                    }
                });
    }

    /**
     * Flushes all pending entries in a loop until no more work arrives.
     *
     * <p><strong>Delivery policy — at-most-once:</strong> {@code pending} is cleared
     * <em>before</em> invoking the callback so that items are never dispatched twice even if the
     * callback throws. A failed batch is logged and discarded; there is no retry or dead-letter
     * queue. This is intentional for analytics data where duplicate events are more harmful than
     * rare loss.
     *
     * <p><strong>Exception handling layers:</strong> The inner {@code try/catch} guards against
     * misbehaving {@link ExposureFlushCallback} implementations (including test doubles). The
     * callback itself ({@link FlagEdgeHandler#dispatchExposureEvents}) also isolates per-event
     * failures so a single bad event never aborts the rest of the batch. Both layers serve
     * different isolation concerns and are not redundant.
     */
    private void doFlush() {
        if (flushing) {
            flushPending = true;
            return;
        }

        if (pending.isEmpty()) {
            return;
        }

        flushing = true;
        flushTimer.cancel();
        try {
            do {
                flushPending = false;
                // At-most-once: clear before dispatch. If onFlush throws, items are gone.
                final List<AggregatedEntry> batch = new ArrayList<>(pending.values());
                pending.clear();
                final List<AggregatedExposureEvent> events = toAggregatedExposureEvents(batch);
                try {
                    flushCallback.onFlush(events);
                } catch (Exception e) {
                    Log.warning(
                            FlagConstants.LOG_TAG,
                            SELF_TAG,
                            "Exposure flush callback failed: %s",
                            e.getLocalizedMessage());
                }
                // Continue if a concurrent flush() or enqueue() added new work while the
                // callback ran (flushPending) or if drainInbound() moved items into pending.
            } while (flushPending || !pending.isEmpty());
        } finally {
            flushing = false;
        }
    }

    private void doShutdown() {
        shuttingDown = true;
        flushTimer.cancel();
        drainInboundToPending();
        doFlush();
        queueExecutor.shutdown();
    }

    @NonNull private static List<AggregatedExposureEvent> toAggregatedExposureEvents(
            @NonNull final List<AggregatedEntry> batch) {
        final List<AggregatedExposureEvent> events = new ArrayList<>(batch.size());
        for (final AggregatedEntry entry : batch) {
            events.add(
                    new AggregatedExposureEvent(
                            entry.aggregationKey,
                            entry.feature,
                            entry.lastEvaluatedAtMillis,
                            entry.displayCount));
        }
        return events;
    }

    /** Testable contract for scheduling one-shot delayed flush tasks. */
    interface FlushTimer {
        void schedule(long delayMs, @NonNull Runnable task);

        void cancel();

        boolean isScheduled();
    }

    private static final class ScheduledFlushTimer implements FlushTimer {

        @NonNull private final ScheduledExecutorService scheduler;

        @Nullable private volatile java.util.concurrent.ScheduledFuture<?> scheduledFuture;

        ScheduledFlushTimer(@NonNull final ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void schedule(final long delayMs, @NonNull final Runnable task) {
            cancel();
            scheduledFuture = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void cancel() {
            final java.util.concurrent.ScheduledFuture<?> future = scheduledFuture;
            if (future != null) {
                future.cancel(false);
                scheduledFuture = null;
            }
        }

        @Override
        public boolean isScheduled() {
            final java.util.concurrent.ScheduledFuture<?> future = scheduledFuture;
            return future != null && !future.isDone();
        }
    }

    private static final class AggregatedEntry {
        String aggregationKey;
        FeatureResult feature;
        int displayCount;
        long lastEvaluatedAtMillis;
    }
}

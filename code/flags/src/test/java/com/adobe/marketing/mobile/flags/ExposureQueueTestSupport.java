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
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Test factories and timer doubles for {@link FeatureExposureQueue}. */
final class ExposureQueueTestSupport {

    private ExposureQueueTestSupport() {}

    @NonNull static FeatureExposureQueue createDeterministicQueue(
            @NonNull final ExposureFlushCallback flushCallback) {
        return newQueue(
                flushCallback,
                TestCallerThreadExecutorService.INSTANCE,
                new NoOpFlushTimer(),
                FlagConstants.ExposureQueue.FLUSH_INTERVAL_MS,
                FlagConstants.ExposureQueue.BATCH_SIZE);
    }

    @NonNull static FeatureExposureQueue createWithScheduledTimer(
            @NonNull final ExposureFlushCallback flushCallback,
            final long flushIntervalMs,
            final int batchSize) {
        final ScheduledExecutorService scheduledExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            final Thread thread = new Thread(r, "FlagExposure-test");
                            thread.setDaemon(true);
                            return thread;
                        });
        return newQueue(
                flushCallback,
                scheduledExecutor,
                new ScheduledFlushTimer(scheduledExecutor),
                flushIntervalMs,
                batchSize);
    }

    @NonNull static FeatureExposureQueue createQueue(
            @NonNull final ExposureFlushCallback flushCallback,
            @NonNull final ExecutorService queueExecutor,
            @NonNull final FeatureExposureQueue.FlushTimer flushTimer,
            final long flushIntervalMs,
            final int batchSize) {
        return newQueue(flushCallback, queueExecutor, flushTimer, flushIntervalMs, batchSize);
    }

    @NonNull private static FeatureExposureQueue newQueue(
            @NonNull final ExposureFlushCallback flushCallback,
            @NonNull final ExecutorService queueExecutor,
            @NonNull final FeatureExposureQueue.FlushTimer flushTimer,
            final long flushIntervalMs,
            final int batchSize) {
        try {
            final Constructor<FeatureExposureQueue> constructor =
                    FeatureExposureQueue.class.getDeclaredConstructor(
                            ExposureFlushCallback.class,
                            ExecutorService.class,
                            FeatureExposureQueue.FlushTimer.class,
                            long.class,
                            int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    flushCallback, queueExecutor, flushTimer, flushIntervalMs, batchSize);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to construct FeatureExposureQueue for tests", e);
        }
    }

    static final class NoOpFlushTimer implements FeatureExposureQueue.FlushTimer {

        @Override
        public void schedule(final long delayMs, @NonNull final Runnable task) {}

        @Override
        public void cancel() {}

        @Override
        public boolean isScheduled() {
            return false;
        }
    }

    static final class ManualFlushTimer implements FeatureExposureQueue.FlushTimer {

        private Runnable task;
        private long delayMs;

        @Override
        public void schedule(final long delayMs, @NonNull final Runnable task) {
            this.delayMs = delayMs;
            this.task = task;
        }

        @Override
        public void cancel() {
            task = null;
            delayMs = 0L;
        }

        void fire() {
            if (task != null) {
                final Runnable runnable = task;
                task = null;
                runnable.run();
            }
        }

        @Override
        public boolean isScheduled() {
            return task != null;
        }

        long getDelayMs() {
            return delayMs;
        }
    }

    private static final class ScheduledFlushTimer implements FeatureExposureQueue.FlushTimer {

        @NonNull private final ScheduledExecutorService scheduler;

        @androidx.annotation.Nullable private volatile java.util.concurrent.ScheduledFuture<?> scheduledFuture;

        ScheduledFlushTimer(@NonNull final ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void schedule(final long delayMs, @NonNull final Runnable task) {
            cancel();
            scheduledFuture =
                    scheduler.schedule(task, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
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
}

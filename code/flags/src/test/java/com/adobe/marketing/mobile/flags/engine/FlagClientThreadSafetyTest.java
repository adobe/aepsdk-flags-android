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

package com.adobe.marketing.mobile.flags.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

/**
 * Thread-safety tests for {@link FlagClient} as a multi-instance, {@link AutoCloseable} client.
 *
 * <p>Uses reflection to bypass network-dependent initialization, isolating the concurrency
 * behaviour of the client lifecycle.
 */
class FlagClientThreadSafetyTest {

    private static FlagClient newStubClient() throws Exception {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain("example.com")
                        .imsOrg("test-org")
                        .sandboxName("test-sandbox")
                        .clientId("thread-test")
                        .build();

        Constructor<FlagClient> ctor =
                FlagClient.class.getDeclaredConstructor(FlagConfiguration.class);
        ctor.setAccessible(true);
        FlagClient client = ctor.newInstance(config);

        Field initField = FlagClient.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        ((AtomicBoolean) initField.get(client)).set(true);

        return client;
    }

    @Nested
    @DisplayName("Concurrent close()")
    class ConcurrentCloseTests {

        @Test
        @DisplayName("Multiple threads calling close() do not throw or corrupt state")
        void concurrentCloseIsIdempotent() throws Exception {
            FlagClient client = newStubClient();
            assertTrue(client.isInitialized());

            int threadCount = 32;
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < threadCount; i++) {
                new Thread(
                                () -> {
                                    try {
                                        gate.await();
                                        client.close();
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    } finally {
                                        done.countDown();
                                    }
                                })
                        .start();
            }

            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
            assertFalse(client.isInitialized());
        }

        @Test
        @DisplayName("close() is visible to isInitialized() across threads")
        void closeVisibleToReaders() throws Exception {
            FlagClient client = newStubClient();

            int readerCount = 16;
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch readersStarted = new CountDownLatch(readerCount);
            CountDownLatch done = new CountDownLatch(readerCount + 1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < readerCount; i++) {
                new Thread(
                                () -> {
                                    try {
                                        gate.await();
                                        readersStarted.countDown();
                                        for (int j = 0; j < 5000; j++) {
                                            client.isInitialized();
                                        }
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    } finally {
                                        done.countDown();
                                    }
                                })
                        .start();
            }

            new Thread(
                            () -> {
                                try {
                                    gate.await();
                                    readersStarted.await(2, TimeUnit.SECONDS);
                                    client.close();
                                } catch (Throwable t) {
                                    failure.compareAndSet(null, t);
                                } finally {
                                    done.countDown();
                                }
                            })
                    .start();

            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
            assertFalse(client.isInitialized());
        }
    }

    @Nested
    @DisplayName("Concurrent evaluation on the same client")
    class ConcurrentEvaluationTests {

        @Test
        @DisplayName("Operations after close() throw FlagClientException, not NPE or other errors")
        void evaluationAfterCloseThrowsCleanly() throws Exception {
            FlagClient client = newStubClient();
            client.close();

            int threadCount = 16;
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < threadCount; i++) {
                new Thread(
                                () -> {
                                    try {
                                        gate.await();
                                        for (int j = 0; j < 100; j++) {
                                            try {
                                                client.isFeatureEnabled("any-flag", null);
                                                failure.compareAndSet(
                                                        null,
                                                        new AssertionError(
                                                                "Should have thrown"
                                                                        + " FlagClientException"));
                                            } catch (FlagClientException expected) {
                                                // correct behaviour
                                            }
                                        }
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    } finally {
                                        done.countDown();
                                    }
                                })
                        .start();
            }

            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
        }
    }

    @Nested
    @DisplayName("Multiple independent instances")
    class MultiInstanceTests {

        @Test
        @DisplayName("Two clients can be created independently")
        void twoIndependentInstances() throws Exception {
            FlagClient clientA = newStubClient();
            FlagClient clientB = newStubClient();

            assertNotSame(clientA, clientB);
            assertTrue(clientA.isInitialized());
            assertTrue(clientB.isInitialized());

            clientA.close();
            assertFalse(clientA.isInitialized());
            assertTrue(clientB.isInitialized(), "Closing A must not affect B");

            clientB.close();
            assertFalse(clientB.isInitialized());
        }

        @Test
        @DisplayName("Closing one client under contention does not affect another")
        void concurrentCloseOneClientDoesNotAffectOther() throws Exception {
            FlagClient clientA = newStubClient();
            FlagClient clientB = newStubClient();

            int threadCount = 16;
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < threadCount; i++) {
                final boolean closeA = (i % 2 == 0);
                new Thread(
                                () -> {
                                    try {
                                        gate.await();
                                        if (closeA) {
                                            clientA.close();
                                        } else {
                                            for (int j = 0; j < 500; j++) {
                                                assertTrue(
                                                        clientB.isInitialized(),
                                                        "Client B must stay initialized");
                                            }
                                        }
                                    } catch (Throwable t) {
                                        failure.compareAndSet(null, t);
                                    } finally {
                                        done.countDown();
                                    }
                                })
                        .start();
            }

            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
            assertFalse(clientA.isInitialized());
            assertTrue(clientB.isInitialized());

            clientB.close();
        }
    }
}

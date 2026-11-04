package org.example;

import org.example.service.ServiceRegistry;
import org.example.service.ServiceRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistryTest {

    private ServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        serviceRegistry = new ServiceRegistryImpl();
    }

    @Test
    void shouldRegisterAddress() {
        String url = "http://10.0.0.1:8080";
        serviceRegistry.register(url);
        assertEquals(url, serviceRegistry.get());
    }

    @Test
    void shouldThrowExceptionWhenSameAddressRegistered() {
        String url = "http://10.0.0.1:8080";
        serviceRegistry.register(url);
        assertThrows(IllegalArgumentException.class, () -> serviceRegistry.register(url));
    }

    @Test
    void shouldThrowUnnormalizedAddress() {
        serviceRegistry.register("http://10.0.0.1:8080");
        String unnormalized = "      http://10.0.0.1:8080      ";
        assertThrows(IllegalArgumentException.class, () -> serviceRegistry.register(unnormalized));
    }

    @Test
    void shouldThrowExceptionWhenRegistryLimitExceeded() {
        String url = "http://10.0.0.1:";
        int port = 8080;
        for (int i = 0; i < 10; i++) {
            serviceRegistry.register(url.concat(String.valueOf(port++)));
        }
        assertThrows(IllegalStateException.class, () -> serviceRegistry.register("http://10.0.0.1:9090"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "         "})
    void shouldFailIfEmptyStringSent(String str) {
        assertThrows(IllegalArgumentException.class, () -> serviceRegistry.register(str));
    }

    @Test
    void shouldFailIfNullSent() {
        assertThrows(IllegalArgumentException.class, () -> serviceRegistry.register(null));
    }

    @Test
    void shouldThrowExceptionWhenRegistryIsEmpty() {
        assertThrows(IllegalStateException.class, () -> serviceRegistry.get());
    }

    @Test
    void shouldReturnInstanceInOrder() {
        String url1 = "http://10.0.0.1:8080";
        String url2 = "http://10.0.0.1:8081";
        String url3 = "http://10.0.0.1:8082";
        serviceRegistry.register(url1);
        serviceRegistry.register(url2);
        serviceRegistry.register(url3);

        assertEquals(url1, serviceRegistry.get());
        assertEquals(url2, serviceRegistry.get());
        assertEquals(url3, serviceRegistry.get());
        assertEquals(url1, serviceRegistry.get());
    }


    @Test
    void shouldThrowExceptionWhenRegistryIsEmptyRandom() {
        assertThrows(IllegalStateException.class, () -> serviceRegistry.getRandom());
    }

    @Test
    void shouldReturnRandomInstance() {
        String url1 = "http://10.0.0.1:8080";
        String url2 = "http://10.0.0.1:8081";
        String url3 = "http://10.0.0.1:8082";
        serviceRegistry.register(url1);
        serviceRegistry.register(url2);
        serviceRegistry.register(url3);

        for (int i = 0; i < 100; i++) {
            assertTrue(Arrays.asList(url1, url2, url3).contains(serviceRegistry.getRandom()));
        }
    }

    //----------MultiThread----------
    @RepeatedTest(200)
    void shouldNotRegisterSameAddressTwice() throws InterruptedException {
        AtomicInteger exceptionCounter = new AtomicInteger(0);
        AtomicInteger successfulRegistryCounter = new AtomicInteger(0);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();                       // herkes burada bekler
                    serviceRegistry.register("http://10.0.0.1:8080");
                    successfulRegistryCounter.getAndIncrement();
                } catch (IllegalArgumentException iae) {
                    exceptionCounter.getAndIncrement();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();      // 100 thread de hazır
        start.countDown();  // hepsini aynı anda bırak
        done.await();       // hepsi bitsin
        executorService.shutdown();

        assertAll(
                () -> assertEquals(100 - 1, exceptionCounter.get()),
                () -> assertEquals(1, successfulRegistryCounter.get())
        );
    }

    @RepeatedTest(200)
    void shouldNotRegisterExceedingLimit() throws InterruptedException {
        AtomicInteger exceptionCounter = new AtomicInteger(0);
        AtomicInteger successfulRegistryCounter = new AtomicInteger(0);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int portCounter = i;
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    String url = "http://10.0.0.1:";
                    int port = 8080;
                    serviceRegistry.register(url.concat(String.valueOf(port + portCounter)));
                    successfulRegistryCounter.getAndIncrement();
                } catch (IllegalStateException ise) {
                    exceptionCounter.getAndIncrement();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();      // 100 thread de hazır
        start.countDown();  // hepsini aynı anda bırak
        done.await();       // hepsi bitsin
        executorService.shutdown();

        assertAll(
                () -> assertEquals(100 - 10, exceptionCounter.get()),
                () -> assertEquals(10, successfulRegistryCounter.get())
        );
    }

    @RepeatedTest(200)
    void shouldNotAllowConcurrentModification() throws InterruptedException {
        AtomicInteger concurrentExceptionCounter = new AtomicInteger(0);
        AtomicInteger illegalStateExceptionCounter = new AtomicInteger(0);
        AtomicInteger successfulGetCounter = new AtomicInteger(0);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        serviceRegistry.register("http://10.0.0.1:8078");
        serviceRegistry.register("http://10.0.0.1:8079");

        for (int i = 0; i < threadCount; i++) {
            final int portCounter = i;
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    String url = "http://10.0.0.1:";
                    int port = 8080;
                    if (portCounter % 2 == 0) {
                        serviceRegistry.register(url.concat(String.valueOf(port + portCounter)));
                    } else {
                        for (int j = 0; j < 1000; j++) {
                            serviceRegistry.get();
                        }
                    }
                    successfulGetCounter.getAndIncrement();
                } catch (ConcurrentModificationException ise) {
                    concurrentExceptionCounter.getAndIncrement();
                } catch (IllegalStateException ise) {
                    if (portCounter % 2 == 0) {
                        illegalStateExceptionCounter.getAndIncrement();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();      // 100 thread de hazır
        start.countDown();  // hepsini aynı anda bırak
        done.await();       // hepsi bitsin
        executorService.shutdown();

        assertAll(
                () -> assertEquals(0, concurrentExceptionCounter.get())
        );
    }

}

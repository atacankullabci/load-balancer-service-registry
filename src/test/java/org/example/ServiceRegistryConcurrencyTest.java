package org.example;

import org.example.service.ServiceRegistry;
import org.example.service.ServiceRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceRegistryConcurrencyTest {

    private ServiceRegistry serviceRegistry;

    @BeforeEach
    public void setUp() {
        serviceRegistry = new ServiceRegistryImpl();
    }

    private Runnable registerRunnable(String address) {
        return () -> serviceRegistry.register(address);
    }

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
        done.await(5, TimeUnit.SECONDS);       // hepsi bitsin
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
        done.await(5, TimeUnit.SECONDS);       // hepsi bitsin
        executorService.shutdown();

        assertAll(
                () -> assertEquals(100 - 10, exceptionCounter.get()),
                () -> assertEquals(10, successfulRegistryCounter.get())
        );
    }

    @RepeatedTest(200)
    void shouldNotAllowConcurrentModification() throws InterruptedException {
        AtomicInteger readerErrorCounter = new AtomicInteger(0);
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
                } catch (RuntimeException re) {
                    if (portCounter % 2 != 0) {
                        readerErrorCounter.getAndIncrement();
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
        done.await(5, TimeUnit.SECONDS);       // hepsi bitsin
        executorService.shutdown();

        assertEquals(0, readerErrorCounter.get());
    }

}

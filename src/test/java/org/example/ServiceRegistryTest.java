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
}

package org.example.service;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ServiceRegistryImpl implements ServiceRegistry {

    private static final int REGISTRY_CAPACITY = 10;

    private final Set<String> registrySet = new LinkedHashSet<>();

    private final AtomicInteger counter = new AtomicInteger(0);

    private final Random random = new Random();

    @Override
    public void register(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address is expected.");
        }

        address = address.trim();

        synchronized (registrySet) {
            if (registrySet.size() == REGISTRY_CAPACITY) {
                throw new IllegalStateException("Allowed limit is " + REGISTRY_CAPACITY + " for registry.");
            }

            if (!registrySet.add(address)) {
                throw new IllegalArgumentException("Same address can not be added to the registry.");
            }
        }
    }

    @Override
    public String get() {
        if (registrySet.isEmpty()) {
            throw new IllegalStateException("Registry is empty.");
        }

        return getAt(Math.floorMod(counter.getAndIncrement(), registrySet.size()));
    }

    @Override
    public String getRandom() {
        if (registrySet.isEmpty()) {
            throw new IllegalStateException("Registry is empty.");
        }

        return getAt(random.nextInt(registrySet.size()));
    }

    private String getAt(int index) {
        return registrySet.stream().skip(index).findFirst().orElseThrow(() -> new IllegalStateException("Registry can not be retrieved."));
    }
}

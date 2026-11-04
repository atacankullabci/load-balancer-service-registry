package org.example.service;

public interface ServiceRegistry {

    void register(String address);

    String get();

    String getRandom();
}

package net.fabricacs.api.event;

@FunctionalInterface
public interface EventHandle {
    void unregister();
}

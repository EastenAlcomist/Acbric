package net.fabricacs.api.event;

public enum EventResult {
    PASS,
    CANCEL;

    public boolean shouldCancel() {
        return this == CANCEL;
    }
}

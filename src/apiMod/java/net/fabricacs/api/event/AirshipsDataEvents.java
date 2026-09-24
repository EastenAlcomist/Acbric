package net.fabricacs.api.event;

public final class AirshipsDataEvents {
    public static final Event<DataLoadStarting> DATA_LOAD_STARTING = new Event<>(listeners -> () -> {
        for (DataLoadStarting listener : listeners) {
            listener.onDataLoadStarting();
        }
    });

    public static final Event<DataLoaded> DATA_LOADED = new Event<>(listeners -> successful -> {
        for (DataLoaded listener : listeners) {
            listener.onDataLoaded(successful);
        }
    });

    private AirshipsDataEvents() {
    }

    @FunctionalInterface
    public interface DataLoadStarting {
        void onDataLoadStarting();
    }

    @FunctionalInterface
    public interface DataLoaded {
        void onDataLoaded(boolean successful);
    }
}

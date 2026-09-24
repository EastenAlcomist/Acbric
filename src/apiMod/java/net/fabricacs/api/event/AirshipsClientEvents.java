package net.fabricacs.api.event;

public final class AirshipsClientEvents {
    public static final Event<ClientTickStart> CLIENT_TICK_START = new Event<>(listeners -> game -> {
        for (ClientTickStart listener : listeners) {
            listener.onClientTickStart(game);
        }
    });

    public static final Event<ClientTickEnd> CLIENT_TICK_END = new Event<>(listeners -> game -> {
        for (ClientTickEnd listener : listeners) {
            listener.onClientTickEnd(game);
        }
    });

    private AirshipsClientEvents() {
    }

    @FunctionalInterface
    public interface ClientTickStart {
        void onClientTickStart(Object airshipGame);
    }

    @FunctionalInterface
    public interface ClientTickEnd {
        void onClientTickEnd(Object airshipGame);
    }
}

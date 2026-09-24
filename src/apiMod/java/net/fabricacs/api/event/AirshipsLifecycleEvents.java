package net.fabricacs.api.event;

public final class AirshipsLifecycleEvents {
    public static final Event<GameStarting> GAME_STARTING = new Event<>(listeners -> args -> {
        String[] copiedArgs = args.clone();
        for (GameStarting listener : listeners) {
            listener.onGameStarting(copiedArgs);
        }
    });

    public static final Event<ClientCreated> CLIENT_CREATED = new Event<>(listeners -> game -> {
        for (ClientCreated listener : listeners) {
            listener.onClientCreated(game);
        }
    });

    public static final Event<LoadingScreenCreated> LOADING_SCREEN_CREATED = new Event<>(listeners -> screen -> {
        for (LoadingScreenCreated listener : listeners) {
            listener.onLoadingScreenCreated(screen);
        }
    });

    public static final Event<MainMenuCreated> MAIN_MENU_CREATED = new Event<>(listeners -> menu -> {
        for (MainMenuCreated listener : listeners) {
            listener.onMainMenuCreated(menu);
        }
    });

    private AirshipsLifecycleEvents() {
    }

    @FunctionalInterface
    public interface GameStarting {
        void onGameStarting(String[] args);
    }

    @FunctionalInterface
    public interface ClientCreated {
        void onClientCreated(Object airshipGame);
    }

    @FunctionalInterface
    public interface LoadingScreenCreated {
        void onLoadingScreenCreated(Object loadingScreen);
    }

    @FunctionalInterface
    public interface MainMenuCreated {
        void onMainMenuCreated(Object mainMenu);
    }
}

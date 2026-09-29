/*
 * AirshipsLifecycleEvents.java — 生命周期事件集合；由主入口及游戏/界面构造方法的 Mixin 同步触发。
 */
package net.fabricacs.api.event;

public final class AirshipsLifecycleEvents {
    /** Main.main 入口；参数数组每次分发复制一次，该次监听器共享副本。 */
    public static final Event<GameStarting> GAME_STARTING = new Event<>("AirshipsLifecycleEvents.GAME_STARTING", listeners -> args -> {
        String[] copiedArgs = args.clone();
        for (GameStarting listener : listeners) {
            listener.onGameStarting(copiedArgs);
        }
    });

    /** AirshipGame 构造正常返回后传递客户端对象。 */
    public static final Event<ClientCreated> CLIENT_CREATED = new Event<>("AirshipsLifecycleEvents.CLIENT_CREATED", listeners -> game -> {
        for (ClientCreated listener : listeners) {
            listener.onClientCreated(game);
        }
    });

    /** LoadingScreen 构造返回后通知；构造重载链可能重复通知同一对象。 */
    public static final Event<LoadingScreenCreated> LOADING_SCREEN_CREATED = new Event<>("AirshipsLifecycleEvents.LOADING_SCREEN_CREATED", listeners -> screen -> {
        for (LoadingScreenCreated listener : listeners) {
            listener.onLoadingScreenCreated(screen);
        }
    });

    /** MainMenu 构造返回后通知。 */
    public static final Event<MainMenuCreated> MAIN_MENU_CREATED = new Event<>("AirshipsLifecycleEvents.MAIN_MENU_CREATED", listeners -> menu -> {
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

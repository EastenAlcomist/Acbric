/*
 * AirshipsClientEvents.java — 客户端输入方法的前后事件；不是固定频率的逻辑更新或后台定时器。
 */
package net.fabricacs.api.event;

public final class AirshipsClientEvents {
    /** AirshipGame.input 方法入口，不能取消。 */
    public static final Event<ClientTickStart> CLIENT_TICK_START = new Event<>(listeners -> game -> {
        for (ClientTickStart listener : listeners) {
            listener.onClientTickStart(game);
        }
    });

    /** AirshipGame.input 正常返回后，异常退出时不保证触发。 */
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

/*
 * AirshipsDataEvents.java — 原版数据加载前后事件；DATA_LOADED 传递真实返回值，不修正游戏错误。
 */
package net.fabricacs.api.event;

public final class AirshipsDataEvents {
    /** Loadable.load 入口；此时数据尚未加载。 */
    public static final Event<DataLoadStarting> DATA_LOAD_STARTING = new Event<>("AirshipsDataEvents.DATA_LOAD_STARTING", listeners -> () -> {
        for (DataLoadStarting listener : listeners) {
            listener.onDataLoadStarting();
        }
    });

    /** Loadable.load 正常返回时传递原始成功标志；每次加载均可能触发。 */
    public static final Event<DataLoaded> DATA_LOADED = new Event<>("AirshipsDataEvents.DATA_LOADED", listeners -> successful -> {
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

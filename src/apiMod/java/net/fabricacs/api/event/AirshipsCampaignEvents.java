/*
 * AirshipsCampaignEvents.java — 战役准备、反序列化、状态替换及离开通知。
 * 同步、不可取消；回调异常沿用 Event 的传播策略。恢复回调只重新绑定，不初始化共享状态。
 */
package net.fabricacs.api.event;

public final class AirshipsCampaignEvents {
    /** 新地图内容及玩家准备完毕，首次自动保存/联机快照之前；每个生成实例一次。 */
    public static final Event<Created> CREATED = new Event<>("AirshipsCampaignEvents.CREATED", listeners -> world -> {
        for (Created listener : listeners) listener.onCreated(world);
    });
    /** 战役 JSON 构造成功；包括单机存档及联机大厅传入的存档，不代表已进入地图界面。 */
    public static final Event<Loaded> LOADED = new Event<>("AirshipsCampaignEvents.LOADED", listeners -> (world, multiplayerLoad) -> {
        for (Loaded listener : listeners) listener.onLoaded(world, multiplayerLoad);
    });
    /** ResumeScreen 成功安装恢复后的战役/界面；不发送 CREATED、LOADED 或旧实例 EXITED。 */
    public static final Event<Restored> RESTORED = new Event<>("AirshipsCampaignEvents.RESTORED", listeners -> (previous, current) -> {
        for (Restored listener : listeners) listener.onRestored(previous, current);
    });
    /** 已观察到的活动战役离开至菜单/大厅、被另一战役替换或正常退出程序；不是保存回调。 */
    public static final Event<Exited> EXITED = new Event<>("AirshipsCampaignEvents.EXITED", listeners -> world -> {
        for (Exited listener : listeners) listener.onExited(world);
    });

    private AirshipsCampaignEvents() {}

    @FunctionalInterface public interface Created { void onCreated(Object campaignWorld); }
    @FunctionalInterface public interface Loaded { void onLoaded(Object campaignWorld, boolean multiplayerLoad); }
    @FunctionalInterface public interface Restored { void onRestored(Object previousWorld, Object currentWorld); }
    @FunctionalInterface public interface Exited { void onExited(Object campaignWorld); }
}

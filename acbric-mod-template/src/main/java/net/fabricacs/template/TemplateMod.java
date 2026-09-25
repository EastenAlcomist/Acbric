/*
 * TemplateMod.java — 独立 MOD 模板入口：展示上下文日志、一次性数据监听及新重命名面板事件。
 */
package net.fabricacs.template;

import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.event.AirshipsClientEvents;
import net.fabricacs.api.event.AirshipsCombatUiEvents;
import net.fabricacs.api.event.AirshipsDataEvents;
import net.fabricacs.api.event.EventScope;

public final class TemplateMod implements AcbricInitializer {
    private boolean firstTickLogged;
    private EventScope subscriptions;

    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.logger().info(context.modName() + " initialized. configDir=" + context.ensureConfigDir());

        subscriptions = context.eventScope("application");
        try {
        subscriptions.registerOnce(AirshipsDataEvents.DATA_LOADED, successful ->
                context.logger().info("ACS data load finished. successful=" + successful));

        // 观察面板 tick；该回调不表示舰船改名已经确认。
        subscriptions.registerOnce(AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK, panel ->
                context.logger().info("Rename ship panel tick hook is active."));

        subscriptions.register(AirshipsClientEvents.CLIENT_TICK_START, game -> {
            if (!firstTickLogged) {
                firstTickLogged = true;
                context.logger().info("Client tick hook is active.");
            }
        });
        } catch (RuntimeException | Error failure) {
            // 注册中途失败时撤销本范围；不会回滚其他初始化副作用。
            subscriptions.close();
            throw failure;
        }
    }

    @Override
    public void onInitializeAcbric() {
        // 为兼容函数式接口保留无参方法；实际初始化使用上面的上下文入口。
    }
}

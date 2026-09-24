/*
 * AcbricApiPreLaunch.java — Fabric 预启动桥：先准备内嵌原版资源，再逐个调用 MOD 的 acbric 入口。
 * 入口初始化异常分别记录；普通事件回调的异常策略由 Event/各事件工厂保持。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.AcbricEntrypoints;
import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AcbricApiPreLaunch implements PreLaunchEntrypoint {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    @Override
    public void onPreLaunch() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        System.out.println("[Acbric API] Extracting bundled vanilla mods.");
        BundledVanillaModLoader.extractAll();

        System.out.println("[Acbric API] Initializing Acbric API entrypoints.");
        FabricLoader.getInstance()
                .getEntrypointContainers(AcbricEntrypoints.INIT, AcbricInitializer.class)
                .forEach(AcbricApiPreLaunch::initializeEntrypoint);
    }

    private static void initializeEntrypoint(EntrypointContainer<AcbricInitializer> container) {
        AcbricModContext context = new AcbricModContext(container.getProvider());
        try {
            context.logger().info("Initializing Acbric entrypoint.");
            container.getEntrypoint().onInitializeAcbric(context);
        } catch (Throwable t) {
            context.logger().error("Acbric entrypoint initialization failed.", t);
        }
    }
}

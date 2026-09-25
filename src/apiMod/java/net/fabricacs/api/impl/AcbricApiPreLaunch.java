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
import java.util.List;

public final class AcbricApiPreLaunch implements PreLaunchEntrypoint {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    @Override
    public void onPreLaunch() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        FabricLoader loader = FabricLoader.getInstance();
        StartupDiagnostics diagnostics = new StartupDiagnostics(loader.getGameDir(), loader.getAllMods());
        try {
            System.out.println("[Acbric API] Extracting bundled vanilla mods.");
            BundledVanillaModLoader.extractAll();
            System.out.println("[Acbric API] Initializing Acbric API entrypoints.");
            initializeEntrypoints(loader.getEntrypointContainers(AcbricEntrypoints.INIT, AcbricInitializer.class), diagnostics);
        } catch (RuntimeException | Error failure) {
            diagnostics.failed(failure);
            throw failure;
        }
    }

    /** 先记录所有待处理入口，再逐个初始化，以便中断报告区分未执行与成功。 */
    static void initializeEntrypoints(List<EntrypointContainer<AcbricInitializer>> containers, StartupDiagnostics diagnostics) {
        var entries = containers.stream().map(diagnostics::register).toList();
        diagnostics.discoveryComplete();
        for (int i = 0; i < containers.size(); i++) {
            diagnostics.starting(entries.get(i));
            Throwable failure = initializeEntrypoint(containers.get(i));
            diagnostics.completed(entries.get(i), failure);
        }
        diagnostics.finish();
    }

    private static Throwable initializeEntrypoint(EntrypointContainer<AcbricInitializer> container) {
        AcbricModContext context = new AcbricModContext(container.getProvider());
        try {
            context.logger().info("Initializing Acbric entrypoint.");
            container.getEntrypoint().onInitializeAcbric(context);
        } catch (Throwable t) {
            context.logger().error("Acbric entrypoint initialization failed.", t);
            return t;
        }
        return null;
    }
}

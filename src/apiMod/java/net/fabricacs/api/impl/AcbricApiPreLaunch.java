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

package net.fabricacs.template;

import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.event.AirshipsClientEvents;
import net.fabricacs.api.event.AirshipsDataEvents;

public final class TemplateMod implements AcbricInitializer {
    private boolean firstTickLogged;

    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.logger().info(context.modName() + " initialized. configDir=" + context.ensureConfigDir());

        AirshipsDataEvents.DATA_LOADED.registerOnce(successful ->
                context.logger().info("ACS data load finished. successful=" + successful));

        AirshipsClientEvents.CLIENT_TICK_START.register(game -> {
            if (!firstTickLogged) {
                firstTickLogged = true;
                context.logger().info("Client tick hook is active.");
            }
        });
    }

    @Override
    public void onInitializeAcbric() {
        // Required by the current AcbricInitializer functional interface.
    }
}

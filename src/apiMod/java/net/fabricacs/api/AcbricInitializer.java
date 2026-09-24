package net.fabricacs.api;

/**
 * Main entrypoint for mods that want to use Acbric API.
 *
 * <p>Declare this in fabric.mod.json under the {@code acbric} entrypoint.</p>
 */
@FunctionalInterface
public interface AcbricInitializer {
    void onInitializeAcbric();

    default void onInitializeAcbric(AcbricModContext context) {
        onInitializeAcbric();
    }
}

package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans each Fabric mod JAR for a bundled vanilla-data folder
 * ({@code acbric_vanilla/}) and extracts it into the game's
 * {@code mods/<fabric-mod-id>/} directory so the vanilla mod
 * system picks it up automatically.
 */
public final class BundledVanillaModLoader {

    /** Name of the folder inside a Fabric mod JAR that holds vanilla data. */
    private static final String BUNDLED_DIR = "acbric_vanilla/";

    private BundledVanillaModLoader() {}

    /**
     * Extracts bundled vanilla mods for all loaded Fabric mods.
     * Call during or after {@code preLaunch}.
     */
    public static void extractAll() {
        // The game's native mod system (Mod.refreshMods) only scans
        // AGame.getGameDirectory()/mods for directory mods (folders with an
        // info.json). The Fabric game dir's "mods" folder holds Fabric .jar
        // mods and is NOT scanned by the native system, so bundled vanilla
        // data must be extracted under the game's own mods directory.
        Path gameModsDir = AGame.getGameDirectory().toPath().resolve("mods");
        try {
            Files.createDirectories(gameModsDir);
        } catch (IOException e) {
            System.err.println("[Acbric] Failed to create mods dir: " + gameModsDir);
            return;
        }

        for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
            try {
                for (Path origin : mc.getOrigin().getPaths()) {
                    extractBundled(origin, gameModsDir, mc.getMetadata().getId());
                }
            } catch (UnsupportedOperationException ignored) {
                // Nested/built-in mods don't have file paths — skip.
            }
        }
    }

    private static void extractBundled(Path jarPath, Path gameModsDir, String modId) {
        if (!Files.isRegularFile(jarPath)) return;
        String fn = jarPath.getFileName().toString().toLowerCase();
        if (!fn.endsWith(".jar") && !fn.endsWith(".zip")) return;

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            // Check if the bundled directory exists
            boolean hasBundle = zip.stream().anyMatch(
                    e -> e.getName().startsWith(BUNDLED_DIR) && !e.isDirectory());
            if (!hasBundle) return;

            Path targetDir = gameModsDir.resolve(modId);
            // Skip if already up-to-date (simple check: target dir exists)
            if (Files.isDirectory(targetDir)) return;

            Files.createDirectories(targetDir);
            System.out.println("[Acbric] Extracting bundled vanilla mod from "
                    + jarPath.getFileName() + " → " + targetDir);

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(BUNDLED_DIR)) continue;

                // Strip the prefix
                String relative = entry.getName().substring(BUNDLED_DIR.length());
                if (relative.isEmpty()) continue;

                Path target = targetDir.resolve(relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Write an info.json so the vanilla mod system (Mod.refreshMods)
            // discovers this extracted directory and Loadable.load() picks up
            // its <DataType>/ subdirectories.
            Path infoJson = targetDir.resolve("info.json");
            if (!Files.exists(infoJson)) {
                Files.writeString(infoJson,
                        "{\n  \"id\": \"" + modId + "\",\n"
                        + "  \"name\": { \"en\": \"" + modId + "\" },\n"
                        + "  \"description\": { \"en\": \"Acbric bundled vanilla data.\" },\n"
                        + "  \"tags\": [\"acbric\"]\n}\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("[Acbric] Failed to extract bundled mod from "
                    + jarPath + ": " + e);
        }
    }
}

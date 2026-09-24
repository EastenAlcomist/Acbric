package net.fabricacs.api.impl;

import com.zarkonnen.airships.Mod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import org.newdawn.slick.Image;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class FabricModListBridge {
    private static final String SYNTHETIC_ID_PREFIX = "acbric_fabric:";
    private static final Set<String> HIDDEN_MOD_IDS = Set.of(
            "airships",
            "fabricloader",
            "java",
            "mixinextras"
    );

    // Cache mod metadata so repeated ModsScreen refreshes don't reload icons.
    private static final java.util.Map<String, Image> iconCache = new java.util.HashMap<>();

    private FabricModListBridge() {
    }

    public static void appendFabricMods() {
        try {
            removeSyntheticFabricMods();

            ArrayList<ModContainer> fabricMods = FabricLoader.getInstance().getAllMods().stream()
                    .filter(FabricModListBridge::shouldDisplay)
                    .sorted(Comparator.comparing(container -> displayName(container.getMetadata()), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toCollection(ArrayList::new));

            for (ModContainer container : fabricMods) {
                Mod.mods.add(createSyntheticMod(container));
            }

            if (!fabricMods.isEmpty()) {
                System.out.println("[Acbric API] Added " + fabricMods.size() + " Fabric mods to the vanilla mod list.");
            }
        } catch (Throwable t) {
            System.err.println("[Acbric API] Failed to add Fabric mods to the vanilla mod list.");
            t.printStackTrace();
        }
    }

    public static boolean isSyntheticFabricMod(Mod mod) {
        return mod != null && mod.id != null && mod.id.startsWith(SYNTHETIC_ID_PREFIX);
    }

    public static String rowName(Mod mod, boolean selected) {
        String name = mod.getName() + " [65c0f9]Fabric";
        return selected ? com.zarkonnen.airships.MyDraw.SELECTED_C + name : name;
    }

    public static String rowStatus(Mod mod) {
        return "Loaded by Fabric";
    }

    private static boolean shouldDisplay(ModContainer container) {
        ModMetadata metadata = container.getMetadata();
        String id = metadata.getId();
        return !HIDDEN_MOD_IDS.contains(id) && "fabric".equals(metadata.getType());
    }

    private static Mod createSyntheticMod(ModContainer container) throws ReflectiveOperationException {
        ModMetadata metadata = container.getMetadata();
        Mod mod = newSyntheticMod(metadata.getId());

        mod.id = SYNTHETIC_ID_PREFIX + metadata.getId();
        mod.name.put(Locale.ENGLISH, displayName(metadata));
        mod.description.put(Locale.ENGLISH, description(container));
        mod.logo = icon(container);
        mod.tags.add("fabric");

        // The vanilla row renderer skips enable/delete controls for preempted mods.
        mod.preemptedBy = mod;

        return mod;
    }

    private static Mod newSyntheticMod(String fabricId) throws ReflectiveOperationException {
        Constructor<Mod> constructor = Mod.class.getDeclaredConstructor(File.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(syntheticDirectory(fabricId), false);
    }

    private static File syntheticDirectory(String fabricId) {
        String safeId = fabricId.replaceAll("[^A-Za-z0-9_.-]", "_");
        Path dir = FabricLoader.getInstance().getGameDir().resolve(".fabric").resolve("acbric").resolve("fabric-mod-list").resolve(safeId);
        return dir.toFile();
    }

    private static String displayName(ModMetadata metadata) {
        String name = metadata.getName();
        return name == null || name.isBlank() ? metadata.getId() : name;
    }

    private static String description(ModContainer container) {
        ModMetadata metadata = container.getMetadata();
        ArrayList<String> lines = new ArrayList<>();

        String description = metadata.getDescription();
        if (description != null && !description.isBlank()) {
            lines.add(description);
            lines.add("");
        }

        lines.add("Fabric mod ID: " + metadata.getId());
        lines.add("Version: " + metadata.getVersion().getFriendlyString());

        String authors = metadata.getAuthors().stream()
                .map(Person::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        if (!authors.isBlank()) {
            lines.add("Authors: " + authors);
        }

        String paths = sourcePaths(container);
        if (!paths.isBlank()) {
            lines.add("Source: " + paths);
        }

        return String.join("\n", lines);
    }

    private static Image icon(ModContainer container) {
        ModMetadata metadata = container.getMetadata();
        String modId = metadata.getId();

        // Return cached icon if already loaded.
        Image cached = iconCache.get(modId);
        if (cached != null) return cached;

        Optional<String> iconPath = metadata.getIconPath(64)
                .or(() -> metadata.getIconPath(128))
                .or(() -> metadata.getIconPath(32));
        if (iconPath.isEmpty()) {
            iconCache.put(modId, null);
            return null;
        }

        Optional<Path> path = container.findPath(iconPath.get());
        if (path.isEmpty()) {
            iconCache.put(modId, null);
            return null;
        }

        try (InputStream in = Files.newInputStream(path.get())) {
            Image img = new Image(in, modId + ":" + iconPath.get(), false);
            iconCache.put(modId, img);
            return img;
        } catch (Throwable t) {
            System.err.println("[Acbric API] Failed to load Fabric mod icon for " + modId + ": " + iconPath.get());
            t.printStackTrace();
            return null;
        }
    }

    private static String sourcePaths(ModContainer container) {
        ModOrigin origin = container.getOrigin();
        ArrayList<Path> paths = new ArrayList<>(origin.getPaths());
        if (paths.isEmpty()) {
            paths.addAll(container.getRootPaths());
        }
        return paths.stream()
                .map(Path::toString)
                .collect(Collectors.joining(", "));
    }

    private static void removeSyntheticFabricMods() {
        HashSet<Mod> syntheticMods = new HashSet<>();
        for (Mod mod : Mod.mods) {
            if (isSyntheticFabricMod(mod)) {
                syntheticMods.add(mod);
            }
        }
        Mod.mods.removeAll(syntheticMods);
    }
}

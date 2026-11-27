/*
 * FabricModListBridge.java — 把已加载 Fabric MOD 映射为原版列表行；处理嵌套来源、图标和重复刷新。
 * Java 启停在重启后生效；详情页使用公共 UI，原生资源 MOD 的操作仍由游戏处理。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.Mod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // 按 MOD 缓存图标；列表重复刷新时避免重复解码，缺失图标也缓存为空。
    private static final java.util.Map<String, Image> iconCache = new java.util.HashMap<>();

    private FabricModListBridge() {
    }

    /** 移除旧合成行后重新追加；某个 MOD 失败不阻断其余行。 */
    public static void appendFabricMods() {
        try {
            removeSyntheticFabricMods();
            JavaModManager.refresh();

            ArrayList<ModContainer> fabricMods = FabricLoader.getInstance().getAllMods().stream()
                    .filter(FabricModListBridge::shouldDisplay)
                    .sorted(Comparator.comparing(container -> displayName(container.getMetadata()), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toCollection(ArrayList::new));

            int added = 0;
            for (ModContainer container : fabricMods) {
                try {
                    Mod.mods.add(createSyntheticMod(container));
                    added++;
                } catch (Exception | LinkageError e) {
                    System.err.println("[Acbric API] Failed to display Fabric mod " + container.getMetadata().getId());
                    e.printStackTrace();
                }
            }

            JavaModManager manager = JavaModManager.current();
            if (manager != null) for (JavaModManager.Entry entry : manager.entries()) {
                if (!entry.loaded() && entry.archive() != null && !HIDDEN_MOD_IDS.contains(entry.id())) {
                    Mod mod = newSyntheticMod(entry.id());
                    mod.id = SYNTHETIC_ID_PREFIX + entry.id();
                    mod.name.put(Locale.ENGLISH, displayName(entry.metadata()));
                    descriptions(mod, entry.metadata(), entry.archive().toString());
                    mod.preemptedBy = mod;
                    Mod.mods.add(mod);
                }
            }
            if (!fabricMods.isEmpty()) {
                System.out.println("[Acbric API] Added " + added + " Fabric mods to the vanilla mod list.");
            }
        } catch (Throwable t) {
            System.err.println("[Acbric API] Failed to add Fabric mods to the vanilla mod list.");
            t.printStackTrace();
        }
    }

    public static boolean isSyntheticFabricMod(Mod mod) {
        return mod != null && mod.id != null && mod.id.startsWith(SYNTHETIC_ID_PREFIX);
    }

    /** 仅用于 MOD 界面的空列表判断；Java 行可显示，但仍不属于原版可加载资源 MOD。 */
    public static ArrayList<Mod> modsForListVisibility() {
        ArrayList<Mod> visible = Mod.getAvailableMods();
        for (Mod mod : Mod.mods) {
            if (isSyntheticFabricMod(mod) && !visible.contains(mod)) visible.add(mod);
        }
        return visible;
    }

    public static String rowName(Mod mod, boolean selected) {
        String name = mod.getName() + " [65c0f9]Fabric";
        return selected ? com.zarkonnen.airships.MyDraw.SELECTED_C + name : name;
    }

    public static String rowStatus(Mod mod) {
        JavaModManager manager = JavaModManager.current();
        if (manager == null || manager.entry(fabricId(mod)) == null)
            return chinese() ? "已加载 · 管理不可用（查看日志）" : "Loaded · manager unavailable (see log)";
        return manager.status(fabricId(mod), chinese());
    }

    public static String fabricId(Mod mod) { return mod.id.substring(SYNTHETIC_ID_PREFIX.length()); }

    public static boolean chinese() {
        return net.fabricacs.api.util.AcbricLanguage.isChinese();
    }

    /** 原版按钮只控制数据 MOD；Fabric 行使用独立的持久化选择，不进入原版热重载集合。 */
    public static void drawManagerButton(Mod mod, com.zarkonnen.airships.MyDraw draw, int x, int y, int width,
                                         java.util.function.Consumer<String> report) {
        JavaModManager manager = JavaModManager.current();
        if (manager == null) return;
        String id = fabricId(mod);
        JavaModManager.Entry entry = manager.entry(id);
        if (entry == null) return;
        String reason = manager.displayReason(id);
        boolean enabled = entry.manageable() && reason.isEmpty();
        String label = enabled ? (manager.enabledNext(id) ? (chinese() ? "停用" : "Disable")
                : (chinese() ? "启用" : "Enable")) : (chinese() ? "只读" : "Read only");
        int bw = draw.bw(label);
        draw.tooltip(x + width - bw, y, bw, com.zarkonnen.airships.MyDraw.BUTTON_H, enabled
                ? (chinese() ? "下次启动生效；再次点击可撤销" : "Applies on restart; click again to undo") : reason);
        draw.button(x + width - bw, y, bw, label, null, new com.zarkonnen.airships.InputRunnable() {
            @Override public void run(com.zarkonnen.catengine.Input input) {
                if (!enabled) { report.accept(reason); return; }
                try { manager.toggle(id); }
                catch (java.io.IOException ex) { report.accept(ex.getMessage()); }
            }
        });
    }

    public static int managerRowHeight(Mod mod, com.zarkonnen.airships.MyDraw draw, int width) {
        int buttons = Math.max(draw.bw(chinese() ? "只读" : "Read only"), draw.bw(chinese() ? "详情" : "Details")) + 12;
        int logo = com.zarkonnen.airships.AGame.BIG_FOUNT.height + com.zarkonnen.airships.AGame.FOUNT.height;
        int textWidth = Math.max(1, width - buttons - logo - com.zarkonnen.airships.MyDraw.UI_SPACING);
        int textHeight = (int) draw.textSize(rowName(mod, false), com.zarkonnen.airships.AGame.BIG_FOUNT, 0, 0, textWidth).height
                + (int) draw.textSize(rowStatus(mod), com.zarkonnen.airships.AGame.FOUNT, 0, 0, textWidth).height;
        return Math.max(logo, Math.max(textHeight, 2 * com.zarkonnen.airships.MyDraw.BUTTON_H + 6)) + 8;
    }

    public static void drawManagerRow(Mod mod, com.zarkonnen.airships.MyDraw draw, int x, int y, int width,
                                      boolean isSelected, Runnable selected, java.util.function.Consumer<String> report) {
        int buttons = Math.max(draw.bw(chinese() ? "只读" : "Read only"), draw.bw(chinese() ? "详情" : "Details")) + 12;
        int logo = com.zarkonnen.airships.AGame.BIG_FOUNT.height + com.zarkonnen.airships.AGame.FOUNT.height;
        int textX = x + logo + com.zarkonnen.airships.MyDraw.UI_SPACING;
        int textWidth = Math.max(1, width - buttons - logo - com.zarkonnen.airships.MyDraw.UI_SPACING);
        if (mod.logo != null) {
            ((org.newdawn.slick.Graphics)draw.frame().nativeRenderer()).drawImage(mod.logo, x + 1, y + 1, x + logo - 1, y + logo - 1, 0, 0, mod.logo.getWidth(), mod.logo.getHeight());
            draw.drawPanelBorder(x, y, logo, logo);
        } else draw.drawPanel(x, y, logo, logo, -1);
        draw.hook(x, y, width, managerRowHeight(mod, draw, width), new com.zarkonnen.catengine.Hook(
                new com.zarkonnen.catengine.Hook.Type[]{com.zarkonnen.catengine.Hook.Type.MOUSE_1_CLICKED}) {
            @Override public void run(com.zarkonnen.catengine.Input in, com.zarkonnen.catengine.util.Pt p, com.zarkonnen.catengine.Hook.Type type) { selected.run(); }
        });
        draw.text(rowName(mod, isSelected), com.zarkonnen.airships.AGame.BIG_FOUNT, textX, y, textWidth);
        int nameHeight = (int) draw.textSize(rowName(mod, false), com.zarkonnen.airships.AGame.BIG_FOUNT, x, y, textWidth).height;
        draw.text(rowStatus(mod), com.zarkonnen.airships.AGame.FOUNT, textX, y + nameHeight, textWidth);
        drawManagerButton(mod, draw, x, y, width, report);
        String details = chinese() ? "详情" : "Details"; int bw = draw.bw(details);
        draw.button(x + width - bw, y + com.zarkonnen.airships.MyDraw.BUTTON_H + 6, bw, details, () -> {
            try { UiBridge.details(fabricId(mod)); } catch (RuntimeException ex) { report.accept(ex.getMessage()); }
        });
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
        descriptions(mod, metadata, sourcePaths(container));
        mod.logo = icon(container);
        mod.tags.add("fabric");

        // 原版行渲染器会跳过被抢占 MOD 的启用/删除按钮，防止错误操作合成条目。
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

    /** 将 custom 描述中的每个语言键写入原版 Mod.description，并按 Locale 精确查找。 */
    private static void descriptions(Mod mod, ModMetadata metadata, String source) {
        Map<Locale, String> localized = localizedDescriptionEntries(metadata);
        if (localized.isEmpty()) {
            mod.description.put(Locale.ENGLISH, description(metadata, Locale.ENGLISH, source));
            for (Locale locale : chineseLocales()) {
                mod.description.put(locale, description(metadata, locale, source));
            }
            return;
        }
        for (Map.Entry<Locale, String> entry : localized.entrySet()) {
            mod.description.put(entry.getKey(), description(metadata, entry.getKey(), source));
        }
        mod.description.putIfAbsent(Locale.ENGLISH, description(metadata, Locale.ENGLISH, source));
    }

    private static String description(ModMetadata metadata, Locale locale, String source) {
        boolean zh = net.fabricacs.api.util.AcbricLanguage.isChinese(locale);
        ArrayList<String> lines = new ArrayList<>();

        String description = localizedDescription(metadata, locale);
        if (description != null && !description.isBlank()) {
            lines.add(description);
            lines.add("");
        }

        lines.add((zh ? "Fabric MOD 标识：" : "Fabric mod ID: ") + metadata.getId());
        lines.add((zh ? "版本：" : "Version: ") + metadata.getVersion().getFriendlyString());

        String authors = metadata.getAuthors().stream()
                .map(Person::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        if (!authors.isBlank()) {
            lines.add((zh ? "作者：" : "Authors: ") + authors);
        }

        if (source != null && !source.isBlank()) {
            lines.add((zh ? "来源：" : "Source: ") + source);
        }

        return String.join("\n", lines);
    }

    /**
     * 解析 Java MOD 的可选双语描述。
     *
     * <p>标准 Fabric metadata 的 description 是单字符串；Acbric MOD 可以在
     * custom.acbric.description 中提供任意游戏语言键。当前 Locale 没有对应值时
     * 回退英文，再无英文时回退原始 description。</p>
     */
    static String localizedDescription(ModMetadata metadata, boolean chinese) {
        return localizedDescription(metadata, chinese ? Locale.forLanguageTag("chi") : Locale.ENGLISH);
    }

    /** 按游戏 Locale 解析本地化描述；所有游戏语言使用同一个解析器。 */
    static String localizedDescription(ModMetadata metadata, Locale locale) {
        String fallback = metadata.getDescription() == null ? "" : metadata.getDescription();
        Map<String, String> localized = descriptionValues(metadata);
        if (localized.isEmpty()) return fallback;
        if (locale == null) locale = Locale.ENGLISH;
        for (String key : descriptionKeys(locale)) {
            String text = localized.get(key);
            if (text != null && !text.isBlank()) return text;
        }
        return fallback;
    }

    private static Map<Locale, String> localizedDescriptionEntries(ModMetadata metadata) {
        LinkedHashMap<Locale, String> result = new LinkedHashMap<>();
        Map<String, String> values = descriptionValues(metadata);
        for (String key : values.keySet()) {
            for (Locale locale : localeAliases(key)) result.put(locale, values.get(key));
        }
        String chinese = firstValue(values, "chi", "zh", "zho");
        if (chinese != null) {
            for (Locale locale : chineseLocales()) result.putIfAbsent(locale, chinese);
        }
        if (!result.containsKey(Locale.ENGLISH)) {
            String english = metadata.getDescription();
            if (english != null && !english.isBlank()) result.put(Locale.ENGLISH, english);
        }
        return result;
    }

    private static Map<String, String> descriptionValues(ModMetadata metadata) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            if (!metadata.containsCustomValue("acbric")) return result;
            CustomValue value = metadata.getCustomValue("acbric");
            if (value.getType() != CustomValue.CvType.OBJECT) return result;
            CustomValue.CvObject root = value.getAsObject();
            if (!root.containsKey("description")) return result;
            CustomValue description = root.get("description");
            if (description.getType() != CustomValue.CvType.OBJECT) return result;
            CustomValue.CvObject localized = description.getAsObject();
            for (Map.Entry<String, CustomValue> entry : localized) {
                CustomValue candidate = entry.getValue();
                if (candidate.getType() != CustomValue.CvType.STRING) continue;
                String text = candidate.getAsString();
                if (text != null && !text.isBlank()) result.put(entry.getKey(), text);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 第三方自定义值不应让整个 MOD 列表刷新失败；回退到标准描述。
        }
        return result;
    }

    private static List<String> descriptionKeys(Locale locale) {
        ArrayList<String> keys = new ArrayList<>();
        String tag = locale.toLanguageTag();
        if (!tag.isBlank()) keys.add(tag);
        String language = locale.getLanguage();
        if (!language.isBlank() && !keys.contains(language)) keys.add(language);
        String text = locale.toString();
        if (!text.isBlank() && !keys.contains(text)) keys.add(text);
        switch (language) {
            case "ja" -> keys.add("jpn");
            case "ko" -> keys.add("kor");
            case "chi", "zh", "zho" -> {
                for (String key : List.of("chi", "zh", "zho")) if (!keys.contains(key)) keys.add(key);
            }
            default -> { }
        }
        if (net.fabricacs.api.util.AcbricLanguage.isChinese(locale)) {
            for (String key : List.of("chi", "zh", "zho")) if (!keys.contains(key)) keys.add(key);
        }
        if (!keys.contains("en")) keys.add("en");
        return keys;
    }

    private static Locale localeFor(String key) {
        try {
            return key == null || key.isBlank() ? null : Locale.forLanguageTag(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<Locale> localeAliases(String key) {
        ArrayList<Locale> result = new ArrayList<>();
        Locale direct = localeFor(key);
        if (direct != null) result.add(direct);
        if (key == null) return result;
        switch (key.toLowerCase(Locale.ROOT)) {
            case "jpn", "ja" -> result.add(Locale.JAPANESE);
            case "kor", "ko" -> result.add(Locale.KOREAN);
            case "chi", "zh", "zho" -> result.addAll(chineseLocales());
            default -> { }
        }
        return result;
    }

    private static String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.containsKey(key)) return values.get(key);
        return null;
    }

    private static List<Locale> chineseLocales() {
        return List.of(Locale.forLanguageTag("chi"), Locale.forLanguageTag("zho"),
                Locale.CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE);
    }

    private static Image icon(ModContainer container) {
        ModMetadata metadata = container.getMetadata();
        String modId = metadata.getId();

        // containsKey 区分尚未读取和已确认没有图标两种状态。
        if (iconCache.containsKey(modId)) return iconCache.get(modId);

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

    /** 嵌套来源通过父 MOD 与包内位置表示，不能直接调用 getPaths。 */
    private static String sourcePaths(ModContainer container) {
        ModOrigin origin = container.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.NESTED) {
            return origin.getParentModId() + "!/" + origin.getParentSubLocation();
        }
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return container.getRootPaths().stream().map(Path::toString).collect(Collectors.joining(", "));
        }
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

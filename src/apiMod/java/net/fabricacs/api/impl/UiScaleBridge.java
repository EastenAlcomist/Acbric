/* UiScaleBridge.java — 按百分比选用原版对应清晰度的资源；从原始档位生成，避免小图放大和累乘。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Fount;
import com.zarkonnen.catengine.Img;
import net.fabricacs.api.ui.UiScale;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class UiScaleBridge {
    private record Slot(Field target, Field source, Field medium, Field large) { }
    private static List<Slot> slots;
    private static int appliedPercent;
    private static Fount appliedFont;
    private static boolean appliedDyslexic;
    private UiScaleBridge() { }

    /** 绘制前提交，使控件布局、纹理尺寸和下一帧命中区域来自同一套度量。 */
    public static void beforeRender(AirshipGame game) {
        int percent = UiScale.percent();
        if (percent == 0) {
            if (appliedPercent != 0) {
                (game.currentGUIScale == null ? GUIScale.SMALL : game.currentGUIScale).activate();
                appliedPercent = 0;
                appliedFont = null;
            }
            return;
        }
        // 语言、字体偏好或原版档位变化会重新激活原生字体，必须重新生成，不能缓存系统 Locale。
        boolean dyslexic = SimplePref.DYSLEXIC_FONT.get();
        if (percent == appliedPercent && AGame.FOUNT == appliedFont && dyslexic == appliedDyslexic) return;
        try {
            if (slots == null) slots = discoverSlots();
            IdentityHashMap<Fount, Fount> fonts = new IdentityHashMap<>();
            List<Object> values = new ArrayList<>(slots.size());
            // 先完整生成再发布；读取失败不留下只改了一半的界面。
            for (Slot slot : slots) {
                // 原版各档位不是同一张位图放大：中/大字号与装饰各有独立资源。
                // 选择不小于目标的档位；100/150/200 原样使用原生对象，其他比例向下采样。
                Field selected = slot.source;
                int basis = 100;
                if (percent > 150 && slot.large != null) { selected = slot.large; basis = 200; }
                else if (percent > 100 && slot.medium != null) { selected = slot.medium; basis = 150; }
                Object source = selected.get(null);
                double factor = percent / (double) basis;
                if (source instanceof Integer value) values.add(ScaledUiImage.dimension(value, factor));
                else if (source instanceof Img img) values.add(factor == 1 ? img : new ScaledUiImage(img, factor));
                else if (source instanceof Fount[] choices) {
                    Fount font = choices[choices.length > 1 && dyslexic ? 1 : 0];
                    // 原版各档位共用固定标题字号；主菜单在标题后硬编码前进 64 像素，不能把标题也放大。
                    values.add(slot.source.getName().equals("HUGE_FOUNTS") ? font : scaleFont(font, factor, fonts));
                } else if (source == null) values.add(null);
                else throw new IllegalStateException("Unsupported native UI metric: " + slot.source);
            }
            for (int i = 0; i < slots.size(); i++) slots.get(i).target.set(null, values.get(i));
            appliedPercent = percent;
            appliedFont = AGame.FOUNT;
            appliedDyslexic = dyslexic;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot apply native UI scale", ex);
        }
    }

    /** 只处理原版 GUIScale 管理的固定类型及其 SMALL 成对字段，不扫描 MOD 或世界数据。 */
    private static List<Slot> discoverSlots() throws ReflectiveOperationException {
        List<Slot> result = new ArrayList<>();
        for (Class<?> type : List.of(MyDraw.class, ScrollBar.class, ShoutOverlay.class, FileScreen.class, TechScreen.class)) {
            for (Field source : type.getFields()) {
                if (!source.getName().startsWith("SMALL_") || !Modifier.isStatic(source.getModifiers())) continue;
                if (source.getType() != int.class && source.getType() != Img.class) continue;
                Field target = type.getField(source.getName().substring(6));
                if (!Modifier.isStatic(target.getModifiers()) || Modifier.isFinal(target.getModifiers()) || target.getType() != source.getType())
                    throw new IllegalStateException("Unsupported native UI metric: " + target);
                result.add(new Slot(target, source, optional(type, "MEDIUM_" + target.getName()),
                        optional(type, "LARGE_" + target.getName())));
            }
        }
        for (String name : List.of("FOUNT", "FOUNT_OUTLINE", "BIG_FOUNT", "BIG_FOUNT_OUTLINE", "BIGGER_FOUNT", "HUGE_FOUNT",
                "MAP", "MAP_OUTLINE", "MAP_SMALL", "MAP_SMALL_OUTLINE", "MAP_FALLBACK", "MAP_FALLBACK_OUTLINE",
                "MAP_FALLBACK_SMALL", "MAP_FALLBACK_SMALL_OUTLINE")) {
            String sourceName = name.equals("HUGE_FOUNT") ? "HUGE_FOUNTS" : "SMALL_" + name;
            result.add(new Slot(AGame.class.getField(name), AGame.class.getField(sourceName),
                    optional(AGame.class, "MEDIUM_" + name), optional(AGame.class, "LARGE_" + name)));
        }
        return List.copyOf(result);
    }

    /** 旧版或资源 MOD 没有高档位字段时仍可使用原有基准，不在运行时引入新硬依赖。 */
    private static Field optional(Class<?> type, String name) {
        try { return type.getField(name); }
        catch (NoSuchFieldException absent) { return null; }
    }

    private static Fount scaleFont(Fount source, double factor, IdentityHashMap<Fount, Fount> cache) {
        if (source == null || factor == 1) return source;
        if (cache.containsKey(source)) return cache.get(source);
        Map<Integer, Img> glyphs = new HashMap<>(source.extended);
        for (int i = 0; i < source.imgs.length; i++) if (source.imgs[i] != null) glyphs.put(i, source.imgs[i]);
        HashMap<String, Fount.Rect> positions = new HashMap<>();
        glyphs.forEach((code, img) -> positions.put(String.valueOf((char) code.intValue()),
                new Fount.Rect(img.srcX, img.srcY, ScaledUiImage.dimension(img.srcWidth, factor), ScaledUiImage.dimension(img.srcHeight, factor))));
        Fount copy = new Fount(source.img, positions, ScaledUiImage.dimension(source.lineHeight, factor),
                (int) Math.round(source.letterSpacing * factor), (int) Math.round(source.letterOffset * factor));
        cache.put(source, copy);
        glyphs.forEach((code, img) -> {
            Img scaled = new ScaledUiImage(img, factor);
            if (code < copy.imgs.length) copy.imgs[code] = scaled; else copy.extended.put(code, scaled);
        });
        for (Fount sub : source.subFounts) copy.withSubFount(scaleFont(sub, factor, cache));
        return copy;
    }
}

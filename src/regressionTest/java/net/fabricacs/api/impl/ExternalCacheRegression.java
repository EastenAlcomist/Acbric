/* ExternalCacheRegression.java — 无图形文件回归：资源更新、旧 raw、损坏修复、嵌套路径及实例分离。 */
package net.fabricacs.api.impl;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

public final class ExternalCacheRegression {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; System.out.println("PASS external cache: " + label); }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        String install = System.getProperty("acbric.external.install"), instance = System.getProperty("acbric.external.instance");
        try {
            Path resource = root.resolve("资源/images"); Files.createDirectories(resource);
            Path png = Files.writeString(resource.resolve("sprite.png"), "image-A");
            Path raw = Files.write(resource.resolve("sprite.png.tex"), new byte[1024]);
            Files.setLastModifiedTime(raw, FileTime.fromMillis(Files.getLastModifiedTime(png).toMillis() + 1000));
            System.clearProperty("acbric.external.install");
            check(ExternalTextureCache.image(png.toFile()).toPath().equals(png), "legacy path unchanged");
            System.setProperty("acbric.external.install", root.toString());
            Path target = root.resolve("instance"); System.setProperty("acbric.external.instance", target.toString());
            Path mapped = ExternalTextureCache.image(png.toFile()).toPath();
            check(mapped.startsWith(target) && Files.readString(mapped).equals("image-A"), "copy only requested PNG to writable instance");
            Path cachedRaw = mapped.getParent().getParent().resolve("generated/sprite.png.tex");
            check(!Files.exists(cachedRaw) && Files.exists(raw), "PNG takes precedence over unverified original raw without deleting it");
            check(ExternalTextureCache.image(png.toFile()).toPath().equals(mapped), "unchanged source reuses namespace");
            FileTime stamp = Files.getLastModifiedTime(png);
            Files.writeString(png, "image-B"); Files.setLastModifiedTime(png, stamp);
            ExternalTextureCache.invalidate();
            Path changed = ExternalTextureCache.image(png.toFile()).toPath();
            check(!changed.equals(mapped) && Files.readString(changed).equals("image-B"), "same-size/same-time edit rehashed after reload");
            check(!Files.exists(changed.getParent().getParent().resolve("generated/sprite.png.tex")), "newer timestamp on old raw cannot hide edited PNG");
            Files.writeString(changed, "broken"); ExternalTextureCache.invalidate();
            check(Files.readString(ExternalTextureCache.image(png.toFile()).toPath()).equals("image-B"), "corrupt mirrored PNG repaired on reload");
            Files.setLastModifiedTime(png, FileTime.fromMillis(Files.getLastModifiedTime(raw).toMillis() + 2000));
            Path newer = ExternalTextureCache.image(png.toFile()).toPath();
            check(!Files.exists(newer.getParent().getParent().resolve("generated/sprite.png.tex")), "stale original raw not reused");
            Path nested = resource.resolve("nested/ship.png"); Files.createDirectories(nested.getParent()); Files.writeString(nested, "nested");
            Path mirror = ExternalTextureCache.image(nested.toFile()).toPath();
            check(mirror.startsWith(target) && Files.readString(mirror).equals("nested"), "nested image never writes adjacent installation generated folder");
            Path gen = resource.getParent().resolve("generated"); Files.createDirectories(gen);
            Files.write(gen.resolve("raw-only.png.tex"), new byte[1024]);
            Path missing = ExternalTextureCache.image(resource.resolve("raw-only.png").toFile()).toPath();
            check(!Files.exists(missing) && Files.size(missing.getParent().getParent().resolve("generated/raw-only.png.tex")) == 1024, "cache-only distribution works without PNG");
            Files.write(resource.resolve("legacy-only.png.tex"), new byte[1024]);
            Path legacyOnly = ExternalTextureCache.image(resource.resolve("legacy-only.png").toFile()).toPath();
            check(Files.size(legacyOnly.getParent().getParent().resolve("generated/legacy-only.png.tex")) == 1024 && Files.exists(resource.resolve("legacy-only.png.tex")), "legacy images raw-only cache copied without migration");
            Files.writeString(gen.resolve("derived.png"), "generated-PNG");
            Path derived = ExternalTextureCache.image(gen.resolve("derived.png").toFile()).toPath();
            check(derived.startsWith(target) && Files.readString(derived).equals("generated-PNG"), "generated PNG lookup preserved inside instance");
            Path other = root.resolve("other/images"); Files.createDirectories(other); Path otherPng = Files.copy(png, other.resolve("sprite.png"));
            check(!ExternalTextureCache.image(otherPng.toFile()).toPath().equals(newer), "same image name from different sources isolated");
            System.setProperty("acbric.external.instance", root.resolve("instance-two").toString());
            check(ExternalTextureCache.image(png.toFile()).toPath().startsWith(root.resolve("instance-two")), "instance cache roots cannot cross");
            check(Files.readString(nested).equals("nested") && Files.exists(raw), "sources unchanged by mirror operations");
        } finally {
            if (install == null) System.clearProperty("acbric.external.install"); else System.setProperty("acbric.external.install", install);
            if (instance == null) System.clearProperty("acbric.external.instance"); else System.setProperty("acbric.external.instance", instance);
            ExternalTextureCache.invalidate();
        }
        return checks;
    }
}

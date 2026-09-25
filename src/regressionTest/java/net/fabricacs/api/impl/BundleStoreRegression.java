/*
 * BundleStoreRegression.java — 验证资源更新、冲突保留、旧目录迁移及中断后的备份恢复。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BundleStoreRegression {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    private static Path jar(Path file, Map<String, String> contents) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : contents.entrySet()) {
                zip.putNextEntry(new ZipEntry("acbric_vanilla/" + entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return file;
    }
    private static JSONObject state(Path dir) throws IOException {
        return new JSONObject(Files.readString(dir.resolve(BundledResourceStore.MANIFEST)));
    }
    private static void fails(Path jar, Path mods, String id, String label) throws Exception {
        boolean failed = false;
        try { BundledResourceStore.install(jar, mods, id, false); } catch (IOException expected) { failed = true; }
        check(failed, label);
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Path mods = root.resolve("mods"), dir = mods.resolve("managed_mod");
        Files.createDirectories(root);
        Path first = jar(root.resolve("first.jar"), Map.of("value.txt", "v1", "removed.txt", "remove", "edited.txt", "original", "deleted.txt", "original"));
        BundledResourceStore.install(first, mods, "managed_mod", false);
        check(state(dir).getJSONObject("files").has("value.txt"), "new install records ownership hashes");
        Files.writeString(dir.resolve("edited.txt"), "user edit");
        Files.delete(dir.resolve("deleted.txt"));
        Files.writeString(dir.resolve("unowned.txt"), "user extra");
        Files.writeString(dir.resolve("collision.txt"), "user collision");
        Path second = jar(root.resolve("second.jar"), Map.of("value.txt", "v2", "edited.txt", "new upstream", "deleted.txt", "new upstream", "added.txt", "new", "collision.txt", "bundle"));
        BundledResourceStore.install(second, mods, "managed_mod", false);
        check(Files.readString(dir.resolve("value.txt")).equals("v2") && Files.isRegularFile(dir.resolve("added.txt")), "update unchanged owned files and add new files");
        check(!Files.exists(dir.resolve("removed.txt")), "remove unchanged upstream-deleted file");
        check(Files.readString(dir.resolve("edited.txt")).equals("user edit") && !Files.exists(dir.resolve("deleted.txt")), "preserve user edit and user deletion");
        check(Files.readString(dir.resolve("unowned.txt")).equals("user extra") && Files.readString(dir.resolve("collision.txt")).equals("user collision"), "preserve unowned files and new-path collisions");
        check(state(dir).getJSONObject("conflicts").length() == 3, "persist three update conflicts");
        String revision = state(dir).getString("revision");
        BundledResourceStore.install(second, mods, "managed_mod", false);
        check(state(dir).getString("revision").equals(revision), "identical repeat does not create another transaction or backup");
        Path backups = root.resolve(".acbric-bundles/managed_mod/backups");
        try (var paths = Files.list(backups)) {
            Path backup = paths.findFirst().orElseThrow();
            check(Files.readString(backup.resolve("edited.txt")).equals("user edit"), "backup includes user changes before update");
        }
        Files.writeString(dir.resolve("edited.txt"), "new upstream");
        BundledResourceStore.install(second, mods, "managed_mod", false);
        check(!state(dir).getJSONObject("conflicts").has("edited.txt"), "manually resolved content clears conflict");
        Path third = jar(root.resolve("third.jar"), Map.of("value.txt", "v3"));
        BundledResourceStore.install(third, mods, "managed_mod", false);
        check(Files.exists(dir.resolve("collision.txt")) && Files.exists(dir.resolve("unowned.txt")), "upstream removal cannot delete unowned collision files");

        Path legacy = Files.createDirectories(mods.resolve("legacy_mod"));
        Files.writeString(legacy.resolve("value.txt"), "v1");
        Files.writeString(legacy.resolve("edited.txt"), "legacy edit");
        BundledResourceStore.install(first, mods, "legacy_mod", false);
        check(!Files.exists(legacy.resolve(BundledResourceStore.MANIFEST)), "legacy directory is not automatically adopted");
        BundledVanillaModMigration.main(new String[]{first.toString(), mods.toString(), "legacy_mod"});
        check(state(legacy).getJSONObject("files").has("value.txt") && !state(legacy).getJSONObject("files").has("edited.txt"), "explicit migration adopts only matching files");
        check(Files.readString(legacy.resolve("edited.txt")).equals("legacy edit") && !Files.exists(legacy.resolve("deleted.txt")), "migration preserves changed and missing legacy files");
        BundledResourceStore.install(second, mods, "legacy_mod", false);
        check(Files.readString(legacy.resolve("value.txt")).equals("v2") && !Files.exists(legacy.resolve("deleted.txt")), "migrated owned file updates while legacy missing file stays protected");

        Path invalid = jar(root.resolve("bad.jar"), Map.of("../escape.txt", "bad"));
        String before = Files.readString(dir.resolve(BundledResourceStore.MANIFEST));
        fails(invalid, mods, "managed_mod", "invalid update is rejected");
        check(Files.readString(dir.resolve("value.txt")).equals("v3") && Files.readString(dir.resolve(BundledResourceStore.MANIFEST)).equals(before), "invalid update leaves installed files and manifest unchanged");
        fails(jar(root.resolve("reserved.jar"), Map.of(".ACBRIC-BUNDLE.JSON", "bad")), mods, "reserved_mod", "bundle cannot supply ownership manifest");
        Files.writeString(dir.resolve(BundledResourceStore.MANIFEST), "bad JSON");
        fails(second, mods, "managed_mod", "corrupt ownership manifest fails closed");
        Files.writeString(dir.resolve(BundledResourceStore.MANIFEST), before);

        Path control = root.resolve(".acbric-bundles/managed_mod");
        try (var channel = java.nio.channels.FileChannel.open(control.resolve("lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            fails(second, mods, "managed_mod", "concurrent updater cannot acquire ownership lock");
            check(Files.readString(dir.resolve("value.txt")).equals("v3"), "busy updater leaves installed content unchanged");
        }
        // 在原目录已移动后让发布失败：缺少暂存目录应恢复备份，不能丢失 MOD。
        var snapshotMethod = BundledResourceStore.class.getDeclaredMethod("snapshot", Path.class);
        snapshotMethod.setAccessible(true);
        var publishMethod = BundledResourceStore.class.getDeclaredMethod("publish", Path.class, Path.class, Path.class, String.class, String.class, boolean.class, Map.class);
        publishMethod.setAccessible(true);
        String failedRevision = UUID.randomUUID().toString();
        boolean rolledBack = false;
        try {
            publishMethod.invoke(null, control, dir, control.resolve("stage-" + failedRevision), "managed_mod", failedRevision, true, snapshotMethod.invoke(null, dir));
        } catch (java.lang.reflect.InvocationTargetException e) { rolledBack = e.getCause() instanceof IOException; }
        check(rolledBack && Files.readString(dir.resolve("value.txt")).equals("v3") && !Files.exists(control.resolve("pending.json")), "failed publication restores original and clears journal");

        Path allRemoved = mods.resolve("removed_bundle");
        BundledResourceStore.install(first, mods, "removed_bundle", false);
        Files.writeString(allRemoved.resolve("edited.txt"), "keep user data");
        Path empty = jar(root.resolve("empty.jar"), Map.of());
        BundledResourceStore.install(empty, mods, "removed_bundle", false);
        check(!Files.exists(allRemoved.resolve("value.txt")) && Files.readString(allRemoved.resolve("edited.txt")).equals("keep user data"), "entire bundle removal deletes only unchanged owned files");

        String transaction = UUID.randomUUID().toString();
        Path backup = control.resolve("backups/" + transaction);
        Files.move(dir, backup);
        Files.createDirectory(control.resolve("stage-" + transaction));
        JSONObject pending = new JSONObject().put("schema", 1).put("id", "managed_mod").put("revision", transaction).put("hadOriginal", true);
        Files.writeString(control.resolve("pending.json"), pending.toString());
        fails(invalid, mods, "managed_mod", "recovery runs before an invalid incoming bundle is rejected");
        check(Files.readString(dir.resolve("value.txt")).equals("v3") && !Files.exists(control.resolve("pending.json")), "interrupted replacement restores original directory");

        transaction = state(dir).getString("revision");
        backup = control.resolve("backups/" + transaction);
        Files.createDirectories(backup);
        Files.writeString(backup.resolve("recovery-marker"), "old backup");
        pending.put("revision", transaction);
        Files.writeString(control.resolve("pending.json"), pending.toString());
        BundledResourceStore.install(third, mods, "managed_mod", false);
        check(!Files.exists(control.resolve("pending.json")) && Files.exists(backup.resolve("recovery-marker")), "completed replacement finalizes journal and retains backup");

        transaction = UUID.randomUUID().toString();
        backup = Files.createDirectories(control.resolve("backups/" + transaction));
        pending.put("revision", transaction);
        Files.writeString(control.resolve("pending.json"), pending.toString());
        fails(third, mods, "managed_mod", "ambiguous recovery destination is not overwritten");
        check(Files.readString(dir.resolve("value.txt")).equals("v3") && Files.isDirectory(backup), "ambiguous recovery preserves destination and backup");
        return checks;
    }
}

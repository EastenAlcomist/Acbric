/* JavaModManagerRegression.java — 启停配置、依赖预检查、磁盘冲突与持久化状态的隔离验证。 */
package net.fabricacs.api.impl;

import net.fabricacs.management.ModSelection;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.metadata.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class JavaModManagerRegression {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); checks++; System.out.println("PASS mod manager: " + text); }
    private static void rejects(Action action, String text) throws Exception {
        try { action.run(); } catch (IOException ex) { check(true, text); return; }
        throw new AssertionError(text);
    }
    private static String metadata(String id, String rest) { return "{\"schemaVersion\":1,\"id\":\""+id+"\",\"version\":\"1.0.0\""+rest+"}"; }
    private static ModMetadata parse(Path root, String json) throws Exception {
        return ModMetadataParser.parseMetadata(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "fixture", List.of(), new VersionOverrides(), new DependencyOverrides(root), false);
    }
    private static void jar(Path path, String json) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("fabric.mod.json")); out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
        }
    }
    private static JavaModManager manager(Path mods, Path config) throws Exception {
        var constructor = JavaModManager.class.getDeclaredConstructor(Path.class, Path.class, Collection.class);
        constructor.setAccessible(true);
        return (JavaModManager) constructor.newInstance(mods, config, List.of());
    }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        String loader = System.getProperty(ModSelection.LOADER_PROPERTY), effective = System.getProperty(ModSelection.EFFECTIVE_PROPERTY), external = System.getProperty(ModSelection.EXTERNAL_PROPERTY);
        Path config = root.resolve("config"), file = ModSelection.file(config);
        try {
            var missing = ModSelection.read(file);
            check(missing.disabled().isEmpty() && !Files.exists(file), "missing config defaults without writing");
            var saved = ModSelection.write(file, missing, Set.of("example_mod"));
            check(ModSelection.read(file).disabled().equals(Set.of("example_mod")), "selection persists");
            byte[] bytes = saved.bytes(); bytes[0] = 0;
            check(saved.bytes()[0] == '{', "snapshot bytes defensively copied");
            rejects(() -> ModSelection.write(file, missing, Set.of()), "stale writer cannot overwrite configuration");
            String original = Files.readString(file);
            for (String id : List.of("acbric_api", "fabricloader", "airships", "java", "mixinextras", "../bad", "Bad"))
                rejects(() -> ModSelection.write(file, saved, Set.of(id)), "protected or invalid ID rejected: " + id);
            check(Files.readString(file).equals(original), "rejected changes preserve bytes");
            String[] invalid = {"[]", "{", "{}", "{\"disabledMods\":null}", "{\"disabledMods\":[1]}",
                    "{\"disabledMods\":[\"test_mod\",\"test_mod\"]}", "{\"disabledMods\":[],\"disabledMods\":[]}",
                    "{\"disabledMods\":[],\"unknown\":1}", "{\"schemaVersion\":2,\"disabledMods\":[]}",
                    "{\"schemaVersion\":1.0,\"disabledMods\":[]}", "{\"disabledMods\":[]} false"};
            for (int i=0;i<invalid.length;i++) {
                Files.writeString(file,invalid[i]); rejects(()->ModSelection.read(file),"malformed config rejected " + i);
                check(Files.readString(file).equals(invalid[i]), "invalid config preserved " + i);
            }
            Files.writeString(file, "{\"disabledMods\":[\"example_mod\"]}");
            System.setProperty(ModSelection.LOADER_PROPERTY,"external_mod");
            ModSelection.applyAtStartup(config);
            check(ModSelection.ids(System.getProperty(ModSelection.EFFECTIVE_PROPERTY)).equals(Set.of("example_mod","external_mod")), "startup merges launch argument selection");
            check(System.getProperty(ModSelection.EXTERNAL_PROPERTY).equals("external_mod"), "external selection kept separate");
            check(ModSelection.ids(System.getProperty(ModSelection.LOADER_PROPERTY)).contains("example_mod"), "Loader receives disabled IDs before discovery");
            System.setProperty(ModSelection.LOADER_PROPERTY,"acbric_api");
            rejects(()->ModSelection.applyAtStartup(config), "core cannot be disabled by launch property");
            System.setProperty(ModSelection.EXTERNAL_PROPERTY, "");
            Files.writeString(file,"{\"disabledMods\":[]}");
            var provider=parse(root,metadata("base_mod",",\"provides\":[\"alias_mod\"]"));
            var consumer=parse(root,metadata("user_mod",",\"depends\":{\"alias_mod\":\">=1.0.0\"}"));
            JavaModManager.validateDependencies(List.of(provider,consumer)); check(true,"provided alias satisfies dependency");
            rejects(()->JavaModManager.validateDependencies(List.of(consumer)), "required dependency missing rejects change");
            var newer=parse(root,metadata("user_mod",",\"depends\":{\"base_mod\":\">=2.0.0\"}"));
            rejects(()->JavaModManager.validateDependencies(List.of(provider,newer)), "wrong dependency version rejected");
            var conflict=parse(root,metadata("user_mod",",\"breaks\":{\"base_mod\":\"*\"}"));
            rejects(()->JavaModManager.validateDependencies(List.of(provider,conflict)), "hard conflict rejected");
            var optional=parse(root,metadata("user_mod",",\"suggests\":{\"missing_mod\":\"*\"}"));
            JavaModManager.validateDependencies(List.of(optional)); check(true,"optional dependency does not block");
            Path mods=root.resolve("mods");Files.createDirectories(mods);
            jar(mods.resolve("base.jar"),metadata("base_mod",""));
            jar(mods.resolve("user.jar"),metadata("user_mod",",\"depends\":{\"base_mod\":\"*\"}"));
            JavaModManager manager=manager(mods,config);
            check(manager.entry("base_mod").manageable(),"unloaded disk MOD remains manageable");
            check(manager.status("base_mod",false).contains("Enable on restart"),"installed but not loaded shows pending enable");
            rejects(()->manager.toggle("base_mod"),"dependent must be disabled first");
            manager.toggle("user_mod"); manager.toggle("base_mod");
            check(!manager.enabledNext("base_mod") && !manager.enabledNext("user_mod"),"dependent then provider can be disabled");
            check(Files.isRegularFile(mods.resolve("base.jar")),"disable leaves archive in place");
            rejects(()->manager.toggle("user_mod"),"enable requires provider first");
            manager.toggle("base_mod");manager.toggle("user_mod");
            check(manager.enabledNext("base_mod") && manager.enabledNext("user_mod"),"reenable in dependency order");
            Files.writeString(file,"{\"disabledMods\":[\"external_edit\"]}");
            rejects(()->manager.toggle("user_mod"),"external config edits are preserved");
            JavaModManager changed=manager(mods,config);
            jar(mods.resolve("user.jar"),metadata("user_mod",",\"name\":\"Changed\""));
            rejects(()->changed.toggle("user_mod"),"changed archive rejected before writing");
            JavaModManager added=manager(mods,config);
            jar(mods.resolve("new.jar"),metadata("new_mod",""));
            rejects(()->added.toggle("user_mod"),"new archive invalidates inventory");
            try (var channel=java.nio.channels.FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.WRITE);var lock=channel.lock()) {
                var current=ModSelection.read(file);
                rejects(()->ModSelection.write(file,current,Set.of()),"concurrent lock rejected");
            }
            check(System.getProperty(ModSelection.EFFECTIVE_PROPERTY).contains("example_mod"),"UI selection never changes startup effective snapshot");
        } finally {
            restore(ModSelection.LOADER_PROPERTY,loader);restore(ModSelection.EFFECTIVE_PROPERTY,effective);restore(ModSelection.EXTERNAL_PROPERTY,external);
        }
        return checks;
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}

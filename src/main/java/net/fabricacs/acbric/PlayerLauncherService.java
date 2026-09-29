/* PlayerLauncherService.java — 玩家启动器流程：读取配置、自动检查保存、启动及本地维护交接。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

class PlayerLauncherService {
    record State(Path instance, Path game, boolean ready) {}
    final Path bundle;
    PlayerLauncherService(Path bundle) { this.bundle = bundle.toAbsolutePath().normalize(); }

    State inspect() throws IOException {
        String binding = FrameworkEntry.read(bundle);
        Path instance = binding == null ? bundle.resolve("instances/default") : FrameworkEntry.resolve(bundle, binding);
        Optional<InstanceSetup.Saved> saved = binding == null ? InstanceSetup.loadSaved(instance) : Optional.of(InstanceSetup.read(instance));
        if (saved.isEmpty()) return new State(instance, null, false);
        var config = saved.orElseThrow();
        // 搬迁或游戏丢失时保留原实例，重新选游戏不会悄悄新建另一份存档目录。
        boolean ready = binding != null && config.framework().equals(bundle) && Files.isRegularFile(config.game().resolve("Airships.json"));
        return new State(instance, config.game(), ready);
    }

    List<Path> discover() { return SteamGameLocator.find(); }

    State configure(State state, Path game, String language) throws IOException {
        InstanceSetup.save(InstanceSetup.preview(bundle, game, state.instance(), language));
        return inspect();
    }

    int launch() throws IOException {
        State state = inspect();
        if (!state.ready()) throw new IOException("SETUP_REQUIRED");
        return ExternalLauncher.run(new String[]{"--game-dir", state.game().toString(), "--instance-dir", state.instance().toString()});
    }

    String language() throws IOException {
        Path file = bundle.resolve(".acbric-language"); ModSelection.rejectLinks(file);
        if (!Files.exists(file)) {
            String binding = FrameworkEntry.read(bundle);
            return binding == null ? "zh" : InstanceSetup.read(FrameworkEntry.resolve(bundle, binding)).language();
        }
        if (Files.size(file) > 16) throw new IOException("LANGUAGE_INVALID");
        String value = Files.readString(file).trim();
        if (!Set.of("zh", "en").contains(value)) throw new IOException("LANGUAGE_INVALID");
        return value;
    }

    void language(String value) throws IOException {
        if (!Set.of("zh", "en").contains(value)) throw new IOException("LANGUAGE_INVALID");
        Path file = bundle.resolve(".acbric-language"); ModSelection.rejectLinks(file);
        Path temp = Files.createTempFile(bundle, ".acbric-language-", ".tmp");
        try { Files.writeString(temp, value); Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }

    /** 将维护脚本复制到临时目录；维护进程等待当前 JVM 退出后才申请独占锁。 */
    Path maintenance(Path archive, boolean restore, String language) throws IOException {
        if (!restore && (archive == null || !Files.isRegularFile(archive))) throw new IOException("PACKAGE_REQUIRED");
        Path job = Files.createTempDirectory("acbric-maintenance-ui-");
        for (String name : List.of("maintenance.ps1", "player-maintenance.ps1")) Files.copy(bundle.resolve(name), job.resolve(name));
        String json = "{\"target\":" + ExternalLaunchSettings.quote(bundle.toString())
                + ",\"archive\":" + ExternalLaunchSettings.quote(archive == null ? "" : archive.toAbsolutePath().toString())
                + ",\"restore\":" + restore + ",\"parent\":" + ProcessHandle.current().pid()
                + ",\"language\":" + ExternalLaunchSettings.quote(language) + "}";
        Files.writeString(job.resolve("request.json"), json, StandardCharsets.UTF_8);
        return job;
    }
}

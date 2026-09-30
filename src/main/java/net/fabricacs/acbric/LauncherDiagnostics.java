/* LauncherDiagnostics.java — 启动器本地诊断；普通界面不展示堆栈，导出只含有界日志，不收集存档。 */
package net.fabricacs.acbric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

final class LauncherDiagnostics {
    final Path directory;
    private Path last;
    LauncherDiagnostics(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); }

    void record(Throwable error) {
        try {
            last = Files.createTempFile(directory, "error-", ".log");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(last, StandardCharsets.UTF_8))) {
                out.println("Acbric " + ExternalLauncher.CORE_VERSION + " / " + Instant.now()); error.printStackTrace(out);
            }
        } catch (IOException ex) { error.addSuppressed(ex); error.printStackTrace(); }
    }

    String details() {
        try { return last == null ? "" : Files.readString(last); }
        catch (IOException ex) { return ex.toString(); }
    }

    void export(Path output, Path instance) throws IOException {
        List<Path> roots = new ArrayList<>(); roots.add(directory);
        if (instance != null) roots.add(instance.resolve("logs/acbric/launcher"));
        // CREATE_NEW 防止覆盖已有报告；只导出普通日志文件，不跟随目录链接。
        var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW);
        try (stream; var zip = new ZipOutputStream(stream)) {
            zip.putNextEntry(new ZipEntry("about.txt"));
            zip.write(("Acbric " + ExternalLauncher.CORE_VERSION + "\nLocal launcher logs; may include local paths. No saves collected.\n").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            int count = 0; long total = 0;
            for (Path root : roots) {
                net.fabricacs.management.ModSelection.rejectLinks(root);
                if (!Files.isDirectory(root)) continue;
                List<Path> logs;
                try (var files = Files.list(root)) {
                    logs = files.filter(p -> p.getFileName().toString().endsWith(".log") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                            .sorted(Comparator.comparingLong(LauncherDiagnostics::modified).reversed()).limit(10).toList();
                }
                for (Path log : logs) {
                    net.fabricacs.management.ModSelection.rejectLinks(log);
                    // 读取有界快照，避免游戏同时追加日志导致报告无限膨胀。
                    byte[] data;
                    try (var input = Files.newInputStream(log)) { data = input.readNBytes(1024 * 1024); }
                    if (total + data.length > 10 * 1024 * 1024) continue;
                    zip.putNextEntry(new ZipEntry("logs/" + (++count) + "-" + log.getFileName())); zip.write(data); zip.closeEntry(); total += data.length;
                }
            }
        } catch (IOException | RuntimeException ex) {
            // 只清理本次成功创建的未完成报告；CREATE_NEW 失败不触碰既有文件。
            try { Files.deleteIfExists(output); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
    private static long modified(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException ex) { return 0; } }

    static String friendly(Throwable ex, boolean zh) {
        String text = ex.toString();
        if (text.contains("BUSY")) return zh ? "游戏或更新程序正在使用这些文件。请关闭后重试。" : "The game or updater is using these files. Close it and try again.";
        if (text.contains("VANILLA_DATA_MISSING")) return zh ? "没有找到原版数据文件夹。请先在原版游戏中运行一次，再点一键同步；也可以选择环境隔离使用副本。" : "No vanilla data folder was found. Run the original game once, then select Sync now — or isolate this instance to use its own copies.";
        if (text.contains("VANILLA_DATA_READONLY") || text.contains("SHARED_DATA_UNAVAILABLE")) return zh ? "原版数据文件夹当前不可写。请检查权限，或选择环境隔离改用实例副本。" : "The vanilla data folder is not writable. Check permissions, or isolate this instance to use its own copies.";
        if (text.contains("ENVIRONMENT_CONFIG_INVALID")) return zh ? "已保存的数据环境配置无法读取。文件已保留，请导出诊断或选择环境隔离重建。" : "The saved environment configuration could not be read. The file is preserved; export diagnostics or isolate the instance again.";
        if (text.contains("SYNC_LINK_UNSUPPORTED") || text.contains("SYNC_SOURCE_INVALID") || text.contains("SYNC_TARGET_INVALID"))
            return zh ? "原版数据里有链接或结构异常的条目，无法安全同步。请改成普通文件夹后重试。" : "The vanilla data contains linked or unexpected items that cannot be synced safely. Replace them with regular folders and try again.";
        if (text.contains("CONFIG_INVALID") || text.contains("ENTRY_MODIFIED") || text.contains("INSTANCE_NOT_FRESH"))
            return zh ? "已有配置无法安全读取。文件已保留，请在高级设置中检查原来的数据目录，或导出诊断寻求帮助。" : "Existing setup could not be read safely. Files are preserved. Check the original data folder in Advanced settings or export diagnostics for help.";
        if (text.contains("AccessDenied") || text.contains("PUBLISH_DENIED")) return zh ? "暂时无法保存配置。请稍后重试，仍然失败时可导出诊断信息。" : "Setup could not be saved. Try again shortly, or export diagnostics if this continues.";
        if (text.contains("INSTALL_") || text.contains("ARCHIVE_") || text.contains("CONFIG_MISSING") || text.contains("SETUP_REQUIRED"))
            return zh ? "此处没有找到可用的游戏。请选择完整的游戏文件夹。" : "A usable game installation was not found here. Select the complete game folder.";
        if (text.contains("BUNDLE_") || text.contains("CORE_")) return zh ? "启动器文件不完整或版本不一致。请重新解压完整的 Acbric 包。" : "Launcher files are incomplete or mismatched. Extract a complete Acbric package again.";
        return zh ? "这次操作没有完成。可以重试，或从帮助中导出诊断信息。" : "This operation could not be completed. Try again or export diagnostics from Help.";
    }
}

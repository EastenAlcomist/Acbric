/*
 * SteamLaunchProbe.java — 由原版 Packr 启动器在进程内加载，记录 Steam 环境、App ID 与 UGC 可用性。
 */
package net.fabricacs.acbric;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamPublishedFileID;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUGC;
import com.codedisaster.steamworks.SteamUGCCallback;
import com.codedisaster.steamworks.SteamUGCQuery;
import com.codedisaster.steamworks.SteamUGCDetails;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class SteamLaunchProbe {
    private static final String RESULT_FILE = "steam-launch-result.properties";
    private static final String RESULT_PROPERTY = "acbric.probe.result";

    private SteamLaunchProbe() {
    }

    public static void main(String[] args) {
        Properties result = new Properties();
        boolean steamInitialized = false;
        SteamUGC ugc = null;
        int holdSeconds = holdSeconds(args);
        try {
            captureProcess(result, args);
            captureClassPath(result);
            result.setProperty("steam.runningBeforeLoad", String.valueOf(SteamAPI.isSteamRunning()));
            SteamAPI.loadLibraries();
            result.setProperty("steam.nativeLoaded", "true");
            steamInitialized = SteamAPI.init();
            result.setProperty("steam.initialized", String.valueOf(steamInitialized));

            if (steamInitialized) {
                SteamUtils utils = new SteamUtils(new SteamUtilsCallback() {
                    @Override
                    public void onSteamShutdown() {
                    }
                });
                result.setProperty("steam.appId", String.valueOf(utils.getAppID()));
                result.setProperty("steam.overlayEnabled", String.valueOf(utils.isOverlayEnabled()));
                result.setProperty("steam.secondsSinceAppActive",
                        String.valueOf(utils.getSecondsSinceAppActive()));

                try {
                    ugc = new SteamUGC(new NoopUGCCallback());
                    result.setProperty("steam.ugcAvailable", "true");
                    result.setProperty("steam.subscribedItems",
                            String.valueOf(ugc.getNumSubscribedItems()));
                } catch (Throwable error) {
                    result.setProperty("steam.ugcAvailable", "false");
                    result.setProperty("steam.ugcError", String.valueOf(error));
                }
            }
            result.setProperty("probe.completed", "true");
        } catch (Throwable error) {
            result.setProperty("probe.completed", "false");
            result.setProperty("probe.error", String.valueOf(error));
        } finally {
            if (ugc != null) {
                try {
                    ugc.dispose();
                } catch (Throwable ignored) {
                }
            }
            if (steamInitialized) {
                try {
                    SteamAPI.shutdown();
                } catch (Throwable ignored) {
                }
            }
            result.setProperty("hold.seconds", String.valueOf(holdSeconds));
            Path output = resultPath();
            try {
                writeAtomically(output, result);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
            }
        }

        try {
            Thread.sleep(Math.max(0, holdSeconds) * 1000L);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }

        boolean transportCompleted = "true".equals(result.getProperty("probe.completed"));
        System.exit(transportCompleted ? 0 : 3);
    }

    private static void captureClassPath(Properties result) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> knot = Class.forName(
                    "net.fabricmc.loader.impl.launch.knot.KnotClient", false, loader);
            result.setProperty("loader.knotClass", String.valueOf(knot != null));
        } catch (Throwable error) {
            result.setProperty("loader.knotClass", "false");
            result.setProperty("loader.knotError", String.valueOf(error));
        }
        try {
            Class<?> provider = Class.forName(
                    "net.fabricacs.acbric.AirshipsGameProvider", false, loader);
            result.setProperty("loader.gameProviderClass", String.valueOf(provider != null));
        } catch (Throwable error) {
            result.setProperty("loader.gameProviderClass", "false");
            result.setProperty("loader.gameProviderError", String.valueOf(error));
        }
        try {
            var services = loader.getResources(
                    "META-INF/services/net.fabricmc.loader.impl.game.GameProvider");
            boolean found = false;
            while (services.hasMoreElements() && !found) {
                try (var input = services.nextElement().openStream()) {
                    found = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("net.fabricacs.acbric.AirshipsGameProvider");
                }
            }
            result.setProperty("loader.gameProviderService", String.valueOf(found));
        } catch (Throwable error) {
            result.setProperty("loader.gameProviderService", "false");
            result.setProperty("loader.gameProviderServiceError", String.valueOf(error));
        }
    }

    private static Path resultPath() {
        String configured = System.getProperty(RESULT_PROPERTY, "");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize().resolve(RESULT_FILE);
    }

    private static void captureProcess(Properties result, String[] args) {
        result.setProperty("args", String.join("|", args));
        result.setProperty("cwd", Path.of("").toAbsolutePath().normalize().toString());
        result.setProperty("jvm.javaVersion", System.getProperty("java.version", ""));
        result.setProperty("jvm.javaHome", System.getProperty("java.home", ""));

        for (String name : new String[]{
                "SteamAppId",
                "SteamGameId",
                "SteamClientLaunch",
                "SteamOverlayGameId",
                "SteamPath",
                "SteamClientDll",
                "SteamClientDll64"
        }) {
            result.setProperty("env." + name, System.getenv().getOrDefault(name, ""));
        }

        ProcessHandle current = ProcessHandle.current();
        result.setProperty("process.pid", String.valueOf(current.pid()));
        current.info().command().ifPresent(command ->
                result.setProperty("process.command", command));
        current.info().commandLine().ifPresent(commandLine ->
                result.setProperty("process.commandLine", commandLine));
        current.parent().ifPresent(parent -> {
            result.setProperty("process.parentPid", String.valueOf(parent.pid()));
            parent.info().command().ifPresent(command ->
                    result.setProperty("process.parentCommand", command));
            parent.info().commandLine().ifPresent(commandLine ->
                    result.setProperty("process.parentCommandLine", commandLine));
        });
    }

    private static int holdSeconds(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("hold=")) {
                continue;
            }
            try {
                return Math.max(0, Math.min(30, Integer.parseInt(arg.substring(5))));
            } catch (NumberFormatException ignored) {
                return 2;
            }
        }
        return 2;
    }

    private static void writeAtomically(Path output, Properties values) throws IOException {
        Path parent = output.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".steam-launch-result-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                values.store(stream, "Acbric Steam launch probe");
            }
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 探针只查询订阅数量，不执行订阅、下载、上传或删除。 */
    private static final class NoopUGCCallback implements SteamUGCCallback {
        @Override
        public void onUGCQueryCompleted(SteamUGCQuery query, int results, int total,
                                        boolean cached, SteamResult result) {
        }

        @Override
        public void onSubscribeItem(SteamPublishedFileID item, SteamResult result) {
        }

        @Override
        public void onUnsubscribeItem(SteamPublishedFileID item, SteamResult result) {
        }

        @Override
        public void onRequestUGCDetails(SteamUGCDetails details, SteamResult result) {
        }

        @Override
        public void onCreateItem(SteamPublishedFileID item, boolean userNeedsToAcceptWorkshopLegalAgreement,
                                 SteamResult result) {
        }

        @Override
        public void onSubmitItemUpdate(SteamPublishedFileID item,
                                       boolean userNeedsToAcceptWorkshopLegalAgreement,
                                       SteamResult result) {
        }

        @Override
        public void onDownloadItemResult(int appId, SteamPublishedFileID item, SteamResult result) {
        }

        @Override
        public void onUserFavoriteItemsListChanged(SteamPublishedFileID item, boolean wasAddRequest,
                                                   SteamResult result) {
        }

        @Override
        public void onSetUserItemVote(SteamPublishedFileID item, boolean voteUp, SteamResult result) {
        }

        @Override
        public void onGetUserItemVote(SteamPublishedFileID item, boolean votedUp, boolean votedDown,
                                      boolean voteSkipped, SteamResult result) {
        }

        @Override
        public void onStartPlaytimeTracking(SteamResult result) {
        }

        @Override
        public void onStopPlaytimeTracking(SteamResult result) {
        }

        @Override
        public void onStopPlaytimeTrackingForAllItems(SteamResult result) {
        }

        @Override
        public void onDeleteItem(SteamPublishedFileID item, SteamResult result) {
        }
    }
}

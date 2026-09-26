/*
 * AirshipsGameProvider.java — 启动层入口：定位 Airships 配置、组装游戏类路径并通过 Fabric 调用游戏主类。
 * 启动库必须留在父加载器，避免 Loader、Mixin、ASM 在游戏加载器中产生第二份类身份。
 */
package net.fabricacs.acbric;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.Arguments;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public final class AirshipsGameProvider implements GameProvider {
    private static final String DEFAULT_MAIN_CLASS = "com.zarkonnen.airships.Main";
    private static final Pattern MAIN_CLASS_PATTERN = Pattern.compile("\"mainClass\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CLASSPATH_PATTERN = Pattern.compile("\"classPath\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");

    private final Arguments arguments = new Arguments();
    private final GameTransformer transformer = new GameTransformer();
    private final List<Path> gameClassPath = new ArrayList<>();

    private Path gameDirectory;
    private Path libsDirectory;
    private String mainClass = DEFAULT_MAIN_CLASS;
    private GameBuildIdentity buildIdentity = GameBuildIdentity.unknown();
    private LaunchDiagnostics diagnostics;
    private ExternalPreflight.InstanceLease externalLease;

    @Override
    public String getGameId() {
        return "airships";
    }

    @Override
    public String getGameName() {
        return "Airships: Conquer the Skies";
    }

    @Override
    public String getRawGameVersion() {
        return buildIdentity.rawVersion();
    }

    @Override
    public String getNormalizedGameVersion() {
        return buildIdentity.normalizedVersion();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        ModMetadata metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                .setName(getGameName())
                .build();
        return Collections.singletonList(new BuiltinMod(Collections.unmodifiableList(gameClassPath), metadata));
    }

    @Override
    public String getEntrypoint() {
        return mainClass;
    }

    @Override
    public Path getLaunchDirectory() {
        return gameDirectory;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return true;
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        return EnumSet.noneOf(BuiltinTransform.class);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        arguments.parse(args);

        if (System.getProperty(ExternalGameInstallation.INSTALL_PROPERTY) != null
                || System.getProperty(ExternalGameInstallation.INSTANCE_PROPERTY) != null) {
            return locateExternal();
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path candidateGameDir = findGameDirectory(cwd);
        if (candidateGameDir == null) {
            return false;
        }

        gameDirectory = candidateGameDir;
        gameClassPath.clear();
        libsDirectory = findLibsDirectory(cwd, gameDirectory);
        if (libsDirectory == null) {
            return false;
        }

        try {
            readAirshipsConfig(gameDirectory.resolve("Airships.json"));
            collectClassPath();
            buildIdentity = GameBuildIdentity.inspect(gameClassPath);
            diagnostics = new LaunchDiagnostics(gameDirectory, buildIdentity);
            configureNativeLibraries();
            net.fabricacs.management.ModSelection.applyAtStartup(gameDirectory.resolve("config"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to locate Airships launch files", e);
        }

        return !gameClassPath.isEmpty();
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        transformer.locateEntrypoints(launcher, gameClassPath);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        // Loader 已解析 MOD，尚未调用 preLaunch；先确认隔离适配来自匹配的发行核心。
        if (externalLease != null && ExternalGameInstallation.MAIN.equals(mainClass)) {
            var core = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("acbric_api")
                    .orElseThrow(() -> new IllegalStateException("CORE_MISSING / 缺少核心 API"));
            Path expected = Path.of(System.getProperty("acbric.external.core")).toAbsolutePath().normalize();
            if (!core.getMetadata().getVersion().getFriendlyString().equals(ExternalLauncher.CORE_VERSION)
                    || core.getOrigin().getPaths().stream().noneMatch(path -> path.toAbsolutePath().normalize().equals(expected)))
                throw new IllegalStateException("CORE_MISMATCH / 实际加载的核心 API 与发行包不匹配");
        }
        for (Path path : gameClassPath) {
            launcher.addToClassPath(path);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        Thread.currentThread().setContextClassLoader(loader);
        if (diagnostics != null) diagnostics.phase("ENTERING_MAIN", null);

        try {
            Class<?> main = Class.forName(mainClass, true, loader);
            Method mainMethod = main.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) getLaunchArguments(false));
            if (diagnostics != null) diagnostics.phase("MAIN_RETURNED", null);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (diagnostics != null) diagnostics.phase("MAIN_FAILED", target);
            if (target instanceof RuntimeException) {
                throw (RuntimeException) target;
            }
            if (target instanceof Error) {
                throw (Error) target;
            }
            throw new RuntimeException(target);
        } catch (ReflectiveOperationException e) {
            if (diagnostics != null) diagnostics.phase("MAIN_FAILED", e);
            throw new RuntimeException("Failed to launch " + mainClass, e);
        } catch (RuntimeException | Error e) {
            if (diagnostics != null) diagnostics.phase("MAIN_FAILED", e);
            throw e;
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return arguments.getExtraArgs().toArray(new String[0]);
    }

    @Override
    public boolean canOpenErrorGui() {
        return true;
    }

    @Override
    public boolean hasAwtSupport() {
        return true;
    }

    private static Path findGameDirectory(Path cwd) {
        if (Files.isRegularFile(cwd.resolve("Airships.json"))) {
            return cwd;
        }

        Path nested = cwd.resolve("game");
        if (Files.isRegularFile(nested.resolve("Airships.json"))) {
            return nested;
        }

        return null;
    }

    private static Path findLibsDirectory(Path cwd, Path gameDir) {
        Path siblingFromRoot = cwd.resolve("libs");
        if (Files.isDirectory(siblingFromRoot)) {
            return siblingFromRoot;
        }

        Path siblingFromGame = gameDir.resolveSibling("libs");
        if (Files.isDirectory(siblingFromGame)) {
            return siblingFromGame;
        }

        return null;
    }

    /** 读取启动类及归档列表；当前为有限的正则提取，不是通用 JSON 解析器。 */
    private void readAirshipsConfig(Path configPath) throws IOException {
        String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        Matcher mainClassMatcher = MAIN_CLASS_PATTERN.matcher(json);
        if (mainClassMatcher.find()) {
            mainClass = mainClassMatcher.group(1);
        }

        Matcher classPathMatcher = CLASSPATH_PATTERN.matcher(json);
        if (!classPathMatcher.find()) {
            return;
        }

        Matcher entryMatcher = STRING_PATTERN.matcher(classPathMatcher.group(1));
        while (entryMatcher.find()) {
            Path path = libsDirectory.resolve(entryMatcher.group(1)).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && isRuntimeLibrary(path)) {
                gameClassPath.add(path);
            }
        }
    }

    private void collectClassPath() throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.list(libsDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path absolute = path.toAbsolutePath().normalize();
                if (!gameClassPath.contains(absolute) && isRuntimeLibrary(absolute)) {
                    gameClassPath.add(absolute);
                }
            }
        }
    }

    /** 按实际类内容识别基础设施库，重命名 JAR 也不能绕过类加载器隔离。 */
    static boolean isRuntimeLibrary(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if ((!fileName.endsWith(".jar") && !fileName.endsWith(".zip"))
                || fileName.equals("steamworks4j-1.3.0.jar")) return false;
        // 兼容旧的混合 libs 布局，但基础设施类只能由启动加载器持有。
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.stream().noneMatch(entry -> {
                String name = entry.getName();
                return name.endsWith(".class") && (name.startsWith("org/objectweb/asm/")
                        || name.startsWith("org/spongepowered/asm/")
                        || name.startsWith("net/fabricmc/loader/")
                        || name.startsWith("net/fabricacs/acbric/"));
            });
        }
    }

    /** 开发目录有 libs/native 时设置本地库路径；分发脚本另指定 game/lib/native。 */
    private void configureNativeLibraries() {
        Path nativeDirectory = libsDirectory.resolve("native").toAbsolutePath().normalize();
        if (!Files.isDirectory(nativeDirectory)) {
            return;
        }

        String nativePath = nativeDirectory.toString();
        System.setProperty("org.lwjgl.librarypath", nativePath);
        System.setProperty("net.java.games.input.librarypath", nativePath);
        System.setProperty("java.library.path", nativePath);
    }

    /** 外部路径必须显式成对提供；不进入旧模式的目录猜测，不在父加载器加载游戏类。 */
    private boolean locateExternal() {
        try {
            String install = System.getProperty(ExternalGameInstallation.INSTALL_PROPERTY);
            String instance = System.getProperty(ExternalGameInstallation.INSTANCE_PROPERTY);
            if (install == null || instance == null) throw new IOException("EXTERNAL_PATHS_REQUIRED / 外部模式须同时指定安装与实例路径");
            // 内部探针保留用于回归；正式入口必须明确提供包含隔离适配的核心 API。
            String probe = System.getProperty(ExternalGameInstallation.PROBE_PROPERTY);
            if (probe != null && !"net.fabricacs.regression.ExternalRuntimeProbe".equals(probe))
                throw new IOException("EXTERNAL_PROBE_INVALID / 无效的内部探针入口");
            if (probe == null) ExternalLauncher.requireCore(System.getProperty("acbric.external.core"));
            ExternalGameInstallation.runtime(System.getProperty("os.name"), Runtime.version().feature(), System.getProperty("os.arch"));
            var plan = ExternalGameInstallation.inspect(Path.of(install), Path.of(instance));
            externalLease = ExternalPreflight.InstanceLease.open(plan);
            externalLease.prepareDirectories(plan.instance());
            Path settings = ExternalLaunchSettings.prepare(plan);
            System.setProperty("acbric.internal.launchSettings", settings.toString());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { externalLease.close(); } catch (IOException ignored) { }
            }, "acbric-instance-release"));
            gameDirectory = plan.instance();
            libsDirectory = plan.install().resolve("lib");
            mainClass = probe == null ? ExternalGameInstallation.MAIN : probe;
            gameClassPath.clear(); gameClassPath.addAll(plan.classPath());
            buildIdentity = plan.identity();
            System.setProperty(ExternalGameInstallation.INSTALL_PROPERTY, plan.install().toString());
            System.setProperty(ExternalGameInstallation.INSTANCE_PROPERTY, plan.instance().toString());
            System.setProperty("dev", "false");
            System.setProperty("steam", "false");
            diagnostics = new LaunchDiagnostics(gameDirectory, buildIdentity);
            diagnostics.phase(probe == null ? "EXTERNAL_GAME_LOCATED" : "EXTERNAL_PROBE_LOCATED", null);
            configureNativeLibraries();
            net.fabricacs.management.ModSelection.applyAtStartup(gameDirectory.resolve("config"));
            return true;
        } catch (IOException | RuntimeException ex) {
            if (externalLease != null) try { externalLease.close(); } catch (IOException ignored) { }
            if (diagnostics != null) diagnostics.phase("EXTERNAL_LOCATE_FAILED", ex);
            throw new RuntimeException("External game lookup failed / 外部游戏定位失败: " + ex.getMessage(), ex);
        }
    }
}

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
    private boolean isRuntimeLibrary(Path path) throws IOException {
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
}

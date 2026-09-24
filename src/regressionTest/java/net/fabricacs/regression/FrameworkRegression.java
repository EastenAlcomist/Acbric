/*
 * FrameworkRegression.java — 无界面回归总入口：套件注册表 + 选择器。
 *
 * 每个套件对应一组修复（CHANGELOG 的 F01–F12）或一条功能线（dev.N 的对应文档），可以单独运行：
 *   regressionTest                    全部套件（CI 与 check 的默认行为）
 *   regressionTest data event         只跑这两套
 *   -Pacbric.suites=event,rename      Gradle 侧的等价写法
 *   regressionTest list               列出套件
 *
 * 不启动 GUI，也不依赖外部测试框架；文件夹具全部位于 build 沙箱。
 * 新增套件请往 SUITES 里登记，不要另起平行的测试入口。
 */
package net.fabricacs.regression;

import net.fabricacs.acbric.AirshipsGameProvider;
import net.fabricacs.api.event.AirshipsDataEvents;
import net.fabricacs.api.impl.BundledVanillaModLoader;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FrameworkRegression {
    private static int checks;

    /** 一个具名测试套件；返回后由调用方统计它新增了多少条断言。 */
    @FunctionalInterface
    private interface Suite {
        void run(Path root) throws Exception;
    }

    /** 套件注册表：名字 -> 套件。顺序即执行顺序，也是 list 的输出顺序。 */
    private static final Map<String, Suite> SUITES = new LinkedHashMap<>();

    /** 套件说明，用于 list 与错误提示；与 SUITES 一一对应。 */
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    /** 便捷别名（前缀匹配之外的补充）。 */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        register("data", "DATA_LOADED 原样传递游戏结果，不清理诊断、不把失败改成成功 (F01)",
                FrameworkRegression::suiteData);
        register("bundle", "内嵌原版资源归属存储：路径穿越、更新、冲突、备份与中断恢复 (F02/F05)",
                FrameworkRegression::suiteBundle);
        register("classpath", "启动类路径按归档内容排除 Loader/Mixin/ASM/垫片，保留游戏库 (F03)",
                FrameworkRegression::suiteClasspath);
        register("event", "事件总线：registerOnce 只执行一次、句柄身份、重复注册 (F04/F12)",
                FrameworkRegression::suiteEvent);
        register("rename", "重命名面板新旧事件顺序与取消契约 (F06)",
                FrameworkRegression::suiteRename);
        register("mods", "Fabric MOD 安装校验与 JAR 启停管理：非法元数据、同名覆盖、依赖预检查 (F07/dev.12)",
                FrameworkRegression::suiteMods);
        register("campaign", "战役数据与生命周期：命名空间保留、加载出口、显式迁移 (CAMPAIGN_DATA.md / CAMPAIGN_LIFECYCLE.md)",
                FrameworkRegression::suiteCampaign);
        register("config", "MOD 配置与设置界面契约：草稿、冲突检查、显式保存 (CONFIG.md / SETTINGS.md)",
                FrameworkRegression::suiteConfig);
        register("scopes", "事件订阅范围与运行期诊断：释放监听器、异常原样抛出 (EVENT_SCOPES.md)",
                FrameworkRegression::suiteScopes);
        register("manifest", "本地代码清单导出与离线比较 (CODE_MANIFEST.md)",
                FrameworkRegression::suiteManifest);
        register("handshake", "代码握手协议与大厅准备门禁 (CODE_HANDSHAKE.md / LOBBY_HANDSHAKE.md)",
                FrameworkRegression::suiteHandshake);
        register("rules", "共享规则声明、一致性检查与旧档显式迁移 (SHARED_RULES.md / RULE_SAVE_MIGRATION.md)",
                FrameworkRegression::suiteRules);
        register("identity", "构建身份与会话启动诊断 (DIAGNOSTICS.md)",
                FrameworkRegression::suiteIdentity);
        register("ui", "公共 UI 组件、文本交互、输入遮蔽与中英文选择 (UI.md)",
                FrameworkRegression::suiteUi);
        register("devtools", "开发者工具与控制台：命令绑定、执行与诊断缓冲 (DEVELOPMENT.md / COMMANDS.md / DEVELOPER_TOOLS.md)",
                FrameworkRegression::suiteDevTools);

        ALIASES.put("cp", "classpath");
        ALIASES.put("classes", "classpath");
        ALIASES.put("bundle-store", "bundle");
        ALIASES.put("bundles", "bundle");
        ALIASES.put("eventbus", "event");
        ALIASES.put("events", "event");
        ALIASES.put("install", "mods");
        ALIASES.put("java-mods", "mods");
        ALIASES.put("management", "mods");
        ALIASES.put("campaign-data", "campaign");
        ALIASES.put("campaigns", "campaign");
        ALIASES.put("settings", "config");
        ALIASES.put("configuration", "config");
        ALIASES.put("event-scopes", "scopes");
        ALIASES.put("code-manifest", "manifest");
        ALIASES.put("code-handshake", "handshake");
        ALIASES.put("lobby", "handshake");
        ALIASES.put("shared-rules", "rules");
        ALIASES.put("rule-saves", "rules");
        ALIASES.put("migration", "rules");
        ALIASES.put("game-identity", "identity");
        ALIASES.put("ui-input", "ui");
        ALIASES.put("language", "ui");
        ALIASES.put("developer-tools", "devtools");
        ALIASES.put("console", "devtools");
        ALIASES.put("renamepanel", "rename");
    }

    private static void register(String name, String description, Suite suite) {
        SUITES.put(name, suite);
        DESCRIPTIONS.put(name, description);
    }

    public static void main(String[] args) throws Exception {
        for (String raw : args) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.equals("list") || token.equals("--list") || token.equals("-l")) {
                printSuites();
                return;
            }
        }

        List<String> selected = select(args);
        Path root = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), "run-");

        int total = 0;
        for (String name : selected) {
            int before = checks;
            System.out.println("== suite: " + name + " -- " + DESCRIPTIONS.get(name) + " ==");
            SUITES.get(name).run(root);
            int ran = checks - before;
            total += ran;
            System.out.println("== suite " + name + ": " + ran + " checks ==");
            System.out.println();
        }

        String scope = selected.size() == SUITES.size()
                ? SUITES.size() + " suites"
                : "suites=" + String.join(",", selected);
        System.out.println("REGRESSION PASS: " + total + " checks; " + scope + "; fixtures=" + root);
    }

    /** 解析套件选择：无参数 = 全部，all = 全部，其余按名字/别名/唯一前缀解析。 */
    private static List<String> select(String[] args) {
        List<String> wanted = new ArrayList<>();
        for (String raw : args) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            if (token.equals("all")) return new ArrayList<>(SUITES.keySet());
            String name = resolve(token);
            if (name == null) {
                throw new IllegalArgumentException(
                        "unknown regression suite: '" + raw + "'\n"
                        + "known suites: " + String.join(", ", SUITES.keySet())
                        + "\nrun with 'list' to see what each one covers");
            }
            if (!wanted.contains(name)) wanted.add(name);
        }
        if (wanted.isEmpty()) return new ArrayList<>(SUITES.keySet());
        return wanted;
    }

    /** 精确名 -> 别名 -> 唯一前缀 -> 唯一子串；歧义时返回 null。 */
    private static String resolve(String token) {
        if (SUITES.containsKey(token)) return token;
        String alias = ALIASES.get(token);
        if (alias != null && SUITES.containsKey(alias)) return alias;

        String hit = null;
        for (String name : SUITES.keySet()) {
            if (!name.startsWith(token)) continue;
            if (hit != null) return null;   // 前缀歧义，例如 "b" 之于 bundle
            hit = name;
        }
        if (hit != null) return hit;

        hit = null;
        for (String name : SUITES.keySet()) {
            if (!name.contains(token)) continue;
            if (hit != null) return null;
            hit = name;
        }
        return hit;
    }

    private static void printSuites() {
        System.out.println("Available regression suites (" + SUITES.size() + "):");
        for (Map.Entry<String, Suite> entry : SUITES.entrySet()) {
            System.out.println("  " + String.format(Locale.ROOT, "%-12s", entry.getKey())
                    + DESCRIPTIONS.get(entry.getKey()));
        }
        System.out.println();
        System.out.println("Usage: regressionTest [all | <suite> ...]");
        System.out.println("  no argument / all   run every suite (what check and CI do)");
        System.out.println("  <suite>             run only the named suites; unique prefixes work (ev -> event)");
        System.out.println("  list                print this list");
    }

    // ---- 套件实现 -----------------------------------------------------------

    private static void suiteData(Path root) throws Exception {
        loadResults();
    }

    private static void suiteBundle(Path root) throws Exception {
        bundles(root);
        checks += net.fabricacs.api.impl.BundleStoreRegression.run(root.resolve("managed-bundles"));
    }

    private static void suiteClasspath(Path root) throws Exception {
        classPaths(root);
    }

    private static void suiteEvent(Path root) throws Exception {
        checks += EventRegression.run();
    }

    private static void suiteRename(Path root) throws Exception {
        checks += RenamePanelRegression.run();
    }

    private static void suiteMods(Path root) throws Exception {
        checks += ModManagementRegression.run(root.resolve("mod-management"));
        checks += net.fabricacs.api.impl.JavaModManagerRegression.run(root.resolve("java-mod-manager"));
    }

    private static void suiteCampaign(Path root) throws Exception {
        checks += CampaignDataRegression.run(root.resolve("campaign-data"));
        checks += CampaignLifecycleRegression.run();
    }

    private static void suiteConfig(Path root) throws Exception {
        checks += ConfigRegression.run(root.resolve("configs"));
        checks += SettingsRegression.run(root.resolve("settings"));
    }

    private static void suiteScopes(Path root) throws Exception {
        checks += net.fabricacs.api.event.EventScopeRegression.run();
        checks += net.fabricacs.api.impl.RuntimeEventDiagnosticsRegression.run(root.resolve("runtime-events"));
    }

    private static void suiteManifest(Path root) throws Exception {
        checks += net.fabricacs.api.impl.CodeManifestRegression.run(root.resolve("code-manifests"));
    }

    private static void suiteHandshake(Path root) throws Exception {
        checks += net.fabricacs.api.impl.CodeHandshakeRegression.run();
        checks += net.fabricacs.api.impl.LobbyCodeGateRegression.run();
    }

    private static void suiteRules(Path root) throws Exception {
        checks += net.fabricacs.api.impl.SharedRulesRegression.run();
        checks += net.fabricacs.api.impl.RuleSaveRegression.run(root.resolve("rule-saves"));
    }

    private static void suiteIdentity(Path root) throws Exception {
        checks += net.fabricacs.acbric.GameIdentityRegression.run(root.resolve("game-identity"));
        checks += net.fabricacs.api.impl.StartupDiagnosticsRegression.run(root.resolve("startup-diagnostics"));
    }

    private static void suiteUi(Path root) throws Exception {
        checks += net.fabricacs.api.ui.UiRegression.run();
        checks += net.fabricacs.api.ui.TextInteractionRegression.run();
        checks += net.fabricacs.api.impl.UiInputDiagnosticsRegression.run(root.resolve("ui-input"));
        checks += net.fabricacs.api.util.LanguageRegression.run();
    }

    private static void suiteDevTools(Path root) throws Exception {
        checks += net.fabricacs.api.impl.DeveloperToolsRegression.run(root.resolve("developer-tools"));
    }

    // ---- 共用工具 -----------------------------------------------------------

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    private static Path jar(Path path, Map<String, String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    private static void extract(Path jar, Path mods, String id) throws Exception {
        Method method = BundledVanillaModLoader.class.getDeclaredMethod(
                "extractBundled", Path.class, Path.class, String.class);
        method.setAccessible(true);
        method.invoke(null, jar, mods, id);
    }

    private static void loadResults() throws Exception {
        Method hook = Class.forName("net.fabricacs.api.mixin.LoadableMixin")
                .getDeclaredMethod("acbric$onDataLoaded", CallbackInfoReturnable.class);
        hook.setAccessible(true);
        List<Boolean> observed = new ArrayList<>();
        AirshipsDataEvents.DATA_LOADED.register(observed::add);
        try {
            for (boolean result : new boolean[]{false, true}) {
                CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("regression", true, result);
                hook.invoke(null, callback);
                check(callback.getReturnValueZ() == result && !callback.isCancelled(),
                        "data hook preserves result " + result);
            }
            check(observed.equals(List.of(false, true)), "DATA_LOADED reports original results exactly once");
        } finally {
            AirshipsDataEvents.DATA_LOADED.clearListeners();
        }
    }

    private static void bundles(Path root) throws Exception {
        Path mods = root.resolve("extraction/mods");
        String[] invalid = {"../escaped.txt", "sub/../../escaped.txt", "/absolute.txt",
                "C:/absolute.txt", "C:relative.txt", "..\\escaped.txt", "sub/file:stream",
                "sub/.. /escaped.txt", "sub./file.txt"};
        for (int i = 0; i < invalid.length; i++) {
            // 先放一个合法条目，验证后续非法路径也不会导致部分发布。
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("acbric_vanilla/safe.txt", "safe");
            entries.put("acbric_vanilla/" + invalid[i], "unsafe");
            Path archive = jar(root.resolve("fixtures/invalid-" + i + ".jar"), entries);
            extract(archive, mods, "invalid_" + i);
            check(!Files.exists(mods.resolve("invalid_" + i)), "reject bundle path " + invalid[i]);
        }
        check(!Files.exists(mods.resolve("escaped.txt")) && !Files.exists(mods.getParent().resolve("escaped.txt")),
                "no escaped resource written");

        Path valid = jar(root.resolve("fixtures/valid.jar"), Map.of("acbric_vanilla/Type/value.json", "[]"));
        extract(valid, mods, "valid_mod");
        check(Files.readString(mods.resolve("valid_mod/Type/value.json")).equals("[]")
                && Files.isRegularFile(mods.resolve("valid_mod/info.json")), "valid bundle and generated info committed");

        Path suppliedInfo = jar(root.resolve("fixtures/info.jar"), Map.of("acbric_vanilla/info.json", "{\"id\":\"custom\"}"));
        extract(suppliedInfo, mods, "custom_info");
        check(Files.readString(mods.resolve("custom_info/info.json")).equals("{\"id\":\"custom\"}"),
                "preserve bundled info.json");

        Files.writeString(mods.resolve("valid_mod/Type/value.json"), "user edited");
        extract(valid, mods, "valid_mod");
        check(Files.readString(mods.resolve("valid_mod/Type/value.json")).equals("user edited"),
                "preserve user-edited managed resource during update");

        Map<String, String> conflict = new LinkedHashMap<>();
        conflict.put("acbric_vanilla/file", "file");
        conflict.put("acbric_vanilla/file/child", "conflict");
        extract(jar(root.resolve("fixtures/conflict.jar"), conflict), mods, "retry_mod");
        check(!Files.exists(mods.resolve("retry_mod")), "failed extraction leaves no installed directory");
        try (var paths = Files.list(mods.getParent().resolve(".acbric-bundles/retry_mod"))) {
            check(paths.noneMatch(p -> p.getFileName().toString().startsWith("stage-") || p.getFileName().toString().startsWith("incoming-")),
                    "failed extraction removes staging directory");
        }
        extract(valid, mods, "retry_mod");
        check(Files.isRegularFile(mods.resolve("retry_mod/info.json")), "valid retry succeeds after failed extraction");

        extract(valid, mods, "../invalid_id");
        check(!Files.exists(mods.getParent().resolve("invalid_id")), "reject invalid mod ID");
        Path outside = Files.createDirectories(root.resolve("link-destination"));
        Path link = mods.resolve("linked_mod");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("SKIP symlink fixture: " + e);
            return;
        }
        extract(valid, mods, "linked_mod");
        check(!Files.exists(outside.resolve("info.json")), "reject symbolic link destination");
        Path parentLink = root.resolve("linked-parent");
        Files.createSymbolicLink(parentLink, outside);
        extract(valid, parentLink.resolve("mods"), "parent_link_mod");
        check(!Files.exists(outside.resolve("mods")), "reject symbolic link ancestor before creating directories");
    }

    @SuppressWarnings("unchecked")
    private static void classPaths(Path root) throws Exception {
        Path libs = Files.createDirectories(root.resolve("classpath/libs"));
        String[] markers = {"net/fabricmc/loader/api/FabricLoader.class",
                "org/spongepowered/asm/mixin/Mixin.class", "org/objectweb/asm/ClassReader.class",
                "org/objectweb/asm/tree/analysis/Analyzer.class", "net/fabricacs/acbric/AirshipsGameProvider.class"};
        for (int i = 0; i < markers.length; i++) jar(libs.resolve("renamed-" + i + ".jar"), Map.of(markers[i], "fixture"));
        Path game = jar(libs.resolve("asplit-A.zip"), Map.of("com/zarkonnen/airships/Main.class", "fixture"));
        Path gameLib = jar(libs.resolve("game-library.jar"), Map.of("some/library/Helper.class", "fixture"));
        jar(libs.resolve("steamworks4j-1.3.0.jar"), Map.of("old/steam.class", "fixture"));
        Path config = root.resolve("classpath/Airships.json");
        Files.writeString(config, "{\"classPath\":[\"renamed-0.jar\",\"renamed-1.jar\",\"renamed-2.jar\",\"asplit-A.zip\"]}");

        AirshipsGameProvider provider = new AirshipsGameProvider();
        Field directory = AirshipsGameProvider.class.getDeclaredField("libsDirectory");
        directory.setAccessible(true);
        directory.set(provider, libs);
        Method read = AirshipsGameProvider.class.getDeclaredMethod("readAirshipsConfig", Path.class);
        read.setAccessible(true);
        read.invoke(provider, config);
        Method collect = AirshipsGameProvider.class.getDeclaredMethod("collectClassPath");
        collect.setAccessible(true);
        collect.invoke(provider);
        Field cp = AirshipsGameProvider.class.getDeclaredField("gameClassPath");
        cp.setAccessible(true);
        List<Path> actual = (List<Path>) cp.get(provider);
        check(actual.size() == 2 && actual.contains(game) && actual.contains(gameLib),
                "config and directory scan exclude renamed launcher libraries, preserve game libraries");
        collect.invoke(provider);
        check(actual.size() == 2, "classpath collection does not duplicate entries");
    }
}

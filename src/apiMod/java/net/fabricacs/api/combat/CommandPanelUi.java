/*
 * CommandPanelUi.java — 命令面板标签、开关和按钮注册入口；交互使用原生绘制与 Hooks 链路。
 */
package net.fabricacs.api.combat;

import net.fabricacs.api.impl.CommandPanelUiRegistry;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CommandPanelUi {
    private final String modId;

    public CommandPanelUi(String modId) {
        if (modId == null || !modId.matches("[a-z][a-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("MOD ID");
        }
        this.modId = modId;
    }

    public String modId() {
        return modId;
    }

    /** 注册固定文字按钮；绘制阶段不执行动作。 */
    public Registration registerButton(String id, String label, Runnable action) {
        return registerButton(id, label, action, CommandPanelPlacement.DEFAULT);
    }

    /** 注册固定文字按钮并指定布局位置。 */
    public Registration registerButton(String id, String label, Runnable action,
                                       CommandPanelPlacement placement) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(action, "action");
        return registerButton(id, context -> label, context -> action.run(), placement);
    }

    /** 标签和动作可使用实时命令面板上下文；供应器必须只读且不做阻塞 I/O。 */
    public Registration registerButton(String id, Function<CommandPanelContext, String> label,
                                       Consumer<CommandPanelContext> action) {
        return registerButton(id, label, action, CommandPanelPlacement.DEFAULT);
    }

    /** 动态文字按钮，使用显式锚点和偏移。 */
    public Registration registerButton(String id, Function<CommandPanelContext, String> label,
                                       Consumer<CommandPanelContext> action,
                                       CommandPanelPlacement placement) {
        return registerButton(id, label, context -> "", context -> true, action, placement);
    }

    /**
     * 注册动态按钮。标签、提示和启用状态在每次命令面板绘制时重新读取；
     * 动作只在原生 Hooks 确认点击后执行。
     */
    public Registration registerButton(String id,
                                       Function<CommandPanelContext, String> label,
                                       Function<CommandPanelContext, String> tooltip,
                                       Predicate<CommandPanelContext> enabled,
                                       Consumer<CommandPanelContext> action) {
        return registerButton(id, label, tooltip, enabled, action, CommandPanelPlacement.DEFAULT);
    }

    /** 完整动态按钮注册；同一锚点和偏移的按钮按注册顺序排在同一行。 */
    public Registration registerButton(String id,
                                       Function<CommandPanelContext, String> label,
                                       Function<CommandPanelContext, String> tooltip,
                                       Predicate<CommandPanelContext> enabled,
                                       Consumer<CommandPanelContext> action,
                                       CommandPanelPlacement placement) {
        return CommandPanelUiRegistry.registerButton(modId, id, Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(tooltip, "tooltip"), Objects.requireNonNull(enabled, "enabled"),
                Objects.requireNonNull(action, "action"), Objects.requireNonNull(placement, "placement"));
    }

    /** 注册固定状态文本；不响应点击。 */
    public Registration registerLabel(String id, String text) {
        return registerLabel(id, text, CommandPanelPlacement.DEFAULT);
    }

    /** 注册固定状态文本并指定位置；不响应点击。 */
    public Registration registerLabel(String id, String text, CommandPanelPlacement placement) {
        Objects.requireNonNull(text, "text");
        return registerLabel(id, context -> text, placement);
    }

    /** 注册动态状态文本；供应器在每次绘制时读取且不得做阻塞 I/O。 */
    public Registration registerLabel(String id, Function<CommandPanelContext, String> text) {
        return registerLabel(id, text, CommandPanelPlacement.DEFAULT);
    }

    /** 注册动态状态文本和提示。 */
    public Registration registerLabel(String id, Function<CommandPanelContext, String> text,
                                      Function<CommandPanelContext, String> tooltip,
                                      CommandPanelPlacement placement) {
        return CommandPanelUiRegistry.registerLabel(modId, id, Objects.requireNonNull(text, "text"),
                Objects.requireNonNull(tooltip, "tooltip"), Objects.requireNonNull(placement, "placement"));
    }

    /** 注册动态状态文本并使用显式位置。 */
    public Registration registerLabel(String id, Function<CommandPanelContext, String> text,
                                      CommandPanelPlacement placement) {
        return registerLabel(id, text, context -> "", placement);
    }

    /** 注册原生样式开关；checked 与 change 必须成对维护 MOD 自己的状态。 */
    public Registration registerToggle(String id, Function<CommandPanelContext, String> label,
                                       Function<CommandPanelContext, Boolean> checked,
                                       BiConsumer<CommandPanelContext, Boolean> change,
                                       CommandPanelPlacement placement) {
        return registerToggle(id, label, checked, context -> "", context -> true, change, placement);
    }

    /** 注册带动态提示和启用状态的原生样式开关。 */
    public Registration registerToggle(String id,
                                       Function<CommandPanelContext, String> label,
                                       Function<CommandPanelContext, Boolean> checked,
                                       Function<CommandPanelContext, String> tooltip,
                                       Predicate<CommandPanelContext> enabled,
                                       BiConsumer<CommandPanelContext, Boolean> change,
                                       CommandPanelPlacement placement) {
        return CommandPanelUiRegistry.registerToggle(modId, id, Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(checked, "checked"), Objects.requireNonNull(tooltip, "tooltip"),
                Objects.requireNonNull(enabled, "enabled"), Objects.requireNonNull(change, "change"),
                Objects.requireNonNull(placement, "placement"));
    }

    /** 游戏对象保持 Object 类型，避免公开签名绑定某一游戏版本的实现类。 */
    public record CommandPanelContext(Object screen, Object panel) {
        public CommandPanelContext {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(panel, "panel");
        }
    }

    public interface Registration extends AutoCloseable {
        String id();

        boolean isRegistered();

        @Override
        void close();
    }
}

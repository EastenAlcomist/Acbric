/* CommandPanelUiRuntimeProbe.java — 在真实 Knot 中确认命令面板 AFTER 注入扩展确实执行。 */
package net.fabricacs.regression;

import com.zarkonnen.airships.CommandButtonsPanel;
import com.zarkonnen.airships.UniScreen;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class CommandPanelUiRuntimeProbe {
    private CommandPanelUiRuntimeProbe() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> panelType = Class.forName("com.zarkonnen.airships.CommandButtonsPanel");
        Field marker = panelType.getDeclaredField("acbric$commandPanelUiAfter");
        marker.setAccessible(true);
        CommandButtonsPanel panel = new CommandButtonsPanel();

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        UniScreen screen = (UniScreen) ((Unsafe) unsafeField.get(null)).allocateInstance(UniScreen.class);
        screen.intent = null;

        // 原生 draw 在 intent 不匹配时立即返回；Mixin 的 RETURN 注入仍应执行扩展桥。
        panel.draw(null, null, null, null, screen);
        if (!marker.getBoolean(panel)) {
            throw new AssertionError("CommandButtonsPanel command-panel UI AFTER injection did not run");
        }
        Class.forName("net.fabricacs.api.impl.CommandPanelUiBridge");
        System.out.println("COMMAND PANEL RUNTIME PASS: transformed AFTER injection and bridge classload");
    }
}

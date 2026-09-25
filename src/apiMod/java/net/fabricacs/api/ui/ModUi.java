/* ModUi.java — 按 MOD 归属注册界面入口；框架详情窗口与功能 MOD 共用公开组件。 */
package net.fabricacs.api.ui;

import net.fabricacs.api.impl.UiBridge;
import java.util.*;
import java.util.function.Supplier;

public final class ModUi {
    private final String modId;
    public ModUi(String modId){if(modId==null || !modId.matches("[a-z][a-z0-9_-]{1,63}"))throw new IllegalArgumentException("MOD ID");this.modId=modId;}
    public String modId(){return modId;}
    public UiWindowHandle open(UiWindow window){return UiBridge.open(modId,Objects.requireNonNull(window));}
    public Registration register(String id,String label,Supplier<UiWindow> factory){return UiBridge.register(modId,id,label,factory);}
    /** 动态工具名称，在详情页绘制时读取，便于跟随游戏语言；供应器必须纯读取。 */
    public Registration register(String id,Supplier<String> label,Supplier<UiWindow> factory){return UiBridge.register(modId,id,label,factory);}
    public interface Registration extends AutoCloseable {
        String id();
        boolean isRegistered();
        @Override void close();
    }
}

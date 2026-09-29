/* UiWindow.java — 窗口描述及关闭原因；普通窗口与模态窗口共用同一组件树。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.function.Consumer;

public record UiWindow(String title,int width,int maxHeight,boolean modal,UiNode content,Consumer<CloseReason> onClose,
                       UiNode footer,Consumer<UiWindowHandle> closeRequest) {
    public enum CloseReason { CLOSED, SCREEN_CHANGED, GAME_EXIT, NATIVE_DIALOG, ERROR, PARENT_CLOSED }
    public UiWindow {
        Objects.requireNonNull(title);Objects.requireNonNull(content);Objects.requireNonNull(onClose);Objects.requireNonNull(closeRequest);
        if(width<160 || width>4096 || maxHeight<120 || maxHeight>4096)throw new IllegalArgumentException("Window size out of range");
        Set<UiNode> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        validate(content,seen,0);if(footer!=null)validate(footer,seen,0);
    }
    /** 保留旧构造签名；原有窗口的关闭语义不变。 */
    public UiWindow(String title,int width,int maxHeight,boolean modal,UiNode content,Consumer<CloseReason> onClose) {this(title,width,maxHeight,modal,content,onClose,null,UiWindowHandle::close);}
    public UiWindow(String title,int width,int maxHeight,boolean modal,UiNode content) { this(title,width,maxHeight,modal,content,reason->{}); }
    /** 页脚与正文分别裁剪和滚动；通常用于始终可见的操作按钮。 */
    public UiWindow withFooter(UiNode footer){return new UiWindow(title,width,maxHeight,modal,content,onClose,Objects.requireNonNull(footer),closeRequest);}
    /** 仅拦截 X、Esc 及 requestClose；程序 close 和生命周期清理始终立即执行。 */
    public UiWindow onCloseRequest(Consumer<UiWindowHandle> request){return new UiWindow(title,width,maxHeight,modal,content,onClose,footer,request);}
    private static void validate(UiNode node,Set<UiNode> seen,int depth) {
        if(depth>24 || !seen.add(node) || seen.size()>512)throw new IllegalArgumentException("UI tree: depth <=24, unique nodes <=512");
        for(UiNode child:node.children)validate(child,seen,depth+1);
    }
}

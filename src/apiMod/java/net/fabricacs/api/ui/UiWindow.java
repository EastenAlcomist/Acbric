/* UiWindow.java — 窗口描述及关闭原因；普通窗口与模态窗口共用同一组件树。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.function.Consumer;

public record UiWindow(String title,int width,int maxHeight,boolean modal,UiNode content,Consumer<CloseReason> onClose) {
    public enum CloseReason { CLOSED, SCREEN_CHANGED, GAME_EXIT, NATIVE_DIALOG, ERROR, PARENT_CLOSED }
    public UiWindow {
        Objects.requireNonNull(title);Objects.requireNonNull(content);Objects.requireNonNull(onClose);
        if(width<160 || width>4096 || maxHeight<120 || maxHeight>4096)throw new IllegalArgumentException("Window size out of range");
        validate(content,Collections.newSetFromMap(new IdentityHashMap<>()),0);
    }
    public UiWindow(String title,int width,int maxHeight,boolean modal,UiNode content) { this(title,width,maxHeight,modal,content,reason->{}); }
    private static void validate(UiNode node,Set<UiNode> seen,int depth) {
        if(depth>24 || !seen.add(node) || seen.size()>512)throw new IllegalArgumentException("UI tree: depth <=24, unique nodes <=512");
        for(UiNode child:node.children)validate(child,seen,depth+1);
    }
}

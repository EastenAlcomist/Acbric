/* UiNode.java — 声明式组件描述；每个窗口持有独立的交互状态，组件不直接引用游戏对象。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.function.*;

public final class UiNode {
    enum Kind { LABEL, BUTTON, TOGGLE, TEXT, ROW, COLUMN, PANEL, SCROLL, SPACE }
    final Kind kind;
    final List<UiNode> children;
    final Supplier<String> text;
    final Consumer<UiWindowHandle> action;
    final BooleanSupplier checked;
    final Consumer<Boolean> change;
    final Consumer<String> edit;
    final int size;
    final Ui.Align alignment;
    final BooleanSupplier enabled;
    final String tip;
    final int width;
    boolean boundText;

    UiNode(Kind kind, List<UiNode> children, Supplier<String> text, Consumer<UiWindowHandle> action,
           BooleanSupplier checked, Consumer<Boolean> change, Consumer<String> edit, int size, Ui.Align alignment,
           BooleanSupplier enabled, String tip, int width) {
        this.kind=kind;this.children=List.copyOf(children);this.text=text;this.action=action;
        this.checked=checked;this.change=change;this.edit=edit;this.size=size;this.alignment=alignment;
        this.enabled=enabled;this.tip=tip;this.width=width;
    }
    private UiNode copy(BooleanSupplier enabled,String tip,int width) {
        UiNode next=new UiNode(kind,children,text,action,checked,change,edit,size,alignment,enabled,tip,width);
        next.boundText=boundText;return next;
    }
    public UiNode enabled(BooleanSupplier value) { return copy(Objects.requireNonNull(value),tip,width); }
    public UiNode tooltip(String value) { return copy(enabled,Objects.requireNonNull(value),width); }
    /** 行布局中的期望宽度；空间不足时裁剪，未设置的兄弟平分剩余空间。 */
    public UiNode width(int value) { if(value<1 || value>4096)throw new IllegalArgumentException("width 1..4096");return copy(enabled,tip,value); }
}

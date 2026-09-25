/* Ui.java — 常用组件与行列布局工厂；回调只在交互阶段执行，绘制阶段不执行动作。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.function.*;

public final class Ui {
    public enum Align { START, CENTER, END, STRETCH }
    private Ui() {}
    private static UiNode node(UiNode.Kind kind,List<UiNode> children,Supplier<String> text,Consumer<UiWindowHandle> action,
                               BooleanSupplier checked,Consumer<Boolean> change,Consumer<String> edit,int size,Align align) {
        return new UiNode(kind,children,text,action,checked,change,edit,size,align,()->true,"",0);
    }
    public static UiNode label(String text) { Objects.requireNonNull(text);return label(()->text); }
    public static UiNode label(Supplier<String> text) { return node(UiNode.Kind.LABEL,List.of(),Objects.requireNonNull(text),null,null,null,null,0,Align.START); }
    public static UiNode button(String text,Consumer<UiWindowHandle> action) { Objects.requireNonNull(text);return button(()->text,action); }
    public static UiNode button(Supplier<String> text,Consumer<UiWindowHandle> action) { return node(UiNode.Kind.BUTTON,List.of(),Objects.requireNonNull(text),Objects.requireNonNull(action),null,null,null,0,Align.START); }
    public static UiNode toggle(String text,BooleanSupplier checked,Consumer<Boolean> change) {
        Objects.requireNonNull(text);return node(UiNode.Kind.TOGGLE,List.of(),()->text,null,Objects.requireNonNull(checked),Objects.requireNonNull(change),null,0,Align.START);
    }
    public static UiNode textField(String initial,int maxLength,Consumer<String> change) {
        Objects.requireNonNull(initial);Objects.requireNonNull(change);
        if(maxLength<1 || maxLength>4096 || initial.codePointCount(0,initial.length())>maxLength || initial.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Single-line text; maxLength 1..4096 code points");
        return node(UiNode.Kind.TEXT,List.of(),()->initial,null,null,null,change,maxLength,Align.START);
    }
    public static UiNode column(int gap,UiNode... children) { return column(gap,Align.STRETCH,children); }
    public static UiNode column(int gap,Align align,UiNode... children) { return container(UiNode.Kind.COLUMN,gap,align,children); }
    public static UiNode row(int gap,UiNode... children) { return row(gap,Align.CENTER,children); }
    public static UiNode row(int gap,Align align,UiNode... children) { return container(UiNode.Kind.ROW,gap,align,children); }
    public static UiNode panel(int padding,UiNode child) { return container(UiNode.Kind.PANEL,padding,Align.STRETCH,child); }
    public static UiNode scroll(int height,UiNode child) {
        if(height<24 || height>4096)throw new IllegalArgumentException("height 24..4096");
        return node(UiNode.Kind.SCROLL,List.of(Objects.requireNonNull(child)),()->"",null,null,null,null,height,Align.STRETCH);
    }
    public static UiNode space(int height) { return container(UiNode.Kind.SPACE,height,Align.STRETCH); }
    private static UiNode container(UiNode.Kind kind,int size,Align align,UiNode... children) {
        if(size<0 || size>4096)throw new IllegalArgumentException("size 0..4096");
        return node(kind,List.of(children),()->"",null,null,null,null,size,Objects.requireNonNull(align));
    }
}

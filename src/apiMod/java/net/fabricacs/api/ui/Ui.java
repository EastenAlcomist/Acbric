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
    /** 受控文本：供应器给出当前值，回调同步写回模型。外部重置值后会更新文字和光标。 */
    public static UiNode textField(Supplier<String> value,int maxLength,Consumer<String> change) {
        Objects.requireNonNull(value);Objects.requireNonNull(change);
        if(maxLength<1||maxLength>4096)throw new IllegalArgumentException("maxLength 1..4096");
        UiNode result=node(UiNode.Kind.TEXT,List.of(),value,null,null,null,change,maxLength,Align.START);result.boundText=true;return result;
    }
    public static UiNode integerField(Supplier<String> value,int min,int max,Consumer<String> change){return number(value,true,min,max,change);}
    public static UiNode numberField(Supplier<String> value,double min,double max,Consumer<String> change){return number(value,false,min,max,change);}
    private static UiNode number(Supplier<String> value,boolean integer,double min,double max,Consumer<String> change){
        net.fabricacs.api.impl.NumberValues.bounds(integer,min,max);
        return column(4,textField(value,128,change),label(()->net.fabricacs.api.impl.NumberValues.error(value.get(),integer,min,max,net.fabricacs.api.util.AcbricLanguage.isChinese())));
    }
    public record Choice(String value,Supplier<String> label){
        public Choice {Objects.requireNonNull(value);Objects.requireNonNull(label);if(value.isEmpty()||value.length()>128||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Choice value");}
        public Choice(String value,String label){this(value,()->label);Objects.requireNonNull(label);}
    }
    /** 选项以模态列表展示，避免覆盖滚动裁剪；值稳定、标签可以跟随语言。 */
    public static UiNode choice(Supplier<String> value,List<Choice> options,Consumer<String> change){
        Objects.requireNonNull(value);Objects.requireNonNull(change);List<Choice> copy=List.copyOf(options);
        if(copy.isEmpty()||copy.size()>64||copy.stream().map(Choice::value).distinct().count()!=copy.size())throw new IllegalArgumentException("1..64 unique choices");
        return button(()->copy.stream().filter(o->o.value().equals(value.get())).map(o->o.label().get()).findFirst()
                .orElseGet(()->net.fabricacs.api.util.AcbricLanguage.text("Choose an option","请选择选项")),parent->{
            List<UiNode> rows=new ArrayList<>();
            for(Choice option:copy)rows.add(button(option.label(),child->{child.close();change.accept(option.value());}));
            rows.add(button(()->net.fabricacs.api.util.AcbricLanguage.text("Cancel","取消"),UiWindowHandle::close));
            parent.dialog(new UiWindow(net.fabricacs.api.util.AcbricLanguage.text("Choose an option","选择选项"),420,440,true,column(6,rows.toArray(UiNode[]::new))));
        });
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

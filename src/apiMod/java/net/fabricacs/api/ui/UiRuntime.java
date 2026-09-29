/*
 * UiRuntime.java — 内部无渲染依赖的布局、命中、焦点及窗口栈；公开仅供游戏适配层使用，不是扩展契约。
 * 交互使用最近一帧的裁剪后命中区域；关闭/切屏后旧布局失效，回调期间改变窗口栈不会触发第二个动作。
 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class UiRuntime {
    public record Rect(int x,int y,int w,int h) {
        public boolean contains(double px,double py){return w>0&&h>0&&px>=x&&py>=y&&px<x+w&&py<y+h;}
        public Rect intersect(Rect b){int l=Math.max(x,b.x),t=Math.max(y,b.y);return new Rect(l,t,Math.max(0,Math.min(x+w,b.x+b.w)-l),Math.max(0,Math.min(y+h,b.y+b.h)-t));}
    }
    public interface Canvas {
        double scale(); int lineHeight(); int buttonHeight(); int textHeight(String text,int width);
        void window(Rect bounds,String title,boolean modal);
        void panel(Rect bounds,Rect clip);
        void label(Rect bounds,Rect clip,String text);
        void button(Rect bounds,Rect clip,String text,boolean enabled,boolean focused);
        void toggle(Rect bounds,Rect clip,String text,boolean value,boolean enabled,boolean focused);
        void text(Rect bounds,Rect clip,String text,int caret,boolean selected,boolean focused,boolean enabled);
        default int textWidth(String text){return text.codePointCount(0,text.length())*Math.max(1,lineHeight()/2);}
        default int[] textAdvances(String text){int[] widths=new int[text.length()+1];for(int i=1;i<=text.length();i++)widths[i]=textWidth(text.substring(0,i));return widths;}
        default void text(Rect bounds,Rect clip,String text,int caret,int anchor,int offset,boolean focused,boolean enabled){text(bounds,clip,text,caret,caret!=anchor,focused,enabled);}
        void tooltip(String text,int x,int y);
        void scrollbar(Rect bounds,Rect clip,int offset,int maximum);
    }
    public record Input(double x,double y,boolean click,boolean down,int wheel,String typed,String paste,
                        boolean tab,boolean shift,boolean enter,boolean escape,boolean back,boolean delete,
                        boolean left,boolean right,boolean home,boolean end,boolean selectAll) {}
    /** 适配层额外状态；旧 Input/Canvas 入口保留给现有探针，非公开 MOD API。 */
    public record Editing(boolean copy,boolean cut,int held,boolean active,boolean leftDown,long nanos,boolean historyUp,boolean historyDown) {
        public Editing(boolean copy,boolean cut,int held,boolean active,boolean leftDown,long nanos){this(copy,cut,held,active,leftDown,nanos,false,false);}
    }
    public record Capture(boolean pointer,boolean keyboard) {}
    private record Box(UiNode node,Rect bounds,Rect clip,boolean enabled,String text,boolean checked) {}
    private record Scroll(UiNode node,Rect bounds,int max) {}
    private static final class State {
        final UiWindowHandle handle;
        // 原生位图字体不一定包含乘号；使用已支持的 ASCII X，避免空白按钮。
        final UiNode close=Ui.button("X",UiWindowHandle::requestClose);
        final Map<UiNode,TextEditor> editors=new IdentityHashMap<>();
        final Map<UiNode,Integer> offsets=new IdentityHashMap<>();
        final List<Box> boxes=new ArrayList<>();final List<Scroll> scrolls=new ArrayList<>();
        final TextKeyRepeat repeat=new TextKeyRepeat();UiNode focus,drag;Rect bounds=new Rect(0,0,0,0);int windowOffset;boolean ready;
        State(UiWindowHandle handle){this.handle=handle;focus=initialFocus(handle.window.content());}
        // 框架控制台的内部焦点提示；不改变其他窗口的默认焦点或公开组件契约。
        private static UiNode initialFocus(UiNode node){if(node.initialFocus)return node;for(UiNode child:node.children){UiNode found=initialFocus(child);if(found!=null)return found;}return null;}
    }
    private final Thread thread=Thread.currentThread();
    private final List<State> stack=new ArrayList<>();
    private final BiConsumer<String,Throwable> errors;
    private Object anchor;
    private boolean pointerCapture,releaseBarrier,closing;
    private double mouseX=-1,mouseY=-1;
    private String inputDecision="idle";
    private boolean leftWasDown,cancelledDrag;
    private final Predicate<String> clipboard;
    /** 内部排障快照：只描述交互状态，不含标题、MOD 文案或用户输入。 */
    public String inputDiagnosticState(){
        return "depth="+stack.size()+",barrier="+releaseBarrier+",ready="+(!stack.isEmpty()&&stack.getLast().ready)+",decision="+inputDecision;
    }
    public UiRuntime(BiConsumer<String,Throwable> errors){this(errors,value->false);}
    public UiRuntime(BiConsumer<String,Throwable> errors,Predicate<String> clipboard){this.errors=Objects.requireNonNull(errors);this.clipboard=Objects.requireNonNull(clipboard);}
    void checkThread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("UI must be used on the game thread");}
    public boolean isOpen(){return !stack.isEmpty();}
    public UiWindowHandle open(String owner,UiWindow window,Object screen){
        checkThread();Objects.requireNonNull(window);Objects.requireNonNull(screen);
        if(closing)throw new IllegalStateException("Cannot open windows during cleanup");
        if(!stack.isEmpty())throw new IllegalStateException("Close the current root window before opening another");
        releaseBarrier=false;leftWasDown=false;cancelledDrag=false;anchor=screen;var handle=new UiWindowHandle(this,owner,window);stack.add(new State(handle));return handle;
    }
    UiWindowHandle push(UiWindowHandle parent,UiWindow window){
        checkThread();if(closing)throw new IllegalStateException("Cannot open windows during cleanup");
        if(stack.isEmpty()||stack.getLast().handle!=parent)throw new IllegalStateException("Only the top window can open a dialog");
        if(stack.size()>=8)throw new IllegalStateException("At most 8 windows");
        resetEditing();var handle=new UiWindowHandle(this,parent.owner,window);stack.add(new State(handle));return handle;
    }
    private void resetEditing(){for(State state:stack){if(state.drag!=null)cancelledDrag=true;state.drag=null;state.repeat.clear();}}
    void requestClose(UiWindowHandle handle){
        checkThread();if(handle.closed)return;
        if(stack.isEmpty()||stack.getLast().handle!=handle)throw new IllegalStateException("Only the top window can request close");
        State state=stack.getLast();
        try{handle.window.closeRequest().accept(handle);}catch(RuntimeException ex){failed(state,ex);}
    }
    public void observe(Object screen,boolean nativeDialog){
        checkThread();if(isOpen()&&screen!=anchor)closeAll(UiWindow.CloseReason.SCREEN_CHANGED);
        else if(isOpen()&&nativeDialog)closeAll(UiWindow.CloseReason.NATIVE_DIALOG);
    }
    public void closeAll(UiWindow.CloseReason reason){checkThread();if(isOpen())close(stack.getFirst().handle,reason);}
    void close(UiWindowHandle handle,UiWindow.CloseReason reason){
        checkThread();if(handle.closed)return;
        int index=-1;for(int i=0;i<stack.size();i++)if(stack.get(i).handle==handle)index=i;
        if(index<0)return;
        List<State> removed=new ArrayList<>(stack.subList(index,stack.size()));stack.subList(index,stack.size()).clear();
        releaseBarrier=true;pointerCapture=false;resetEditing();
        // 清理期间禁止重开窗口，避免切屏/异常退出后遗留不可见的输入屏障。
        for(State state:removed){state.handle.closed=true;state.boxes.clear();state.scrolls.clear();state.focus=null;state.drag=null;state.editors.clear();state.repeat.clear();}
        boolean previousClosing=closing;closing=true;
        try {
        for(int i=removed.size()-1;i>=0;i--){UiWindowHandle h=removed.get(i).handle;
            for(int n=h.resources.size()-1;n>=0;n--)try{h.resources.get(n).close();}catch(Exception ex){report(h.owner,ex);}
            h.resources.clear();
            UiWindow window=h.window;h.window=null;
            try{window.onClose().accept(h==handle?reason:UiWindow.CloseReason.PARENT_CLOSED);}catch(RuntimeException ex){report(h.owner,ex);}
        }
        } finally {closing=previousClosing;if(stack.isEmpty())anchor=null;}
    }
    private void report(String owner,Throwable ex){try{errors.accept(owner,ex);}catch(RuntimeException ignored){/* 诊断输出故障不得中断剩余资源释放。 */}}
    private void failed(State state,Throwable ex){close(state.handle,UiWindow.CloseReason.ERROR);report(state.handle.owner,ex);}
    private static void enabled(UiNode node,boolean parent,Map<UiNode,Boolean> values){
        boolean value=parent&&node.enabled.getAsBoolean();values.put(node,value);
        for(UiNode child:node.children)enabled(child,value,values);
    }
    private static int px(double scale,int value){return Math.max(0,(int)Math.round(scale*value));}
    private static String text(UiNode node,Map<UiNode,String> values){return values.computeIfAbsent(node,n->{String s=Objects.requireNonNull(n.text.get(),"UI text");if(s.length()>16384)throw new IllegalArgumentException("UI text too long");return s;});}
    private static int[] widths(UiNode node,int width,double scale){
        int[] result=new int[node.children.size()];int free=Math.max(0,width-px(scale,node.size)*Math.max(0,result.length-1)),flex=0;
        for(int i=0;i<result.length;i++){int wanted=px(scale,node.children.get(i).width);if(wanted==0)flex++;else{result[i]=Math.min(free,wanted);free-=result[i];}}
        for(int i=0;i<result.length;i++)if(node.children.get(i).width==0){result[i]=flex==0?0:free/flex;free-=result[i];flex--;}
        return result;
    }
    private int height(UiNode node,int width,Canvas c,Map<UiNode,String> values){
        int pad=px(c.scale(),node.size);
        return switch(node.kind){
            case LABEL -> c.textHeight(text(node,values),Math.max(1,width));
            case BUTTON,TOGGLE,TEXT -> c.buttonHeight();
            case SPACE,SCROLL -> pad;
            case PANEL -> pad*2+height(node.children.getFirst(),Math.max(0,width-pad*2),c,values);
            case COLUMN -> {int total=pad*Math.max(0,node.children.size()-1);for(UiNode child:node.children)total+=height(child,child.width==0?width:Math.min(width,px(c.scale(),child.width)),c,values);yield total;}
            case ROW -> {int max=0;int[] widths=widths(node,width,c.scale());for(int i=0;i<widths.length;i++)max=Math.max(max,height(node.children.get(i),widths[i],c,values));yield max;}
        };
    }
    private void layout(State s,UiNode n,Rect r,Rect clip,boolean parentEnabled,Canvas c,Map<UiNode,String> values){
        boolean enabled=parentEnabled&&n.enabled.getAsBoolean();Rect visible=r.intersect(clip);int pad=px(c.scale(),n.size);
        switch(n.kind){
            case COLUMN -> {int y=r.y;for(UiNode child:n.children){int w=child.width==0?r.w:Math.min(r.w,px(c.scale(),child.width));int h=height(child,w,c,values);int x=r.x+(n.alignment==Ui.Align.CENTER?(r.w-w)/2:n.alignment==Ui.Align.END?r.w-w:0);layout(s,child,new Rect(x,y,w,h),clip,enabled,c,values);y+=h+pad;}}
            case ROW -> {int x=r.x;int[] widths=widths(n,r.w,c.scale());for(int i=0;i<widths.length;i++){UiNode child=n.children.get(i);int h=n.alignment==Ui.Align.STRETCH?r.h:height(child,widths[i],c,values);int y=r.y+(n.alignment==Ui.Align.CENTER?(r.h-h)/2:n.alignment==Ui.Align.END?r.h-h:0);layout(s,child,new Rect(x,y,widths[i],h),clip,enabled,c,values);x+=widths[i]+pad;}}
            case PANEL -> {s.boxes.add(new Box(n,r,visible,enabled,"",false));layout(s,n.children.getFirst(),new Rect(r.x+pad,r.y+pad,Math.max(0,r.w-pad*2),Math.max(0,r.h-pad*2)),visible,enabled,c,values);}
            case SCROLL -> {int contentW=Math.max(0,r.w-px(c.scale(),10));int h=height(n.children.getFirst(),contentW,c,values);int max=Math.max(0,h-r.h);int offset=Math.clamp(s.offsets.getOrDefault(n,0),0,max);s.offsets.put(n,offset);s.scrolls.add(new Scroll(n,visible,max));layout(s,n.children.getFirst(),new Rect(r.x,r.y-offset,contentW,h),visible,enabled,c,values);}
            case SPACE -> {}
            default -> {String value=text(n,values);if(n.kind==UiNode.Kind.TEXT)editor(s,n,value);s.boxes.add(new Box(n,r,visible,enabled,value,n.kind==UiNode.Kind.TOGGLE&&n.checked.getAsBoolean()));}
        }
    }
    public void render(Canvas canvas,int width,int height){
        checkThread();for(State s:List.copyOf(stack)){
            if(s.handle.closed)continue;
            try{
                s.ready=false;s.boxes.clear();s.scrolls.clear();
                double scale=canvas.scale();if(!Double.isFinite(scale)||scale<=0||scale>8)throw new IllegalArgumentException("UI scale");
                int margin=Math.min(px(scale,12),Math.max(0,Math.min(width,height)/8));
                int w=Math.max(1,Math.min(px(scale,s.handle.window.width()),width-margin*2));
                int h=Math.max(1,Math.min(px(scale,s.handle.window.maxHeight()),height-margin*2));
                int title=canvas.buttonHeight()+margin;
                Map<UiNode,String> values=new IdentityHashMap<>();
                int bodyW=Math.max(1,w-margin*2-px(scale,10));int natural=height(s.handle.window.content(),bodyW,canvas,values);
                UiNode footer=s.handle.window.footer();int footerNatural=footer==null?0:height(footer,bodyW,canvas,values);
                int gap=footer==null?0:px(scale,8);
                h=Math.min(h,natural+footerNatural+gap+title+margin*2);s.bounds=new Rect((width-w)/2,(height-h)/2,w,h);
                int available=Math.max(0,h-title-margin);
                // 极小视口中为正文保留空间，页脚自身也可滚动，不让隐藏按钮产生命中。
                int footerH=footer==null?0:Math.min(footerNatural,Math.max(0,available-canvas.buttonHeight()-gap));
                Rect body=new Rect(s.bounds.x+margin,s.bounds.y+title,bodyW,Math.max(0,available-footerH-gap));
                int max=Math.max(0,natural-body.h);s.windowOffset=Math.clamp(s.windowOffset,0,max);s.scrolls.add(new Scroll(null,body,max));
                layout(s,s.handle.window.content(),new Rect(body.x,body.y-s.windowOffset,body.w,natural),body,true,canvas,values);
                if(footer!=null){
                    Rect area=new Rect(body.x,body.y+body.h+gap,body.w,footerH);int footerMax=Math.max(0,footerNatural-footerH);
                    int offset=Math.clamp(s.offsets.getOrDefault(footer,0),0,footerMax);s.offsets.put(footer,offset);s.scrolls.add(new Scroll(footer,area,footerMax));
                    layout(s,footer,new Rect(area.x,area.y-offset,area.w,footerNatural),area,true,canvas,values);
                }
                Rect close=new Rect(s.bounds.x+w-margin-canvas.buttonHeight(),s.bounds.y+margin/2,canvas.buttonHeight(),canvas.buttonHeight());
                s.boxes.add(new Box(s.close,close,close.intersect(s.bounds),true,"X",false));
                if(s.focus!=null&&s.boxes.stream().noneMatch(b->b.node==s.focus&&b.enabled&&b.clip.w>0&&b.clip.h>0))s.focus=null;
                canvas.window(s.bounds,s.handle.window.title(),s.handle.window.modal());
                for(Box b:s.boxes){if(b.clip.w<=0||b.clip.h<=0)continue;boolean focused=s==stack.getLast()&&s.focus==b.node;
                    switch(b.node.kind){
                        case LABEL -> canvas.label(b.bounds,b.clip,b.text);
                        case PANEL -> canvas.panel(b.bounds,b.clip);
                        case BUTTON -> canvas.button(b.bounds,b.clip,b.text,b.enabled,focused);
                        case TOGGLE -> canvas.toggle(b.bounds,b.clip,b.text,b.checked,b.enabled,focused);
                        case TEXT -> {TextEditor editor=s.editors.get(b.node);editor.measure(canvas::textAdvances,scale,canvas.lineHeight(),b.bounds.w);canvas.text(b.bounds,b.clip,editor.text,editor.caret,editor.anchor,editor.offset,focused,b.enabled);}
                        default -> {}
                    }
                }
                for(Scroll scroll:s.scrolls)if(scroll.max>0)canvas.scrollbar(scroll.bounds,scroll.bounds,scroll.node==null?s.windowOffset:s.offsets.get(scroll.node),scroll.max);
                if(s==stack.getLast())for(int i=s.boxes.size()-1;i>=0;i--){Box b=s.boxes.get(i);if(!b.node.tip.isEmpty()&&b.clip.contains(mouseX,mouseY)){canvas.tooltip(b.node.tip,(int)mouseX,(int)mouseY);break;}}
                s.ready=true;
            }catch(RuntimeException ex){failed(s,ex);}
        }
    }
    public Capture input(Input in){return input(in,new Editing(false,false,0,true,in.down,System.nanoTime()));}
    public Capture input(Input in,Editing editing){
        checkThread();mouseX=in.x;mouseY=in.y;
        boolean pressed=editing.leftDown&&!leftWasDown;leftWasDown=editing.leftDown;
        if(!editing.active){resetEditing();pointerCapture=false;inputDecision="inactive";return new Capture(isOpen()||releaseBarrier,isOpen()||releaseBarrier);}
        boolean discardPointer=cancelledDrag;if(!editing.leftDown)cancelledDrag=false;
        inputDecision="idle";
        if(releaseBarrier){
            inputDecision="release-barrier";
            if(!in.down&&!in.enter&&!in.escape&&!in.tab)releaseBarrier=false;
            return new Capture(true,true);
        }
        if(stack.isEmpty())return new Capture(false,false);
        State s=stack.getLast();boolean inside=s.bounds.contains(in.x,in.y);
        if(in.down&&inside)pointerCapture=true;
        boolean pointer=s.handle.window.modal()||inside||pointerCapture||discardPointer||!s.ready;
        boolean keyboard=s.handle.window.modal()||s.focus!=null||!s.ready;
        if(!in.down)pointerCapture=false;
        try{
            if(!s.ready){inputDecision="await-render";return new Capture(pointer,keyboard);}
            Map<UiNode,Boolean> enabled=new IdentityHashMap<>();enabled(s.handle.window.content(),true,enabled);if(s.handle.window.footer()!=null)enabled(s.handle.window.footer(),true,enabled);enabled.put(s.close,true);
            if(in.escape&&keyboard){inputDecision="escape-close";requestClose(s.handle);return new Capture(true,true);}
            UiNode previousFocus=s.focus;
            if(pressed&&!discardPointer){
                Box target=hit(s,in.x,in.y);
                if(target!=null&&target.node.kind==UiNode.Kind.TEXT&&target.enabled&&enabled.get(target.node)){
                    s.focus=target.node;s.drag=target.node;keyboard=true;position(s,target,in.x,in.shift);
                }
            }
            boolean wasDrag=s.drag!=null;
            if(s.drag!=null){
                Box drag=s.boxes.stream().filter(b->b.node==s.drag&&b.enabled&&enabled.get(b.node)&&b.clip.w>0&&b.clip.h>0).findFirst().orElse(null);
                if(drag==null)s.drag=null;
                else if(editing.leftDown||in.click)position(s,drag,in.x,true);
                if(!editing.leftDown)s.drag=null;
            }
            if(in.click&&!wasDrag&&!discardPointer){
                Box target=hit(s,in.x,in.y);s.focus=null;
                inputDecision=target==null?"click-miss":"click-disabled";
                if(target!=null&&target.enabled&&enabled.get(target.node)){
                    inputDecision="click-"+target.node.kind;s.focus=target.node;keyboard=true;
                    if(target.node.kind!=UiNode.Kind.TEXT){s.repeat.clear();activate(s,target);return new Capture(true,true);}
                    position(s,target,in.x,in.shift);
                }
            }
            if(in.wheel!=0&&inside)for(int i=s.scrolls.size()-1;i>=0;i--){Scroll scroll=s.scrolls.get(i);if(scroll.bounds.contains(in.x,in.y)&&scroll.max>0){int current=scroll.node==null?s.windowOffset:s.offsets.get(scroll.node);int next=(int)Math.clamp((long)current-(long)in.wheel*32/120,0,scroll.max);if(scroll.node==null)s.windowOffset=next;else s.offsets.put(scroll.node,next);s.ready=false;break;}}
            if(in.tab&&!in.shift&&keyboard&&s.focus!=null&&enabled.getOrDefault(s.focus,false)&&s.focus.completion!=null){s.focus.completion.accept(s.handle);return new Capture(true,true);}
            if(in.tab&&keyboard){List<UiNode> focusable=s.boxes.stream().filter(b->b.enabled&&enabled.get(b.node)&&interactive(b.node)&&b.clip.w>0&&b.clip.h>0).map(Box::node).toList();if(!focusable.isEmpty()){int index=focusable.indexOf(s.focus);s.focus=focusable.get(index<0?(in.shift?focusable.size()-1:0):Math.floorMod(index+(in.shift?-1:1),focusable.size()));}}
            if(previousFocus!=s.focus)s.repeat.clear();
            Box focused=s.boxes.stream().filter(b->b.node==s.focus&&b.enabled&&enabled.get(b.node)).findFirst().orElse(null);
            if(focused!=null){
                if(focused.node.kind==UiNode.Kind.TEXT){
                    if(focused.node.history!=null&&(editing.historyUp||editing.historyDown)){s.repeat.clear();focused.node.history.accept(editing.historyUp?-1:1);return new Capture(true,true);}
                    edit(s,focused.node,in,editing);
                    if(in.enter&&focused.node.submit!=null&&s.handle.isOpen()&&!stack.isEmpty()&&stack.getLast()==s){focused.node.submit.accept(s.handle);return new Capture(true,true);}
                }
                else if(in.enter){activate(s,focused);return new Capture(true,true);}
            }
            else s.repeat.clear();
            return new Capture(pointer,keyboard);
        }catch(RuntimeException ex){failed(s,ex);return new Capture(true,true);}
    }
    private static boolean interactive(UiNode node){return node.kind==UiNode.Kind.BUTTON||node.kind==UiNode.Kind.TOGGLE||node.kind==UiNode.Kind.TEXT;}
    private void activate(State s,Box box){if(box.node.kind==UiNode.Kind.BUTTON)box.node.action.accept(s.handle);else if(box.node.kind==UiNode.Kind.TOGGLE)box.node.change.accept(!box.node.checked.getAsBoolean());}
    private static Box hit(State s,double x,double y){for(int i=s.boxes.size()-1;i>=0;i--){Box b=s.boxes.get(i);if(b.clip.contains(x,y)&&interactive(b.node))return b;}return null;}
    private void position(State s,Box box,double x,boolean extend){
        TextEditor e=box.node.boundText?editor(s,box.node,Objects.requireNonNull(box.node.text.get())):s.editors.get(box.node);
        e.move(e.hit(x-box.bounds.x-4),extend);
    }
    private TextEditor editor(State s,UiNode node,String value){
        if(value.codePointCount(0,value.length())>node.size||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid bound single-line text");
        TextEditor e=s.editors.computeIfAbsent(node,k->new TextEditor(value));
        if(node.boundText&&!value.equals(e.text)){e.reset(value);s.drag=null;s.repeat.clear();}
        return e;
    }
    private boolean copy(String value){try{return clipboard.test(value);}catch(RuntimeException ex){return false;}}
    private void edit(State s,UiNode node,Input in,Editing extra){
        TextEditor e=node.boundText?editor(s,node,Objects.requireNonNull(node.text.get())):s.editors.get(node);String before=e.text;
        int pressed=(in.back?1:0)|(in.delete?2:0)|(in.left?4:0)|(in.right?8:0)|(in.home?16:0)|(in.end?32:0);
        int keys=s.repeat.pulse(pressed,extra.held,extra.nanos);
        if(in.selectAll)e.all();
        if((extra.copy||extra.cut)&&e.selected()&&copy(e.selection())&&extra.cut)e.delete(false);
        if((keys&16)!=0)e.move(0,in.shift);if((keys&32)!=0)e.move(e.text.length(),in.shift);
        if((keys&4)!=0)e.left(in.shift);if((keys&8)!=0)e.right(in.shift);
        if((keys&1)!=0)e.delete(true);else if((keys&2)!=0)e.delete(false);
        String addition=in.paste!=null?in.paste:in.typed;
        if(addition!=null&&!addition.isEmpty())e.insert(addition,node.size);
        if(!before.equals(e.text))node.edit.accept(e.text);
    }
}

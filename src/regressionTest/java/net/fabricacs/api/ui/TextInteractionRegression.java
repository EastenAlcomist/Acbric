/* TextInteractionRegression.java — 验证真实窗口命中下的文本选区、拖动、剪贴板失败、重复键及固定页脚。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class TextInteractionRegression {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS UI editing: "+name);}
    private static final class Canvas implements UiRuntime.Canvas {
        Map<String,UiRuntime.Rect> buttons=new LinkedHashMap<>();UiRuntime.Rect field;int caret,anchor,offset;String value;double factor=1;
        public double scale(){return factor;}public int lineHeight(){return (int)(16*factor);}public int buttonHeight(){return (int)(24*factor);}
        public int textHeight(String text,int width){return lineHeight();}
        public int textWidth(String text){return (int)(factor*text.codePoints().map(c->c=='i'?4:c=='W'?16:c>0xFFFF?20:c>127?12:8).sum());}
        public void window(UiRuntime.Rect b,String t,boolean m){}public void panel(UiRuntime.Rect b,UiRuntime.Rect c){}public void label(UiRuntime.Rect b,UiRuntime.Rect c,String t){}
        public void button(UiRuntime.Rect b,UiRuntime.Rect c,String t,boolean e,boolean f){buttons.put(t,b.intersect(c));}
        public void toggle(UiRuntime.Rect b,UiRuntime.Rect c,String t,boolean v,boolean e,boolean f){}
        public void text(UiRuntime.Rect b,UiRuntime.Rect c,String t,int p,boolean s,boolean f,boolean e){}
        public void text(UiRuntime.Rect b,UiRuntime.Rect c,String t,int p,int a,int o,boolean f,boolean e){field=b.intersect(c);value=t;caret=p;anchor=a;offset=o;}
        public void tooltip(String t,int x,int y){}public void scrollbar(UiRuntime.Rect b,UiRuntime.Rect c,int o,int m){}
        void render(UiRuntime r){buttons.clear();r.render(this,800,600);}
    }
    private static UiRuntime.Input in(double x,double y,boolean click,boolean down,boolean shift,String typed,String key,int wheel){return new UiRuntime.Input(x,y,click,down,wheel,typed,null,key.equals("TAB"),shift,key.equals("ENTER"),key.equals("ESC"),key.equals("BACK"),key.equals("DELETE"),key.equals("LEFT"),key.equals("RIGHT"),key.equals("HOME"),key.equals("END"),key.equals("ALL"));}
    private static UiRuntime.Input key(String key,boolean shift){return in(-1,-1,false,false,shift,"",key,0);}
    private static void click(UiRuntime r,UiRuntime.Rect b){r.input(in(b.x()+1,b.y()+1,true,false,false,"","",0));}
    public static int run(){
        checks=0;Canvas c=new Canvas();List<Throwable> errors=new ArrayList<>();AtomicReference<String> value=new AtomicReference<>("Wi😀中Z"),clipboard=new AtomicReference<>("");boolean[] clipboardOk={true},enabled={true};int[] callbacks={0},buttons={0};
        UiRuntime r=new UiRuntime((o,e)->errors.add(e),v->{if(!clipboardOk[0])return false;clipboard.set(v);return true;});Object screen=new Object();
        var h=r.open("test",new UiWindow("Editor",360,260,true,Ui.column(8,Ui.textField(value::get,64,v->{value.set(v);callbacks[0]++;}).enabled(()->enabled[0]),Ui.button("Other",w->buttons[0]++))),screen);c.render(r);
        r.input(in(c.field.x()+4+19,c.field.y()+4,true,false,false,"","",0));c.render(r);check(c.caret==2,"click uses measured variable glyph widths");
        r.input(key("RIGHT",true));c.render(r);check(c.anchor==2&&c.caret==4,"Shift Right selects one complete emoji");
        int count=callbacks[0];r.input(key("",false),new UiRuntime.Editing(true,false,0,true,false,0));check(clipboard.get().equals("😀")&&callbacks[0]==count,"copy only selected text without edit callback");
        clipboardOk[0]=false;r.input(key("",false),new UiRuntime.Editing(false,true,0,true,false,0));check(value.get().equals("Wi😀中Z"),"failed clipboard write does not delete selection");
        clipboardOk[0]=true;r.input(key("",false),new UiRuntime.Editing(false,true,0,true,false,0));check(value.get().equals("Wi中Z")&&callbacks[0]==count+1,"cut publishes one edit after successful copy");
        r.input(key("HOME",false));r.input(key("END",true));r.input(key("LEFT",false));c.render(r);check(c.caret==0&&c.anchor==0,"Left collapses a selection to its start");
        r.input(key("END",true));r.input(key("RIGHT",false));c.render(r);check(c.caret==4&&c.anchor==4,"Right collapses selection to its end");
        r.input(key("HOME",false));r.input(key("RIGHT",true));r.input(in(-1,-1,false,false,false,"A","",0));check(value.get().equals("Ai中Z"),"typing replaces a partial selection");
        r.input(key("END",false));c.render(r);r.input(in(c.field.x()+4+8,c.field.y()+4,true,false,true,"","",0));c.render(r);check(c.anchor==4&&c.caret==1,"Shift click extends from original anchor");
        r.input(key("DELETE",false));check(value.get().equals("A"),"Delete removes partial selection");
        value.set("Wi😀中Z");c.render(r);
        double y=c.field.y()+4,x=c.field.x()+4;
        r.input(in(x,y,false,true,false,"","",0));r.input(in(x+40,y,false,true,false,"","",0));c.render(r);check(c.anchor==0&&c.caret==4,"drag selects measured Unicode range");
        var other=c.buttons.get("Other");r.input(in(other.x()+other.w()-1,other.y()+2,true,false,false,"","",0));check(buttons[0]==0,"drag release over another button does not activate it");
        c.render(r);r.input(in(x,y,false,true,false,"","",0));r.input(in(x,y,false,true,false,"","",0),new UiRuntime.Editing(false,false,0,false,true,0));
        r.input(in(x+40,y,false,true,false,"","",0),new UiRuntime.Editing(false,false,0,true,true,1));c.render(r);check(c.caret==0,"focus loss clears mouse selection gesture");
        r.input(in(other.x()+2,other.y()+2,true,false,false,"","",0));check(buttons[0]==0,"cancelled drag release after focus return cannot activate a button");
        value.set("abcdefghijklmnopqrstuvwxyz");c.render(r);click(r,c.field);r.input(key("END",false));
        r.input(key("BACK",false),new UiRuntime.Editing(false,false,1,true,false,0));check(value.get().length()==25,"delete key immediately acts once");
        r.input(key("",false),new UiRuntime.Editing(false,false,1,true,false,399_000_000L));check(value.get().length()==25,"held key waits initial delay");
        r.input(key("",false),new UiRuntime.Editing(false,false,1,true,false,400_000_000L));check(value.get().length()==24,"held delete repeats after delay");
        r.input(key("",false),new UiRuntime.Editing(false,false,1,true,false,9_000_000_000L));check(value.get().length()==23,"long frame does not replay accumulated deletes");
        r.input(key("",false),new UiRuntime.Editing(false,false,1,false,false,10_000_000_000L));
        r.input(key("",false),new UiRuntime.Editing(false,false,1,true,false,11_000_000_000L));check(value.get().length()==23,"held key cannot resume itself after focus loss");
        r.input(key("BACK",false),new UiRuntime.Editing(false,false,1,true,false,12_000_000_000L));click(r,c.buttons.get("Other"));click(r,c.field);
        r.input(key("",false),new UiRuntime.Editing(false,false,1,true,false,13_000_000_000L));check(value.get().length()==22,"focus changes clear pending repetition");
        enabled[0]=false;count=callbacks[0];r.input(key("BACK",false));check(callbacks[0]==count,"disabled field rejects editing");enabled[0]=true;
        value.set("W".repeat(50));c.render(r);r.input(key("END",false));c.render(r);check(c.offset>0,"long text scrolls to keep caret visible");
        r.input(in(c.field.x()+4,c.field.y()+4,true,false,false,"","",0));c.render(r);check(c.caret>0&&c.caret<50,"click accounts for horizontal offset");
        r.input(key("HOME",false));c.render(r);check(c.offset==0&&c.caret==0,"Home reveals beginning again");
        c.factor=2;c.render(r);r.input(in(c.field.x()+4+32,c.field.y()+4,true,false,false,"","",0));c.render(r);check(c.caret==1,"font scaling also updates hit measurements");
        value.set("😀");c.render(r);check(c.caret==2&&c.anchor==2&&c.offset==0,"external controlled update resets selection and scroll");
        h.close();c.factor=1;

        UiNode[] rows=new UiNode[40];for(int i=0;i<rows.length;i++)rows[i]=Ui.button("Row "+i,w->{});
        int[] closeRequests={0},cleanups={0};
        var fixed=r.open("test",new UiWindow("Footer",400,300,true,Ui.column(8,rows),reason->cleanups[0]++)
                .withFooter(Ui.row(8,Ui.button("Apply",w->buttons[0]++),Ui.button("Cancel",UiWindowHandle::requestClose)))
                .onCloseRequest(w->{closeRequests[0]++;w.confirm("Discard?","Unsaved","Discard","Keep",w::close);}),screen);
        c.render(r);var apply=c.buttons.get("Apply");check(apply.h()==24,"footer visible before scrolling long content");
        var row=c.buttons.get("Row 0");r.input(in(row.x()+1,row.y()+1,false,false,false,"","",-1200));c.render(r);check(c.buttons.get("Apply").equals(apply),"body scroll does not move footer");
        int before=buttons[0];click(r,apply);check(buttons[0]==before+1,"fixed footer keeps correct hit geometry");
        click(r,c.buttons.get("X"));c.render(r);check(fixed.isOpen()&&closeRequests[0]==1&&c.buttons.containsKey("Keep"),"X requests close and opens confirmation");
        click(r,c.buttons.get("Keep"));r.input(key("",false));c.render(r);check(fixed.isOpen()&&cleanups[0]==0,"dismiss confirmation preserves parent resources");
        r.input(key("ESC",false));c.render(r);check(closeRequests[0]==2,"Escape uses same close request");
        click(r,c.buttons.get("Discard"));check(!fixed.isOpen()&&cleanups[0]==1,"confirmed close disposes parent exactly once");
        var force=r.open("test",new UiWindow("Forced",300,200,true,Ui.label("x")).onCloseRequest(w->{throw new AssertionError("Must not intercept forced cleanup");}),screen);
        r.observe(new Object(),false);check(!force.isOpen(),"screen transition bypasses confirmation");
        var direct=r.open("test",new UiWindow("Direct",300,200,true,Ui.label("x")).onCloseRequest(w->{throw new AssertionError("Must not intercept close");}),screen);direct.close();check(!direct.isOpen(),"programmatic close retains old unconditional behavior");
        UiNode shared=Ui.label("duplicate");try{new UiWindow("Bad",300,200,true,shared).withFooter(shared);throw new AssertionError();}catch(IllegalArgumentException expected){check(true,"content and footer share one uniqueness validation");}
        var tiny=r.open("test",new UiWindow("Tiny",300,200,true,Ui.column(8,rows)).withFooter(Ui.column(8,Ui.label("status"),Ui.button("Bottom",w->{}))),screen);c.factor=2;r.render(c,180,120);check(tiny.isOpen()&&errors.isEmpty(),"tiny viewport clips footer safely");tiny.close();

        TextEditor e=new TextEditor("A😀中Z");e.move(1,false);e.move(4,true);e.insert("123456",5);check(e.text.equals("A123Z"),"replacement limit counts retained and selected Unicode code points");
        e.all();e.insert("\n\r\t",5);check(e.text.equals("A123Z")&&e.selected(),"control-only paste preserves selection and text");
        e.insert("\uD800B",5);check(e.text.equals("B"),"paste drops isolated surrogate instead of corrupting text");
        check(errors.isEmpty(),"all interaction paths leave error handler empty");
        r.closeAll(UiWindow.CloseReason.GAME_EXIT);r.input(key("",false));int[] submitted={0},completed={0},history={0};
        UiNode command=Ui.textField(value::get,64,value::set).onSubmit(w->submitted[0]++);command.completion=w->completed[0]++;command.history=d->history[0]+=d;
        r.open("test",new UiWindow("Command",360,260,true,Ui.column(8,command.width(280),Ui.button("Other",w->buttons[0]++))),screen);c.render(r);click(r,c.field);r.input(key("ENTER",false));
        check(submitted[0]==1,"focused text Enter submits once after copied modifiers");
        r.input(key("TAB",false));check(completed[0]==1,"command text Tab invokes completion");
        r.input(key("",false),new UiRuntime.Editing(false,false,0,true,false,0,true,false));check(history[0]==-1,"command history Up uses explicit key press");
        r.input(key("",false),new UiRuntime.Editing(false,false,0,false,false,0,false,true));check(history[0]==-1,"inactive window cannot navigate command history");
        r.input(key("TAB",true));r.input(key("ENTER",false));check(!r.isOpen()&&submitted[0]==1,"Shift Tab reaches title close through normal focus traversal");
        try{Ui.label("x").onSubmit(w->{});throw new AssertionError();}catch(IllegalStateException expected){check(true,"submit handler only valid on text field");}
        return checks;
    }
}

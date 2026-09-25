/* UiRegression.java — 无图形上下文验证布局、裁剪命中、焦点、Unicode 编辑和清理契约。 */
package net.fabricacs.api.ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiRegression {
    private static int checks;
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;System.out.println("PASS UI: "+message);}
    private static final class Canvas implements UiRuntime.Canvas {
        final Map<String,UiRuntime.Rect> controls=new LinkedHashMap<>();final List<UiRuntime.Rect> clips=new ArrayList<>();
        double factor=1;int windows;String value="";int caret;
        public double scale(){return factor;}public int lineHeight(){return 16;}public int buttonHeight(){return 24;}
        public int textHeight(String text,int width){return Math.max(16,((text.length()*8+Math.max(1,width)-1)/Math.max(1,width))*16);}
        public void window(UiRuntime.Rect r,String title,boolean modal){windows++;}
        public void panel(UiRuntime.Rect r,UiRuntime.Rect clip){clips.add(clip);}
        public void label(UiRuntime.Rect r,UiRuntime.Rect clip,String text){clips.add(clip);}
        public void button(UiRuntime.Rect r,UiRuntime.Rect clip,String text,boolean enabled,boolean focus){controls.put(text,r.intersect(clip));}
        public void toggle(UiRuntime.Rect r,UiRuntime.Rect clip,String text,boolean checked,boolean enabled,boolean focus){controls.put(text,r.intersect(clip));}
        public void text(UiRuntime.Rect r,UiRuntime.Rect clip,String text,int caret,boolean selected,boolean focus,boolean enabled){controls.put("field",r.intersect(clip));value=text;this.caret=caret;}
        public void tooltip(String text,int x,int y){}public void scrollbar(UiRuntime.Rect r,UiRuntime.Rect clip,int offset,int max){clips.add(clip);}
    }
    private static UiRuntime.Input input(double x,double y,boolean click,int wheel,String typed,String key){
        return new UiRuntime.Input(x,y,click,false,wheel,typed,null,"TAB".equals(key)||"SHIFT_TAB".equals(key),"SHIFT_TAB".equals(key),"ENTER".equals(key),"ESCAPE".equals(key),"BACK".equals(key),"DELETE".equals(key),"LEFT".equals(key),"RIGHT".equals(key),"HOME".equals(key),"END".equals(key),"ALL".equals(key));
    }
    private static void click(UiRuntime runtime,UiRuntime.Rect r){runtime.input(input(r.x()+1,r.y()+1,true,0,"",""));}
    public static int run() throws Exception {
        checks=0;List<Throwable> errors=new ArrayList<>();UiRuntime runtime=new UiRuntime((o,e)->errors.add(e));Object screen=new Object();Canvas canvas=new Canvas();
        int[] clicks={0};boolean[] checked={false};AtomicReference<String> edited=new AtomicReference<>("");
        UiNode root=Ui.column(8,Ui.label("Layout"),Ui.row(4,Ui.button("Left",h->clicks[0]++).width(80),Ui.button("Right",h->clicks[0]++)),
                Ui.toggle("Toggle",()->checked[0],v->checked[0]=v),Ui.textField("",4,edited::set));
        UiWindowHandle h=runtime.open("test_mod",new UiWindow("Test",400,400,true,root),screen);
        check(runtime.input(input(0,0,true,0,"","")).pointer(),"unrendered window blocks opening input");
        runtime.render(canvas,800,600);
        check(canvas.controls.get("Left").w()==80,"fixed row width respected");
        check(canvas.controls.get("Right").x()>canvas.controls.get("Left").x()+80,"row gap separates controls");
        click(runtime,canvas.controls.get("Left"));check(clicks[0]==1,"one click activates once");
        click(runtime,canvas.controls.get("Toggle"));check(checked[0],"toggle changes bound value");
        click(runtime,canvas.controls.get("field"));
        check(runtime.input(input(-1,-1,false,0,"A😀中"," ")).keyboard(),"focused text captures keyboard");
        check(edited.get().equals("A😀中"),"Unicode text is preserved");
        runtime.input(input(-1,-1,false,0,"","LEFT"));runtime.input(input(-1,-1,false,0,"","BACK"));
        check(edited.get().equals("A中"),"backspace removes complete code point");
        runtime.input(input(-1,-1,false,0,"","ALL"));runtime.input(input(-1,-1,false,0,"123456",""));
        check(edited.get().equals("1234"),"select all and code-point length bound");
        runtime.input(input(-1,-1,false,0,"","HOME"));runtime.input(input(-1,-1,false,0,"","DELETE"));check(edited.get().equals("234"),"home and forward delete");
        runtime.input(input(-1,-1,false,0,"","TAB"));runtime.input(input(-1,-1,false,0,"","ENTER"));
        check(!h.isOpen(),"Tab reaches close button and Enter closes");
        check(runtime.input(input(1,1,true,0,"","")).pointer(),"closed frame release barrier consumes click");
        check(!runtime.input(input(1,1,false,0,"","")).pointer(),"release barrier ends without freezing input");

        List<String> cleanup=new ArrayList<>();
        UiWindowHandle parent=runtime.open("test_mod",new UiWindow("Parent",300,250,true,Ui.button("Parent",w->clicks[0]++),r->cleanup.add(r.name())),screen);
        parent.manage(()->cleanup.add("resource1"));parent.manage(()->{cleanup.add("resource2");throw new Exception("cleanup error");});
        UiWindowHandle child=parent.confirm("Confirm","Question","Yes","No",()->clicks[0]+=100);
        runtime.render(canvas,800,600);click(runtime,canvas.controls.get("Yes"));
        check(!child.isOpen()&&parent.isOpen()&&clicks[0]==101,"confirmation closes child and invokes accepted action once");
        parent.message("Message","Message text");runtime.observe(new Object(),false);
        check(!parent.isOpen()&&!runtime.isOpen(),"screen change closes full window stack");
        check(cleanup.equals(List.of("resource2","resource1","SCREEN_CHANGED")),"cleanup LIFO continues after one failure");
        parent.close();check(cleanup.size()==3,"close is idempotent");check(errors.size()==1,"cleanup exception reported");
        try{parent.dialog(new UiWindow("closed",200,200,true,Ui.label("x")));throw new AssertionError();}catch(IllegalStateException expected){check(true,"closed handle cannot open dialog");}

        UiWindowHandle normal=runtime.open("test_mod",new UiWindow("Normal",300,200,false,Ui.button("Normal",w->clicks[0]++)),screen);
        runtime.render(canvas,800,600);var free=runtime.input(input(0,0,false,0,"",""));check(!free.pointer()&&!free.keyboard(),"normal window leaves outside input available");
        click(runtime,canvas.controls.get("Normal"));check(runtime.input(input(-1,-1,false,0,"","")).keyboard(),"normal window owns focused keyboard");
        runtime.input(input(0,0,true,0,"",""));check(!runtime.input(input(0,0,false,0,"","")).keyboard(),"outside click clears normal-window focus");
        runtime.observe(screen,true);check(!normal.isOpen(),"native dialog takes priority and closes MOD UI");

        int[] hiddenClicks={0};UiNode[] many=new UiNode[12];for(int i=0;i<many.length;i++)many[i]=Ui.button("item"+i,w->hiddenClicks[0]++);
        var scrolling=runtime.open("test_mod",new UiWindow("Scroll",260,220,true,Ui.scroll(80,Ui.column(0,many))),screen);
        canvas.controls.clear();runtime.render(canvas,800,600);check(!canvas.controls.containsKey("item11"),"offscreen controls not rendered as visible hits");
        UiRuntime.Rect first=canvas.controls.get("item0");
        runtime.input(input(first.x()+1,first.y()+1,false,-120,"",""));
        click(runtime,first);check(hiddenClicks[0]==0,"wheel invalidates old hit map until next render");
        canvas.controls.clear();runtime.render(canvas,800,600);check(!canvas.controls.containsKey("item0"),"scroll offset changes visible controls");
        check(canvas.clips.stream().allMatch(r->r.w()>=0&&r.h()>=0),"clip extents are nonnegative");
        canvas.factor=2;runtime.render(canvas,180,120);check(errors.size()==1,"tiny viewport and scale change remain valid");scrolling.close();

        boolean[] enabled={true};var disabled=runtime.open("test_mod",new UiWindow("Disabled",260,200,true,Ui.button("Guard",w->clicks[0]++).enabled(()->enabled[0])),screen);
        canvas.factor=1;runtime.render(canvas,800,600);int count=clicks[0];enabled[0]=false;click(runtime,canvas.controls.get("Guard"));check(clicks[0]==count,"disabled state rechecked at activation");disabled.close();
        var failure=runtime.open("test_mod",new UiWindow("Failure",260,200,true,Ui.button("Fail",w->{throw new IllegalStateException("boom");})),screen);
        runtime.render(canvas,800,600);click(runtime,canvas.controls.get("Fail"));check(!failure.isOpen()&&errors.size()==2,"callback failure closes and reports owning window");
        var threadWindow=runtime.open("test_mod",new UiWindow("Thread",260,200,true,Ui.label("x")),screen);AtomicReference<Throwable> wrong=new AtomicReference<>();
        Thread worker=new Thread(()->{try{threadWindow.close();}catch(Throwable t){wrong.set(t);}});worker.start();worker.join();check(wrong.get() instanceof IllegalStateException&&threadWindow.isOpen(),"off-thread mutation rejected");runtime.closeAll(UiWindow.CloseReason.GAME_EXIT);
        UiNode duplicate=Ui.label("same");try{new UiWindow("Duplicate",200,200,true,Ui.column(0,duplicate,duplicate));throw new AssertionError();}catch(IllegalArgumentException expected){check(true,"shared nodes in one tree rejected");}
        check(!runtime.isOpen(),"runtime empty after exit");
        enabled[0]=true;
        var ancestor=runtime.open("test_mod",new UiWindow("Ancestor",300,200,true,Ui.panel(4,Ui.button("Child",w->clicks[0]++)).enabled(()->enabled[0])),screen);
        runtime.render(canvas,800,600);enabled[0]=false;count=clicks[0];click(runtime,canvas.controls.get("Child"));
        check(clicks[0]==count,"disabled ancestor rechecked before child activation");ancestor.close();
        var reverse=runtime.open("test_mod",new UiWindow("Reverse",300,200,true,Ui.button("First",w->clicks[0]++)),screen);
        runtime.render(canvas,800,600);runtime.input(input(0,0,false,0,"","SHIFT_TAB"));runtime.input(input(0,0,false,0,"","ENTER"));
        check(!reverse.isOpen(),"initial Shift Tab focuses last control");
        var reentrant=runtime.open("test_mod",new UiWindow("Reentrant",300,200,true,Ui.label("x"),r->runtime.open("test_mod",new UiWindow("Bad",300,200,true,Ui.label("x")),screen)),screen);
        runtime.observe(new Object(),false);
        check(!runtime.isOpen()&&!reentrant.isOpen()&&errors.size()==3,"cleanup cannot reopen a stale-screen window");
        check(reentrant.window==null&&reentrant.resources.isEmpty(),"closed handle releases callbacks and managed resources");
        UiRuntime brokenReporter=new UiRuntime((o,e)->{throw new IllegalStateException("logger");});
        var clean=brokenReporter.open("test_mod",new UiWindow("Cleanup",300,200,true,Ui.label("x")),screen);
        List<Integer> resources=new ArrayList<>();clean.manage(()->resources.add(1));clean.manage(()->{throw new Exception("resource");});clean.close();
        check(resources.equals(List.of(1))&&!brokenReporter.isOpen(),"diagnostic failure cannot interrupt cleanup");
        return checks;
    }
}

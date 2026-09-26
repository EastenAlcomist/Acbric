/* UiBridge.java — 原生游戏接入与按 MOD 隔离的界面入口；不替换 Screen，也不暂停底层模拟。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;
import com.zarkonnen.airships.AirshipGame.ScaledInput;
import com.zarkonnen.catengine.*;
import com.zarkonnen.catengine.util.Pt;
import net.fabricacs.api.ui.*;
import java.util.*;
import java.util.function.Supplier;

public final class UiBridge {
    private static final Map<String,Entry> entries=new LinkedHashMap<>();
    private static AirshipGame currentGame;
    private static UiRuntime runtime;
    private static Pt cursor;
    private static boolean filteredWithWindow;
    private static boolean pointerMasked;
    private static boolean graveHeld;
    private static UiInputDiagnostics inputDiagnostics;
    private UiBridge(){}
    private static final class Entry implements ModUi.Registration {
        final String owner,id;Supplier<String> label;Supplier<UiWindow> factory;
        Entry(String owner,String id,Supplier<String> label,Supplier<UiWindow> factory){this.owner=owner;this.id=id;this.label=label;this.factory=factory;}
        public String id(){return id;}
        public boolean isRegistered(){synchronized(entries){return factory!=null;}}
        public void close(){synchronized(entries){entries.remove(owner+":"+id,this);factory=null;label=null;}}
    }
    public static ModUi.Registration register(String owner,String id,String label,Supplier<UiWindow> factory){
        Objects.requireNonNull(label);return register(owner,id,()->label,factory);
    }
    public static ModUi.Registration register(String owner,String id,Supplier<String> label,Supplier<UiWindow> factory){
        if(id==null||!id.matches("[a-z][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("UI entry ID");
        Objects.requireNonNull(label);Objects.requireNonNull(factory);
        synchronized(entries){if(entries.size()>=256||entries.containsKey(owner+":"+id))throw new IllegalStateException("Duplicate or excessive UI entry");Entry entry=new Entry(owner,id,label,factory);entries.put(owner+":"+id,entry);return entry;}
    }
    private static UiRuntime runtime(AirshipGame game){
        if(currentGame!=game){if(runtime!=null)runtime.closeAll(UiWindow.CloseReason.GAME_EXIT);currentGame=game;cursor=null;pointerMasked=false;graveHeld=false;
            DeveloperTools.bind(game);
            inputDiagnostics=new UiInputDiagnostics(()->AGame.getGameDirectory().toPath());runtime=new UiRuntime((owner,error)->{
            DiagnosticHub.publish(owner,net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,"UI callback failed / 界面回调失败",error);
            System.err.println("[Acbric UI/"+owner+"] "+error);error.printStackTrace();game.showError("Acbric UI ["+owner+"]: "+error.getMessage());
        },UiBridge::writeClipboard);}
        return runtime;
    }
    private static boolean nativeDialog(AirshipGame game){return game.error!=null||game.helpText!=null||game.mpChatOverlayActive;}
    public static UiWindowHandle open(String owner,UiWindow window){
        AirshipGame game=currentGame;
        if(game==null||game.s==null||nativeDialog(game))throw new IllegalStateException("UI requires an active game screen without a native dialog");
        return runtime(game).open(owner,window,game.s);
    }
    public static Input filter(AirshipGame game,Input input){
        filteredWithWindow=runtime!=null&&currentGame==game&&runtime.isOpen();
        if(runtime==null||currentGame!=game)return input;
        runtime.observe(game.s,nativeDialog(game));
        cursor=input.cursor();Pt click=input.clicked();boolean ctrl=input.keyDown("LCONTROL")||input.keyDown("RCONTROL")||input.keyDown("LMETA")||input.keyDown("RMETA");
        Boolean active=displayActive();
        boolean gravePressed=input.keyPressed("GRAVE"),openConsole=gravePressed&&!graveHeld;
        // 物理键兼容 ` 和 Shift+~；按住/系统重复不能重新打开，失焦期间也更新状态。
        graveHeld=input.keyDown("GRAVE")||gravePressed;
        if(openConsole&&!ctrl&&!input.keyDown("LALT")&&!input.keyDown("RALT")&&!Boolean.FALSE.equals(active)
                &&game.s!=null&&!nativeDialog(game)&&!runtime.isOpen()){
            open("acbric_api",DeveloperConsoleUi.consoleWindow());
            // 开窗这一帧不把 ~、Enter 或鼠标事件送给新窗口或下层游戏；保留时钟和网络推进。
            pointerMasked=true;return new UiMaskedInput(input,true,true);
        }
        Input raw=unwrap(input);String typed="",paste=null;
        if(runtime.isOpen()){
            // 原生 typedText 支持多字符提交；测试输入或自定义 Input 不假定属于 Slick。
            if(raw instanceof com.zarkonnen.catengine.SlickEngine.MyInput)typed=AirshipGame.getTypedText(input);
            else if(input.lastInput()!=0)typed=String.valueOf(input.lastInput());
            if(ctrl&&input.keyPressed("V"))paste=AGame.getClipboardString();
        }
        // Slick 在切屏/窗口移动后，事件坐标可暂时偏离轮询坐标。原生点击仍决定是否触发，
        // 命中位置则采用与悬停相同、且已完成 ScaledInput 变换的本帧坐标；缺失时退回事件坐标。
        // 自定义 Input 保持其显式事件坐标语义，不解包后直接取坐标以免绕过游戏缩放。
        Pt point=click!=null&&(!(raw instanceof com.zarkonnen.catengine.SlickEngine.MyInput)||cursor==null)?click:cursor;
        var uiInput=new UiRuntime.Input(point==null?-1:point.x,point==null?-1:point.y,click!=null&&input.clickButton()==1,input.mouseDown()!=null,
                input.scrollAmount(),ctrl?"":typed,paste,input.keyPressed("TAB"),input.keyDown("LSHIFT")||input.keyDown("RSHIFT"),input.keyPressed("ENTER"),input.keyPressed("ESCAPE"),
                input.keyPressed("BACK"),input.keyPressed("DELETE"),input.keyPressed("LEFT"),input.keyPressed("RIGHT"),input.keyPressed("HOME"),input.keyPressed("END"),ctrl&&input.keyPressed("A"));
        boolean trace=inputDiagnostics.event(runtime.isOpen(),active,input.mouseDownButton(),click!=null,uiInput.enter()||uiInput.escape()||uiInput.tab());
        String before=trace?runtime.inputDiagnosticState():null;
        int held=(input.keyDown("BACK")?1:0)|(input.keyDown("DELETE")?2:0)|(input.keyDown("LEFT")?4:0)|(input.keyDown("RIGHT")?8:0)|(input.keyDown("HOME")?16:0)|(input.keyDown("END")?32:0);
        var capture=runtime.input(uiInput,new UiRuntime.Editing(ctrl&&input.keyPressed("C"),ctrl&&input.keyPressed("X"),held,!Boolean.FALSE.equals(active),input.mouseDownButton()==1,System.nanoTime(),input.keyPressed("UP"),input.keyPressed("DOWN")));
        if(trace)inputDiagnostics.write("clickButton="+input.clickButton()+" click="+uiInput.click()+" x="+uiInput.x()+" y="+uiInput.y()
                +" cursor="+coordinates(cursor)+" event="+coordinates(click)
                +" nav="+uiInput.enter()+"/"+uiInput.escape()+"/"+uiInput.tab()+" before={"+before+"} after={"+runtime.inputDiagnosticState()+"} capture="+capture);
        pointerMasked=capture.pointer();
        return capture.pointer()||capture.keyboard()?new UiMaskedInput(input,capture.pointer(),capture.keyboard()||capture.pointer()):input;
    }
    /** 剪贴板暂时忙碌时保留文本，尤其不能在剪切复制失败后删除选区。 */
    private static boolean writeClipboard(String value){
        try{java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(value),null);return true;}
        catch(RuntimeException ex){return false;}
    }
    public static void created(AirshipGame game){runtime(game);DeveloperTools.bind(game);}
    private static String coordinates(Pt point){return point==null?"null":point.x+","+point.y;}
    /** 无窗口测试或不可用的原生显示不应被诊断逻辑变成游戏故障；null 表示未知。 */
    private static Boolean displayActive(){
        if(java.awt.GraphicsEnvironment.isHeadless())return null;
        try{return org.lwjgl.opengl.Display.isCreated()?org.lwjgl.opengl.Display.isActive():null;}
        catch(LinkageError|RuntimeException ignored){return null;}
    }
    private static Pt renderCursor(){return cursor==null?new Pt(-1000000,-1000000):cursor;}
    /** 输入阶段的 null 表示遮蔽；原生绘制却要求非空坐标，必须在 Screen.render 之前恢复。
     * 只恢复绘制状态，不解开 Input 包装、点击或键盘屏障；关闭后的释放帧同样需要处理。 */
    public static void beforeRender(AirshipGame game,MyDraw.State state){
        if(currentGame==game&&runtime!=null&&(pointerMasked||runtime.isOpen())){
            game.cursor=renderCursor();state.tick(0,game.cursor);
        }
    }
    public static void render(AirshipGame game,Frame frame,MyDraw.State originalState){
        if(runtime==null||currentGame!=game)return;
        runtime.observe(game.s,nativeDialog(game));
        if(!runtime.isOpen())return;
        MyDraw.State state=new MyDraw.State();state.tick(0,renderCursor());
        MyDraw draw=new MyDraw(frame,new Hooks(),state,game.integration);
        runtime.render(new NativeUiCanvas(draw,frame.mode().width,frame.mode().height),frame.mode().width,frame.mode().height);
    }
    public static boolean openedDuringNativeInput(AirshipGame game){return currentGame==game&&runtime!=null&&runtime.isOpen()&&!filteredWithWindow;}
    public static void exit(AirshipGame game){if(currentGame==game&&runtime!=null)runtime.closeAll(UiWindow.CloseReason.GAME_EXIT);DeveloperTools.exit(game);}
    public static Input unwrap(Input input){for(int i=0;i<16;i++){if(input instanceof BlankInput blank)input=blank.originalIn;else if(input instanceof ScaledInput scaled)input=scaled.originalIn;else return input;}throw new IllegalArgumentException("Too many input wrappers");}
    public static UiMaskedInput mask(Input input){for(int i=0;i<16;i++){if(input instanceof UiMaskedInput mask)return mask;if(input instanceof BlankInput blank)input=blank.originalIn;else if(input instanceof ScaledInput scaled)input=scaled.originalIn;else return null;}return null;}

    /** 框架自身也通过公开组件 API 构建详情页，第三方注册入口只在此处被发现。 */
    public static void details(String modId){
        JavaModManager manager=JavaModManager.current();if(manager==null||manager.entry(modId)==null)return;
        var mod=manager.entry(modId);boolean zh=FabricModListBridge.chinese();List<UiNode> nodes=new ArrayList<>();
        nodes.add(Ui.label(mod.metadata().getName()+" · "+mod.metadata().getVersion().getFriendlyString()));
        nodes.add(Ui.label("ID: "+modId));nodes.add(Ui.label(()->manager.status(modId,zh)));
        nodes.add(Ui.label(mod.metadata().getDescription()));
        nodes.add(Ui.label(mod.archive()==null?manager.displayReason(modId):(zh?"来源：":"Source: ")+mod.archive()));
        if(mod.manageable()&&manager.reason(modId).isEmpty())nodes.add(Ui.button(()->manager.enabledNext(modId)?(zh?"下次启动停用":"Disable on restart"):(zh?"下次启动启用":"Enable on restart"),h->{
            try{manager.toggle(modId);}catch(java.io.IOException ex){h.message(zh?"无法更改":"Cannot change",ex.getMessage());}
        }));
        synchronized(entries){for(Entry entry:entries.values())if(entry.owner.equals(modId)&&entry.factory!=null){
            nodes.add(Ui.button(()->{Supplier<String> label;synchronized(entries){label=entry.label;}return label==null?net.fabricacs.api.util.AcbricLanguage.text("Entry unregistered","入口已注销"):label.get();},h->{Supplier<UiWindow> factory; synchronized(entries){factory=entry.factory;}if(factory==null){h.message("Acbric UI",zh?"入口已注销":"Entry unregistered");return;}
                UiWindow next=Objects.requireNonNull(factory.get(),"UI window");h.close();open(entry.owner,next);
            }));
        }}
        nodes.add(Ui.button(zh?"关闭":"Close",UiWindowHandle::close));
        new ModUi("acbric_api").open(new UiWindow(zh?"MOD 详情与工具":"MOD details and tools",560,520,true,Ui.column(10,nodes.toArray(UiNode[]::new))));
    }
}

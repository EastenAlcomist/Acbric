/* SettingsUiRegression.java — 通过真实窗口栈/命中路径检查设置动作，绘制终端仅录制。 */
package net.fabricacs.api.ui;

import com.zarkonnen.airships.Lang;
import net.fabricacs.api.config.*;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class SettingsUiRegression {
    private static int checks;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
    private static final class Canvas implements UiRuntime.Canvas {
        Map<String,UiRuntime.Rect> buttons=new LinkedHashMap<>();Map<String,Boolean> enabled=new HashMap<>();List<String> labels=new ArrayList<>();
        String value;UiRuntime.Rect field;int caret;
        public double scale(){return 1;}public int lineHeight(){return 16;}public int buttonHeight(){return 24;}public int textHeight(String s,int w){return 16;}
        public void window(UiRuntime.Rect r,String t,boolean m){}public void panel(UiRuntime.Rect r,UiRuntime.Rect c){}
        public void label(UiRuntime.Rect r,UiRuntime.Rect c,String s){labels.add(s);}
        public void button(UiRuntime.Rect r,UiRuntime.Rect c,String s,boolean e,boolean f){buttons.put(s,r.intersect(c));enabled.put(s,e);}
        public void toggle(UiRuntime.Rect r,UiRuntime.Rect c,String s,boolean v,boolean e,boolean f){button(r,c,s,e,f);}
        public void text(UiRuntime.Rect r,UiRuntime.Rect c,String s,int p,boolean a,boolean f,boolean e){field=r.intersect(c);value=s;caret=p;}
        public void tooltip(String s,int x,int y){}public void scrollbar(UiRuntime.Rect r,UiRuntime.Rect c,int o,int max){}
        void render(UiRuntime runtime){buttons.clear();labels.clear();enabled.clear();runtime.render(this,1000,900);}
    }
    private static UiRuntime.Input input(double x,double y,boolean click,String typed,boolean all){return new UiRuntime.Input(x,y,click,false,0,typed,null,false,false,false,false,false,false,false,false,false,false,all);}
    private static void tap(UiRuntime runtime,Canvas canvas,String label){runtime.input(input(-1,-1,false,"",false));canvas.render(runtime);var r=Objects.requireNonNull(canvas.buttons.get(label),label);runtime.input(input(r.x()+1,r.y()+1,true,"",false));canvas.render(runtime);}
    public static int run(Path root)throws Exception{
        checks=0;Locale old=Lang.currentLocale;Lang.currentLocale=Locale.ENGLISH;
        try{
            List<Throwable> errors=new ArrayList<>();UiRuntime runtime=new UiRuntime((o,e)->errors.add(e));Canvas canvas=new Canvas();Object screen=new Object();
            AtomicReference<String> value=new AtomicReference<>("1234");
            var bound=runtime.open("test_ui",new UiWindow("Bound",400,260,true,Ui.textField(value::get,32,value::set).width(250).enabled(()->true)),screen);
            canvas.render(runtime);runtime.input(input(canvas.field.x()+2,canvas.field.y()+2,true,"",false));
            value.set("😀");runtime.input(input(-1,-1,false,"A",false));canvas.render(runtime);
            check(value.get().equals("😀A")&&canvas.caret==3,"external update before input resets caret safely and copied node stays bound");
            value.set("x");canvas.render(runtime);check(canvas.value.equals("x")&&canvas.caret==1,"external reset reflected by render");bound.close();
            ModConfig config=new ModConfig(root,"settings_ui","settings",1,new JSONObject().put("enabled",true).put("count",2).put("mode","a"),j->{if(j.getInt("count")==7)throw new IllegalArgumentException("business rule");});
            List<ConfigField> fields=List.of(
                ConfigField.bool("enabled",new ConfigField.Text("Enabled","启用"),ConfigField.Effect.IMMEDIATE),
                ConfigField.integer("count",new ConfigField.Text("Count","数量"),1,10,ConfigField.Effect.NEW_CAMPAIGN),
                ConfigField.choice("mode",new ConfigField.Text("Mode","模式"),List.of(new ConfigField.Option("a",new ConfigField.Text("Mode A","模式甲")),new ConfigField.Option("b",new ConfigField.Text("Mode B","模式乙"))),ConfigField.Effect.RESTART));
            ConfigEditor editor=new ConfigEditor(config,fields);int[] callbacks={0};boolean[] failCallback={false};
            var window=runtime.open("test_ui",SettingsUi.window("Settings",editor,result->{callbacks[0]++;if(failCallback[0])throw new IllegalStateException("callback");}),screen);
            canvas.render(runtime);check(canvas.buttons.containsKey("Apply")&&canvas.labels.contains("Count · New campaigns only"),"English form/effect labels");
            runtime.input(input(canvas.field.x()+1,canvas.field.y()+1,true,"",false));runtime.input(input(-1,-1,false,"-",true));canvas.render(runtime);
            check(editor.value("count").equals("-")&&!canvas.enabled.get("Apply")&&canvas.labels.stream().anyMatch(s->s.contains("Enter an integer")),"typed invalid number remains visible with error and disabled Apply");
            tap(runtime,canvas,"Apply");check(callbacks[0]==0&&!Files.exists(config.path()),"invalid Apply never saves");
            tap(runtime,canvas,"Defaults");check(canvas.value.equals("2")&&canvas.enabled.get("Apply")&&!Files.exists(config.path()),"defaults reset displayed editor without saving");
            tap(runtime,canvas,"Mode A");check(canvas.buttons.containsKey("Mode B"),"choice opens modal list");
            tap(runtime,canvas,"Mode B");check(editor.value("mode").equals("b")&&canvas.buttons.containsKey("Mode B"),"option updates draft and closes child");
            tap(runtime,canvas,"Mode B");tap(runtime,canvas,"Cancel");check(editor.value("mode").equals("b")&&window.isOpen(),"choice cancel leaves parent and selected value");
            editor.set("count","7");tap(runtime,canvas,"Apply");check(window.isOpen()&&callbacks[0]==0&&editor.value("count").equals("7")&&!Files.exists(config.path()),"MOD validation failure keeps form and draft");
            tap(runtime,canvas,"OK");editor.set("count","3");tap(runtime,canvas,"Apply");check(callbacks[0]==1&&config.read().data().getInt("count")==3&&!editor.isDirty(),"successful Apply saves then notifies once");
            check(canvas.labels.stream().anyMatch(s->s.contains("After restart")&&s.contains("New campaigns only")),"saved status lists actual changed effects");
            byte[] saved=Files.readAllBytes(config.path());editor.set("count","4");tap(runtime,canvas,"Reload file");tap(runtime,canvas,"Cancel");check(editor.value("count").equals("4"),"cancel reload preserves draft");
            tap(runtime,canvas,"Reload file");tap(runtime,canvas,"Reload");check(editor.value("count").equals("3")&&canvas.value.equals("3"),"explicit reload updates model and bound field");
            editor.set("count","4");tap(runtime,canvas,"Cancel");check(window.isOpen()&&editor.isDirty(),"dirty Cancel requires confirmation");tap(runtime,canvas,"Keep editing");check(window.isOpen()&&editor.value("count").equals("4"),"keep editing retains draft");tap(runtime,canvas,"Cancel");tap(runtime,canvas,"Discard");check(!window.isOpen()&&editor.value("count").equals("3")&&Arrays.equals(saved,Files.readAllBytes(config.path())),"cancel closes and discards draft only");
            Lang.currentLocale=Locale.forLanguageTag("chi");window=runtime.open("test_ui",SettingsUi.window("设置",editor,r->callbacks[0]++),screen);canvas.render(runtime);
            check(canvas.buttons.containsKey("应用")&&canvas.buttons.containsKey("恢复默认值")&&canvas.labels.contains("模式 · 下次启动生效"),"Chinese form and effect labels");
            editor.set("count","5");tap(runtime,canvas,"X");check(window.isOpen()&&canvas.buttons.containsKey("继续编辑"),"Chinese unsaved confirmation");tap(runtime,canvas,"继续编辑");tap(runtime,canvas,"X");tap(runtime,canvas,"丢弃修改");check(!window.isOpen()&&!editor.isDirty()&&config.read().data().getInt("count")==3,"X close discards unsaved draft");
            Lang.currentLocale=Locale.ENGLISH;window=runtime.open("test_ui",SettingsUi.window("Settings",editor,r->{callbacks[0]++;throw new IllegalStateException("after-save");}),screen);
            editor.set("count","6");tap(runtime,canvas,"Apply");check(window.isOpen()&&config.read().data().getInt("count")==6&&canvas.labels.stream().anyMatch(s->s.contains("Saved, but")),"callback failure explicitly reports that disk save succeeded");
            tap(runtime,canvas,"OK");editor.set("count","8");JSONObject changed=new JSONObject(Files.readString(config.path()));changed.getJSONObject("data").put("count",9);Files.writeString(config.path(),changed.toString());tap(runtime,canvas,"Apply");
            check(window.isOpen()&&editor.value("count").equals("8")&&canvas.labels.stream().anyMatch(s->s.contains("Config changed")),"disk conflict visible and draft retained");
            window.close();check(errors.isEmpty(),"expected settings errors do not escape to fatal UI handler");
        }finally{Lang.currentLocale=old;}
        System.out.println("SETTINGS UI REGRESSION PASS: "+checks+" checks");return checks;
    }
}

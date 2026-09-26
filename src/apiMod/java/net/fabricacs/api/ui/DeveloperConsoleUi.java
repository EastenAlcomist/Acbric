/* DeveloperConsoleUi.java — 框架开发者窗口的内部适配；使用公开命令和诊断服务，不在绘制时执行命令。 */
package net.fabricacs.api.ui;
import net.fabricacs.api.command.*;
import net.fabricacs.api.diagnostics.*;
import net.fabricacs.api.impl.*;
import net.fabricacs.api.util.AcbricLanguage;
import java.util.*;

/** 内部界面适配器；不属于稳定的 MOD API。 */
public final class DeveloperConsoleUi {
    private DeveloperConsoleUi(){}
    private static String t(String en,String zh){return AcbricLanguage.text(en,zh);}
    public static UiWindow statusWindow(){
        DiagnosticSnapshot[] state={DeveloperDiagnostics.snapshot()};
        return new UiWindow(t("Developer tools","开发者工具"),800,660,true,Ui.column(10,
            Ui.label(()->DeveloperTools.describe(state[0],AcbricLanguage.isChinese())),
            Ui.label(t("MOD entrypoint status (not a gameplay compatibility guarantee)","MOD 入口状态（不代表玩法兼容性保证）")),
            Ui.scroll(270,Ui.label(()->DeveloperTools.modList(state[0],AcbricLanguage.isChinese())))))
            .withFooter(Ui.row(8,Ui.button(()->t("Refresh","刷新"),h->state[0]=DeveloperDiagnostics.snapshot()),
                Ui.button(()->t("Console","控制台"),h->{h.close();new ModUi("acbric_api").open(consoleWindow());}),
                Ui.button(()->t("Export","导出"),h->{var r=Commands.execute("acbric_api:diagnostics export");h.message(t("Diagnostics","诊断"),r.message().resolve(AcbricLanguage.isChinese()));}),
                Ui.button(()->t("Close","关闭"),UiWindowHandle::close)));
    }
    public static UiWindow consoleWindow(){return new Console().window();}
    private static final class Console {
        String input="",owner="",level="ALL",draft="";int historyIndex,page;
        final List<String> history=new ArrayList<>();List<DiagnosticMessage> visible=List.of();
        void navigate(int direction){if(historyIndex==history.size())draft=input;historyIndex=Math.clamp(historyIndex+direction,0,history.size());input=historyIndex==history.size()?draft:history.get(historyIndex);}
        void execute(){
            if(input.isBlank())return;String line=input;input="";draft="";
            if(history.isEmpty()||!history.getLast().equals(line))history.add(line);if(history.size()>32)history.removeFirst();historyIndex=history.size();
            String id=line.strip().split("[ :]")[0];if(id.isBlank())id="acbric_api";
            DiagnosticHub.publish(id,DiagnosticMessage.Level.INFO,"> "+line,null);
            CommandResult result=Commands.execute(line);
            DiagnosticHub.publish(id,result.status()==CommandResult.Status.SUCCESS?DiagnosticMessage.Level.INFO:DiagnosticMessage.Level.WARN,result.message().resolve(AcbricLanguage.isChinese()),null);page=0;
        }
        void complete(UiWindowHandle parent){
            List<String> values=Commands.complete(input);if(values.size()==1){input=values.getFirst();return;}
            if(values.isEmpty()){parent.message(t("Completion","补全"),t("No static suggestions.","没有静态补全候选。"));return;}
            List<UiNode> rows=new ArrayList<>();for(String value:values)rows.add(Ui.button(value,h->{input=value;h.close();}));
            parent.dialog(new UiWindow(t("Completion","补全"),700,440,true,Ui.column(6,rows.toArray(UiNode[]::new))).withFooter(Ui.button(()->t("Cancel","取消"),UiWindowHandle::close)));
        }
        List<DiagnosticMessage> records(){return DeveloperDiagnostics.messages(owner,level.equals("ALL")?null:DiagnosticMessage.Level.valueOf(level));}
        String output(){
            var all=records();page=Math.min(page,Math.max(0,(all.size()-1)/6));int end=Math.max(0,all.size()-page*6),start=Math.max(0,end-6);visible=List.copyOf(all.subList(start,end));
            return visible.isEmpty()?t("No messages match the filters.","没有符合筛选条件的消息。"):
                String.join("\n\n",visible.stream().map(m->"#"+m.sequence()+" ["+m.level()+"/"+m.modId()+"] "+DiagnosticHub.clip(m.message(),1000)).toList());
        }
        void copy(UiWindowHandle h,String value){
            try{java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(value),null);}
            catch(RuntimeException ex){h.message(t("Clipboard","剪贴板"),t("Clipboard unavailable. Export diagnostics instead.","剪贴板暂不可用，可以导出诊断。"));}
        }
        void details(UiWindowHandle h){
            // 点击时重新截取本页，后续日志不会把用户正在查看的记录替换掉。
            output();List<UiNode> rows=new ArrayList<>();for(var m:visible){String value=m.message()+"\n"+m.detail();rows.add(Ui.button("#"+m.sequence()+" "+m.modId(),p->p.dialog(new UiWindow(t("Message details","消息详情"),760,550,true,Ui.label(value)).withFooter(Ui.row(8,Ui.button(()->t("Copy","复制"),c->copy(c,value)),Ui.button(()->t("Close","关闭"),UiWindowHandle::close))))));}
            h.dialog(new UiWindow(t("Page messages","本页消息"),600,400,true,Ui.column(6,rows.toArray(UiNode[]::new))).withFooter(Ui.button(()->t("Close","关闭"),UiWindowHandle::close)));
        }
        UiWindow window(){
            UiNode field=Ui.textField(()->input,2048,v->input=v).onSubmit(h->execute());field.history=this::navigate;field.completion=this::complete;field.initialFocus=true;
            List<Ui.Choice> levels=List.of(new Ui.Choice("ALL",()->t("All levels","全部级别")),new Ui.Choice("INFO",()->t("Info","信息")),new Ui.Choice("WARN",()->t("Warning","警告")),new Ui.Choice("ERROR",()->t("Error","错误")));
            return new UiWindow(t("Acbric Console","Acbric 控制台"),920,720,true,Ui.column(8,
                Ui.label(t("Owner filter (empty = all) / level","所属 MOD 筛选（空为全部）/ 级别")),
                Ui.row(8,Ui.textField(()->owner,64,v->{owner=v;page=0;}),Ui.choice(()->level,levels,v->{level=v;page=0;})),
                Ui.scroll(300,Ui.label(this::output)),
                Ui.row(8,Ui.button(()->t("Older","较早"),h->page++),Ui.button(()->t("Newer","较新"),h->page=Math.max(0,page-1)),Ui.button(()->t("Latest","最新"),h->page=0),Ui.button(()->t("Details","详情"),this::details),Ui.button(()->t("Copy page","复制本页"),h->{output();copy(h,String.join("\n\n",visible.stream().map(m->"#"+m.sequence()+" ["+m.level()+"/"+m.modId()+"] "+m.message()+"\n"+m.detail()).toList()));})),
                Ui.label(t("Enter: run · Up/Down: history · Tab: complete · Shift+Tab: focus","Enter 执行 · 上下箭头历史 · Tab 补全 · Shift+Tab 切换焦点")),field))
                .withFooter(Ui.column(6,Ui.row(8,Ui.button(()->t("Run","执行"),h->execute()),Ui.button(()->t("Previous","上一条"),h->navigate(-1)),Ui.button(()->t("Next","下一条"),h->navigate(1)),Ui.button(()->t("Complete","补全"),this::complete)),
                    Ui.row(8,Ui.button(()->t("Help","帮助"),h->{input="acbric_api:help";execute();}),Ui.button(()->t("Export","导出"),h->{input="acbric_api:diagnostics export";execute();}),Ui.button(()->t("Close","关闭"),UiWindowHandle::close))));
        }
    }
}

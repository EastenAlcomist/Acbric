/* SettingsUi.java — 双语设置页和草稿操作；保存失败留在窗口，生效回调失败与落盘失败分别报告。 */
package net.fabricacs.api.ui;

import net.fabricacs.api.config.*;
import net.fabricacs.api.util.AcbricLanguage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

public final class SettingsUi {
    private SettingsUi(){}
    private static String t(String en,String zh){return AcbricLanguage.text(en,zh);}
    private static String label(ConfigField.Text text){return text.resolve(AcbricLanguage.isChinese());}
    private static String effect(ConfigField.Effect effect){return switch(effect){
        case IMMEDIATE->t("Apply now","立即生效");case RESTART->t("After restart","下次启动生效");case NEW_CAMPAIGN->t("New campaigns only","仅新战役生效");};}
    /** 注册只保存工厂；点击入口才 load。加载/迁移错误显示为可关闭窗口，不覆盖原配置。 */
    public static ModUi.Registration register(ModUi ui,String id,Supplier<String> title,ModConfig config,List<ConfigField> fields,Consumer<ConfigEditor.Applied> applied){
        Objects.requireNonNull(ui);Objects.requireNonNull(title);Objects.requireNonNull(config);Objects.requireNonNull(applied);List<ConfigField> copy=List.copyOf(fields);
        return ui.register(id,title,()->{
            try{return window(title.get(),new ConfigEditor(config,copy),applied);}
            catch(IOException|RuntimeException ex){
                System.err.println("[Acbric settings/"+ui.modId()+"] "+ex);
                return new UiWindow(title.get(),520,300,true,Ui.column(10,Ui.label(t("Cannot open settings. Check the config file, field declarations and explicit migration.","无法打开设置，请检查配置文件、字段声明及显式迁移。")),Ui.button(()->t("Close","关闭"),UiWindowHandle::close)));
            }
        });
    }
    public static UiWindow window(String title,ConfigEditor editor,Consumer<ConfigEditor.Applied> applied){
        Objects.requireNonNull(editor);Objects.requireNonNull(applied);List<UiNode> nodes=new ArrayList<>();
        class Status {Supplier<String> message=()->t("Changes stay in this draft until Apply. Closing discards unapplied edits.","修改暂存于草稿，点击应用才保存。关闭窗口会丢弃未应用的修改。");}
        Status status=new Status();nodes.add(Ui.label(()->status.message.get()));
        for(ConfigField f:editor.fields()){
            nodes.add(Ui.label(()->label(f.label())+" · "+effect(f.effect())));
            if(!f.description().english().isEmpty()||!f.description().chinese().isEmpty())nodes.add(Ui.label(()->label(f.description())));
            Supplier<String> text=()->(String)editor.value(f.key());Consumer<String> change=v->editor.set(f.key(),v);
            switch(f.kind()){
                case BOOLEAN->nodes.add(Ui.toggle(label(f.label()),()->(Boolean)editor.value(f.key()),v->editor.set(f.key(),v)));
                case INTEGER->nodes.add(Ui.integerField(text,(int)f.minimum(),(int)f.maximum(),change));
                case DECIMAL->nodes.add(Ui.numberField(text,f.minimum(),f.maximum(),change));
                case TEXT->nodes.add(Ui.textField(text,4096,change));
                case CHOICE->nodes.add(Ui.choice(text,f.options().stream().map(o->new Ui.Choice(o.value(),()->label(o.label()))).toList(),change));
            }
            if(f.kind()!=ConfigField.Kind.INTEGER&&f.kind()!=ConfigField.Kind.DECIMAL)nodes.add(Ui.label(()->editor.error(f.key(),AcbricLanguage.isChinese())));
        }
        nodes.add(Ui.label(()->editor.isDirty()?t("Unapplied changes","有未应用的修改"):t("No unapplied changes","无未应用的修改")));
        nodes.add(Ui.row(8,
            Ui.button(()->t("Apply","应用"),h->{
                ConfigEditor.Applied result;
                try{result=editor.apply();}
                catch(IOException|RuntimeException ex){
                    System.err.println("[Acbric settings] "+ex);
                    boolean conflict=ex instanceof ConfigException c&&c.code()==ConfigException.Code.CONFLICT;
                    status.message=()->conflict?t("Config changed or is locked. Draft retained; reopen for current in-memory values, or explicitly Reload file to discard edits and reread disk.","配置已变化或被锁定，草稿已保留。重开可读取当前内存值；重新读取文件会丢弃草稿并读取磁盘。")
                            :t("Could not save. Draft retained; check validation and file access.","保存失败，草稿已保留，请检查配置校验和文件权限。");
                    return;
                }
                status.message=()->t("Saved. Effects: ","已保存。生效方式：")+(result.effects().isEmpty()?t("No value changes","值未变化"):result.effects().stream().sorted().map(SettingsUi::effect).reduce((a,b)->a+" / "+b).orElse(""));
                try{applied.accept(result);}catch(RuntimeException ex){System.err.println("[Acbric settings] Saved; callback failed: "+ex);status.message=()->t("Saved, but the MOD's apply callback failed. Do not assume runtime values changed; check the log.","配置已保存，但 MOD 生效回调失败。请勿假定运行中的值已更新，详情见日志。");}
            }).enabled(editor::isValid),
            Ui.button(()->t("Cancel","取消"),h->{editor.cancel();h.close();})));
        nodes.add(Ui.row(8,
            Ui.button(()->t("Defaults","恢复默认值"),h->{editor.restoreDefaults();status.message=()->t("Defaults are in the draft. Apply to save.","默认值已放入草稿，点击应用才会保存。");}),
            Ui.button(()->t("Reload file","重新读取文件"),h->h.confirm(t("Reload settings","重新读取设置"),t("Discard this draft and pending in-memory config changes, then read the file?","丢弃草稿及配置句柄内未保存的修改，然后重新读取文件？"),t("Reload","重新读取"),t("Cancel","取消"),()->{
                try{editor.reload();status.message=()->t("Reloaded from file.","已从文件重新读取。");}
                catch(IOException|RuntimeException ex){System.err.println("[Acbric settings] "+ex);status.message=()->t("Reload failed. Draft retained; check the config file and migration.","重新读取失败，草稿已保留，请检查文件及迁移。");}
            }))));
        return new UiWindow(title,620,620,true,Ui.column(8,nodes.toArray(UiNode[]::new)),reason->editor.cancel());
    }
}

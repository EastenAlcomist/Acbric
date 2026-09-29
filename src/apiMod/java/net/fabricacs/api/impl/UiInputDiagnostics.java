/* UiInputDiagnostics.java — UI 输入事件的有界本地排障记录；不记录文本，不改变输入行为。 */
package net.fabricacs.api.impl;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Supplier;

final class UiInputDiagnostics {
    private static final int LIMIT=65536;
    private final Supplier<Path> directory;
    private boolean open,initialized,failed;
    private Boolean active;
    private int down;
    UiInputDiagnostics(Supplier<Path> directory){this.directory=directory;}
    // 仅窗口切换、焦点变化、鼠标按下/释放、点击及导航键写盘；悬停和空闲帧不刷日志。
    boolean event(boolean nextOpen,Boolean nextActive,int nextDown,boolean click,boolean navigation){
        boolean relevant=open||nextOpen;
        boolean changed=!initialized||open!=nextOpen||!java.util.Objects.equals(active,nextActive)||down!=nextDown||click||navigation;
        initialized=true;open=nextOpen;active=nextActive;down=nextDown;
        return !failed&&relevant&&changed;
    }
    void write(String state){
        if(failed)return;
        try{
            Path dir=directory.get();Files.createDirectories(dir);
            Path file=dir.resolve("acbric-ui-input.log"),old=dir.resolve("acbric-ui-input.previous.log");
            String line=Instant.now()+" active="+active+" downButton="+down+" "+state+System.lineSeparator();
            byte[] bytes=line.getBytes(StandardCharsets.UTF_8);
            if(Files.exists(file)&&Files.size(file)+bytes.length>LIMIT)Files.move(file,old,StandardCopyOption.REPLACE_EXISTING);
            Files.write(file,bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Exception ex){
            failed=true;System.err.println("[Acbric UI] Input diagnostics unavailable / 输入诊断无法写入: "+ex);
        }
    }
}

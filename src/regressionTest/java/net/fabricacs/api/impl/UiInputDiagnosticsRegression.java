/* UiInputDiagnosticsRegression.java — 输入日志事件过滤、轮转及写入失败隔离检查。 */
package net.fabricacs.api.impl;

import java.nio.file.*;

public final class UiInputDiagnosticsRegression {
    private static int checks;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    public static int run(Path root)throws Exception{
        checks=0;UiInputDiagnostics log=new UiInputDiagnostics(()->root);
        check(!log.event(false,true,0,false,false),"no UI means no log");
        check(log.event(true,true,0,false,false),"opening logged");log.write("depth=1 decision=idle");
        check(!log.event(true,true,0,false,false),"idle/hover does not write");
        check(log.event(true,false,0,false,false),"focus loss logged");
        check(log.event(true,true,0,false,false),"focus return logged");
        check(log.event(true,true,1,false,false),"press without native click logged");
        check(log.event(true,true,0,false,false),"release without native click logged");
        check(log.event(true,true,0,true,false),"native click logged");
        check(log.event(true,true,0,false,true),"navigation logged");
        check(log.event(false,true,0,false,false),"closing logged");
        check(!log.event(false,false,0,true,false),"outside UI events omitted");
        for(int i=0;i<1000;i++)log.write("click=false before={depth=1,barrier=false,ready=true} after={decision=idle} sequence="+i);
        Path current=root.resolve("acbric-ui-input.log"),old=root.resolve("acbric-ui-input.previous.log");
        check(Files.size(current)<=65536&&Files.size(old)<=65536,"both retained files bounded");
        check(Files.readString(current).contains("sequence=999"),"latest evidence retained");
        UiInputDiagnostics broken=new UiInputDiagnostics(()->current);broken.write("unwritable");
        check(!broken.event(true,true,1,true,false),"failed logger disables itself without stopping input");
        return checks;
    }
}

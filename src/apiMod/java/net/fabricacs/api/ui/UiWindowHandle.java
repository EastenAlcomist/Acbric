/* UiWindowHandle.java — 窗口句柄；关闭时释放托管资源，关闭动作幂等。 */
package net.fabricacs.api.ui;

import java.util.*;

public final class UiWindowHandle implements AutoCloseable {
    final UiRuntime runtime;
    final String owner;
    UiWindow window;
    final List<AutoCloseable> resources=new ArrayList<>();
    boolean closed;
    UiWindowHandle(UiRuntime runtime,String owner,UiWindow window){this.runtime=runtime;this.owner=owner;this.window=window;}
    public String modId(){return owner;}
    public boolean isOpen(){return !closed;}
    public <T extends AutoCloseable> T manage(T resource){runtime.checkThread();Objects.requireNonNull(resource);if(closed)throw new IllegalStateException("Window closed");resources.add(resource);return resource;}
    public UiWindowHandle dialog(UiWindow dialog){runtime.checkThread();if(closed)throw new IllegalStateException("Window closed");if(!dialog.modal())throw new IllegalArgumentException("Child dialogs must be modal");return runtime.push(this,dialog);}
    public UiWindowHandle message(String title,String text){return dialog(new UiWindow(title,440,360,true,Ui.column(10,Ui.label(text),Ui.button(()->net.fabricacs.api.util.AcbricLanguage.text("OK","确定"),UiWindowHandle::close))));}
    public UiWindowHandle confirm(String title,String text,String yes,String no,Runnable accepted){
        Objects.requireNonNull(accepted);
        return dialog(new UiWindow(title,440,360,true,Ui.column(10,Ui.label(text),Ui.row(8,
                Ui.button(yes,h->{h.close();accepted.run();}),Ui.button(no,UiWindowHandle::close)))));
    }
    @Override public void close(){runtime.close(this,UiWindow.CloseReason.CLOSED);}
}

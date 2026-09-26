/* DeveloperDiagnostics.java — 框架状态、消息和本地异步导出的公共入口；查询与 UI 共用数据源。 */
package net.fabricacs.api.diagnostics;
import net.fabricacs.api.impl.DeveloperTools;
import net.fabricacs.api.impl.DiagnosticHub;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
public final class DeveloperDiagnostics {
    private DeveloperDiagnostics(){}
    /** 游戏线程读取 MOD 管理状态，不触发目录重扫。 */
    public static DiagnosticSnapshot snapshot(){return DeveloperTools.snapshot();}
    /** 可从任意线程读取；null 级别、空 MOD ID 表示不筛选。返回顺序从旧到新。 */
    public static List<DiagnosticMessage> messages(String modId,DiagnosticMessage.Level level){return DiagnosticHub.messages(modId,level);}
    /** 游戏线程截取快照，后台仅处理快照与当前会话报告，结果写固定本地导出目录。 */
    public static CompletableFuture<Path> export(){return DeveloperTools.export();}
}

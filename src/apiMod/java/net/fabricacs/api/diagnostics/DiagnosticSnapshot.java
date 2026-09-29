/* DiagnosticSnapshot.java — 当前诊断状态的不可变视图；加载、入口成功和下次启用分别记录。 */
package net.fabricacs.api.diagnostics;
import java.util.List;
public record DiagnosticSnapshot(String apiVersion,String gameVersion,String gameFingerprint,String sessionId,String phase,List<ModStatus> mods,long droppedMessages) {
    public DiagnosticSnapshot {mods=List.copyOf(mods);}
    public record ModStatus(String id,String name,String version,boolean loaded,Boolean enabledNext,String initialization,String source,String managementNote,String failure){}
}

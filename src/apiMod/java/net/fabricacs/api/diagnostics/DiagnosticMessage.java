/* DiagnosticMessage.java — 不持有 Throwable 或游戏对象的有界诊断消息快照。 */
package net.fabricacs.api.diagnostics;
import java.time.Instant;
public record DiagnosticMessage(long sequence,Instant time,String modId,Level level,String message,String detail) {
    public enum Level { INFO, WARN, ERROR }
}

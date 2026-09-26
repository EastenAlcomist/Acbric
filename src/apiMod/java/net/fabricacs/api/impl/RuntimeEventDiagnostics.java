/*
 * RuntimeEventDiagnostics.java — 受管理事件的有界运行期错误记录，按启动会话落盘。
 * 不保存监听器、参数或 Throwable 引用，不改变错误传播；诊断自身失败仅尽力报告。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public final class RuntimeEventDiagnostics {
    private static volatile Reporter reporter = new Reporter(null);
    private RuntimeEventDiagnostics() {}
    static void initialize(Path sessionDirectory) { reporter = new Reporter(sessionDirectory.resolve("runtime-events.jsonl")); }

    /** 每个订阅只记录第 1/2/4/8/16 次失败，其余异常仍照常传播。 */
    public static void report(String modId, String scope, String event, String callback, String listener, long occurrence, Throwable failure) {
        if (occurrence < 1 || occurrence > 16 || (occurrence & (occurrence - 1)) != 0) return;
        try { reporter.write(modId, scope, event, callback, listener, occurrence, failure); }
        catch (Throwable ignored) { /* 日志、路径甚至异常自身的格式化失败都不能替换 MOD 原异常。 */ }
    }
    static final class Reporter {
        final Path path;
        int records;
        long bytes;
        boolean diskFailed;
        Reporter(Path path) { this.path = path; }
        synchronized void write(String modId, String scope, String event, String callback, String listener, long occurrence, Throwable failure) throws Exception {
            if (records >= 64 || bytes >= 1024 * 1024) return;
            LimitedWriter trace = new LimitedWriter();
            failure.printStackTrace(new PrintWriter(trace));
            JSONObject row = new JSONObject().put("schema", 1).put("time", Instant.now().toString())
                    .put("modId", clip(modId, 256)).put("scope", clip(scope, 256)).put("event", clip(event, 256))
                    .put("callback", clip(callback, 256)).put("listener", clip(listener, 512))
                    .put("thread", clip(Thread.currentThread().getName(), 256)).put("occurrence", occurrence)
                    .put("type", clip(failure.getClass().getName(), 512)).put("message", clip(failure.getMessage(), 1024))
                    .put("stackTrace", trace.text.toString()).put("stackTruncated", trace.truncated);
            byte[] encoded = (row + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes + encoded.length > 1024 * 1024) return;
            records++; bytes += encoded.length;
            DiagnosticHub.publish(modId,net.fabricacs.api.diagnostics.DiagnosticMessage.Level.ERROR,"Event / 事件: "+event+" ["+scope+"] #"+occurrence,failure);
            // 单行 JSON 也输出至控制台，异常文本中的换行会转义，文件不可写时仍有线索。
            try { System.err.println("[Acbric runtime event] " + row); } catch (Throwable ignored) {}
            if (path != null && !diskFailed) {
                try {
                    Files.createDirectories(path.getParent());
                    Files.write(path, encoded, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception failureWriting) {
                    diskFailed = true;
                    try { System.err.println("[Acbric runtime event] Cannot write report: " + path + "; " + failureWriting.getClass().getName()); }
                    catch (Throwable ignored) {}
                }
            }
        }
    }
    private static String clip(String text, int length) { return text == null ? "" : text.substring(0, Math.min(length, text.length())); }
    private static final class LimitedWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        boolean truncated;
        @Override public void write(char[] buffer, int offset, int length) {
            int kept = Math.min(length, 4096 - text.length());
            text.append(buffer, offset, kept); truncated |= kept < length;
        }
        @Override public void flush() {}
        @Override public void close() {}
    }
}

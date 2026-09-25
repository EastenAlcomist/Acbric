/* RuntimeEventDiagnosticsRegression.java — 验证诊断归属、有界输出、不可写路径及日志失败不掩盖原异常。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.event.*;
import org.json.JSONObject;
import java.nio.file.*;
import java.io.*;

public final class RuntimeEventDiagnosticsRegression {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    public interface Callback { void call(); }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root); var previous = System.err;
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(console, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setErr(capture); RuntimeEventDiagnostics.initialize(root.resolve("session"));
            Event<Callback> event = new Event<>("test.RUNTIME", ls -> () -> { for(var l:ls) l.call(); });
            EventScope scope = new EventScope("test_mod", "campaign"); RuntimeException failure = new RuntimeException("line1\nline2");
            scope.register(event, () -> { throw failure; });
            for(int i=0;i<20;i++) { try { event.invoker().call(); throw new AssertionError(); } catch (RuntimeException expected) { check(expected == failure, "diagnostic preserves exception identity"); } }
            var rows = Files.readAllLines(root.resolve("session/runtime-events.jsonl"));
            check(rows.size() == 5, "repeat failures limited to 1/2/4/8/16");
            JSONObject first = new JSONObject(rows.getFirst()), last = new JSONObject(rows.getLast());
            check(first.getString("modId").equals("test_mod") && first.getString("event").equals("test.RUNTIME") && first.getString("scope").equals("campaign")
                    && first.getString("callback").equals("call") && first.getString("thread").equals(Thread.currentThread().getName()), "report records mod event scope method thread");
            check(first.getString("message").equals("line1\nline2") && first.getString("stackTrace").contains("RuntimeEventDiagnosticsRegression") && last.getInt("occurrence") == 16, "JSON escapes text and retains stack/occurrence");
            scope.close();
            var reporter = new RuntimeEventDiagnostics.Reporter(root.resolve("bounded/errors.jsonl"));
            RuntimeException huge = new RuntimeException("x".repeat(20000));
            for(int i=0;i<100;i++) reporter.write("m","s","e","c","l",1,huge);
            var bounded = Files.readAllLines(root.resolve("bounded/errors.jsonl"));
            check(bounded.size() == 64 && Files.size(root.resolve("bounded/errors.jsonl")) <= 1024*1024, "global report count and byte limits");
            JSONObject truncated = new JSONObject(bounded.getFirst());
            check(truncated.getString("message").length()==1024 && truncated.getString("stackTrace").length()==4096 && truncated.getBoolean("stackTruncated"), "long messages and stacks bounded");
            Path blocked = root.resolve("blocked"); Files.writeString(blocked, "file instead of directory"); RuntimeEventDiagnostics.initialize(blocked);
            RuntimeEventDiagnostics.report("m","s","e","c","l",1,failure);
            check(Files.readString(blocked).equals("file instead of directory") && console.toString(java.nio.charset.StandardCharsets.UTF_8).contains("Cannot write report"), "unwritable report path does not escape or replace file");
            RuntimeEventDiagnostics.initialize(root.resolve("format-error"));
            RuntimeException malicious = new RuntimeException() { @Override public void printStackTrace(PrintWriter writer) { throw new AssertionError("format failed"); } };
            EventScope hostile = new EventScope("test_mod", "format-error"); hostile.register(event, () -> { throw malicious; });
            try { event.invoker().call(); throw new AssertionError(); } catch (RuntimeException caught) { check(caught == malicious, "formatting failure cannot mask original throwable"); }
            hostile.close();
        } finally { System.setErr(previous); RuntimeEventDiagnostics.initialize(root.resolve("after-tests")); }
        System.out.println("RUNTIME EVENT DIAGNOSTICS PASS: " + checks + " checks"); return checks;
    }
}

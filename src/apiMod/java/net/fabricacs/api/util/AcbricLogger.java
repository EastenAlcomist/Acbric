package net.fabricacs.api.util;

import java.io.PrintStream;
import java.util.Objects;

public final class AcbricLogger {
    private final String modId;

    public AcbricLogger(String modId) {
        this.modId = Objects.requireNonNull(modId, "modId");
    }

    public void info(String message) {
        log(System.out, "INFO", message, null);
    }

    public void warn(String message) {
        log(System.err, "WARN", message, null);
    }

    public void error(String message) {
        log(System.err, "ERROR", message, null);
    }

    public void error(String message, Throwable throwable) {
        log(System.err, "ERROR", message, throwable);
    }

    private void log(PrintStream stream, String level, String message, Throwable throwable) {
        stream.println("[Acbric/" + modId + "/" + level + "] " + message);
        if (throwable != null) {
            throwable.printStackTrace(stream);
        }
    }
}

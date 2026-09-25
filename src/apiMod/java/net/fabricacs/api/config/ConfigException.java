/* ConfigException.java — 可辨别的配置失败原因；I/O 失败仍可按 IOException 统一处理。 */
package net.fabricacs.api.config;

import java.io.IOException;

public final class ConfigException extends IOException {
    public enum Code { INVALID_FORMAT, VALIDATION_FAILED, NEWER_VERSION, MIGRATION_FAILED, CONFLICT }
    private final Code code;
    public ConfigException(Code code, String message) { super(message); this.code = code; }
    public ConfigException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code code() { return code; }
}

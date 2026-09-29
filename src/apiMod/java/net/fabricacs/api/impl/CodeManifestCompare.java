/*
 * CodeManifestCompare.java — 离线比较两个导出的代码清单；命令行工具，不启动游戏或连接房间。
 * 退出码：0 代码匹配，1 不同，2 无法验证，3 输入无效。结果不代表整体联机兼容。
 */
package net.fabricacs.api.impl;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.io.IOException;
import org.json.JSONObject;

public final class CodeManifestCompare {
    private CodeManifestCompare() {}
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: CodeManifestCompare <left.json> <right.json>");
            System.exit(3);
        }
        try {
            var result = CodeManifest.compare(read(Path.of(args[0])), read(Path.of(args[1])));
            System.out.println(result.json().toString(2));
            System.exit(switch (result.status()) { case "CODE_MATCH" -> 0; case "DIFFERENT" -> 1; default -> 2; });
        } catch (IOException | RuntimeException failure) {
            System.out.println(new JSONObject().put("status", "INVALID_INPUT").put("error", "Unreadable, unsupported or malformed code manifest").toString(2));
            System.exit(3);
        }
    }
    static CodeManifest read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(CodeManifest.MAX_JSON_BYTES + 1); }
        if (bytes.length > CodeManifest.MAX_JSON_BYTES) throw new IOException("Manifest exceeds size limit");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return CodeManifest.parse(text);
    }
}

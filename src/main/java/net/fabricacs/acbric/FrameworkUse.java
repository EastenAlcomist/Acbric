/* FrameworkUse.java — 用共享文件句柄保护正在使用的框架；更新器以独占句柄互斥，嵌套启动复用引用。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

final class FrameworkUse implements AutoCloseable {
    private static final Map<Path, State> OPEN = new HashMap<>();
    private static final class State {
        final FileChannel channel;
        int count = 1;
        State(FileChannel channel) { this.channel = channel; }
    }
    private final Path root;
    private boolean closed;
    private FrameworkUse(Path root) { this.root = root; }

    static synchronized FrameworkUse open(Path framework) throws IOException {
        Path root = framework.toAbsolutePath().normalize();
        if (Files.exists(root.resolve(".acbric-maintenance/pending.json")))
            throw new IOException("UPDATE_RECOVERY_REQUIRED / Run Update Acbric.cmd to recover / 更新未完成，请运行 Update Acbric.cmd 恢复");
        State existing = OPEN.get(root);
        if (existing != null) { existing.count++; return new FrameworkUse(root); }
        Path path = root.resolve(".acbric-framework.lock"); ModSelection.rejectLinks(path);
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            OPEN.put(root, new State(channel));
            // Windows 上更新器用 FileShare.None 打开同一文件；此句柄关闭前不可更新。
            return new FrameworkUse(root);
        } catch (IOException ex) {
            throw new IOException("FRAMEWORK_BUSY / Framework is being updated / 框架正在更新，请稍后重试", ex);
        }
    }

    @Override public void close() throws IOException {
        synchronized (FrameworkUse.class) {
            if (closed) return;
            closed = true;
            State state = OPEN.get(root);
            if (--state.count == 0) { OPEN.remove(root); state.channel.close(); }
        }
    }
}

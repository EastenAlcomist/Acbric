/* SetupDirectoryPublisher.java — 安装配置目录的原子发布；有限重试短暂占用，持续失败保留诊断。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.file.*;

final class SetupDirectoryPublisher {
    private static final int ATTEMPTS = 6;
    @FunctionalInterface interface Pause { void sleep(long millis) throws InterruptedException; }
    private SetupDirectoryPublisher() {}

    static void publish(Path source, Path target) throws IOException {
        publish(source, target, Thread::sleep);
    }

    /** 测试可控制等待，但始终使用真实文件系统改名；不退回逐文件复制。 */
    static void publish(Path source, Path target, Pause pause) throws IOException {
        AccessDeniedException first = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("SETUP_INTERRUPTED / Setup interrupted / 配置已中断");
            ModSelection.rejectLinks(source); ModSelection.rejectLinks(target);
            // 重试期间也不得接管其他程序刚创建的目标目录。
            if (!Files.notExists(target))
                throw new IOException("SETUP_TARGET_CONFLICT / Target exists or cannot be inspected; preserved / 目标已存在或无法检查，未覆盖: " + target);
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AccessDeniedException ex) {
                if (first == null) first = ex;
                if (attempt == ATTEMPTS) {
                    IOException failure = new IOException("SETUP_PUBLISH_DENIED / Cannot rename setup directory after " + attempt
                            + " attempts; check file occupancy and directory permissions / 配置目录改名被拒绝，已重试，请检查文件占用和目录权限。未生成正式入口。"
                            + "\nsource=" + source + "\ntarget=" + target
                            + "\nsourceExists=" + Files.exists(source) + "; targetExists=" + Files.exists(target)
                            + "; java=" + System.getProperty("java.version") + "; os=" + System.getProperty("os.name"), ex);
                    if (first != ex) failure.addSuppressed(first);
                    throw failure;
                }
                try { pause.sleep(100L << (attempt - 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException failure = new InterruptedIOException("SETUP_INTERRUPTED / Setup interrupted / 配置已中断");
                    failure.initCause(interrupted); failure.addSuppressed(ex); throw failure;
                }
            }
        }
    }
}

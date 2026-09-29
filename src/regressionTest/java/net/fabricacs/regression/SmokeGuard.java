/* SmokeGuard.java — 仅 JDK 21 测试进程使用：拦截 Java 层越界写入和网络，非产品沙箱。 */
package net.fabricacs.regression;

import java.nio.file.Path;
import java.security.Permission;

@SuppressWarnings("removal")
public final class SmokeGuard extends SecurityManager {
    private final Path root = Path.of(System.getProperty("acbric.test.root")).toAbsolutePath().normalize();
    @Override public void checkPermission(Permission permission) { }
    @Override public void checkPermission(Permission permission, Object context) { }
    private void output(String file) {
        if (!Path.of(file).toAbsolutePath().normalize().startsWith(root)) {
            // ZipFS 只读打开前会问 Files.isWritable，查询自身不写入；真正打开写句柄仍拦截。
            boolean query = java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals("java.nio.file.Files") && frame.getMethodName().equals("isWritable")
                    || frame.getClassName().equals("java.io.File") && frame.getMethodName().equals("canWrite"));
            if (query) return;
            System.err.println("SMOKE_WRITE_DENIED: " + file);
            new Exception("Write permission caller").printStackTrace();
            throw new SecurityException("Write outside isolated test / 写入超出隔离测试目录: " + file);
        }
    }
    @Override public void checkWrite(String file) { output(file); }
    @Override public void checkDelete(String file) { output(file); }
    @Override public void checkConnect(String host, int port) { throw new SecurityException("Smoke test offline / 测试禁止联网"); }
    @Override public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
    @Override public void checkListen(int port) { throw new SecurityException("Smoke test offline"); }
    @Override public void checkAccept(String host, int port) { checkConnect(host, port); }
    @Override public void checkExec(String command) { throw new SecurityException("Smoke test cannot start child processes"); }
}

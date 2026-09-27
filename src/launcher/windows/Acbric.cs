// Acbric.cs — Windows 图形引导程序：隐藏控制台，使用包内 Java，保留早期错误和恢复入口。
using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using System.Drawing;

internal static class Acbric {
    static string Root = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
    static bool Chinese = true;
    static string LogFolder;
    static string Text(string zh, string en) { return Chinese ? zh : en; }
    // Windows 参数独立编码，不通过 cmd 拼接或执行路径文本。
    static string Quote(string value) {
        var result = new StringBuilder("\""); int slashes = 0;
        foreach (char c in value) {
            if (c == '\\') { slashes++; continue; }
            if (c == '"') { result.Append('\\', slashes * 2 + 1); result.Append(c); }
            else { result.Append('\\', slashes); result.Append(c); }
            slashes = 0;
        }
        result.Append('\\', slashes * 2); return result.Append('"').ToString();
    }
    static Process Start(string file, string arguments, bool capture) {
        var info = new ProcessStartInfo(file, arguments);
        info.WorkingDirectory = Root; info.UseShellExecute = false; info.CreateNoWindow = true;
        info.RedirectStandardError = capture; info.RedirectStandardOutput = capture;
        return Process.Start(info);
    }
    static string Powershell { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), @"WindowsPowerShell\v1.0\powershell.exe"); } }
    static void Error(string message) {
        using (var form = new Form()) {
            form.Text = "Acbric"; form.ClientSize = new Size(470, 160); form.StartPosition = FormStartPosition.CenterScreen;
            var label = new Label { Text = message, AutoSize = false, Left = 20, Top = 20, Width = 430, Height = 75 };
            var logs = new Button { Text = Text("打开诊断文件夹", "Open diagnostics folder"), Left = 20, Top = 112, Width = 205 };
            logs.Click += delegate { if (Directory.Exists(LogFolder)) Process.Start(new ProcessStartInfo(LogFolder) { UseShellExecute = true }); };
            var close = new Button { Text = Text("关闭", "Close"), Left = 320, Top = 112, Width = 110 };
            close.Click += delegate { form.Close(); }; form.Controls.Add(label); form.Controls.Add(logs); form.Controls.Add(close); form.ShowDialog();
        }
    }
    [STAThread]
    static int Main(string[] args) {
        Application.EnableVisualStyles();
        LogFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Acbric", "launcher-logs");
        try {
            Directory.CreateDirectory(LogFolder);
            bool recovering = args.Length == 3 && args[0] == "--recover-root";
            if (recovering) {
                Root = Path.GetFullPath(args[1]);
                try { using (var parent = Process.GetProcessById(Int32.Parse(args[2]))) { if (!parent.WaitForExit(15000)) throw new IOException("Recovery parent still running"); } }
                catch (ArgumentException) { /* 原入口已退出。 */ }
            }
            string lang = Path.Combine(Root, ".acbric-language");
            if (File.Exists(lang) && new FileInfo(lang).Length <= 16) Chinese = File.ReadAllText(lang).Trim() != "en";
            if (args.Length == 2 && args[0] == "--maintenance") {
                string job = Path.GetFullPath(args[1]);
                Start(Powershell, "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File " + Quote(Path.Combine(job, "player-maintenance.ps1")) + " -Job " + Quote(job), false).Dispose();
                return 0;
            }
            if (args.Length != 0 && !recovering) throw new ArgumentException("Unsupported launcher arguments");
            // 中断维护时不加载可能残缺的 Java/JAR，用包内恢复工具恢复后再启动。
            if (File.Exists(Path.Combine(Root, @".acbric-maintenance\pending.json"))) {
                if (!recovering) {
                    if (MessageBox.Show(Text("上次更新没有完成。现在恢复到更新前的版本？", "The last update did not finish. Restore the previous version now?"), "Acbric", MessageBoxButtons.OKCancel) != DialogResult.OK) return 0;
                    string copy = Path.Combine(Path.GetTempPath(), "acbric-recovery-" + Guid.NewGuid().ToString("N") + ".exe");
                    File.Copy(Application.ExecutablePath, copy);
                    Start(copy, "--recover-root " + Quote(Root) + " " + Process.GetCurrentProcess().Id, false).Dispose();
                    return 0;
                }
                string recoveryLog = Path.Combine(LogFolder, "recovery-" + Guid.NewGuid().ToString("N") + ".log");
                using (var recovery = Start(Powershell, "-NoProfile -ExecutionPolicy Bypass -File " + Quote(Path.Combine(Root, "update.ps1")) + " -TargetDir " + Quote(Root) + " -Restore", true)) {
                    var error = recovery.StandardError.ReadToEndAsync(); string output = recovery.StandardOutput.ReadToEnd(); recovery.WaitForExit();
                    File.WriteAllText(recoveryLog, output + error.Result);
                    if (recovery.ExitCode != 0) throw new IOException("Recovery failed; see " + recoveryLog);
                }
                string restored = Path.Combine(Root, "Acbric.exe");
                if (File.Exists(restored)) Start(restored, "", false).Dispose();
                else MessageBox.Show(Text("恢复完成。请使用旧版 Start Acbric.cmd 启动。", "Restored. Use the previous Start Acbric.cmd launcher."), "Acbric");
                return 0;
            }
            string java = Path.Combine(Root, @"runtime\bin\javaw.exe");
            if (!File.Exists(java)) {
                string home = Environment.GetEnvironmentVariable("JAVA_HOME");
                if (!String.IsNullOrEmpty(home)) java = Path.Combine(home, @"bin\javaw.exe");
            }
            if (!File.Exists(java)) {
                Error(Text("缺少启动所需的组件。请下载并完整解压带 Java 的 Acbric 包。", "Required components are missing. Download and fully extract Acbric with bundled Java.")); return 2;
            }
            string ready = Path.Combine(LogFolder, "ready-" + Guid.NewGuid().ToString("N"));
            string bootstrap = Path.Combine(LogFolder, "bootstrap-" + Guid.NewGuid().ToString("N") + ".log");
            using (var writer = new StreamWriter(bootstrap, false, Encoding.UTF8))
            using (var child = Start(java, Quote("-Dfile.encoding=UTF-8") + " " + Quote("-Dacbric.launcher.logs=" + LogFolder) + " " + Quote("-Dacbric.launcher.ready=" + ready)
                    + " -cp " + Quote(Path.Combine(Root, @"loader-libs\*")) + " net.fabricacs.acbric.PlayerLauncher", true)) {
                object gate = new object();
                bool finished = false;
                DataReceivedEventHandler record = delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) lock (gate) { if (!finished) { writer.WriteLine(e.Data); writer.Flush(); } } };
                child.OutputDataReceived += record; child.ErrorDataReceived += record; child.BeginOutputReadLine(); child.BeginErrorReadLine();
                using (var splash = new Form()) {
                    splash.Text = "Acbric"; splash.ClientSize = new Size(330, 100); splash.StartPosition = FormStartPosition.CenterScreen; splash.ControlBox = false;
                    splash.Controls.Add(new Label { Left = 22, Top = 30, Width = 290, Text = Text("正在打开 Acbric…", "Opening Acbric…") }); splash.Show();
                    var watch = Stopwatch.StartNew();
                    while (!File.Exists(ready) && !child.HasExited && watch.ElapsedMilliseconds < 60000) { Application.DoEvents(); Thread.Sleep(50); }
                    splash.Close();
                }
                if (File.Exists(ready)) {
                    File.Delete(ready); lock (gate) { finished = true; } child.CancelOutputRead(); child.CancelErrorRead(); return 0;
                }
                if (!child.HasExited) child.Kill();
                child.WaitForExit();
            }
            Error(Text("启动器未能打开。请确认压缩包已完整解压；诊断记录已保留。", "The launcher could not open. Make sure the package is fully extracted. Diagnostic logs were saved.")); return 2;
        } catch (Exception ex) {
            try { File.AppendAllText(Path.Combine(LogFolder, "bootstrap-errors.log"), DateTime.Now + "\n" + ex + "\n"); } catch { }
            Error(Text("暂时无法打开 Acbric。请重试，或提供诊断信息寻求帮助。", "Acbric could not open. Try again or provide diagnostics for help.")); return 2;
        }
    }
}

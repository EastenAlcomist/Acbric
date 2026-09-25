/* StartupCodeManifest.java — 保留本进程真实入口完成后的清单；联机不信任磁盘上的可编辑报告。 */
package net.fabricacs.api.impl;

import java.util.List;

final class StartupCodeManifest {
    static volatile CodeManifest current = new CodeManifest("unknown", "unavailable", "unknown", List.of());
    private StartupCodeManifest() {}
}

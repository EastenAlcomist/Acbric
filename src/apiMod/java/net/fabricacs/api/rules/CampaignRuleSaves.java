/* CampaignRuleSaves.java — 战役规则离线预检查、显式转换预览与另存；不会构造世界或改写原档。 */
package net.fabricacs.api.rules;

import net.fabricacs.api.impl.RuleSaveSession;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CampaignRuleSaves {
    private CampaignRuleSaves() {}

    /** 读取原生 IODirectory 目录存档；在 MOD 声明完成后、游戏线程调用。 */
    public static Inspection inspect(Path source) throws IOException {
        return new Inspection(new RuleSaveSession(source));
    }

    public enum Status { MATCH, MISSING, MIGRATABLE, UNSUPPORTED_VERSION, INVALID, RETAINED }

    /** savedVersion/targetVersion 为 -1 表示不存在存档条目/当前声明。 */
    public record Issue(String modId, int savedVersion, int targetVersion, Status status) {}

    public record Report(List<Issue> issues) {
        public Report { issues = List.copyOf(issues); }
        public boolean compatible() {
            return issues.stream().allMatch(i -> i.status() == Status.MATCH || i.status() == Status.RETAINED);
        }
        /** 稳定状态码及版本帮助定位问题；不将校验器异常或规则值写进提示。 */
        public String summary() {
            StringBuilder text = new StringBuilder(compatible() ? "Acbric rules compatible / 规则兼容" : "Acbric rule preflight failed / 规则预检查未通过");
            for (Issue issue : issues) {
                if (issue.status() != Status.MATCH && issue.status() != Status.RETAINED)
                    text.append("\n").append(issue.modId()).append(": ").append(issue.status())
                        .append(" (").append(issue.savedVersion()).append(" -> ").append(issue.targetVersion()).append(")");
            }
            if (!compatible()) text.append("\nUse an explicit save conversion or matching MOD version. / 请显式转换存档或使用匹配的 MOD 版本。");
            return text.toString();
        }
    }

    /** before 为 null 表示显式接纳缺失规则；快照不可变。 */
    public record Change(String modId, SharedRuleSnapshot before, SharedRuleSnapshot after) {}

    public static final class Inspection {
        private final RuleSaveSession session;
        private Inspection(RuleSaveSession session) { this.session = session; }
        public Path source() { return session.source(); }
        public Report report() { return session.report(); }
        /** 每个缺失 MOD 必须显式给出初始值；仅预览时运行已注册迁移，尚不写文件。 */
        public Plan prepare(Map<String, JSONObject> adoptions) throws IOException {
            return new Plan(session.prepare(adoptions));
        }
    }

    public static final class Plan {
        private final RuleSaveSession.Prepared prepared;
        private Plan(RuleSaveSession.Prepared prepared) { this.prepared = prepared; }
        public List<Change> changes() { return prepared.changes(); }
        public Report report() { return prepared.report(); }
        /** 输出必须不存在；原档/声明自预览后发生变化会拒绝，不重新执行迁移回调。 */
        public Path writeNew(Path destination) throws IOException { return prepared.writeNew(destination); }
    }
}

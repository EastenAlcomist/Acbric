/* RuleSaveSession.java — 一次离线规则检查与转换事务；预览执行回调，发布仅写已验证副本。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.rules.*;
import net.fabricacs.api.rules.CampaignRuleSaves.*;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class RuleSaveSession {
    private final SavedRuleFiles source;
    private final CampaignDataStore store;
    private final RuleSet saved;
    private final Map<String, SharedRules> declarations;
    private final long revision;
    private final Report report;
    private boolean preparing;

    public RuleSaveSession(Path path) throws IOException {
        revision = SharedRulesRegistry.revision();
        declarations = SharedRulesRegistry.declarations();
        source = SavedRuleFiles.read(path);
        store = source.store();
        try { saved = SharedRulesRegistry.stored(store); }
        catch (RuntimeException invalid) { throw new IOException("RULE_STORAGE_INVALID", invalid); }
        report = inspect(saved, declarations);
        unchanged();
    }
    public Path source() { return source.path(); }
    public Report report() { return report; }

    private void unchanged() throws IOException {
        if (revision != SharedRulesRegistry.revision()) throw new IOException("RULE_DECLARATIONS_CHANGED: inspect again / 请重新检查");
    }

    static Report inspect(RuleSet saved, Map<String, SharedRules> declarations) {
        List<Issue> issues = new ArrayList<>();
        TreeSet<String> ids = new TreeSet<>(saved.entries.keySet()); ids.addAll(declarations.keySet());
        for (String id : ids) {
            SharedRuleSnapshot previous = saved.entries.get(id);
            SharedRules handle = declarations.get(id);
            int before = previous == null ? -1 : previous.version();
            int target = handle == null ? -1 : handle.current().version();
            Status status;
            if (handle == null) status = Status.RETAINED;
            else if (previous == null) status = Status.MISSING;
            else if (before != target) status = before < target && SharedRulesRegistry.migration(id, before) != null
                    ? Status.MIGRATABLE : Status.UNSUPPORTED_VERSION;
            else {
                try { handle.checked(previous); status = Status.MATCH; }
                catch (RuntimeException bad) { status = Status.INVALID; }
            }
            issues.add(new Issue(id, before, target, status));
        }
        return new Report(issues);
    }

    static void requireCompatible(CampaignDataStore store) throws IOException {
        long revision = SharedRulesRegistry.revision();
        Report result;
        try { result = inspect(SharedRulesRegistry.stored(store), SharedRulesRegistry.declarations()); }
        catch (RuntimeException invalid) { throw new IOException("RULE_STORAGE_INVALID: invalid Acbric rule data / 规则数据损坏", invalid); }
        if (revision != SharedRulesRegistry.revision()) throw new IOException("RULE_DECLARATIONS_CHANGED");
        if (!result.compatible()) throw new IOException(result.summary());
    }

    public synchronized Prepared prepare(Map<String, JSONObject> adoptions) throws IOException {
        unchanged();
        if (preparing) throw new IOException("REENTRANT_RULE_CONVERSION");
        Objects.requireNonNull(adoptions);
        preparing = true;
        try {
            Map<String, SharedRuleSnapshot> next = new TreeMap<>(saved.entries);
            Set<String> missing = new TreeSet<>();
            for (Issue issue : report.issues()) if (issue.status() == Status.MISSING) missing.add(issue.modId());
            if (!adoptions.keySet().equals(missing)) throw new IOException("EXPLICIT_ADOPTION_REQUIRED: " + missing);
            List<Change> changes = new ArrayList<>();
            for (Issue issue : report.issues()) {
                if (issue.status() == Status.MATCH || issue.status() == Status.RETAINED) continue;
                String id = issue.modId(); SharedRules handle = declarations.get(id);
                SharedRuleSnapshot old = saved.entries.get(id);
                JSONObject values;
                if (issue.status() == Status.MISSING) values = adoptions.get(id);
                else if (issue.status() == Status.MIGRATABLE) {
                    // 回调仅得到副本；迁移失败不会产生可提交计划或修改任何文件。
                    values = SharedRulesRegistry.migration(id, old.version()).migrate(old.version(), old.values());
                } else throw new IOException("RULE_CONVERSION_UNSUPPORTED: " + id + " / " + issue.status());
                SharedRuleSnapshot accepted = handle.checked(new SharedRuleSnapshot(issue.targetVersion(), values));
                next.put(id, accepted); changes.add(new Change(id, old, accepted));
            }
            RuleSet converted = new RuleSet("SAVED", next, "");
            Report after = inspect(converted, declarations);
            if (!after.compatible()) throw new IOException(after.summary());
            unchanged();
            CampaignDataStore copy = new CampaignDataStore(); copy.restore(store.snapshot());
            var old = copy.read("acbric_api");
            if (old.isPresent() && old.get().dataVersion() != 1) throw new IOException("RULE_STORAGE_VERSION");
            JSONObject data = old.isEmpty() ? new JSONObject() : old.get().data();
            copy.write("acbric_api", 1, data.put("sharedRules", converted.text()));
            return new Prepared(this, source.convert(copy), List.copyOf(changes), after);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("RULE_CONVERSION_FAILED: original save retained / 原档保留", failure); }
        finally { preparing = false; }
    }

    public static final class Prepared {
        private final RuleSaveSession session;
        private final SavedRuleFiles output;
        private final List<Change> changes;
        private final Report report;
        private Prepared(RuleSaveSession session, SavedRuleFiles output, List<Change> changes, Report report) {
            this.session = session; this.output = output; this.changes = changes; this.report = report;
        }
        public List<Change> changes() { return changes; }
        public Report report() { return report; }
        public synchronized Path writeNew(Path destination) throws IOException {
            session.unchanged();
            return session.source.publish(output, destination, session::unchanged);
        }
    }
}

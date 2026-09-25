/*
 * ModConfig.java — MOD 命名空间下的显式 JSON 配置句柄；读写、迁移与磁盘提交分开。
 * 仅缺失文件使用默认值，失败保留旧快照；共享玩法参数须由 MOD 显式固化到战役数据。
 */
package net.fabricacs.api.config;

import net.fabricacs.api.impl.CampaignDataStore;
import net.fabricacs.api.impl.CampaignJson;
import net.fabricacs.api.impl.ConfigFileStore;
import net.fabricacs.api.impl.ConfigJson;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public final class ModConfig {
    private final ConfigFileStore file;
    private final int version;
    private final JSONObject defaults;
    private final Validator validator;
    private ConfigSnapshot current;
    private JSONObject envelope;
    private byte[] disk;
    private boolean callback;

    public ModConfig(Path configRoot, String modId, String name, int dataVersion, JSONObject defaults, Validator validator) throws ConfigException {
        file = new ConfigFileStore(Objects.requireNonNull(configRoot), modId, name);
        if (dataVersion < 0) throw new IllegalArgumentException("Negative config version");
        version = dataVersion;
        this.defaults = CampaignJson.copy(defaults, 64);
        this.validator = Objects.requireNonNull(validator);
        validate(this.defaults);
    }
    public Path path() { return file.path(); }
    public Path backupPath() { return file.backupPath(); }
    /** 首次读取磁盘；已加载则返回当前快照，不丢弃尚未保存的修改。 */
    public synchronized ConfigSnapshot load() throws IOException { writable(); return current == null ? reload() : read(); }
    public synchronized ConfigSnapshot read() {
        if (current == null) throw new IllegalStateException("Load config first: " + path());
        return current;
    }
    /** 成功后替换内存，包括丢弃未保存修改；失败时保留上次内存值。 */
    public synchronized ConfigSnapshot reload() throws IOException {
        writable();
        byte[] bytes = file.read();
        JSONObject candidate;
        int candidateVersion;
        try {
            candidate = bytes == null ? new JSONObject().put("format", 1).put("version", version).put("data", defaults)
                    : ConfigJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            if (CampaignDataStore.version(candidate, "format") != 1) throw new IllegalArgumentException("Unsupported config envelope");
            candidateVersion = CampaignDataStore.version(candidate, "version");
            new ConfigSnapshot(candidateVersion, candidate.getJSONObject("data"));
        } catch (RuntimeException | java.nio.charset.CharacterCodingException e) {
            throw new ConfigException(ConfigException.Code.INVALID_FORMAT, "Invalid config: " + path(), e);
        }
        if (candidateVersion > version) throw new ConfigException(ConfigException.Code.NEWER_VERSION, "Config version " + candidateVersion + " is newer than " + version + ": " + path());
        JSONObject data = candidate.getJSONObject("data");
        if (candidateVersion == version) data = prepare(data);
        ConfigSnapshot snapshot = new ConfigSnapshot(candidateVersion, data);
        // 所有验证完成后才替换磁盘基线和内存；未知封装字段保留。
        envelope = CampaignJson.copy(candidate, 72);
        current = snapshot;
        disk = bytes;
        return snapshot;
    }
    public synchronized void update(JSONObject data) throws ConfigException {
        writable(); requireCurrentVersion();
        current = new ConfigSnapshot(version, prepare(data));
    }
    public synchronized boolean migrate(Migration migration) throws ConfigException {
        writable(); Objects.requireNonNull(migration);
        ConfigSnapshot previous = read();
        if (previous.dataVersion() == version) return false;
        JSONObject migrated;
        callback = true;
        try { migrated = CampaignJson.copy(migration.migrate(previous.dataVersion(), previous.data()), 64); }
        catch (Exception e) { throw new ConfigException(ConfigException.Code.MIGRATION_FAILED, "Config migration failed; original retained: " + path(), e); }
        finally { callback = false; }
        current = new ConfigSnapshot(version, prepare(migrated));
        return true;
    }
    /** 只有显式保存才创建目录/文件；冲突或 I/O 失败时保留内存待保存值。 */
    public synchronized void save() throws IOException {
        writable(); requireCurrentVersion();
        JSONObject candidate = CampaignJson.copy(envelope, 72).put("version", version).put("data", current.data());
        byte[] bytes = (candidate.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
        file.save(disk, bytes);
        disk = bytes;
        envelope = candidate;
    }
    private void requireCurrentVersion() {
        if (read().dataVersion() != version) throw new IllegalStateException("Explicit migration required before update/save: " + path());
    }
    private void writable() { if (callback) throw new IllegalStateException("Config callbacks must not re-enter load/reload/update/migrate/save"); }
    private JSONObject prepare(JSONObject input) throws ConfigException {
        JSONObject merged;
        try { merged = CampaignJson.copy(input, 64); merge(merged, defaults); merged = CampaignJson.copy(merged, 64); }
        catch (RuntimeException e) { throw new ConfigException(ConfigException.Code.VALIDATION_FAILED, "Invalid config data: " + path(), e); }
        validate(merged);
        return merged;
    }
    private void validate(JSONObject input) throws ConfigException {
        callback = true;
        try { validator.validate(CampaignJson.copy(input, 64)); }
        catch (Exception e) { throw new ConfigException(ConfigException.Code.VALIDATION_FAILED, "Config validation failed: " + path(), e); }
        finally { callback = false; }
    }
    private static void merge(JSONObject data, JSONObject defaults) {
        var keys = defaults.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            if (!data.has(key)) data.put(key, defaults.get(key));
            else if (data.get(key) instanceof JSONObject actual && defaults.get(key) instanceof JSONObject fallback) merge(actual, fallback);
        }
    }
    @FunctionalInterface public interface Validator { void validate(JSONObject data) throws Exception; }
    @FunctionalInterface public interface Migration { JSONObject migrate(int previousVersion, JSONObject data) throws Exception; }
}

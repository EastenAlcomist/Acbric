# MOD settings UI (dev.18)

dev.19 validation: 810 standard checks (41 new), and 356 across two real Fabric game builds (169 main + 5 restart + 4 corrupt-config startup each). All 80 public types/428 member signatures from dev.18 are retained. Clipboard success uses a substitute; failure uses actual headless AWT. Native font/input paths are checked with GPU-free drawing terminals; Windows clipboard, screenshots, IME and focus transitions still require manual validation. dev.18 and earlier counts below are historical.

2026-09-26 player acceptance: the user reported no problems testing dev.18 and requested a commit. All 28 changed files matched the build-validation snapshot before acceptance notes were added. Code is unchanged; the existing 769 standard checks and 304 integration checks across two game versions remain the validation evidence. No detailed manual test matrix was supplied, so this feedback does not establish coverage of every GPU, IME or third-party MOD combination.

Final validation: 769 standard checks (57 new) and 304 checks across two game versions covering Fabric/UI, showcase saving, fresh-process reload and corrupt-config startup; dev.17 public signatures retain 71 types/354 members. English/Chinese UI and settings examples are compiled. Drawing terminals are GPU-free; player feedback is recorded above, and automated checks do not cover OS display, IME or focus switching.

English | [中文](SETTINGS.zh-CN.md)

Requires `acbric_api >=0.3.3-dev.18`. MODs explicitly declare fields and English/Chinese labels. Settings appear under the MOD's Details/tools entry and use the existing ModConfig format, backups and conflict protection. Arbitrary JSON is not automatically turned into an editor.

## Registration example

```java
import net.fabricacs.api.*;
import net.fabricacs.api.config.*;
import net.fabricacs.api.ui.SettingsUi;
import net.fabricacs.api.util.AcbricLanguage;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

public final class Example implements AcbricInitializer {
    private boolean showHud;
    @Override public void onInitializeAcbric() { }
    @Override public void onInitializeAcbric(AcbricModContext context) {
        try {
            ModConfig config = context.config("settings", 1,
                new JSONObject().put("showHud", true).put("count", 2).put("mode", "normal"), data -> {
                    if (!(data.get("showHud") instanceof Boolean)
                        || !(data.get("count") instanceof Integer)
                        || data.getInt("count") < 1 || data.getInt("count") > 10
                        || !List.of("normal", "compact").contains(data.getString("mode")))
                        throw new IllegalArgumentException("Invalid settings / 设置无效");
                });
            // Reading does not write defaults; the MOD explicitly adopts startup values.
            showHud = config.load().data().getBoolean("showHud");
            SettingsUi.register(context.ui(), "settings",
                () -> AcbricLanguage.text("Settings", "设置"), config, List.of(
                    ConfigField.bool("showHud", new ConfigField.Text("Show HUD", "显示 HUD"),
                        ConfigField.Effect.IMMEDIATE),
                    ConfigField.integer("count", new ConfigField.Text("Starting count", "初始数量"),
                        1, 10, ConfigField.Effect.NEW_CAMPAIGN),
                    ConfigField.choice("mode", new ConfigField.Text("Display mode", "显示模式"), List.of(
                        new ConfigField.Option("normal", new ConfigField.Text("Normal", "普通")),
                        new ConfigField.Option("compact", new ConfigField.Text("Compact", "紧凑"))),
                        ConfigField.Effect.RESTART)
                ), result -> showHud = result.snapshot().data().getBoolean("showHud"));
        } catch (IOException ex) {
            // Keep damaged files. Projects may expose their own error entry instead.
            throw new IllegalStateException("Cannot load settings / 无法读取设置", ex);
        }
    }
}
```

`SettingsUi.register` only registers a factory. Opening the tool creates ConfigEditor and calls `config.load()`, retaining the current in-memory snapshot when already loaded. Use Reload file to reread disk. Old versions require explicit MOD migration. Invalid declarations, missing fields, wrong types or load errors produce a closable error window without resetting the file.

## Fields and sessions

ConfigField offers `bool`, `integer`, `decimal`, `text`, `choice`, and `.description(new ConfigField.Text(english, chinese))`. Keys address top-level JSON properties matching `[a-zA-Z][a-zA-Z0-9_-]{0,63}`. A session has 1–32 unique fields. Every declared field must have a valid value in `ModConfig.defaults()`. Undeclared data and unknown envelope fields are retained.

| Kind | Constraints and draft representation |
| --- | --- |
| BOOLEAN | Boolean draft |
| INTEGER | int bounds; String draft, Integer on submission |
| DECIMAL | Finite double bounds; String draft; decimal/exponent syntax, rejecting non-finite values, out-of-range values and nonzero underflow to zero |
| TEXT | Single line, 1–4096 maximum Unicode code points; String draft |
| CHOICE | 1–64 unique stable String values with separate localized labels; unknown values are invalid and can be reselected |

Numeric drafts may temporarily be empty, a minus sign, or incomplete input. Validation displays an error and disables Apply instead of clamping or replacing input. Integer fields reject decimal syntax. Bounds are checked against the entered decimal value before double conversion; floating-point precision limits still apply. Existing storage normalizes numbers (e.g. `1.0` may reload as Integer); decimal fields accept Number values.

ConfigEditor is confined to its creation thread; create it in the tool factory on the game thread. `fields()` exposes immutable declarations; `value(key)` and `set(key,value)` edit an independent draft. Numbers are represented as Strings. `isDirty()` compares draft representations. `isValid()` and `error(key,chinese)` check field constraints only; the MOD's full/cross-field validator runs on Apply.

- `cancel()` resets the draft to this session's latest successful load/apply baseline. It does not undo other callers' changes to ModConfig.
- `restoreDefaults()` changes only declared fields in the draft.
- `apply()` returns `Applied(snapshot, changedKeys, effects)`. Keys are compared by value, so `0.50` versus `0.5` creates no false numeric change notification. Success updates the baseline; failure retains the draft.
- `reload()` explicitly discards the draft and calls ModConfig.reload. The UI confirms first because this also discards pending in-memory updates in the shared handle. Failure retains the draft. If the file is valid but incompatible with field declarations, the handle may have reloaded successfully while the editor retains its previous draft; fix declarations/migration and reopen.
- `SettingsUi.window(title, editor, applied)` directly builds a window. In dev.19, Cancel/X/Esc confirm when a draft is dirty. Keep editing or dismissing the confirmation preserves it; Discard closes. Clean drafts close immediately. Programmatic close, screen changes, native overlays, errors and exit still dispose immediately and discard unapplied edits. Closing ordinary child dialogs preserves the parent draft.

Apply may explicitly create a defaults file even without value changes. Every successful Apply invokes `applied` once; use changedKeys to avoid unnecessary runtime updates. The callback runs on the game thread after saving. Callback failure is explicitly reported as saved-but-not-applied, with no file rollback or automatic callback retry. Validators and callbacks should avoid reentry and irreversible side effects.

## Commit protection and effects

`ModConfig.defaults()` returns an isolated default snapshot. `save(ConfigSnapshot expected, JSONObject data)` requires expected to be the exact current object returned by that handle's read/load; a separately constructed equivalent snapshot is insufficient. An intervening update/reload/migration or successful draft commit invalidates older snapshots. Validation and saving finish before memory changes. The old update/save methods retain their behavior. Disk locking, backup and byte-based conflict checks remain in use.

Rare I/O failures after replacement, such as lock-close failure, may report failure despite a replaced file. The old in-memory baseline and draft are retained; explicitly reread to establish what happened. See [configuration contract](CONFIG.md).

`IMMEDIATE / RESTART / NEW_CAMPAIGN` are MOD declarations, adopted by MOD code in its success callback, startup load, and campaign creation respectively. The UI does not hot-reload MODs, broadcast configuration, or rewrite existing campaigns. Applied.snapshot contains the full configuration; callbacks should apply only appropriate immediate fields to live state.

Shared gameplay values still follow the existing shared-rule/save workflow. The new-campaign label does not automatically update registered shared-rule candidates. Where candidates are registered at startup, changed values need the next startup according to that contract.

## Reusable UI components

- `Ui.textField(Supplier<String>, maxLength, Consumer<String>)`: controlled text. The supplier returns non-null, length-valid single-line text; change must synchronously update the model. External changes reset selection and move the caret to the end. The old String overload retains window-local editing.
- `Ui.integerField(value,min,max,change)` and `Ui.numberField(value,min,max,change)` combine controlled text with bilingual validation. Both value/change use Strings; the caller controls submission.
- `Ui.choice(value,List<Ui.Choice>,change)` opens a modal list. Choice values are stable and labels may use Suppliers for localization. Selecting invokes change; cancelling leaves the value unchanged.

No nested field paths, arbitrary JSON editor, sliders, automatic migration or dedicated IME panel are included. Language follows the game; reopen to refresh titles and fixed labels.

## Validation

769 standard checks include 57 new numeric/draft/commit checks. The plain-JDK test process patches `jdk.unsupported` with FloatIO.jar to load the native JSON decimal formatter; actual game processes still load it through Knot, with no production launcher argument changes. SettingsUiRegression needs game language/font resources and runs through the workspace Fabric probe rather than the resource-free standard entry point.

Game versions 1.2.15.2 and 1.2.14 cover forms, validation failures, cancel/defaults, callback failures, external conflicts, independent showcase saving and fresh-process reload. Drawing terminals do not use a GPU. Chinese layout, resolutions, IME and screenshot/focus changes still need real-game checks. Showcase 0.2.0 requires dev.18 and writes `game/config/acbric_ui_showcase/ui-settings.json`; its new-campaign action only copies a mock value.

## Fixed actions and close confirmation (dev.19)

Apply, Cancel, Defaults and Reload file occupy a fixed footer; fields scroll separately. The footer shows draft state or Saved; detailed effects remain at the end of the body. Save/reload failures and saved-but-callback-failed results also open bilingual message dialogs. Dismissing the message retains the settings form; errors remain visible even when the body is scrolled elsewhere. At tiny resolutions the footer can scroll independently; see the UI contract.

No configuration format change is required. Showcase 0.3.0 requires dev.19 and adds long-text filling to test horizontal scrolling; previous 0.2.0 settings remain usable. This release does not add game logic, a hotkey registry or network synchronization.

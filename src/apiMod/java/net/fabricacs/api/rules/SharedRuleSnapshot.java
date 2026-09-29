/* SharedRuleSnapshot.java — 已声明或已固化的规则版本及不可变值快照；读取返回副本。 */
package net.fabricacs.api.rules;

import net.fabricacs.api.impl.RulesJson;
import org.json.JSONObject;

public final class SharedRuleSnapshot {
    private final int version;
    private final String values;
    public SharedRuleSnapshot(int version, JSONObject values) {
        if(version<0)throw new IllegalArgumentException("Negative rule version");
        this.version=version;this.values=RulesJson.encode(values);
    }
    public int version(){return version;}
    public JSONObject values(){return RulesJson.parse(values);}
    @Override public boolean equals(Object other){return other instanceof SharedRuleSnapshot s && version==s.version && values.equals(s.values);}
    @Override public int hashCode(){return 31*version+values.hashCode();}
}

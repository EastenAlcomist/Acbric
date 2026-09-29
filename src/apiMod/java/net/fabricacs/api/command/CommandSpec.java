/* CommandSpec.java — 显式命令契约；影响范围是作者声明，不是同进程 Java 沙箱。 */
package net.fabricacs.api.command;
import java.util.*;
public record CommandSpec(String id,CommandText description,List<CommandArgument> arguments,Effect effect,Requirement requirement) {
    public enum Effect { READ_ONLY, LOCAL, SHARED }
    public enum Requirement { ACTIVE_SCREEN, CAMPAIGN }
    public CommandSpec {
        if(id==null||!id.matches("[a-z][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("Command ID");
        Objects.requireNonNull(description);Objects.requireNonNull(effect);Objects.requireNonNull(requirement);arguments=List.copyOf(arguments);
        if(arguments.size()>16||arguments.stream().map(CommandArgument::name).distinct().count()!=arguments.size())throw new IllegalArgumentException("Up to 16 unique arguments");
        boolean optional=false;for(var argument:arguments){if(optional&&!argument.optional())throw new IllegalArgumentException("Required argument after optional");optional|=argument.optional();}
    }
}

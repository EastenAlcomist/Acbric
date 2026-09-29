/* CommandResult.java — 单次执行的明确结果；失败不表示 MOD 副作用已经回滚。 */
package net.fabricacs.api.command;
import java.util.Objects;
public record CommandResult(Status status,CommandText message) {
    public enum Status { SUCCESS, INVALID_ARGUMENTS, UNAVAILABLE, FAILED }
    public CommandResult {Objects.requireNonNull(status);Objects.requireNonNull(message);}
    public static CommandResult success(String english,String chinese){return new CommandResult(Status.SUCCESS,new CommandText(english,chinese));}
}

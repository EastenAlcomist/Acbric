/* CommandText.java — 命令元数据及结果的中英文文本，不在注册阶段读取游戏语言。 */
package net.fabricacs.api.command;
import java.util.Objects;
public record CommandText(String english,String chinese) {
    public CommandText {Objects.requireNonNull(english);Objects.requireNonNull(chinese);if(english.length()>8192||chinese.length()>8192)throw new IllegalArgumentException("Text <=8192 UTF-16 units");}
    public String resolve(boolean chinese){return chinese?this.chinese:english;}
}

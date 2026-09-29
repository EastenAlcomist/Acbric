/* CommandArgument.java — 位置参数声明；类型和边界同时用于解析、帮助与静态补全。 */
package net.fabricacs.api.command;
import java.util.*;
public record CommandArgument(String name,Type type,boolean optional,double minimum,double maximum,List<String> choices) {
    public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN, CHOICE }
    public CommandArgument {
        if(name==null||!name.matches("[a-z][a-z0-9_]{0,31}"))throw new IllegalArgumentException("Argument name");
        Objects.requireNonNull(type);choices=List.copyOf(choices);
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum>maximum)throw new IllegalArgumentException("Argument bounds");
        if(type==Type.INTEGER&&(minimum<Integer.MIN_VALUE||maximum>Integer.MAX_VALUE||minimum!=Math.rint(minimum)||maximum!=Math.rint(maximum)))throw new IllegalArgumentException("Integer bounds");
        if(type==Type.CHOICE&&(choices.isEmpty()||choices.size()>64||new HashSet<>(choices).size()!=choices.size()))throw new IllegalArgumentException("1..64 unique choices");
        if(type!=Type.CHOICE&&!choices.isEmpty())throw new IllegalArgumentException("Choices only on CHOICE");
        for(String s:choices)if(s.isEmpty()||s.length()>128||s.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Choice value");
    }
    public static CommandArgument text(String name,boolean optional){return new CommandArgument(name,Type.TEXT,optional,0,0,List.of());}
    public static CommandArgument integer(String name,int min,int max,boolean optional){return new CommandArgument(name,Type.INTEGER,optional,min,max,List.of());}
    public static CommandArgument decimal(String name,double min,double max,boolean optional){return new CommandArgument(name,Type.DECIMAL,optional,min,max,List.of());}
    public static CommandArgument bool(String name,boolean optional){return new CommandArgument(name,Type.BOOLEAN,optional,0,0,List.of());}
    public static CommandArgument choice(String name,List<String> values,boolean optional){return new CommandArgument(name,Type.CHOICE,optional,0,0,values);}
    public Object parse(String value){return switch(type){
        case TEXT->value;
        case INTEGER->{int n=Integer.parseInt(value);if(n<minimum||n>maximum)throw new IllegalArgumentException("Range");yield n;}
        case DECIMAL->{java.math.BigDecimal exact=new java.math.BigDecimal(value);double n=exact.doubleValue();if(!Double.isFinite(n)||n==0&&exact.signum()!=0||exact.compareTo(java.math.BigDecimal.valueOf(minimum))<0||exact.compareTo(java.math.BigDecimal.valueOf(maximum))>0)throw new IllegalArgumentException("Range");yield n;}
        case BOOLEAN->{if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Boolean");yield Boolean.valueOf(value);}
        case CHOICE->{if(!choices.contains(value))throw new IllegalArgumentException("Choice");yield value;}
    };}
}

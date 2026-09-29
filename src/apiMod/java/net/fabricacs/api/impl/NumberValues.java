/* NumberValues.java — 数字草稿解析；允许编辑暂存非法文本，提交时严格拒绝非有限值和越界。 */
package net.fabricacs.api.impl;

import java.math.BigDecimal;

public final class NumberValues {
    private NumberValues() {}
    public static void bounds(boolean integer,double min,double max) {
        if(!Double.isFinite(min)||!Double.isFinite(max)||min>max
                ||integer&&(min<Integer.MIN_VALUE||max>Integer.MAX_VALUE||min!=Math.rint(min)||max!=Math.rint(max)))
            throw new IllegalArgumentException("Invalid numeric bounds");
    }
    public static Number parse(String text,boolean integer,double min,double max) {
        bounds(integer,min,max);
        if(text==null||text.length()>128||!text.matches(integer?"[+-]?[0-9]+":"[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?"))
            throw new IllegalArgumentException("Invalid number");
        BigDecimal exact=new BigDecimal(text);
        if(exact.compareTo(BigDecimal.valueOf(min))<0||exact.compareTo(BigDecimal.valueOf(max))>0)
            throw new IllegalArgumentException("Number outside bounds");
        if(integer)return Integer.valueOf(exact.intValueExact());
        double value=exact.doubleValue();
        if(!Double.isFinite(value)||value==0.0&&exact.signum()!=0)throw new IllegalArgumentException("Number cannot be represented");
        return Double.valueOf(value);
    }
    public static String error(String text,boolean integer,double min,double max,boolean chinese) {
        try{parse(text,integer,min,max);return "";}catch(IllegalArgumentException|ArithmeticException ex){
            return (chinese?(integer?"请输入整数，范围：":"请输入有限数值，范围："):(integer?"Enter an integer in range: ":"Enter a finite number in range: "))
                    +BigDecimal.valueOf(min).stripTrailingZeros().toPlainString()+" … "+BigDecimal.valueOf(max).stripTrailingZeros().toPlainString();
        }
    }
}

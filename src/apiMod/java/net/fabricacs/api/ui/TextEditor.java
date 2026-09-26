/* TextEditor.java — 单行文本选区与水平视口；索引只停留在完整 Unicode 码点边界。 */
package net.fabricacs.api.ui;

import java.util.function.Function;

final class TextEditor {
    String text;
    int caret,anchor,offset;
    private String measured;
    private double scale;
    private int lineHeight;
    private int[] stops={0},widths={0};
    TextEditor(String text){reset(text);}
    void reset(String value){text=value;caret=anchor=value.length();offset=0;measured=null;}
    boolean selected(){return caret!=anchor;}
    int start(){return Math.min(caret,anchor);}
    int end(){return Math.max(caret,anchor);}
    String selection(){return text.substring(start(),end());}
    void move(int destination,boolean extend){caret=destination;if(!extend)anchor=caret;}
    void left(boolean extend){move(!extend&&selected()?start():previous(caret),extend);}
    void right(boolean extend){move(!extend&&selected()?end():next(caret),extend);}
    int previous(int index){return index==0?0:text.offsetByCodePoints(index,-1);}
    int next(int index){return index==text.length()?index:text.offsetByCodePoints(index,1);}
    void all(){anchor=0;caret=text.length();}
    void delete(boolean backwards){
        if(!selected()){anchor=backwards?previous(caret):next(caret);}
        replace("");
    }
    private void replace(String value){int start=start();text=text.substring(0,start)+value+text.substring(end());caret=anchor=start+value.length();}
    void insert(String value,int limit){
        int retained=text.codePointCount(0,text.length())-text.codePointCount(start(),end());
        String clean=value.codePoints().filter(c->!Character.isISOControl(c)&&!(c>=0xD800&&c<=0xDFFF))
                .limit(Math.max(0,limit-retained)).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
        if(!clean.isEmpty())replace(clean);
    }
    void measure(Function<String,int[]> measure,double scale,int lineHeight,int width){
        if(!text.equals(measured)||this.scale!=scale||this.lineHeight!=lineHeight){
            int[] advances=measure.apply(text);int count=text.codePointCount(0,text.length());stops=new int[count+1];widths=new int[count+1];
            for(int i=1;i<=count;i++){stops[i]=text.offsetByCodePoints(stops[i-1],1);widths[i]=Math.max(widths[i-1],advances[stops[i]]);}
            measured=text;this.scale=scale;this.lineHeight=lineHeight;
        }
        int before=widthAt(caret),available=Math.max(1,width-8);
        offset=Math.clamp(offset,Math.max(0,before-available+1),before);
        offset=Math.min(offset,Math.max(0,widths[widths.length-1]-available+1));
    }
    int widthAt(int position){int i=java.util.Arrays.binarySearch(stops,position);return i<0?0:widths[i];}
    int hit(double x){
        // 模型可在两帧之间由 MOD 更新；旧字形测量不再用于新文本。
        if(!text.equals(measured))return caret;
        double target=x+offset;
        for(int i=1;i<widths.length;i++)if(target<(widths[i-1]+widths[i])/2.0)return stops[i-1];
        return text.length();
    }
}

/* NativeUiCanvas.java — 复用原生绘制与字体的适配器；每个控件独立裁剪并恢复图形状态。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.util.Clr;
import net.fabricacs.api.ui.UiRuntime;
import net.fabricacs.api.ui.UiRuntime.Rect;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.geom.Rectangle;

final class NativeUiCanvas implements UiRuntime.Canvas {
    private final MyDraw draw;private final Graphics graphics;private final int width,height;
    NativeUiCanvas(MyDraw draw,int width,int height){this.draw=draw;graphics=(Graphics)draw.frame().nativeRenderer();this.width=width;this.height=height;}
    public double scale(){return Math.max(.5,MyDraw.BUTTON_H/24.0);}
    public int lineHeight(){return AGame.FOUNT.lineHeight;}
    public int buttonHeight(){return MyDraw.BUTTON_H;}
    // 不把来自配置、用户输入或 MOD 描述的方括号解释为游戏富文本颜色指令。
    private static String literal(String text){return text.replace('[','［').replace(']','］');}
    public int textHeight(String text,int width){return Math.max(lineHeight(),(int)draw.textSize(literal(text),AGame.FOUNT,0,0,Math.max(1,width)).height);}
    private void clipped(Rect clip,Runnable action){
        Rectangle old=graphics.getClip();IntRect oldHooks=draw.state.hookClipRect;
        int x=clip.x(),y=clip.y(),right=x+clip.w(),bottom=y+clip.h();
        if(old!=null){x=Math.max(x,(int)old.getX());y=Math.max(y,(int)old.getY());right=Math.min(right,(int)old.getMaxX());bottom=Math.min(bottom,(int)old.getMaxY());}
        if(right<=x||bottom<=y)return;
        graphics.setClip(x,y,right-x,bottom-y);draw.restrictHooks(x,y,right-x,bottom-y);
        try{action.run();}finally{graphics.setClip(old);draw.state.hookClipRect=oldHooks;}
    }
    public void window(Rect bounds,String title,boolean modal){
        if(modal)draw.rect(new Clr(0,0,0,130),0,0,width,height);
        clipped(bounds,()->{draw.drawShadowedPanel(bounds.x(),bounds.y(),bounds.w(),bounds.h());draw.text(literal(title),AGame.BIG_FOUNT,bounds.x()+8,bounds.y()+4,Math.max(1,bounds.w()-buttonHeight()-24));});
    }
    public void panel(Rect bounds,Rect clip){clipped(clip,()->draw.drawPanel(bounds.x(),bounds.y(),bounds.w(),bounds.h(),-1));}
    public void label(Rect bounds,Rect clip,String text){clipped(clip,()->draw.text(literal(text),AGame.FOUNT,bounds.x(),bounds.y(),Math.max(1,bounds.w())));}
    private void focus(Rect bounds,boolean focused){if(focused)draw.rect(new Clr(100,190,240),bounds.x(),bounds.y()+bounds.h()-2,bounds.w(),2);}
    public void button(Rect bounds,Rect clip,String text,boolean enabled,boolean focused){clipped(clip,()->{draw.button(bounds.x(),bounds.y(),bounds.w(),literal(text),(Runnable)null,enabled);focus(bounds,focused);});}
    public void toggle(Rect bounds,Rect clip,String text,boolean value,boolean enabled,boolean focused){clipped(clip,()->{draw.toggle(bounds.x(),bounds.y(),bounds.w(),literal(text),null,in->{},value,enabled);focus(bounds,focused);});}
    public void text(Rect bounds,Rect clip,String text,int caret,boolean selected,boolean focused,boolean enabled){
        clipped(clip,()->{
            draw.rect(new Clr(25,30,35),bounds.x(),bounds.y(),bounds.w(),bounds.h());draw.drawPanelBorder(bounds.x(),bounds.y(),bounds.w(),bounds.h());
            Rect inner=new Rect(bounds.x()+4,bounds.y()+2,Math.max(0,bounds.w()-8),Math.max(0,bounds.h()-4));
            clipped(inner.intersect(clip),()->{
                // tw 是开关按钮宽度（大字体加装饰），不能用于正文光标。测量和绘制必须使用同一字体。
                int before=(int)draw.textSize(literal(text.substring(0,caret)),AGame.FOUNT).x;
                int offset=Math.max(0,before-inner.w()+4);
                int y=inner.y()+Math.max(0,(inner.h()-lineHeight())/2);
                if(selected&&focused)draw.rect(new Clr(50,80,130),inner.x(),inner.y(),inner.w(),inner.h());
                draw.text(literal(text),AGame.FOUNT,inner.x()-offset,y);
                if(focused&&enabled)draw.rect(new Clr(220,230,245),inner.x()+before-offset,y,1,lineHeight());
            });focus(bounds,focused);
        });
    }
    public void scrollbar(Rect bounds,Rect clip,int offset,int max){clipped(clip,()->{int h=Math.max(6,(int)((long)bounds.h()*bounds.h()/(bounds.h()+max)));int y=bounds.y()+(int)((long)offset*Math.max(0,bounds.h()-h)/Math.max(1,max));draw.rect(new Clr(115,135,155),bounds.x()+bounds.w()-3,y,3,h);});}
    public void tooltip(String text,int x,int y){int w=Math.min(320,Math.max(1,width-16));int h=Math.max(1,Math.min(height-8,textHeight(text,w)+12));int px=Math.clamp(x+14,0,Math.max(0,width-w-12)),py=Math.clamp(y+18,0,Math.max(0,height-h));Rect r=new Rect(px,py,w+12,h);clipped(r,()->{draw.drawShadowedPanel(px,py,w+12,h);draw.text(literal(text),AGame.FOUNT,px+6,py+6,w);});}
}

/* UiMaskedInput.java — 只遮蔽被窗口占用的交互，保留时间、模式、音频和网络推进所需委托。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.BlankInput;
import com.zarkonnen.catengine.Input;
import com.zarkonnen.catengine.util.Pt;

public final class UiMaskedInput extends BlankInput {
    public final boolean pointer,keyboard;
    public UiMaskedInput(Input input,boolean pointer,boolean keyboard){super(input);this.pointer=pointer;this.keyboard=keyboard;}
    @Override public boolean keyDown(String key){return !keyboard&&originalIn.keyDown(key);}
    @Override public boolean keyPressed(String key){return !keyboard&&originalIn.keyPressed(key);}
    @Override public String lastKeyPressed(){return keyboard?null:originalIn.lastKeyPressed();}
    @Override public char lastInput(){return keyboard?'\0':originalIn.lastInput();}
    @Override public Pt cursor(){return pointer?null:originalIn.cursor();}
    @Override public Pt mouseDown(){return pointer?null:originalIn.mouseDown();}
    @Override public int mouseDownButton(){return pointer?0:originalIn.mouseDownButton();}
    @Override public Pt clicked(){return pointer?null:originalIn.clicked();}
    @Override public int clickButton(){return pointer?0:originalIn.clickButton();}
    @Override public int scrollAmount(){return pointer?0:originalIn.scrollAmount();}
    @Override public Input setCursorVisible(boolean value){originalIn.setCursorVisible(value);return this;}
}

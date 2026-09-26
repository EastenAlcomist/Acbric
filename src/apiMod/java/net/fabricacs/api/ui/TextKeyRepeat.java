/* TextKeyRepeat.java — 只重复文本导航与删除；单调时钟计时，不补发停顿期间的积压动作。 */
package net.fabricacs.api.ui;

final class TextKeyRepeat {
    static final int BACK=1,DELETE=2,LEFT=4,RIGHT=8,HOME=16,END=32;
    private int key;
    private long due;
    void clear(){key=0;due=0;}
    int pulse(int pressed,int held,long now){
        if(pressed!=0){key=Integer.highestOneBit(pressed);due=now+400_000_000L;return pressed;}
        if((key&held)==0){clear();return 0;}
        if(now-due>=0){due=now+45_000_000L;return key;}
        return 0;
    }
}

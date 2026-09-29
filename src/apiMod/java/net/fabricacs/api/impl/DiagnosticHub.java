/* DiagnosticHub.java — 线程安全的有界消息环；不截获 System.out，不持有错误或游戏对象引用。 */
package net.fabricacs.api.impl;
import net.fabricacs.api.diagnostics.*;
import java.time.Instant;
import java.util.*;
import java.io.*;
public final class DiagnosticHub {
    private static final Buffer BUFFER=new Buffer();
    private DiagnosticHub(){}
    public static String clip(String value,int limit){if(value==null)return "";if(value.length()<=limit)return value;int end=limit;if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);}
    public static void publish(String owner,DiagnosticMessage.Level level,String message,Throwable failure){
        try{
            String detail="";
            if(failure!=null){var writer=new BoundedWriter();failure.printStackTrace(new PrintWriter(writer));detail=writer.value.toString();}
            BUFFER.add(owner,level,message,detail);
        }catch(RuntimeException ignored){/* 诊断失败不改变被观察代码的行为。 */}
    }
    public static List<DiagnosticMessage> messages(String owner,DiagnosticMessage.Level level){return BUFFER.read(owner,level);}
    public static long dropped(){return BUFFER.dropped();}
    static final class BoundedWriter extends Writer {
        final StringBuilder value=new StringBuilder();public void write(char[] c,int off,int len){int n=Math.min(len,4096-value.length());if(n>0)value.append(c,off,n);}
        public void flush(){}public void close(){}
    }
    public static final class Buffer {
        private final Deque<DiagnosticMessage> rows=new ArrayDeque<>();private long sequence,dropped;private int characters;
        public synchronized void add(String owner,DiagnosticMessage.Level level,String message,String detail){
            var row=new DiagnosticMessage(++sequence,Instant.now(),clip(owner,64),Objects.requireNonNull(level),clip(message,4096),clip(detail,4096));
            rows.addLast(row);characters+=size(row);
            while(rows.size()>512||characters>524288){characters-=size(rows.removeFirst());dropped++;}
        }
        private int size(DiagnosticMessage row){return row.modId().length()+row.message().length()+row.detail().length();}
        public synchronized List<DiagnosticMessage> read(String owner,DiagnosticMessage.Level level){return rows.stream().filter(r->(owner==null||owner.isBlank()||owner.equals(r.modId()))&&(level==null||level==r.level())).toList();}
        public synchronized long dropped(){return dropped;}
    }
}

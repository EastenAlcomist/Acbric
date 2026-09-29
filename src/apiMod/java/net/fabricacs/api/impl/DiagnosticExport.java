/* DiagnosticExport.java — 后台有界导出；只读取当前会话白名单报告，不打包配置、存档或任意路径。 */
package net.fabricacs.api.impl;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class DiagnosticExport {
    private DiagnosticExport(){}
    public static Path write(Path directory,Path session,String snapshot,String messages)throws IOException{
        BundledResourceStore.rejectLinks(directory);Files.createDirectories(directory);BundledResourceStore.rejectLinks(directory);
        Path target=directory.resolve("acbric-diagnostics-"+UUID.randomUUID()+".zip");boolean success=false,created=false;
        List<String> index=new ArrayList<>();
        try{
            OutputStream file=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);created=true;
            try(ZipOutputStream zip=new ZipOutputStream(file,StandardCharsets.UTF_8)){
                entry(zip,"snapshot.json",snapshot.getBytes(StandardCharsets.UTF_8));entry(zip,"messages.json",messages.getBytes(StandardCharsets.UTF_8));
                for(String name:List.of("launch.properties","startup.json","code-manifest.json","runtime-events.jsonl")){
                    Path path=session==null?null:session.resolve(name);
                    try{
                        if(path==null)throw new IOException("No session");BundledResourceStore.rejectLinks(path);
                        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing or not regular");
                        byte[] data;try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){data=in.readNBytes(1024*1024+1);}
                        if(data.length>1024*1024)throw new IOException("Report exceeds 1 MiB");entry(zip,"session/"+name,data);index.add(name+": included / 已包含");
                    }catch(IOException|RuntimeException ex){index.add(name+": unavailable / 不可用 ("+ex.getClass().getSimpleName()+")");}
                }
                entry(zip,"README.txt",("Local diagnostics only; no config/save files. Snapshot and report reads may have different timestamps.\n本地诊断包，不含配置或存档；快照和各报告读取时间可能不同。\n"+String.join("\n",index)).getBytes(StandardCharsets.UTF_8));
            }
            success=true;return target;
        }finally{if(created&&!success)Files.deleteIfExists(target);}
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException{
        if(bytes.length>4*1024*1024)throw new IOException("Snapshot exceeds 4 MiB");zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
}

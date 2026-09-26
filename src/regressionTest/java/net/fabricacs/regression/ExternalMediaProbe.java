/* ExternalMediaProbe.java — 真实 GPU 下验证 DLC/MOD 资源、原生重载及录像 GIF；仅用于隔离验收。 */
package net.fabricacs.regression;

import com.zarkonnen.airships.*;
import net.fabricacs.api.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import org.json.*;

public final class ExternalMediaProbe implements AcbricInitializer {
    private static final String ID = "external_media_native";
    private static int frames, checks, stage, exports, glErrors;
    private static boolean started, exporting, done;
    private static Recording recording;
    private static com.zarkonnen.catengine.Input currentInput;
    private static final JSONArray outputReports=new JSONArray();
    private static Runnable pending;
    private static org.lwjgl.opengl.KHRDebugCallback glTrace;
    /** 绘制只记检查点；场景切换和纹理操作留到下一原生输入边界，避免破坏未结束的绘制批次。 */
    public static void inputComplete(com.zarkonnen.catengine.Input input) {
        currentInput=input;
        Runnable action=pending;pending=null;
        if(action!=null) action.run();
    }
    private static Path instance() { return Path.of(System.getProperty("acbric.test.instance",System.getProperty("acbric.external.instance"))); }
    private static Path mod() { return instance().resolve("userdata/mods/" + ID); }
    private static AirshipGame game() throws Exception {
        var f = AirshipGame.class.getDeclaredField("instance"); f.setAccessible(true); return (AirshipGame)f.get(null);
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++; System.out.println("PASS media: " + label);
    }
    private static void png(Path path, int rgb) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<16;y++) for (int x=0;x<16;x++) image.setRGB(x,y,rgb);
        ImageIO.write(image,"PNG",path.toFile());
    }
    @Override public void onInitializeAcbric() { throw new AssertionError("Context required"); }
    @Override public void onInitializeAcbric(AcbricModContext context) {
        try {
            if (Boolean.getBoolean("acbric.test.legacy"))
                System.load(Path.of(System.getProperty("acbric.test.install")).resolve("lib/native/OpenAL64.dll").toString());
            if ("gif".equals(System.getProperty("acbric.test.media.part"))) return;
            Files.createDirectories(mod().resolve("ModuleType"));
            Files.writeString(mod().resolve("info.json"), new JSONObject().put("id",ID).put("name",new JSONObject().put("en","External media fixture").put("chi","外部媒体测试夹具")).put("description",new JSONObject().put("en","Isolated acceptance test").put("chi","隔离验收测试")).toString());
            Files.writeString(mod().resolve("ModuleType/probe.json"), new JSONArray().put(new JSONObject().put("name","EXTERNAL_MEDIA_CANNON").put("deriveFrom","CANNON").put("cost",91)).toString());
            png(mod().resolve("logo.png"),0x336699);
            png(mod().resolve("images/heroes.png"), 0xff0000);
            png(mod().resolve("generated/external_media_generated.png"), 0x00ff00);

        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void color(String name, int r, int g, int b) {
        var image = SpriteUtils.loadImage(name);
        check(image != null && image.getWidth()==16 && image.getHeight()==16, name + " native GPU texture dimensions");
        var c=image.getColor(0,0);
        check(c.getRed()==r && c.getGreen()==g && c.getBlue()==b,name + " expected native pixel");
    }
    public static void menuRendered() {
        if (started || ++frames < 30) return;
        started=true;pending=ExternalMediaProbe::menuReady;
    }
    private static void menuReady() {
        try {
            if (org.lwjgl.opengl.GLContext.getCapabilities().GL_KHR_debug) {
                glTrace=new org.lwjgl.opengl.KHRDebugCallback((source,type,id,severity,message)-> {
                    if(type==org.lwjgl.opengl.KHRDebug.GL_DEBUG_TYPE_ERROR) {
                        if (++glErrors==1) { System.err.println("MEDIA GL ERROR: "+message);new Exception("Native GL caller").printStackTrace(); }
                    }
                });
                org.lwjgl.opengl.KHRDebug.glDebugMessageCallback(glTrace);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.KHRDebug.GL_DEBUG_OUTPUT);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            }
            check(org.lwjgl.opengl.Display.isCreated() && org.lwjgl.openal.AL.isCreated(),"real GPU/audio ready");
            if ("gif".equals(System.getProperty("acbric.test.media.part"))) { createRecording(); openReplay(); return; }
            check(EHeroes.it.installed && EHeroes.it.enabled,"installed heroes DLC enabled");
            check(EHeroes.it.getInstallDir().toPath().equals(Path.of(System.getProperty("acbric.test.install",System.getProperty("acbric.external.install"))).resolve("expansions/heroes")),"DLC installation belongs to selected game");
            check(Loadable.all(HeroType.class).stream().anyMatch(h -> h.sourceExpansion == EHeroes.it),"DLC hero definitions loaded");
            check(Mod.getById(ID)!=null,"native fixture discovered in instance");
            Mod.getById(ID).setPermanentlyEnabled(true);
            Mod.overrideModsToLoad(null,null,Expansion.installeds());
            stage=4; reload();
        } catch (Throwable e) { fail(e); }
    }
    private static void reload() throws Exception {
        ModsScreen screen=new ModsScreen(game()); game().s=screen;
        var method=ModsScreen.class.getDeclaredMethod("reload");method.setAccessible(true);method.invoke(screen);
    }
    public static void modsRendered(ModsScreen screen) { pending=()->modsReady(screen); }
    private static void modsReady(ModsScreen screen) {
        if (stage==0 || done) return;
        try {
            var field=ModsScreen.class.getDeclaredField("mrpd");field.setAccessible(true);
            if (field.get(screen)!=null) return;
            if (stage==4) {
            System.out.println("MEDIA MOD: enabled="+Mod.getEnabledModIDs()+"; info="+Mod.getById(ID).infoLog+"; load="+Mod.getById(ID).loadLog+"; build="+Mod.getById(ID).buildLog+"; modules="+Loadable.all(ModuleType.class).stream().filter(m->m.name.equals("EXTERNAL_MEDIA_CANNON")).map(m->m.name+":"+m.getCost(Tech.getStandardBonuses())).toList());
            check(Mod.getEnabledMods().stream().anyMatch(m -> ID.equals(m.id)) && ModuleType.ofName("EXTERNAL_MEDIA_CANNON").getCost(Tech.getStandardBonuses())==91,"native MOD discovered and derived module loaded");
            color("heroes",255,0,0);
            color("external_media_generated",0,255,0);
            png(mod().resolve("images/heroes.png"),0x0000ff);
            stage=1; reload();
            } else if (stage==1) {
                color("heroes",0,0,255);
                Mod.overrideModsToLoad(new ArrayList<>(List.of(ID)),null,new ArrayList<>());
                stage=5;reload();
            } else if (stage==5) {
                check(!EHeroes.it.enabled && Mod.getEnabledMods().stream().anyMatch(m -> ID.equals(m.id)),"native MOD remains enabled without DLC");
                color("heroes",0,0,255);
                check(ModuleType.ofName("EXTERNAL_MEDIA_CANNON").getCost(Tech.getStandardBonuses())==91,"native module remains loaded without DLC");
                Mod.overrideModsToLoad(new ArrayList<>(),null,new ArrayList<>());
                stage=2;reload();
            } else if (stage==2) {
                check(!EHeroes.it.enabled && Loadable.all(HeroType.class).stream().noneMatch(h -> h.sourceExpansion==EHeroes.it),"DLC disable/reload removes expansion definitions");
                check(Mod.getEnabledMods().stream().noneMatch(m -> ID.equals(m.id)) && Loadable.all(ModuleType.class).stream().noneMatch(m -> m.name.equals("EXTERNAL_MEDIA_CANNON")),"native MOD disable/reload removes module");
                check(SpriteUtils.loadImage2("heroes")==null,"disabled MOD/DLC texture no longer resolves");
                Mod.overrideModsToLoad(new ArrayList<>(),null,Expansion.installeds());
                stage=3;reload();
            } else {
                check(EHeroes.it.enabled,"DLC re-enabled through native reload");
                var img=SpriteUtils.loadImage("heroes");
                var expected=ImageIO.read(EHeroes.it.getDataDir().toPath().resolve("images/heroes.png").toFile());
                check(img.getWidth()==expected.getWidth() && img.getHeight()==expected.getHeight(),"DLC GPU texture restored after removing MOD override");
                int pixel=expected.getRGB(0,0);var c=img.getColor(0,0);
                check(c.getRed()==((pixel>>16)&255) && c.getGreen()==((pixel>>8)&255) && c.getBlue()==(pixel&255),"DLC texture pixel matches selected installation");
                stage=0; createRecording(); openReplay();
            }
        } catch(Throwable e) { fail(e); }
    }
    private static void createRecording() throws Exception {
        Combat c=new Combat(game(),TimeOfDay.ofName("DAY"));
        c.setRandomSeed(89412L);
        var terrain=LandFormation.generate(new GuardedRandom(89412L),false,LandscapeType.ofName("GRASSLAND"));
        c.landFormations.add(terrain.a);c.landFormations.addAll(terrain.b);
        Path ships=Path.of(System.getProperty("acbric.test.install",System.getProperty("acbric.external.install"))).resolve("default_ships");
        Path design;
        try(var files=Files.list(ships)) { design=files.filter(f -> f.toString().endsWith(".json")).sorted().findFirst().orElseThrow(); }
        for(int i=0;i<2;i++) {
            Airship ship=new Airship(new JSONObject(Files.readString(design)));
            ship.networkID="media-"+i;ship.setX(i==0 ? -800:800);ship.setY(-100);
            ship.flipped=i==1;ship.flipTo=ship.flipped;ship.repair(true);
            c.sides.get(i).ships.add(ship);
        }
        c.initWheelsLegsTentaclesAndBarrels();
        for(int i=0;i<30;i++) c.tick(16,c.sides.get(0),0,1);
        recording=c.recording; recording.header.customName="外部 GIF 验收";
        check(recording.initialCombat!=null && recording.tickCommands.size()>=20,"native simulation produced real two-ship recording");
        c.saveRecording();
        check(Files.isDirectory(instance().resolve("userdata/recordings")),"native recording stored in instance");
    }
    private static void openReplay() throws Exception {
        Combat c=new Combat(game(),recording.initialCombat);c.recording=null;c.playback=recording;c.speed=CombatSpeed.STOP;
        c.resetMoveTos=true;c.physics=null;c.initWheelsLegsTentaclesAndBarrels();
        UniScreen screen=new UniScreen(game(),new PlaybackIntent());screen.combat=c;screen.mySide=c.sides.get(0);
        game().s=screen;ZoomToFitButton.zoomToFit(currentInput,screen,1.0);frames=0;exporting=false;
    }
    public static void replayRendered(UniScreen screen) {
        if(glErrors>0 && Boolean.getBoolean("acbric.test.strictGL")) { fail(new AssertionError("Native GL errors="+glErrors)); return; }
        pending=()->replayReady(screen);
    }
    private static void replayReady(UniScreen screen) {
        if (!(screen.intent instanceof PlaybackIntent pi) || recording==null || done) return;
        try {
            if (!exporting) {
                if (++frames < 10) return;
                if (exports==0 && !Boolean.getBoolean("acbric.test.legacy")) {
                    // 用普通文件占住目录，确认框架在原生全局回退前中止，随后恢复夹具。
                    Path gifs=instance().resolve("userdata/gifs"), held=instance().resolve("userdata/gifs-held");
                    boolean existed=Files.exists(gifs);
                    if(existed) Files.move(gifs,held);
                    Files.writeString(gifs,"blocked fixture");
                    boolean rejected=false;
                    try { pi.takeGif(screen,false,false); } catch(java.io.UncheckedIOException expected) { rejected=true; }
                    finally { Files.delete(gifs); if(existed) Files.move(held,gifs); }
                    check(rejected && pi.gsw==null,"unusable GIF directory rejected before fallback");
                }
                pi.takeGif(screen,exports==1,exports==1);
                check(pi.gsw!=null && pi.gf.toPath().getParent().equals(instance().resolve("userdata/gifs")),"native GIF capture started in instance");
                exporting=true;return;
            }
            if(pi.takeGifUntil!=-1 || pi.gsw!=null) return;
            check(pi.gf.isFile() && pi.gf.length()>0,"native GIF encoder wrote file");
            try(var input=ImageIO.createImageInputStream(pi.gf)) {
                var reader=ImageIO.getImageReadersByFormatName("gif").next();
                try {
                    reader.setInput(input);
                    check(reader.getNumImages(true)>=2,"GIF contains multiple decodable frames");
                    int divisor=exports==0?1:2;
                    check(reader.getWidth(0)==pi.mostRecentScreenMode.width/divisor && reader.getHeight(0)==pi.mostRecentScreenMode.height/divisor,"GIF output dimensions match normal/scaled mode");
                    for(int i=0;i<reader.getNumImages(true);i++) reader.read(i);
                    var metadata=(javax.imageio.metadata.IIOMetadataNode)reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0");
                    var control=(javax.imageio.metadata.IIOMetadataNode)metadata.getElementsByTagName("GraphicControlExtension").item(0);
                    int delay=Integer.parseInt(control.getAttribute("delayTime"));
                    check(delay==(exports==0?3:6),"GIF timing matches native normal/slow mode");
                    outputReports.put(new JSONObject().put("file",pi.gf.getName()).put("width",reader.getWidth(0)).put("height",reader.getHeight(0)).put("frames",reader.getNumImages(true)).put("delayCentiseconds",delay));
                } finally {reader.dispose();}
            }
            if(++exports<2) {openReplay();return;}
            done=true;
            Files.writeString(instance().resolve("media-checkpoint.json"),new JSONObject().put("checks",checks).put("outputs",outputReports).put("exports",exports).put("glErrors",glErrors).put("strictGL",Boolean.getBoolean("acbric.test.strictGL")).put("recordingTicks",recording.tickCommands.size()).toString(2));
            System.out.println("EXTERNAL MEDIA PASS: "+checks);
            game().startExit();
        } catch(Throwable e) { fail(e); }
    }
    private static void fail(Throwable e) { e.printStackTrace();System.err.println("EXTERNAL MEDIA FAILED: "+e);System.exit(2); }
}

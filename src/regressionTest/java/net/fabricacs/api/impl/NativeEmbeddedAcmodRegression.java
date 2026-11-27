/* NativeEmbeddedAcmodRegression.java — 默认 API 内 ACMOD 载体发现与元数据边界。 */
package net.fabricacs.api.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class NativeEmbeddedAcmodRegression {
    private static int checks;

    private NativeEmbeddedAcmodRegression() {
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS native embedded: " + label);
    }

    private static void reject(String label, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (IOException expected) {
            check(true, label);
        }
    }

    public static int run(Path root) throws Exception {
        checks = 0;
        Path carrierMod = root.resolve("steam/123/carrier");
        Path carrier = Files.createDirectories(carrierMod.resolve("AcbricMod"));
        Path jar = carrier.resolve("payload.jar");
        writeJar(jar, "example_acmod");

        var found = NativeEmbeddedAcmodScanner.find(carrierMod).orElseThrow();
        check(found.jar().equals(jar.toAbsolutePath().normalize()), "single local or Steam carrier JAR selected");
        check(found.modId().equals("example_acmod"), "Fabric ID parsed");
        check(NativeEmbeddedAcmodScanner.find(root.resolve("missing")).isEmpty(), "missing carrier folder ignored");

        writeJar(carrier.resolve("second.jar"), "second_acmod");
        reject("multiple JARs rejected", () -> NativeEmbeddedAcmodScanner.find(carrierMod));

        Path protectedJar = root.resolve("protected.jar");
        writeJar(protectedJar, "acbric_api");
        reject("protected ID rejected", () -> NativeEmbeddedAcmodScanner.readFabricId(protectedJar));

        Path noMetadata = root.resolve("no-metadata.jar");
        writeRawJar(noMetadata, "README.txt", "payload");
        reject("missing metadata rejected", () -> NativeEmbeddedAcmodScanner.readFabricId(noMetadata));

        deleteTree(root);
        return checks;
    }

    private static void writeJar(Path path, String id) throws IOException {
        writeRawJar(path, "fabric.mod.json",
                "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0.0\"}");
    }

    private static void writeRawJar(Path path, String entryName, String text) throws IOException {
        try (OutputStream output = Files.newOutputStream(path);
             JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(entryName));
            jar.write(text.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}

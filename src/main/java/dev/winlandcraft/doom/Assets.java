package dev.winlandcraft.doom;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

final class Assets {
    static synchronized Path extract(Path root, String name) throws IOException {
        byte[] bytes;
        try (InputStream in = Assets.class.getResourceAsStream("/runtime/" + name)) {
            if (in == null) throw new IOException("Missing bundled " + name);
            bytes = in.readAllBytes();
        }
        Path dir = root.resolve("runtime").resolve(hash(bytes));
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (!Files.isRegularFile(target) || !hash(Files.readAllBytes(target)).equals(hash(bytes))) {
            Path temporary = Files.createTempFile(dir, "extract-", ".tmp");
            try { Files.write(temporary, bytes); Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temporary); }
        }
        return target.toAbsolutePath();
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private Assets() {}
}

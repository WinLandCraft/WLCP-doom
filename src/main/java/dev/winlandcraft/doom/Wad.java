package dev.winlandcraft.doom;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

record Wad(Path path, String canonicalName, String profile) {
    static Wad inspect(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        long size = Files.size(path);
        if (size < 12 || size > 128L * 1024 * 1024) throw new IOException("WAD must be between 12 bytes and 128 MB.");
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4]; b.get(magic);
        if (!Arrays.equals(magic, new byte[]{'I','W','A','D'}))
            throw new IOException("Open a base-game IWAD (DOOM1, DOOM, DOOM2, TNT, PLUTONIA or Freedoom). Add-on PWADs are not supported yet.");
        int count = b.getInt(), directory = b.getInt();
        if (count < 1 || count > 100_000 || directory < 12 || directory + count * 16L > size)
            throw new IOException("Invalid WAD directory.");
        Set<String> lumps = new HashSet<>();
        for (int i = 0; i < count; i++) {
            b.position(directory + i * 16);
            int offset = b.getInt(), length = b.getInt();
            if (offset < 0 || length < 0 || offset + (long)length > size) throw new IOException("Invalid WAD lump bounds.");
            byte[] name = new byte[8]; b.get(name);
            int n = 0; while (n < 8 && name[n] != 0) n++;
            lumps.add(new String(name, 0, n, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT));
        }
        if (!lumps.containsAll(Set.of("PLAYPAL", "COLORMAP", "PNAMES", "TEXTURE1")))
            throw new IOException("This IWAD does not contain Doom game data.");
        String original = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String canonical;
        if (lumps.contains("MAP01")) canonical = switch (original) {
            case "tnt.wad", "plutonia.wad" -> original;
            default -> lumps.contains("FREEDOOM") ? "freedoom2.wad" : "doom2.wad";
        };
        else if (lumps.contains("E1M1")) canonical = lumps.contains("FREEDOOM") ? "freedoom1.wad" : lumps.contains("E2M1") ? "doom.wad" : "doom1.wad";
        else throw new IOException("No Doom episode or map found in this WAD.");
        return new Wad(path, canonical, Assets.hash(bytes).substring(0, 24));
    }

    /** Separate persistent save slots allow independent windows without concurrent save writes. */
    SaveSlot acquire(Path root) throws IOException {
        Path profileDir = root.resolve("saves").resolve(profile);
        for (int i = 1; i <= 64; i++) {
            Path directory = profileDir.resolve("player-" + i);
            Files.createDirectories(directory);
            FileChannel channel = FileChannel.open(directory.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return new SaveSlot(directory, channel, lock);
            } catch (OverlappingFileLockException busy) { /* another panel owns it */ }
            channel.close();
        }
        throw new IOException("All 64 save slots are already in use.");
    }
    record SaveSlot(Path directory, FileChannel channel, FileLock lock) implements AutoCloseable {
        public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
}

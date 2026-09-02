package wlcp.bridge;

import com.sun.media.sound.AudioSynthesizer;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import javax.sound.midi.*;
import javax.sound.sampled.*;
import s.*;

/** Software MIDI synthesis to PCM; never opens a desktop audio device. */
public final class PcmMusic implements IMusic {
    private Sequencer sequencer;
    private AudioSynthesizer synth;
    private AudioInputStream stream;
    private volatile float volume = 0.65f;
    private volatile boolean running;
    private boolean loaded;

    public void InitMusic() {
        try {
            synth = (AudioSynthesizer)MidiSystem.getSynthesizer();
            stream = synth.openStream(new AudioFormat(48000, 16, 2, true, false), Map.of());
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synth.getReceiver());
        } catch (Exception failure) {
            System.err.println("Music unavailable; sound effects remain enabled: " + failure);
            if (sequencer != null) sequencer.close();
            if (synth != null) synth.close();
            sequencer = null; stream = null;
        }
        running = true;
        Thread.ofPlatform().daemon().name("doom-pcm").start(this::audioLoop);
    }
    private void audioLoop() {
        byte[] music = new byte[480 * 4];
        float[] mix = new float[480 * 2];
        ByteBuffer packet = ByteBuffer.allocate(mix.length * 4).order(ByteOrder.BIG_ENDIAN);
        long deadline = System.nanoTime();
        while (running) {
            Arrays.fill(mix, 0);
            try {
                if (stream != null) {
                    int n = 0;
                    while (n < music.length) {
                        int read = stream.read(music, n, music.length - n);
                        if (read < 0) throw new EOFException();
                        n += read;
                    }
                    for (int i = 0; i < mix.length; i++)
                        mix[i] = (short)((music[i * 2] & 255) | (music[i * 2 + 1] << 8)) / 32768f * volume;
                }
            } catch (IOException failure) { stream = null; }
            PcmSound.mix(mix);
            packet.clear();
            for (float sample : mix) packet.putFloat(Math.clamp(sample, -1f, 1f));
            Bridge.packet(2, packet.array());
            deadline += 10_000_000;
            long now = System.nanoTime();
            if (now - deadline > 100_000_000) deadline = now;
            LockSupport.parkNanos(Math.max(0, deadline - now));
        }
    }
    public void ShutdownMusic() {
        running = false;
        if (sequencer != null) sequencer.close();
        if (synth != null) synth.close();
    }
    public void SetMusicVolume(int volume) { this.volume = Math.clamp(volume, 0, 127) / 127f; }
    public void PauseSong(int handle) { if (sequencer != null) sequencer.stop(); }
    public void ResumeSong(int handle) { if (loaded) sequencer.start(); }
    public int RegisterSong(byte[] data) {
        if (sequencer == null) return -1;
        try {
            Sequence song;
            try { song = MidiSystem.getSequence(new ByteArrayInputStream(data)); }
            catch (InvalidMidiDataException mus) { song = MusReader.getSequence(new ByteArrayInputStream(data)); }
            sequencer.stop(); sequencer.setSequence(song); loaded = true;
            return 0;
        } catch (Exception failure) { loaded = false; return -1; }
    }
    public void PlaySong(int handle, boolean loop) {
        if (loaded) { sequencer.setLoopCount(loop ? Sequencer.LOOP_CONTINUOUSLY : 0); sequencer.setTickPosition(0); sequencer.start(); }
    }
    public void StopSong(int handle) { PauseSong(handle); }
    public void UnRegisterSong(int handle) { loaded = false; }
}

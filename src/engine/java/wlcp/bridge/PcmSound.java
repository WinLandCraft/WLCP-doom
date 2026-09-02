package wlcp.bridge;

import data.sfxinfo_t;
import data.sounds;
import doom.DoomMain;
import java.util.*;
import s.*;

/** Mix Doom's unsigned mono DMX samples directly to the host's 48 kHz stereo format. */
public final class PcmSound implements ISoundDriver {
    private final DoomMain<?, ?> game;
    private final Map<Integer, DMXSound> samples = new HashMap<>();
    private final LinkedHashMap<Integer, Voice> voices = new LinkedHashMap<>();
    private int nextHandle = 1, channels = 8;
    private static volatile PcmSound active;

    public PcmSound(DoomMain<?, ?> game) { this.game = game; active = this; }
    public boolean InitSound() { return true; }
    public void UpdateSound() {}
    public void SubmitSound() {}
    public synchronized void ShutdownSound() { voices.clear(); }
    public void SetChannels(int count) { channels = Math.clamp(count, 1, 32); }
    public int GetSfxLumpNum(sfxinfo_t info) { return game.wadLoader.GetNumForName("ds" + info.name); }
    public synchronized int StartSound(int id, int volume, int separation, int pitch, int priority) {
        DMXSound sample = samples.computeIfAbsent(id, key -> {
            sfxinfo_t info = sounds.S_sfx[key];
            while (info.link != null) info = info.link;
            int lump = game.wadLoader.CheckNumForName("ds" + info.name);
            if (lump < 0) lump = game.wadLoader.GetNumForName("dspistol");
            return game.wadLoader.CacheLumpNum(lump, 0, DMXSound.class);
        });
        if (sample.type != 3 || sample.data.length == 0 || sample.speed <= 0) return -1;
        if (voices.size() >= channels) voices.remove(voices.keySet().iterator().next());
        int handle = nextHandle++;
        Voice voice = new Voice(sample);
        voices.put(handle, voice);
        UpdateSoundParams(handle, volume, separation, pitch);
        return handle;
    }
    public synchronized void StopSound(int handle) { voices.remove(handle); }
    public synchronized boolean SoundIsPlaying(int handle) { return voices.containsKey(handle); }
    public synchronized void UpdateSoundParams(int handle, int volume, int separation, int pitch) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        double pan = Math.clamp(separation, 0, 255) / 255.0;
        float gain = Math.clamp(volume, 0, 127) / 127f;
        voice.left = gain * (float)Math.sqrt(1 - pan);
        voice.right = gain * (float)Math.sqrt(pan);
        voice.step = voice.sample.speed / 48000.0 * Math.pow(2, (pitch - 128) / 64.0);
    }
    public static void mix(float[] output) {
        PcmSound driver = active;
        if (driver != null) driver.addSamples(output);
    }
    private synchronized void addSamples(float[] output) {
        for (var it = voices.values().iterator(); it.hasNext();) {
            Voice voice = it.next();
            for (int frame = 0; frame < output.length / 2; frame++) {
                int pos = (int)voice.position;
                if (pos >= voice.sample.data.length) break;
                int end = Math.min(pos + 1, voice.sample.data.length - 1);
                float a = ((voice.sample.data[pos] & 255) - 128) / 128f;
                float b = ((voice.sample.data[end] & 255) - 128) / 128f;
                float value = a + (b - a) * (float)(voice.position - pos);
                output[frame * 2] += value * voice.left * 0.6f;
                output[frame * 2 + 1] += value * voice.right * 0.6f;
                voice.position += voice.step;
            }
            if (voice.position >= voice.sample.data.length) it.remove();
        }
    }
    private static final class Voice {
        final DMXSound sample;
        double position, step;
        float left, right;
        Voice(DMXSound sample) { this.sample = sample; }
    }
}

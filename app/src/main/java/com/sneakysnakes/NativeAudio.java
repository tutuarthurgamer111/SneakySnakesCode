package com.sneakysnakes;

import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import java.io.*;
import java.util.*;

final class NativeAudio {
    private static SoundPool pool;
    private static final Map<String,Integer> sounds = new HashMap<>();
    private static MediaPlayer music;
    private static AssetManager assets;
    private static boolean enabled = true;
    private static boolean pausedByFocus = false;
    private static boolean musicWasPlayingBeforeFocusLoss = false;
    private static final Random random = new Random();

    static synchronized void init(AssetManager manager) {
        if (pool != null) return;
        assets = manager;
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        String[] names = {"click","eat","hit","combo","levelup","win"};
        for (String n : names) {
            try (AssetFileDescriptor fd = assets.openFd("audio/" + n + ".mp3")) {
                sounds.put(n, pool.load(fd, 1));
            } catch (Exception ignored) {}
        }
    }

    static synchronized void play(String name) {
        if (!enabled || pausedByFocus || pool == null) return;
        Integer id = sounds.get(name);
        if (id != null) pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    static synchronized void menuMusic() { startMusic("background.wav"); }
    static synchronized void randomTrack() { startMusic("track" + (random.nextInt(4)+1) + ".wav"); }

    private static void startMusic(String file) {
        stopMusic();
        if (!enabled || pausedByFocus || assets == null) return;
        try {
            AssetFileDescriptor fd = assets.openFd("audio/" + file.replace(".wav", ".mp3"));
            music = new MediaPlayer();
            music.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            music.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            music.setLooping(true);
            music.setVolume(0.75f, 0.75f);
            music.setOnPreparedListener(MediaPlayer::start);
            music.prepareAsync();
        } catch (Exception ignored) { stopMusic(); }
    }

    static synchronized void pauseForFocusLoss() {
        pausedByFocus = true;
        musicWasPlayingBeforeFocusLoss = music != null && music.isPlaying();
        if (music != null) { try { if (music.isPlaying()) music.pause(); } catch (Exception ignored) {} }
        if (pool != null) pool.autoPause();
    }

    static synchronized void resumeAfterFocusGain() {
        pausedByFocus = false;
        if (!enabled) return;
        if (music != null && musicWasPlayingBeforeFocusLoss) { try { music.start(); } catch (Exception ignored) {} }
        musicWasPlayingBeforeFocusLoss = false;
        if (pool != null) pool.autoResume();
    }

    static synchronized void stopMusic() {
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) {}
            try { music.release(); } catch (Exception ignored) {}
            music = null;
        }
    }

    static synchronized boolean isEnabled() { return enabled; }
    static synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) { stopMusic(); if (pool != null) pool.autoPause(); }
        else if (pool != null && !pausedByFocus) pool.autoResume();
    }

    static synchronized void release() {
        stopMusic();
        if (pool != null) { pool.release(); pool = null; }
        sounds.clear();
    }
}
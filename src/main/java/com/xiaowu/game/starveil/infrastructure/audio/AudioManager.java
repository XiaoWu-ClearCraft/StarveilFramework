package com.xiaowu.game.starveil.infrastructure.audio;

import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import javafx.application.Platform;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class AudioManager {
    private static AudioManager instance;
    private final Map<String, SoundPlayer> soundCache;
    private static volatile SoundPlayer backgroundMusic;

    private static final Map<String, File> tempFileCache = new HashMap<>();
    private static final File tempDir;
    private static boolean shutdownHookRegistered = false;

    static {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "starveil-audio-tmp-" + UUID.randomUUID().toString().substring(0, 8));
        tempDir.mkdirs();
    }

    private static void ensureShutdownHook() {
        if (shutdownHookRegistered) return;
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanupTempFiles(), "audio-temp-cleanup"));
    }

    private static void cleanupTempFiles() {
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try { f.delete(); } catch (Exception ignored) {}
                }
            }
            try { tempDir.delete(); } catch (Exception ignored) {}
        }
    }

    private static double backgroundMusicVolume = 0.8;
    private static double soundEffectsVolume = 0.8;
    private static double creditsBGMVolume = 1.0;

    private AudioManager() {
        soundCache = new HashMap<>();
        instance = this;
    }

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public static void playBackgroundMusic(String musicFile, boolean loop) {
        if (Platform.isFxApplicationThread()) {
            playBackgroundMusicImpl(musicFile, loop);
        } else {
            Platform.runLater(() -> playBackgroundMusicImpl(musicFile, loop));
        }
    }

    private static void playBackgroundMusicImpl(String musicFile, boolean loop) {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            boolean isCalledByBGMManager = false;
            for (StackTraceElement element : stackTrace) {
                if (element.getClassName().equals("com.xiaowu.game.audio.BGMManager")) {
                    isCalledByBGMManager = true;
                    break;
                }
            }

            if (isCalledByBGMManager) {
                BGMManager.getInstance().stopRandomBGM();
            }

            stopBackgroundMusic();

            Logger("DEBUG", "尝试播放背景音乐: " + musicFile);
            if (!audioAvailable(musicFile)) return;

            SoundPlayer player = new SoundPlayer(musicFile, loop);
            player.setVolume(backgroundMusicVolume);
            backgroundMusic = player;
            player.play();

        } catch (Exception e) {
            Logger("ERROR", "无法播放背景音乐: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void playBackgroundMusic(String musicFile) {
        playBackgroundMusic(musicFile, false);
    }

    public static void playCreditsBGM(String musicFile) {
        if (Platform.isFxApplicationThread()) {
            playCreditsBGMImpl(musicFile);
        } else {
            Platform.runLater(() -> playCreditsBGMImpl(musicFile));
        }
    }

    private static void playCreditsBGMImpl(String musicFile) {
        try {
            BGMManager.getInstance().stopRandomBGM();
            stopBackgroundMusic();

            Logger("DEBUG", "尝试播放致谢名单BGM: " + musicFile);
            if (!audioAvailable(musicFile)) return;

            SoundPlayer player = new SoundPlayer(musicFile, false);
            player.setVolume(creditsBGMVolume);
            backgroundMusic = player;
            player.play();

        } catch (Exception e) {
            Logger("ERROR", "无法播放致谢名单BGM: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void stopBackgroundMusic() {
        SoundPlayer bgm = backgroundMusic;
        if (bgm != null) {
            backgroundMusic = null;
            bgm.stop();
        }
    }

    public void pauseBackgroundMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.pause();
        }
    }

    public void resumeBackgroundMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.resume();
        }
    }

    public boolean isBackgroundMusicPlaying() {
        return backgroundMusic != null && backgroundMusic.isPlaying();
    }

    public void playSound(String soundFile, boolean loop) {
        if (Platform.isFxApplicationThread()) {
            playSoundImpl(soundFile, loop);
        } else {
            Platform.runLater(() -> playSoundImpl(soundFile, loop));
        }
    }

    /**
     * 音频资源是否可用。
     *
     * <p>框架<b>默认不带任何音频</b>（音频由内容项目提供），所以「资源不存在」是<b>正常状态</b>
     * 而不是故障。这种情况必须静默跳过 —— 抛异常 + 打 ERROR + 堆栈，会让「没装音频」
     * 看起来像游戏坏了，把真正的问题淹掉。
     *
     * @return 资源是否存在；不存在时记一条 DEBUG
     */
    private static boolean audioAvailable(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return false;
        }
        if (ResourceResolver.getResource(resourcePath) != null) {
            return true;
        }
        Logger("DEBUG", "音频资源不存在，静默跳过: " + resourcePath);
        return false;
    }

    private void playSoundImpl(String soundFile, boolean loop) {
        try {
            SoundPlayer soundPlayer;

            if (soundCache.containsKey(soundFile)) {
                soundPlayer = soundCache.get(soundFile);
                soundPlayer.stop();
            } else {
                soundPlayer = new SoundPlayer(soundFile, loop);
                soundCache.put(soundFile, soundPlayer);
            }

            soundPlayer.setVolume(soundEffectsVolume);
            soundPlayer.play();

        } catch (Exception e) {
            Logger("ERROR", "无法播放音效: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void playSound(String soundFile) {
        playSound(soundFile, false);
    }

    public void stopSound(String soundFile) {
        if (soundCache.containsKey(soundFile)) {
            soundCache.get(soundFile).stop();
        }
    }

    public void stopAllSounds() {
        for (SoundPlayer soundPlayer : soundCache.values()) {
            soundPlayer.stop();
        }
    }

    public boolean isSoundPlaying(String soundFile) {
        if (soundCache.containsKey(soundFile)) {
            return soundCache.get(soundFile).isPlaying();
        }
        return false;
    }

    public void setBackgroundMusicVolume(double volume) {
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(volume);
        }
    }

    public void setSoundVolume(String soundFile, double volume) {
        if (soundCache.containsKey(soundFile)) {
            soundCache.get(soundFile).setVolume(volume);
        }
    }

    public void setAllSoundsVolume(double volume) {
        for (SoundPlayer soundPlayer : soundCache.values()) {
            soundPlayer.setVolume(volume);
        }
    }

    public static double getBackgroundMusicVolume() {
        return backgroundMusicVolume;
    }

    public static void setBackgroundMusicVolumeGlobal(double volume) {
        backgroundMusicVolume = Math.max(0.0, Math.min(1.0, volume));
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(backgroundMusicVolume);
        }
    }

    public static double getSoundEffectsVolume() {
        return soundEffectsVolume;
    }

    public static void setSoundEffectsVolumeGlobal(double volume) {
        soundEffectsVolume = Math.max(0.0, Math.min(1.0, volume));
        if (instance != null && instance.soundCache != null) {
            for (SoundPlayer soundPlayer : instance.soundCache.values()) {
                soundPlayer.setVolume(soundEffectsVolume);
            }
        }
    }

    public static double getCreditsBGMVolume() {
        return creditsBGMVolume;
    }

    public static void setCreditsBGMVolume(double volume) {
        creditsBGMVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public void shutdown() {
        stopBackgroundMusic();
        stopAllSounds();
    }

    /**
     * 单个音频播放器。
     *
     * <p>本版本核心只内置 Windows 音频后端（JavaFX MediaPlayer）；
     * 其它平台由平台适配插件替换 {@link PlatformInjectPoints#AUDIO_PLAY} 注入点。
     */
    private static class SoundPlayer {
        private final String resourcePath;
        private final boolean loop;
        private javafx.scene.media.MediaPlayer fxPlayer;
        private volatile boolean stopped = false;
        private double currentVolume = 1.0;
        private File audioFile;

        SoundPlayer(String resourcePath, boolean loop) {
            URL url = ResourceResolver.getResource(resourcePath);
            if (url == null) {
                throw new RuntimeException("音频资源不存在: " + resourcePath);
            }
            this.resourcePath = resourcePath;
            this.loop = loop;
            this.audioFile = resolveAudioFile(url);
        }

        private File resolveAudioFile(URL url) {
            // 文件系统路径(AppImage 或开发模式): 直接使用
            if ("file".equals(url.getProtocol())) {
                try {
                    return new File(url.toURI());
                } catch (Exception ignored) {}
            }
            // JAR 模式: 提取到临时目录(带缓存)
            synchronized (tempFileCache) {
                File cached = tempFileCache.get(resourcePath);
                if (cached != null && cached.exists()) return cached;
            }
            try {
                String ext = "";
                int dot = resourcePath.lastIndexOf('.');
                if (dot >= 0) ext = resourcePath.substring(dot);
                File f = new File(tempDir, UUID.randomUUID().toString() + ext);
                f.deleteOnExit();
                try (InputStream in = url.openStream()) {
                    Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                synchronized (tempFileCache) {
                    tempFileCache.put(resourcePath, f);
                }
                ensureShutdownHook();
                return f;
            } catch (Exception e) {
                Logger("ERROR", "提取音频文件失败: " + e.getMessage());
                return null;
            }
        }

        /**
         * 播放音频。
         *
         * <p>核心使用 JavaFX MediaPlayer（Windows 自带原生库）。
         * 其它平台由平台适配插件负责提供该平台的 JavaFX 原生库
         * （放进 {@code <插件id>-lib/} 目录即可被插件加载器加入 classpath）。
         */
        void play() {
            if (audioFile == null) {
                Logger("ERROR", "无法获取音频文件: " + resourcePath);
                return;
            }
            stop();
            stopped = false;
            playWithJavaFX();
        }

        private void playWithJavaFX() {
            Runnable task = () -> {
                try {
                    Logger("DEBUG", "启动 JavaFX MediaPlayer: " + resourcePath);
                    javafx.scene.media.Media media = new javafx.scene.media.Media(audioFile.toURI().toString());
                    fxPlayer = new javafx.scene.media.MediaPlayer(media);
                    fxPlayer.setCycleCount(loop ? javafx.scene.media.MediaPlayer.INDEFINITE : 1);
                    fxPlayer.setVolume(currentVolume);
                    fxPlayer.play();
                } catch (Exception e) {
                    Logger("ERROR", "JavaFX 播放失败: " + e.getMessage());
                    stopped = true;
                }
            };
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        }

        void stop() {
            stopped = true;
            if (fxPlayer != null) {
                Runnable task = () -> {
                    fxPlayer.stop();
                    fxPlayer.dispose();
                };
                if (Platform.isFxApplicationThread()) {
                    task.run();
                } else {
                    Platform.runLater(task);
                }
                fxPlayer = null;
            }
        }

        void pause() {
            if (fxPlayer != null) {
                Runnable task = fxPlayer::pause;
                if (Platform.isFxApplicationThread()) {
                    task.run();
                } else {
                    Platform.runLater(task);
                }
            }
        }

        void resume() {
            if (fxPlayer != null) {
                Runnable task = fxPlayer::play;
                if (Platform.isFxApplicationThread()) {
                    task.run();
                } else {
                    Platform.runLater(task);
                }
            }
        }

        void setVolume(double volume) {
            currentVolume = Math.max(0.0, Math.min(1.0, volume));
            if (fxPlayer != null) {
                Platform.runLater(() -> fxPlayer.setVolume(currentVolume));
            }
        }

        boolean isPlaying() {
            if (fxPlayer != null) {
                return fxPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING;
            }
            return false;
        }

        void close() {
            stop();
        }
    }
}

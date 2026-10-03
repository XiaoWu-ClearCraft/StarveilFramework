package com.xiaowu.game.starveil.infrastructure.audio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.config.GameConstants;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BGM 管理器 - 负责随机播放背景音乐
 */
public class BGMManager {
    private static BGMManager instance;
    private final List<String> bgmFiles;
    private final List<String> playQueue; // 播放队列
    private final Random random;
    private String currentBGM;
    private boolean isPaused = false;
    private boolean wasPlayingBeforePause = false;
    private long intervalMillis = 30000; // 默认间隔时间：30秒

    private final ScheduledExecutorService scheduler;
    private ScheduledExecutorService playScheduler;

    private BGMManager() {
        bgmFiles = new ArrayList<>();
        playQueue = new ArrayList<>();
        random = new Random();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BGM-Manager");
            t.setDaemon(true);
            return t;
        });
        loadBGMConfig();
        initializePlayQueue(); // 初始化播放队列
    }

    public static BGMManager getInstance() {
        if (instance == null) {
            instance = new BGMManager();
        }
        return instance;
    }

    /**
     * 初始化播放队列
     */
    private void initializePlayQueue() {
        playQueue.clear();
        playQueue.addAll(bgmFiles);
        Collections.shuffle(playQueue, random);
        LoggerManager.Logger("DEBUG", "初始化播放队列，共 " + playQueue.size() + " 首歌曲");
    }

/**
     * 从 JSON 文件加载 BGM 配置
     */
    private void loadBGMConfig() {
        try {
            InputStream is = com.xiaowu.game.starveil.infrastructure.ResourceResolver.getResourceAsStream("starveil:data/config/bgm-config.json");
            if (is == null) {
                LoggerManager.Logger("WARNING", "BGM 配置文件不存在: starveil:data/config/bgm-config.json");
                return;
            }

            Gson gson = new Gson();
            JsonObject config = gson.fromJson(new InputStreamReader(is), JsonObject.class);

            // 读取 BGM 文件列表
            if (config.has("bgm_files") && config.get("bgm_files").isJsonArray()) {
                JsonArray files = config.getAsJsonArray("bgm_files");
                for (JsonElement file : files) {
                    if (file.isJsonPrimitive()) {
                        bgmFiles.add(file.getAsString());
                    }
                }
                LoggerManager.Logger("DEBUG", "加载了 " + bgmFiles.size() + " 个 BGM 文件");
            }

            // 读取间隔时间（毫秒）
            if (config.has("interval_ms")) {
                intervalMillis = config.get("interval_ms").getAsLong();
                LoggerManager.Logger("DEBUG", "BGM 间隔时间: " + intervalMillis + " 毫秒");
            }

        } catch (Exception e) {
            LoggerManager.Logger("WARNING", "加载 BGM 配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 开始随机播放 BGM
     */
    public void startRandomBGM() {
        if (bgmFiles.isEmpty()) {
            LoggerManager.Logger("WARNING", "没有可用的 BGM 文件");
            return;
        }

        if (isPaused) {
            LoggerManager.Logger("DEBUG", "BGM 已暂停，不开始随机播放");
            return;
        }

        // 如果播放队列为空，重新初始化
        if (playQueue.isEmpty()) {
            initializePlayQueue();
        }

        // 停止当前正在播放的计划任务
        stopScheduledPlay();

        // 立即播放一首
        playRandomBGM();

        // 监听播放完成，然后安排间隔时间后播放下一首
        scheduleNextBGMAfterCompletion();
    }

    /**
     * 停止随机播放 BGM
     */
    public void stopRandomBGM() {
        stopScheduledPlay();
        AudioManager.stopBackgroundMusic();
        currentBGM = null;
        wasPlayingBeforePause = false;
        initializePlayQueue(); // 重置播放队列
    }

    /**
     * 暂停 BGM
     */
    public void pauseBGM() {
        if (isPaused) {
            LoggerManager.Logger("DEBUG", "BGM 已经处于暂停状态");
            return;
        }

        isPaused = true;
        wasPlayingBeforePause = AudioManager.getInstance().isBackgroundMusicPlaying();

        if (wasPlayingBeforePause) {
            AudioManager.getInstance().pauseBackgroundMusic();
            LoggerManager.Logger("INFO", "BGM 已暂停");
        }

        // 停止定时播放
        stopScheduledPlay();
    }

    /**
     * 恢复 BGM
     */
    public void resumeBGM() {
        if (!isPaused) {
            LoggerManager.Logger("DEBUG", "BGM 未处于暂停状态");
            return;
        }

        isPaused = false;

        if (wasPlayingBeforePause) {
            AudioManager.getInstance().resumeBackgroundMusic();
            LoggerManager.Logger("INFO", "BGM 已恢复");
            wasPlayingBeforePause = false;

            // 如果当前音乐还在播放，继续监听完成事件
            if (AudioManager.getInstance().isBackgroundMusicPlaying()) {
                scheduleNextBGMAfterCompletion();
            } else {
                // 如果已经播放完成，立即安排播放下一首
                playRandomBGM();
                scheduleNextBGMAfterCompletion();
            }
        }
    }

    /**
     * 播放随机 BGM
     */
    public void playRandomBGM() {
        if (bgmFiles.isEmpty()) {
            LoggerManager.Logger("WARNING", "没有可用的 BGM 文件");
            return;
        }

        // 如果播放队列为空，重新初始化
        if (playQueue.isEmpty()) {
            LoggerManager.Logger("INFO", "播放队列已空，重新初始化");
            initializePlayQueue();
        }

        // 从队列中取出下一首
        String nextBGM = playQueue.remove(0);
        currentBGM = nextBGM;

        LoggerManager.Logger("INFO", "播放 BGM: " + currentBGM + " (剩余 " + playQueue.size() + " 首)");

        AudioManager.playBackgroundMusic(currentBGM, false);

        // 安排下一首的播放
        scheduleNextBGMAfterCompletion();
    }

    /**
     * 安排下一首 BGM（在当前 BGM 播放完成后等待间隔时间）
     */
    private void scheduleNextBGMAfterCompletion() {
        if (isPaused) {
            LoggerManager.Logger("DEBUG", "BGM 已暂停，不安排下一首");
            return;
        }

        // 停止之前的调度器
        stopScheduledPlay();

        playScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BGM-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // 使用轮询检查音乐是否播放完成
        playScheduler.scheduleAtFixedRate(() -> {
            if (isPaused) {
                LoggerManager.Logger("DEBUG", "BGM 已暂停，停止检查");
                return;
            }

            boolean isPlaying = AudioManager.getInstance().isBackgroundMusicPlaying();

            if (!isPlaying) {
                // 音乐已停止，等待间隔时间后播放下一首
                LoggerManager.Logger("DEBUG", "音乐播放完成，等待间隔时间...");

                try {
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException e) {
                    LoggerManager.Logger("DEBUG", "等待间隔时间被中断");
                    return;
                }

                if (isPaused) {
                    LoggerManager.Logger("DEBUG", "BGM 已暂停，不播放下一首");
                    return;
                }

                // 播放下一首
                LoggerManager.Logger("DEBUG", "间隔时间结束，播放下一首 BGM");
                playRandomBGM();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS); // 每秒检查一次
    }

    /**
     * 停止定时播放
     */
    private void stopScheduledPlay() {
        if (playScheduler != null && !playScheduler.isShutdown()) {
            playScheduler.shutdown();
            playScheduler = null;
        }
    }

    /**
     * 设置 BGM 间隔时间（毫秒）
     */
    public void setInterval(long intervalMillis) {
        this.intervalMillis = intervalMillis;
        com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys.BGM_INTERVAL
                .set(intervalMillis);
        LoggerManager.Logger("INFO", "BGM 间隔时间已更新: " + intervalMillis + " 毫秒");
        // 不重新安排，下次播放时使用新间隔时间
    }

    /**
     * 获取 BGM 间隔时间（毫秒）
     */
    public long getInterval() {
        return intervalMillis;
    }

    /**
     * 获取 BGM 文件列表
     */
    public List<String> getBGMFiles() {
        return Collections.unmodifiableList(bgmFiles);
    }

    /**
     * 检查是否暂停
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 获取当前播放的 BGM
     */
    public String getCurrentBGM() {
        return currentBGM;
    }

    /**
     * 关闭 BGM 管理器
     */
    public void shutdown() {
        stopRandomBGM();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
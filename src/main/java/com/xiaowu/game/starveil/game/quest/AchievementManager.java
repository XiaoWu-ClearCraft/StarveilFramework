package com.xiaowu.game.starveil.game.quest;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.infrastructure.persistence.FileCrypto;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

 
public class AchievementManager {

    private static AchievementManager instance;

    private static final String ACHIEVEMENTS_JSON = "starveil:data/config/achievements.json";
    private static final String DATA_DIR = com.xiaowu.game.starveil.config.GameConstants.DATA_DIR;
    private static final String ACHIEVEMENTS_DATA_FILE = DATA_DIR + File.separator + "achievements.dat";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Achievement> achievements = new HashMap<>();
    private static Set<String> unlockedAchievements = new HashSet<>();

    private AchievementManager() {
        initialize();
    }

    public static AchievementManager getInstance() {
        if (instance == null) instance = new AchievementManager();
        return instance;
    }

     
    public static void unlockAchievement(String achievementId) {
        if (unlockedAchievements.contains(achievementId)) return;
        Achievement ach = achievements.get(achievementId);
        if (ach == null) {
            System.err.println("成就ID不存在: " + achievementId);
            return;
        }
        unlockedAchievements.add(achievementId);
        saveUnlockedAchievements();

         
        NotificationManager.getInstance()
                .showNotification("成就解锁!", ach.name, ach.iconPath, 5);

        System.out.println("成就解锁成功: " + ach.name + " (" + achievementId + ")");
    }

    /**
     * 锁定一个成就（撤销已解锁状态）。
     *
     * <p>给调试用：验证「已解锁 / 未解锁」两条分支时，不必去手改存档文件。
     *
     * @return 是否真的发生了状态变化（本来就没解锁则返回 false）
     */
    public static boolean lockAchievement(String achievementId) {
        if (achievementId == null || !unlockedAchievements.remove(achievementId)) {
            return false;
        }
        saveUnlockedAchievements();
        System.out.println("成就已锁定: " + achievementId);
        return true;
    }

    /** 丢弃内存状态，从磁盘重新读取（调试窗口的「重载文件」）。 */
    public void reloadFromDisk() {
        loadUnlockedAchievements();
        fireChanged();
    }

    // ==================== 变更通知 ====================

    /**
     * 成就状态变化监听（解锁 / 锁定 / 重置）。
     *
     * <p>存在的意义是让调试窗口<b>按需</b>重读 {@code achievements.dat}，
     * 而不是每 500ms 无脑读一次磁盘 + 解密。
     */
    private static final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public static void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public static void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private static void fireChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // 单个监听者出错不应影响其它监听者
            }
        }
    }

     
    public boolean isAchievementUnlocked(String id) { return unlockedAchievements.contains(id); }
    public Map<String, Achievement> getAllAchievements() { return new HashMap<>(achievements); }
    public Set<String> getUnlockedAchievements() { return new HashSet<>(unlockedAchievements); }
    public Map<String, Achievement> getLockedAchievements() {
        Map<String, Achievement> locked = new HashMap<>();
        achievements.forEach((k, v) -> { if (!unlockedAchievements.contains(k)) locked.put(k, v); });
        return locked;
    }
    public double getAchievementProgress() {
        return achievements.isEmpty() ? 0 : (double) unlockedAchievements.size() / achievements.size();
    }
    public String getDataDirectoryPath() { return new File(DATA_DIR).getAbsolutePath(); }

     
    public void resetAllAchievements() {
        unlockedAchievements.clear();
        saveUnlockedAchievements();
        System.out.println("所有成就已重置");
    }

    public void testMultipleAchievements() {
        new Thread(() -> {
            try {
                unlockAchievement("welcome");
                Thread.sleep(1000);
                unlockAchievement("first_blood");
                Thread.sleep(800);
                unlockAchievement("explorer");
            } catch (InterruptedException ignored) {}
        }).start();
    }

     
    private void initialize() {
        ensureDataDirectory();
        loadAchievementsDefinition();
        loadUnlockedAchievements();
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (Exception e) {
            System.err.println("创建data目录失败: " + e.getMessage());
        }
    }

    private void loadAchievementsDefinition() {
        try (InputStream in = ResourceResolver.getResourceAsStream(ACHIEVEMENTS_JSON)) {
            if (in != null) {
                Type type = new TypeToken<Map<String, Achievement>>(){}.getType();
                Map<String, Achievement> loaded = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
                if (loaded != null) achievements = loaded;
            } else {
                // 成就定义由内容项目提供；没有就【留空】。
                //
                // 刻意不回退到内置默认成就：那是示例数据，混进来会污染真实内容，
                // 也让「没配成就」看起来像「配了几个莫名其妙的成就」。
                // 主菜单会据此隐藏成就入口（见 Menu.hasAchievements）。
                System.err.println("未提供成就定义(" + ACHIEVEMENTS_JSON + ")，成就列表留空");
            }
        } catch (Exception e) {
            System.err.println("加载成就定义失败，成就列表留空: " + e.getMessage());
        }
    }

    /**
     * 内置示例成就。
     *
     * <p><b>已不再使用</b>：没提供成就定义时改为留空，不再拿示例数据兜底。
     * 保留方法体只是为了方便以后需要临时造数据时参考。
     */
    @SuppressWarnings("unused")
    private void createDefaultAchievements() {
        achievements.put("first_blood", new Achievement("first_blood", "第一滴血", "完成第一次击杀", "/images/achievements/first_blood.png"));
        achievements.put("welcome", new Achievement("welcome", "欢迎来到游戏", "首次启动游戏", null));
        achievements.put("explorer", new Achievement("explorer", "探索者", "发现所有隐藏区域", "/images/achievements/explorer.png"));
        achievements.put("veteran", new Achievement("veteran", "老玩家", "游戏时间达到10小时", null));
        achievements.put("perfectionist", new Achievement("perfectionist", "完美主义者", "以最高难度完成游戏", "/images/achievements/perfectionist.png"));
        saveAchievementsDefinitionToFile();
    }

    private void saveAchievementsDefinitionToFile() {
        try {
            Path path = Paths.get(DATA_DIR, "achievements_reference.json");
            Files.write(path, gson.toJson(achievements).getBytes());
        } catch (Exception e) {
            System.err.println("保存默认成就参考文件失败: " + e.getMessage());
        }
    }

     
    private void loadUnlockedAchievements() {
        Path path = Paths.get(ACHIEVEMENTS_DATA_FILE);
        if (!Files.exists(path)) return;
        try {
            String json = FileCrypto.loadAndDecrypt(path.toString());
            Type type = new TypeToken<Set<String>>(){}.getType();
            Set<String> loaded = gson.fromJson(json, type);
            if (loaded != null) unlockedAchievements = loaded;
        } catch (Exception e) {
            System.err.println("加载成就数据失败: " + e.getMessage());
        }
    }

    private static void saveUnlockedAchievements() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            String json = gson.toJson(unlockedAchievements);
            FileCrypto.encryptAndSave(ACHIEVEMENTS_DATA_FILE, json);
        } catch (Exception e) {
            System.err.println("保存成就数据失败: " + e.getMessage());
        }
        // 无论落盘成功与否，内存状态都已经变了 —— 监听者要据此刷新
        fireChanged();
    }

     
    public static class Achievement {
        public String id;
        public String name;
        public String description;
        public String iconPath;
        public Achievement() {}
        public Achievement(String id, String name, String description, String iconPath) {
            this.id = id; this.name = name; this.description = description; this.iconPath = iconPath;
        }
    }
}
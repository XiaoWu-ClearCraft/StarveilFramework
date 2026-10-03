package com.xiaowu.game.starveil.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.core.GameUI;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 存档管理器 - 负责游戏存档的保存和加载
 */
public class SaveManager {
    private static SaveManager instance;
    private static final String SAVE_DIR = com.xiaowu.game.starveil.config.GameConstants.DATA_DIR;
    private static final String SAVE_FILE = "save.dat";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 存档数据结构
    public static class SaveData {
        public String worldMapName;  // 地图名称（可能为null）
        public String mapFile;       // 地图文件路径
        public Integer worldMapId;   // 地图ID
        public double playerX;
        public double playerY;
        public double playerHealth; // 玩家当前血量
        public double playerMaxHealth; // 玩家最大血量
        public double playerStamina; // 玩家当前体力值
        public double playerMaxStamina; // 玩家最大体力值
        public double playerMagic; // 玩家当前魔力值
        public double playerMaxMagic; // 玩家最大魔力值
        public String selectedCharacter; // 玩家角色ID（皮肤）
        /**
         * 存档时的章节号。读档后由 {@code ChapterDirector} 从这一章继续。
         * 旧存档没有这个字段时会反序列化成 0，读档逻辑会把它当成「第 1 章」。
         */
        public int currentChapter;
        public List<String> backpackItems; // 背包物品（9个格子，空为""）
        public String handItem; // 手上物品ID
        public List<NPCData> npcs;
        public Set<String> deadNpcIds; // 已死亡的 NPC ID
        public List<EventState> events;
        public Map<String, String> saveVariables; // 存档变量
        public long saveTime;
        public String saveName;

        public SaveData() {
            npcs = new ArrayList<>();
            deadNpcIds = new HashSet<>();
            events = new ArrayList<>();
            saveVariables = new HashMap<>();
            saveTime = System.currentTimeMillis();
        }
    }

    // NPC 数据
    public static class NPCData {
        public String id;
        public double x;
        public double y;
        public String facing;
        public double currentHealth;
        public double maxHealth;
    }

    // 事件状态
    public static class EventState {
        public String id;
        public boolean triggered;
        public boolean enabled;
        public boolean isExecuting;
    }

    // 存档槽位数据
    public static class SaveSlot {
        public int slotIndex;
        public SaveData saveData;
        public boolean isEmpty;

        public SaveSlot(int slotIndex) {
            this.slotIndex = slotIndex;
            this.isEmpty = true;
        }
    }

    private SaveManager() {
        ensureSaveDirectory();
    }

    public static SaveManager getInstance() {
        if (instance == null) {
            instance = new SaveManager();
        }
        return instance;
    }

    /**
     * 确保存档目录存在
     */
    private void ensureSaveDirectory() {
        File saveDir = new File(SAVE_DIR);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    /**
     * 保存当前游戏状态到指定槽位
     * @param slotIndex 槽位索引（0-8）
     */
    public void saveGame(int slotIndex) {
        try {
            // 获取世界地图
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap == null) {
                LoggerManager.Logger("ERROR", "无法获取世界地图");
                return;
            }

            // 获取玩家组件
            World world = GameInstance.getEcsWorld();
            int playerEntity = GameInstance.getPlayerEntityId();
            if (world == null || playerEntity < 0) {
                LoggerManager.Logger("ERROR", "无法获取玩家");
                return;
            }
            Transform pt = world.get(playerEntity, Transform.class);
            Health ph = world.get(playerEntity, Health.class);
            Stamina ps = world.get(playerEntity, Stamina.class);
            Magic pm = world.get(playerEntity, Magic.class);
            PlayerController pc = world.get(playerEntity, PlayerController.class);

            // 获取背包
            com.xiaowu.game.starveil.game.item.Inventory inventory = null;
            GameInstance gi = GameInstance.getCurrentInstance();
            if (gi != null) {
                inventory = gi.getInventory();
            }

            // 创建存档数据
            SaveData saveData = new SaveData();
            // 当前章节号 —— 读档时从这里继续，而不是永远回到第 1 章
            saveData.currentChapter = com.xiaowu.game.starveil.game.story.ChapterDirector
                    .getInstance().currentChapter();
            saveData.worldMapName = worldMap.getCurrentMapName();
            saveData.mapFile = worldMap.getCurrentMapFile();  // 保存地图文件路径
            saveData.worldMapId = worldMap.getCurrentWorldId();
            saveData.playerX = pt != null ? pt.x : 500;
            saveData.playerY = pt != null ? pt.y : 500;
            saveData.playerHealth = ph != null ? ph.current : 100;
            saveData.playerMaxHealth = ph != null ? ph.max : 100;
            saveData.playerStamina = ps != null ? ps.current : 100;
            saveData.playerMaxStamina = ps != null ? ps.max : 100;
            saveData.playerMagic = pm != null ? pm.current : 100;
            saveData.playerMaxMagic = pm != null ? pm.max : 100;
            saveData.selectedCharacter = pc != null ? pc.selectedCharacter : "default";
            saveData.saveName = "存档 " + (slotIndex + 1);

            // 保存背包数据
            if (inventory != null) {
                saveData.backpackItems = inventory.serializeBackpack();
                saveData.handItem = inventory.getHandSlot();
            }

            // 保存 NPC 数据（存活 NPC）
            if (world != null) {
                for (int e : world.view(Npc.class)) {
                    Npc npc = world.get(e, Npc.class);
                    Transform nt = world.get(e, Transform.class);
                    Health nh = world.get(e, Health.class);
                    Sprite ns = world.get(e, Sprite.class);
                    if (npc == null || nt == null || nh == null) continue;
                    NPCData npcData = new NPCData();
                    npcData.id = npc.id;
                    npcData.x = nt.x;
                    npcData.y = nt.y;
                    npcData.facing = ns != null ? ns.facing.name() : Facing.LEFT.name();
                    npcData.currentHealth = nh.current;
                    npcData.maxHealth = nh.max;
                    saveData.npcs.add(npcData);
                }
            }

            // 保存已死亡的 NPC ID
            saveData.deadNpcIds = worldMap.getDeadNpcIds();

            // 保存事件状态
            saveData.events = worldMap.getEventStates();

            // 保存存档变量
            saveData.saveVariables = SaveDataManager.getInstance().exportAll();
            LoggerManager.Logger("DEBUG", "已保存 " + saveData.saveVariables.size() + " 个存档变量");

            // 读取现有存档
            Map<Integer, SaveData> allSaves = loadAllSaves();
            allSaves.put(slotIndex, saveData);

            // 保存到文件
            saveAllSaves(allSaves);

            LoggerManager.Logger("INFO", "游戏已保存到槽位 " + (slotIndex + 1));

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "保存游戏失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从指定槽位加载游戏
     * @param slotIndex 槽位索引（0-8）
     */
    public void loadGame(int slotIndex) {
        try {
            // 读取所有存档
            Map<Integer, SaveData> allSaves = loadAllSaves();
            SaveData saveData = allSaves.get(slotIndex);

            if (saveData == null) {
                LoggerManager.Logger("WARNING", "槽位 " + (slotIndex + 1) + " 没有存档");
                return;
            }

            // 获取世界地图
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap == null) {
                LoggerManager.Logger("ERROR", "无法获取世界地图 - 请先启动游戏");
                return;
            }

            // 玩家（仅校验世界是否存在；玩家本身不在此处恢复）
            World world = GameInstance.getEcsWorld();
            if (world == null) {
                LoggerManager.Logger("ERROR", "无法获取玩家");
                return;
            }

            // 加载地图（这会重新创建 events 列表）
            if (saveData.mapFile != null && !saveData.mapFile.isEmpty()) {
                worldMap.loadFromFile(saveData.mapFile);
                worldMap.setDeadNpcIds(saveData.deadNpcIds);
                worldMap.removeDeadNpcs();
                LoggerManager.Logger("INFO", "已加载地图文件: " + saveData.mapFile);
            } else {
                LoggerManager.Logger("WARNING", "存档中没有地图信息，无法加载地图");
            }

            // 恢复 NPC 状态（位置、朝向、血量）
            for (NPCData npcData : saveData.npcs) {
                int npcEntity = worldMap.findNpcEntity(npcData.id);
                if (npcEntity < 0) continue;
                World npcWorld = worldMap.getEcsWorld();
                if (npcWorld == null) continue;
                Transform nt = npcWorld.get(npcEntity, Transform.class);
                Sprite ns = npcWorld.get(npcEntity, Sprite.class);
                Health nh = npcWorld.get(npcEntity, Health.class);
                if (nt != null) {
                    nt.x = npcData.x;
                    nt.y = npcData.y;
                }
                if (ns != null && npcData.facing != null) {
                    try {
                        ns.facing = Facing.valueOf(npcData.facing);
                        ns.displayFacing = ns.facing;
                        if (ns.facing.isHorizontal()) {
                            ns.lastHorizontalFacing = ns.facing;
                        }
                    } catch (IllegalArgumentException e) {
                        LoggerManager.Logger("WARNING", "无效的朝向: " + npcData.facing);
                    }
                }
                if (nh != null && npcData.maxHealth > 0) {
                    nh.max = npcData.maxHealth;
                    nh.immortal = false;
                    nh.current = Math.max(0, Math.min(nh.max, npcData.currentHealth));
                    nh.dead = nh.current <= 0;
                }
            }

            // 恢复事件状态（必须在加载地图之后，因为 loadFromFile() 会清空并重新创建 events 列表）
            worldMap.setEventStates(saveData.events);

            // 恢复存档变量
            SaveDataManager.getInstance().importAll(saveData.saveVariables);
            LoggerManager.Logger("DEBUG", "已加载 " + saveData.saveVariables.size() + " 个存档变量");

            LoggerManager.Logger("INFO", "已从槽位 " + (slotIndex + 1) + " 加载存档");

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "加载游戏失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从指定槽位加载游戏并启动（用于从主菜单加载）
     * @param slotIndex 槽位索引（0-8）
     * @param gameInstance 游戏实例
     */
    public void loadGameAndStart(int slotIndex, GameInstance gameInstance) {
        try {
            // 读取所有存档
            Map<Integer, SaveData> allSaves = loadAllSaves();
            SaveData saveData = allSaves.get(slotIndex);

            if (saveData == null) {
                LoggerManager.Logger("WARNING", "槽位 " + (slotIndex + 1) + " 没有存档");
                return;
            }

            // 获取世界地图
            WorldMap worldMap = gameInstance.getWorldMapInstance();
            if (worldMap == null) {
                LoggerManager.Logger("ERROR", "无法获取世界地图");
                return;
            }

            // 获取玩家
            World world = GameInstance.getEcsWorld();
            int playerEntity = GameInstance.getPlayerEntityId();
            if (world == null || playerEntity < 0) {
                LoggerManager.Logger("ERROR", "无法获取玩家");
                return;
            }

            // 加载地图（这会重新创建 events 列表）
            if (saveData.mapFile != null && !saveData.mapFile.isEmpty()) {
                worldMap.loadFromFile(saveData.mapFile);
                worldMap.setDeadNpcIds(saveData.deadNpcIds);
                worldMap.removeDeadNpcs();
                LoggerManager.Logger("INFO", "已加载地图文件: " + saveData.mapFile);
            } else {
                LoggerManager.Logger("WARNING", "存档中没有地图信息，无法加载地图");
            }

            // 设置玩家位置
            // 如果存档中的玩家位置是默认值（500, 500），则使用 spawn 点
            Transform pt = world.get(playerEntity, Transform.class);
            if (pt == null) return;
            if (saveData.playerX == 500 && saveData.playerY == 500 && worldMap.getWorldSpawn() != null) {
                pt.x = worldMap.getWorldSpawn().x;
                pt.y = worldMap.getWorldSpawn().y;
                LoggerManager.Logger("INFO", "存档中玩家位置为默认值，已设置到 spawn 位置");
            } else {
                pt.x = saveData.playerX;
                pt.y = saveData.playerY;
            }

            // 恢复玩家血量
            Health ph = world.get(playerEntity, Health.class);
            if (ph != null) {
                ph.max = saveData.playerMaxHealth > 0 ? saveData.playerMaxHealth : 100;
                ph.immortal = false;
                ph.current = Math.max(0, Math.min(ph.max, saveData.playerHealth));
                ph.dead = ph.current <= 0;
            }

            // 恢复玩家体力值
            Stamina pst = world.get(playerEntity, Stamina.class);
            if (pst != null) {
                pst.current = Math.max(0, Math.min(pst.max, saveData.playerStamina));
                pst.isExhausted = pst.current < pst.minToSprint;
            }

            // 恢复玩家魔力值
            Magic pm = world.get(playerEntity, Magic.class);
            if (pm != null) {
                pm.current = Math.max(0, Math.min(pm.max, saveData.playerMagic));
                pm.isDepleted = pm.current <= 0;
            }

            // 恢复角色选择
            if (saveData.selectedCharacter != null) {
                com.xiaowu.game.starveil.game.ecs.factory.EntityFactory
                        .applyPlayerCharacter(world, playerEntity, saveData.selectedCharacter);
            }

            // 恢复背包数据
            GameInstance gi = gameInstance;
            if (gi != null && gi.getInventory() != null) {
                com.xiaowu.game.starveil.game.item.Inventory inventory = gi.getInventory();
                if (saveData.backpackItems != null) {
                    inventory.deserializeBackpack(saveData.backpackItems);
                }
                if (saveData.handItem != null && !saveData.handItem.isEmpty()) {
                    inventory.setHandSlot(saveData.handItem);
                }
                // 更新手上物品显示
                GameUI.getInstance().updateHandSlot(inventory.getHandSlot());
            }

            // 恢复 NPC 状态（位置、朝向、血量）
            for (NPCData npcData : saveData.npcs) {
                int npcEntity = worldMap.findNpcEntity(npcData.id);
                if (npcEntity < 0) continue;
                World npcWorld = worldMap.getEcsWorld();
                if (npcWorld == null) continue;
                Transform nt = npcWorld.get(npcEntity, Transform.class);
                Sprite ns = npcWorld.get(npcEntity, Sprite.class);
                Health nh = npcWorld.get(npcEntity, Health.class);
                if (nt != null) {
                    nt.x = npcData.x;
                    nt.y = npcData.y;
                }
                if (ns != null && npcData.facing != null) {
                    try {
                        ns.facing = Facing.valueOf(npcData.facing);
                        ns.displayFacing = ns.facing;
                        if (ns.facing.isHorizontal()) {
                            ns.lastHorizontalFacing = ns.facing;
                        }
                    } catch (IllegalArgumentException e) {
                        LoggerManager.Logger("WARNING", "无效的朝向: " + npcData.facing);
                    }
                }
                if (nh != null && npcData.maxHealth > 0) {
                    nh.max = npcData.maxHealth;
                    nh.immortal = false;
                    nh.current = Math.max(0, Math.min(nh.max, npcData.currentHealth));
                    nh.dead = nh.current <= 0;
                }
            }

            // 恢复事件状态（必须在加载地图之后，因为 loadFromFile() 会清空并重新创建 events 列表）
            worldMap.setEventStates(saveData.events);

            // 恢复存档变量
            SaveDataManager.getInstance().importAll(saveData.saveVariables);
            LoggerManager.Logger("DEBUG", "已加载 " + saveData.saveVariables.size() + " 个存档变量");

            // 确保玩家不在非法位置
            ensurePlayerInValidPosition(worldMap);

            // 从存档记录的章节继续（旧存档没有这个字段 → 0 → 当成第 1 章）
            int chapter = saveData.currentChapter > 0
                    ? saveData.currentChapter
                    : com.xiaowu.game.starveil.game.story.ChapterDirector.FIRST_CHAPTER;
            com.xiaowu.game.starveil.game.story.ChapterDirector director =
                    com.xiaowu.game.starveil.game.story.ChapterDirector.getInstance();
            // 章节模式与世界是否加载是绑定的（VISUAL_NOVEL 不加载世界），
            // 读档时世界已经按「第 1 章的模式」建好了；如果存档章节的模式不同，
            // 这里只给出警告，避免用错模式的世界去跑章节。
            com.xiaowu.game.starveil.game.story.ChapterMode savedMode = director.resolveMode(chapter);
            if (savedMode != director.resolveMode(com.xiaowu.game.starveil.game.story.ChapterDirector.FIRST_CHAPTER)) {
                LoggerManager.Logger("WARNING",
                        "存档章节 " + chapter + " 的模式 " + savedMode
                                + " 与启动时的章节模式不同，世界状态可能不匹配");
            }
            director.gotoChapter(chapter);
            LoggerManager.Logger("INFO", "剧情将从第 " + chapter + " 章继续");

            LoggerManager.Logger("INFO", "已从槽位 " + (slotIndex + 1) + " 加载存档并启动游戏");

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "加载游戏失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取所有存档槽位
     * @return 存档槽位数组（9个槽位）
     */
    public SaveSlot[] getAllSaveSlots() {
        SaveSlot[] slots = new SaveSlot[9];
        Map<Integer, SaveData> allSaves = loadAllSaves();

        for (int i = 0; i < 9; i++) {
            SaveSlot slot = new SaveSlot(i);
            SaveData saveData = allSaves.get(i);
            if (saveData != null) {
                slot.saveData = saveData;
                slot.isEmpty = false;
            }
            slots[i] = slot;
        }

        return slots;
    }

    /**
     * 检查指定槽位是否有存档
     * @param slotIndex 槽位索引（0-8）
     * @return 是否有存档
     */
    public boolean hasSaveData(int slotIndex) {
        Map<Integer, SaveData> allSaves = loadAllSaves();
        return allSaves.containsKey(slotIndex);
    }

    /**
     * 删除指定槽位的存档
     * @param slotIndex 槽位索引（0-8）
     */
    public void deleteSave(int slotIndex) {
        try {
            Map<Integer, SaveData> allSaves = loadAllSaves();
            allSaves.remove(slotIndex);
            saveAllSaves(allSaves);
            LoggerManager.Logger("INFO", "已删除槽位 " + (slotIndex + 1) + " 的存档");
        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "删除存档失败: " + e.getMessage());
        }
    }

    /**
     * 加载所有存档
     */
    private Map<Integer, SaveData> loadAllSaves() {
        try {
            File saveFile = new File(SAVE_DIR, SAVE_FILE);
            if (!saveFile.exists()) {
                return new HashMap<>();
            }

            byte[] encryptedData = java.nio.file.Files.readAllBytes(saveFile.toPath());
            String json = FileCrypto.loadAndDecrypt(saveFile.getAbsolutePath());

            if (json == null || json.isEmpty()) {
                return new HashMap<>();
            }

            Type type = new TypeToken<Map<Integer, SaveData>>(){}.getType();
            Map<Integer, SaveData> saves = gson.fromJson(json, type);

            return saves != null ? saves : new HashMap<>();

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "加载存档失败: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 确保玩家在合法位置（不在空气墙内）
     */
    private void ensurePlayerInValidPosition(WorldMap worldMap) {
        if (worldMap == null) return;
        World world = GameInstance.getEcsWorld();
        int playerEntity = GameInstance.getPlayerEntityId();
        if (world == null || playerEntity < 0) return;
        Transform player = world.get(playerEntity, Transform.class);
        if (player == null) return;

        double playerX = player.x;
        double playerY = player.y;
        double playerWidth = player.width;
        double playerHeight = player.height;

        // 检查玩家是否在空气墙内
        if (worldMap.isBlockedByAirWall(playerX, playerY, playerWidth, playerHeight)) {
            LoggerManager.Logger("WARNING", "玩家在非法位置（空气墙内），正在调整位置");

            // 优先使用地图出生点
            if (worldMap.getWorldSpawn() != null) {
                player.x = worldMap.getWorldSpawn().x;
                player.y = worldMap.getWorldSpawn().y;
                LoggerManager.Logger("INFO", "玩家位置已重置到地图出生点");
            } else {
                // 如果没有配置 spawn 点，尝试找到最近的合法位置
                double[] validPos = findNearestValidPosition(worldMap, playerX, playerY, playerWidth, playerHeight);
                if (validPos != null) {
                    player.x = validPos[0];
                    player.y = validPos[1];
                    LoggerManager.Logger("INFO", "玩家位置已调整到合法位置: (" + validPos[0] + ", " + validPos[1] + ")");
                } else {
                    // 使用默认位置
                    player.x = 500;
                    player.y = 500;
                    LoggerManager.Logger("INFO", "玩家位置已重置到默认位置 (500, 500)");
                }
            }
        }
    }

    /**
     * 查找最近的合法位置
     */
    private double[] findNearestValidPosition(WorldMap worldMap, double startX, double startY, double width, double height) {
        // 搜索半径
        double searchRadius = 200;
        double step = 10;

        // 从中心向外搜索
        for (double radius = step; radius <= searchRadius; radius += step) {
            // 检查圆周上的点
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                double testX = startX + radius * Math.cos(radians);
                double testY = startY + radius * Math.sin(radians);

                // 检查是否在边界内
                if (!worldMap.isPlayerOutOfBounds(testX, testY, width, height)) {
                    // 检查是否在空气墙内
                    if (!worldMap.isBlockedByAirWall(testX, testY, width, height)) {
                        return new double[]{testX, testY};
                    }
                }
            }
        }

        return null;
    }

    /**
     * 保存所有存档
     */
    private void saveAllSaves(Map<Integer, SaveData> saves) {
        try {
            String json = gson.toJson(saves);
            File saveFile = new File(SAVE_DIR, SAVE_FILE);
            FileCrypto.encryptAndSave(saveFile.getAbsolutePath(), json);
            LoggerManager.Logger("DEBUG", "存档数据已加密保存");
        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "保存存档失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 格式化保存时间
     */
    public static String formatSaveTime(long timestamp) {
        Date date = new Date(timestamp);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
}
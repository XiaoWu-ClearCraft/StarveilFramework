# 剧情开发指南

本指南覆盖「怎么写内容」：内容由哪些部分组成、章节与剧情脚本怎么写、动画键怎么配。

内容如何接入框架、打包与分发，见 [构建指南](build-guide.md)。

> **不建议更改框架源码。** 框架提供能力，内容提供数据与脚本。

## 目录

1. 内容概览
2. 章节系统
3. 剧情脚本创作
4. 动画键与玩法模式

## 内容概览

本仓库是**框架**，不含任何游戏内容。这一页说明内容由哪些部分组成、各自放哪、
以及框架提供的入口。

> **不建议更改框架源码。** 内容与框架的边界是清晰的：框架提供能力，
> 内容提供数据与脚本。需要新能力时优先考虑插件与注入点，
> 直接改框架会让你在框架升级时合并不了。

具体如何把内容接入、打包、分发，见 [构建指南](build-guide.md)。

---

### 内容由什么组成

| 部分 | 位置（内容项目内） | 说明 |
|---|---|---|
| 初始化入口 | `com.xiaowu.game.starveil.content.init.init` | **唯一硬约定**，见下 |
| 章节脚本 | `content/chapter<N>/Chapter<N>.java` | 命名强制，见下 |
| 地图 | `assets/starveil/data/worlds/*.json` | 世界尺寸、空气墙、NPC、出生点 |
| 物品定义 | `assets/starveil/data/items/items.json` | |
| 成就定义 | `assets/starveil/data/config/achievements.json` | 缺失则成就列表留空、主菜单隐藏成就入口 |
| 任务 / 提示 / 致谢 | `assets/starveil/data/config/*.json` | |
| 贴图 | `assets/starveil/textures/**` | |
| 音频 | `assets/starveil/sounds/**` | |
| 字体 | `assets/starveil/fonts/**` | |
| 角色贴图配置 | `assets/starveil/data/entities/player.json` | 动画键写法见下 |

资源路径**不用写类型**：调用方本来就知道自己在加载什么
（立绘、图标、菜单背景 → 贴图；字体 → 字体；音乐 → 音频；关卡/配置 → 数据），
类型由代码补上。所以下面三种写法等价，按顺手的来：

```java
// 命名空间 + 类型目录下面的路径（推荐，一眼看出是哪个资源根的）
s.say("???", "早上好~", null, "starveil:character/normal/relaxed.png");
// 相对路径（属于 starveil 命名空间）
s.image("backgrounds/void-fog.png");
// 自己写类型（需要精确控制或跨类型时）
s.image("starveil:textures/backgrounds/void-fog.png");
```

三者的解析结果分别是：

```
starveil:character/normal/relaxed.png  → /assets/starveil/textures/character/normal/relaxed.png
backgrounds/void-fog.png               → /assets/starveil/textures/backgrounds/void-fog.png
```

规则：

- **路径里自己写了类型**（`starveil:textures/…`）就以你写的为准 —— 那是明确指定；
- 否则按调用点的类型补类型段（贴图/音频/字体/数据/文案）；
- **后缀可以省**：会按类型试常见后缀（贴图 `.png/.jpg/.jpeg/.webp`、音频 `.mp3/.ogg/.wav`、
  字体 `.ttf/.otf`、数据与文案 `.json`），找到才用；
- 相对路径（不带命名空间）默认属于 `starveil` 命名空间；写 `xxx:...` 就是 `xxx` 命名空间的事，
  见下。

#### 命名空间：谁的资源

`starveil:` 是**引擎与游戏内容**的命名空间，映射到 `assets/starveil/` —— 它是刻意留着的：
换个名字就换一个资源根，插件因此可以带自己的资源而不和游戏内容撞路径。

```java
// 插件加载时框架已按插件 id 自动注册：assets/<插件id>/…
Starveil.resources().registerNamespace("myplugin");        // → /assets/myplugin
Starveil.resources().registerNamespace("plug", "/assets/shared/assets");   // 自定义根目录

Starveil.resources().getURL("myplugin:textures/icon.png"); // → /assets/myplugin/textures/icon.png
Starveil.resources().namespaces();                        // 已注册的命名空间
```

规则与「数据键」的命名空间一致：`starveil` 是**引擎保留**的（不给注册 / 注销），
插件用自己的名字（建议就用插件 id）。类型注入对每个命名空间都生效
（`myplugin:icon.png` 在贴图调用点 → `/assets/myplugin/textures/icon.png`）。

命名空间里第二段**不是**已知类型时，按「命名空间根下的普通路径」处理，
所以插件可以有自己的一套目录结构。未注册的命名空间会直接报错，并在日志里列出已注册的名字。

> 泛用入口（`Starveil.resources().openStream(...)`、`ResourceResolver` 这类**不知道类型**的地方）
> 没法补类型：那里的路径要么写相对路径让框架推断，要么把类型写全
> （`starveil:character/...` 在泛用入口下会当成 `assets/starveil/character/...`，日志会提示）。

---

### 初始化入口

框架启动最早期会探测并调用：

```
com.xiaowu.game.starveil.content.init.init
```

需要静态 `init()`（或静态 `main(String[])`）。这是内容**唯一**的非框架初始化点，
用来声明本作的外观、语言与起始章节。写法见[构建指南](build-guide.md#内容需要提供什么)。

框架对没有声明的项一律**静默降级**（纯黑 / 系统字体 / 不播音频 / 系统图标），
不会报错，也不会替你猜。

**游戏名也在这里声明**：框架是通用的，在内容声明之前它只知道自己在跑一个「游戏」，
默认名是 `My Game`。

```java
ContentConfig.setGameName("My Game");     // 默认值就是它
// ContentConfig.setWindowTitle("My Game"); // 想整串自定义窗口标题再放开
```

声明之后这个名字会出现在：窗口标题、`{TITLE}` / `{window.title}` 占位符、
`s.dialog(...)` 的说话人（旁白）、主菜单与暂停菜单的标题、教程欢迎语。
不声明则整局都显示 `My Game` —— 这也是「框架自带内容为空」的一部分。

---

### 章节与世界的加载时机

**去哪张图，永远由章节自己决定** —— 框架不猜。声明 `NORMAL` 的章节，
第一件事就是自己把世界加载起来：

```java
public class Chapter1 implements StoryChapter {
    private static final String WORLD = "starveil:data/worlds/first.json";

    @Override public ChapterMode mode() { return ChapterMode.NORMAL; }

    @Override public void write(StoryScript s) {
        s.enterWorld(WORLD);   // ← 先加载世界
        s.say("霁雾", "到了。");  // ← 这行一定在世界亮起来之后才执行
    }
}
```

**`s.enterWorld(...)` 是阻塞的，而且会等到亮幕动画结束才返回。**
这就是「淡入和对话不同时开始」的保证：它返回时画面已经亮了，
后面的台词不可能出现在黑屏上。

### 从主界面 / 上一章切进来的时间线

```
点开始游戏（或上一章结束）
  → 落幕（等动画结束）
  → 加载页面：黑幕 + 加载指示器
  → 控制权交给本章脚本，脚本开始跑
  → 本章调 s.enterWorld(...)：挂载世界 → 停顿 → 亮幕（等动画结束）
  → 之后的对话与演出
```

注意**控制权在「加载页面出现之后」就已经在本章手里了**，
所以「加载哪张图」完全由本章的 `enterWorld(...)` 决定。

| 章节声明 | 框架做什么 |
|---|---|
| `NORMAL` 且当前已有世界 | 什么都不做：接着用当前世界（要换图自己 `enterWorld`） |
| `NORMAL` 且当前没有世界 | 准备好加载页面，然后交权 —— 由章节 `enterWorld` 完成加载 |
| `VISUAL_NOVEL` | 卸下世界（如果之前有），脚本从干净的黑幕开始，通常紧接着 `s.image(...)` 铺背景 |

**没有世界时不显示 HUD**（血条 / 体力条 / 背包槽都围着「地图上有个玩家」存在），
这一点由框架统一拦，不需要内容操心。

### 章节之间的过场

一章结束后进入下一章时，框架先**落幕**再把控制权交给下一章
（不卸下世界 —— 卸不卸由下一章的 `mode()` 决定，见上表）：

```
上一章结束 → 落幕（等动画）→ 下一章脚本开跑（画面是加载页面）
```

两种情况会跳过过场，不白白黑一次屏：

- 下一章也要世界、而当前已经有世界（接着用就行）；
- 上一章是纯视觉小说（本来就在黑幕上）；
- 特殊键 `starveil:chapter_fade` 被设为 `false`（见 [数据键](data-keys.md)）。

> **教程需要世界。** 在没有世界时启动教程（`TutorialManager.startTutorial()`）
> 会记一条 ERROR 并忽略本次请求 —— 教程要求玩家在地图上走动，
> 而教程又会锁住剧情推进，真让它启动起来会双双卡死。

---

### 章节

命名**强制**：

```
com.xiaowu.game.starveil.content.chapter<N>.Chapter<N>
```

不合规会在解析时直接报错并指出期望的类名。

- 章节的 `mode()` 声明这一章的**起始状态**：`NORMAL`（平面）或 `VISUAL_NOVEL`（纵向）
- 世界由**章节自己**决定：调用 `s.enterWorld(path)` 时才加载，框架不会替你挂图
- 起始章节号由 `content.init.init` 通过 `ChapterDirector.setStartChapter(n)` 指定，默认 1

详见 [章节系统](#章节系统)。

---

### 剧情脚本

脚本体内可以阻塞（跑在专属剧情线程上），写法见 [剧情脚本创作](#剧情脚本创作)。

---

### 动画键

```
<方向>[:<变体>][:<效果>][:<角度>]
```

例如 `LEFT`、`LEFT:WALK`、`LEFT:LENGTHWAYS`、`LEFT:LENGTHWAYS:WALK:90`。
支持 `@同级键` 复用配置值。详见 [动画键与玩法模式](#动画键与玩法模式)。

---

### 成就

成就**定义与解锁时机都由内容决定**，框架不预设任何成就 ID：

- 定义：`assets/starveil/data/config/achievements.json`（`id → {id, name, description, iconPath}`）
- 解锁：在内容代码里调用 `AchievementManager.unlockAchievement("你的id")`，
  例如在章节脚本里按剧情进度解锁
- ID 未定义时解锁会记 `ERROR` —— 用它来发现自己拼错了成就 ID

缺这份定义文件时成就列表留空、主菜单隐藏成就入口，不会报错。

> ⚠ **不要在 `content.init.init()` 里解锁**：解锁要弹通知，而那一刻渲染引擎还没建好。
> 最早也要等到章节脚本开始运行。框架已把通知失败降级成一条 WARNING，
> 不会因此崩掉，但成就通知也就看不到了。

---

### 事件广播

框架在关键时机广播事件（菜单显示、游戏开始、章节切换、存档、设置开关、
语言切换、教程完成），**谁关心谁监听，没有监听者时广播就是空操作**：

```java
// 在 content.init.init() 里注册，之后每次进主菜单都会收到
LifecycleEvents.onMenuShown(e -> AchievementManager.unlockAchievement("welcome"));

// 只想第一次进游戏时做点什么
LifecycleEvents.onceMenuShown(e -> { /* ... */ });

// 章节切换时
LifecycleEvents.onChapterChanged(e ->
        LoggerManager.Logger("INFO", "进入第 " + e.chapter() + " 章"));
```

完整事件清单、时机上的注意点、以及怎么广播自己的事件，见
[生命周期事件](lifecycle-events.md)。

---

### 本地化

框架自带 `assets/starveil/lang/zh_cn.json`（框架级文案），内容可以：

- **替换**整份文件：`I18n.registerLanguageResource("zh_cn", "starveil:lang/my.json")`
- **注入/覆盖**个别文案：`I18n.inject("zh_cn", "framework.name", "雾隐星阑")`

可选语言列表由内容通过 `ContentConfig.addLanguage(code, 显示名)` 提供；
**不提供时设置界面不显示语言切换**。

---

### 相关文档

- [构建指南](build-guide.md) —— 如何把内容接入框架、打包、分发
- [章节系统](#章节系统)
- [剧情脚本创作](#剧情脚本创作)
- [动画键与玩法模式](#动画键与玩法模式)

## 章节系统

说明**章节的生命周期**：怎么被加载、怎么切换、怎么声明运行模式。

> 剧情**写法**（`say` / `choose` / 循环分支等）见 `docs/story-authoring.md`。
> 本文只讲章节的调度与模式。

---

### 1. 命名规范（强制）

章节类必须严格匹配：

```
com.xiaowu.game.starveil.content.chapter{N}.Chapter{N}
```

例如：

```
com.xiaowu.game.starveil.content.chapter1.Chapter1      ✅
com.xiaowu.game.starveil.content.chapter12.Chapter12    ✅
com.xiaowu.game.starveil.content.Chapter2               ❌ 少一层包
com.xiaowu.game.starveil.content.chapter1.Script1       ❌ 类名前缀不对
com.xiaowu.game.starveil.content.chapter1.Chapter1$Inner ❌ 内部类
```

**不符合规范会被直接拒绝并抛异常**，不再静默跳过。

> 为什么这么严：旧实现是读地图 JSON 的 `chapter` 字段、用字符串**猜**类名
> （`chapter1` / `Chapter1` / FQCN 三种格式轮着试），猜不到就
> `Logger("WARNING", "找不到章节类，跳过章节加载")` 然后什么都不发生。
> 过章节死活不触发却毫无线索，排查成本极高。现在错就错在明面上。

---

### 2. 章节模式

章节自己声明要不要加载世界：

```java
public class Chapter1 implements StoryChapter {

    @Override
    public ChapterMode mode() {
        return ChapterMode.NORMAL;   // 默认值
    }
    ...
}
```

| 模式 | 行为 |
|---|---|
| `NORMAL`（默认） | 加载完整世界、创建玩家、注册 ECS 系统管线。玩家能在场景里走动，剧情通过地图事件触发。 |
| `VISUAL_NOVEL` | **不加载世界**、不创建玩家、不注册 ECS 系统。屏幕纯黑，背景由章节自己用 `s.image(path)` 铺。 |

`mode()` 会在**加载世界之前**被读取（`GameInstance.initialize`），
所以实现里**不要做任何有副作用的初始化**。

#### 纯视觉小说章节示例

```java
public class ChapterPrologue implements StoryChapter {

    @Override public String id() { return "prologue"; }

    @Override public ChapterMode mode() { return ChapterMode.VISUAL_NOVEL; }

    @Override public void write(StoryScript s) {
        s.image("starveil:textures/backgrounds/void-fog.png");
        s.narrate("雾很浓。");
        s.say("霁雾", "你终于来了。");
        s.hideImage();
        s.nextChapter();          // 交给下一章
    }
}
```

---

### 3. 章节推进

**章节与世界解耦**：不再由「进了哪张地图」决定跑哪一章，
而是从**第 1 章**开始、由章节代码自行决定何时进下一章。

```java
// 本章结束后进入下一章
s.nextChapter();

// 本章结束后跳到指定章节
s.gotoChapter(5);
```

- 请求**不会立即生效** —— 当前 `write()` 跑完、收尾动作（收立绘、恢复操作权等）
  都执行完之后，`ChapterDirector` 才启动下一章。
- 这也保证**同一时刻只有一章在跑**。
- 游戏启动时 `GameInstance.initialize()` 末尾会调
  `ChapterDirector.getInstance().start()`，默认从第 1 章开始。

`ChapterDirector` 的几个可用点：

| 方法 | 用途 |
|---|---|
| `currentChapter()` | 当前章节号（存档会记录它） |
| `resolveMode(n)` | 查询第 n 章的模式；解析失败退化成 `NORMAL`，不会让游戏进不去 |
| `resolve(n)` | 解析并校验章节类，不合规直接抛异常 |
| `reset()` | 新游戏 / 回主菜单时重置到第 1 章 |

---

### 4. 剧情暂停（选择性冻结）

**剧情期间整个世界默认冻结** —— 玩家、NPC、体力恢复等全部停下，
而且**不会弹暂停界面**（与按 ESC 的暂停菜单是两件事）。

由 `ChatManager.blockGameLayer()` / `unblockGameLayer()` 驱动，所以：

- 章节开始时自动冻结；
- `s.awaitTutorial()` 会走 `giveControl()` → `unblockGameLayer()`，
  **教程练习阶段会自动恢复**，玩家照常能走动；
- 章节结束时自动恢复。

#### 让某些系统继续跑

对应「除非代码里明确指定了某些元素，如 NPC 运动」：

```java
s.keepRunningDuringStory(NpcSystem.class);
```

- 可以一次传多个：`s.keepRunningDuringStory(NpcSystem.class, NpcDeathSystem.class)`
- `s.clearStoryPauseExemptions()` 清掉全部豁免
- 表现层的 `SpriteSyncSystem` **已经默认放行**，不需要也不应该重复声明
  （否则被豁免的 NPC 动了、画面却不动）

#### 剧情期间被跳过的东西

除了 ECS 之外，游戏循环里这些也会在剧情期间跳过，避免「点着对话就把地图事件点出来」：

- 地图事件检查 `worldMap.checkEvents`
- 交互提示 `checkAndShowPrompts`
- 拾取提示 `pickupPrompt.update`
- F 键交互（拾取 / 使用手上物品）

**教程与致谢名单不受影响**，照常更新。

---

### 5. 运行时模式切换（NORMAL → 视觉小说）

切换章节时，`ChapterDirector` 会**自动**把运行模式调整到章节声明的那一种 ——
章节作者不需要关心「现在世界加载了没有」。

从 **NORMAL 切到 VISUAL_NOVEL** 时是这样（整个过程可等待，动画结束才继续）：

```
1. lockControls(LOCK_MAP_TRANSITION)
2. 落幕                          worldMap.curtainDown()      ← 等动画结束
3. 卸下世界                      worldMap.unloadWorld()
   - 清空地图、销毁全部实体、连玩家视图也摘掉
   - playerEntity = -1，worldLoaded = false，视口变纯黑
   - HUD 隐藏（没有世界就没有血条的意义）
4. 停顿 220ms（给渲染管线时间）
5. 亮幕                          worldMap.curtainUp()        ← 等动画结束
6. unlockControls(LOCK_MAP_TRANSITION)
```

对比 `WorldMap.switchMap()`：少了「`initialize()` + `loadFromFile()` + 设置玩家位置」，
其余步骤一一对应，所以过场观感完全一致。

黑幕拉开后底下是空的（视口黑底），由视觉小说层接着演 —— 章节可以立刻
`s.image(path)` 铺背景。

#### 手动触发

一般不需要，但也可以直接调：

```java
GameInstance.getCurrentInstance().enterVisualNovelMode();   // 返回 CompletableFuture
GameInstance.getCurrentInstance().isWorldLoaded();
```

---

### 6. 从视觉小说切回世界：`s.enterWorld(...)`

反方向**不靠引擎自动恢复**，而是由章节自己决定回到哪个世界：

```java
@Override public ChapterMode mode() { return ChapterMode.VISUAL_NOVEL; }

@Override public void write(StoryScript s) {
    s.image("starveil:textures/backgrounds/void-fog.png");
    s.narrate("雾散了。");
    s.hideImage();

    s.enterWorld("starveil:data/worlds/wu-home.json");   // ← 过场结束后已站在屋里
    s.say("霁雾", "我们到了。");
}
```

**为什么让章节决定而不是引擎自动恢复**：引擎不知道「该回到哪张图」，
硬要自动恢复就得额外维护世界状态快照（什么时候抓、抓多少、失效怎么办）。
而章节天然知道自己要去哪 —— 把选择权交给它，整条链路都简单了。

`enterWorld` 内部走的是同一套可等待的过场：

```
lockControls → 落幕(等) → 停顿 → mountWorld() → 停顿 → 亮幕(等) → unlockControls
```

**它返回时「世界已挂好」且「幕布已经拉开」**（地图加载、玩家创建、
法阵/拾取提示/调试 UI 重建、相机居中），所以紧接着的对话不会出现在黑屏上 ——
这正是「淡入和对话不同时开始」的实现方式。

> **`mountWorld()` 与游戏启动时建世界是同一条代码路径**，所以不会出现
> 「启动能跑、中途切回来缺东西」这种漂移。

#### `mode()` 描述的是章节的「起始状态」

```java
// 这一章开始时没有世界（引擎会自动卸下），中途自己 enterWorld 进入
@Override public ChapterMode mode() { return ChapterMode.VISUAL_NOVEL; }
```

所以典型的来回横跳可以这样写：

| 章节 | `mode()` | 说明 |
|---|---|---|
| Chapter1 | `NORMAL` | 启动时加载世界 |
| Chapter2 | `VISUAL_NOVEL` | 切进来时**自动卸下世界**，纯对话 |
| Chapter3 | `VISUAL_NOVEL` | 开场仍是纯 VN，中途 `s.enterWorld(...)` 进入某张图 |
| Chapter4 | `NORMAL` | 世界已在，无需切换 |

---

### 7. 存档

存档会记录 `currentChapter`，读档后由 `ChapterDirector` 从那一章继续，
并且会**自动**把模式调整到该章节声明的那一种（见 §5）。
旧存档没有这个字段（反序列化成 0）会被当成第 1 章。

## 剧情脚本创作

本指南说明怎么用新的剧情 API 写章节。核心思路一句话：

> **章节就是一段普通的、阻塞式的 Java 代码，跑在全游戏唯一一条剧情线程上。**

---

### 1. 最小示例

```java
public class Chapter2 implements StoryChapter {

    public Chapter2() {
        StoryScripts.bind(this, "author_chapter2");   // 地图 JSON 里的 event[].id
    }

    @Override
    public String id() {
        return "chapter2";
    }

    @Override
    public void write(StoryScript s) {
        s.say("???", "你终于来了。");
        s.say("霁雾", "跟我来。");

        int answer = s.choose("霁雾", "准备好了吗?", List.of("准备好了", "再等等"));
        if (answer == 1) {
            s.say("霁雾", "……那我等你。");
            return;
        }

        s.image("starveil:textures/backgrounds/main-menu.png");
        s.say("霁雾", "就是这里。");
        s.hideImage();
    }
}
```

不需要 `new Thread`，不需要 `insertChain`，不需要锚点和返回步骤。

---

### 2. 运行模型

```
地图事件（FX 线程）
   └─ StoryScripts.bind 注册的监听器
        └─ StoryScheduler.submit(...)  ──►  排队
                                              │
                              ┌───────────────┴────────────────┐
                              │   starveil-story（唯一一条线程） │
                              │   write(script) 顺序执行        │
                              │   每个 UI 方法阻塞到玩家做完     │
                              └───────────────┬────────────────┘
                                              │ Platform.runLater
                                              ▼
                                         JavaFX 渲染线程
```

要点：

| 事项 | 说明 |
| --- | --- |
| 线程数 | 固定 1 条（daemon），与章节数量无关 |
| 脚本互斥 | 队列串行执行，不会两段剧情同时抢对话框 |
| 操作权 | 脚本运行期间游戏层被挡住；用 `giveControl()` / `takeControl()` 临时交还 |
| 取消 | 读档 / 回主菜单 / 退出时 `StoryScripts.resetRuntime()` 一次性清干净 |
| 异常 | 脚本抛异常会被记录，操作权仍会归还，不影响后续章节 |

---

### 3. API 速查

#### 对话

| 方法 | 说明 |
| --- | --- |
| `say(speaker, text)` | 显示一句对话，等玩家点掉 |
| `say(speaker, text, voicePath)` | 带配音 |
| `say(speaker, text, voicePath, standee)` | 同时切换立绘；`""` 表示收起 |
| `say(speaker, text, voicePath, standee, offsetX, offsetY)` | 立绘带显示偏移（半身立绘，见下） |
| `say(…, standee, offsetX, offsetY, scale)` | 再带缩放 |
| `narrate(text)` | 旁白 |
| `multi(speaker, part1, part2, …)` | 合成一次点击推进的多段文本 |
| `standee(path)` / `hideStandee()` | 只切立绘，不显示对话 |
| `standee(path, offsetX, offsetY)` / `standee(path, offsetX, offsetY, scale)` | 只切立绘并指定显示样式 |
| `image(path)` / `hideImage()` | 剧情图 |

##### 立绘显示半身（偏移 / 缩放）

立绘默认贴着**画布左下角**、高度是画布的 **68%**（全身）。想只露上半身，
就把立绘往下推一点，让下半身落到画布外 —— 逻辑画布自带裁剪，多出来的部分不会画出来：

```java
// 路径直接写相对路径即可（类型与后缀都由框架推断）
String jiwu = "character/normal/relaxed.png";

// 全身立绘 → 只露上半身：往下推 320（逻辑像素）
s.say("霁雾", "早上好~", null, jiwu, 0, 320);

// 嫌人物小，同时放大 40%（半身特写更常见）
s.say("霁雾", "好久不见。", null, jiwu, 0, 320, 1.4);

// 只换立绘、不带对话也一样
s.standee(jiwu, 0, 320);
s.standee(jiwu, 0, 0, 1.0);   // 回到默认（全身）
```

规则四条：

- **单位是逻辑画布像素**（1920×1080），X 向右为正、Y 向下为正；负数就是往左上挪
  （`offsetX` 为负能把立绘挪出画面左边一部分）。
- **样式是粘性的**：设过一次就一直生效，后面只换立绘路径不会重置；想回到默认显式传
  `0, 0, 1`。所以「半身框」可以在章节开头定一次。
- **对话框会自动让位**：让位宽度按立绘实际的右边界算，所以把立绘横着挪走/放大之后，
  对话框不会压在立绘上；整张立绘挪到画布外时就不让位了。
- **让位有上限**（默认画布宽的一半，可调）：立绘再宽也不会把对话框挤成一条。
  超过上限的那部分会让立绘与对话框重叠 —— 这时框架把立绘**横向居中**到预留区中间
  （左右超出的部分一样多，纵向仍按你给的 `offsetY`），并保证**对话框压在立绘之上**，
  所以重叠也读得到字。

```java
// 想让对话框让出更多 / 更少（占画布宽的比例，0~0.8，运行期改也生效）
ContentConfig.setStandeeReservedMaxRatio(0.35);
```

#### 交互

| 方法 | 返回值 |
| --- | --- |
| `ask(prompt)` | 玩家输入的字符串（取消为 `""`） |
| `ask(prompt, simulatedText)` | 同上，输入框预填文本 |
| `askDate(prompt)` | 日期输入结果 |
| `choose(prompt, option…)` / `choose(speaker, prompt, option…)` | 选项下标，取消为 `-1` |
| `map()` / `map(allowedMaps, allowedButtons)` | 地图按钮 id，取消为 `-1` |

#### 提示与等待

| 方法 | 说明 |
| --- | --- |
| `notify(title, message)` / `notify(title, message, icon, seconds)` | 右上角通知 |
| `popup(title, message)` | 游戏内弹窗（不等待） |
| `dialog(message)` / `dialog(title, message)` | 系统提示框，等玩家点确定（原生 MessageBox，由剧情线程发起，不卡渲染） |
| `delay(millis)` | 等待若干毫秒 |
| `await(signal)` / `await(signal, timeoutMillis)` | 等待剧情信号 |
| `awaitUntil(condition)` | 每 50ms 轮询条件 |
| `awaitTutorial()` | 等教程完成，期间自动把操作权交还玩家 |

#### 状态

| 方法 | 存储位置 |
| --- | --- |
| `flag(key)` / `setFlag(key, bool)` / `counter(key)` / `setCounter` / `addCounter` | 存档内变量（随存档保存） |
| `data(key)` / `setData(key, value)` | 全局配置（跨存档） |
| `playerName()` / `setPlayerName(name)` | 玩家名字 |

#### 时间与网络

| 方法 | 返回值 |
| --- | --- |
| `timestamp()` | 现在的时间戳（毫秒，**可信时间**） |
| `dateTime()` | 现在的本地日期时间（`LocalDateTime`） |
| `hour()` | 现在几点，0-23 |
| `time(pattern)` / `formatTime(timestamp, pattern)` | 时间字符串，如 `s.time("HH:mm")` |
| `dateTimeOf(timestamp)` / `hourOf(timestamp)` | 把存下来的时间戳转回日期时间 / 小时 |
| `isTimeTrusted()` | 可信时间是不是真的可信（首次启动 + 没网时为 `false`） |
| `isOnline()` | 现在能不能上互联网 |

**可信时间**指「联网要回来的基准时间戳 + 单调时钟推进」：玩家在游戏运行期间改系统时钟
也影响不了它（跨重启改才有影响，重新联网同步即可修正）。存档里记时刻用 `timestamp()`，
显示用 `time(...)`。想拿现成的分档问候语自己判断小时即可：

```java
int h = s.hour();
String hello = h < 5  ? "还没睡呀？"
             : h < 11 ? "早上好"
             : h < 14 ? "中午好"
             : h < 18 ? "下午好"
             :          "晚上好";
s.say("霁雾", hello + "~");
```

> 内容与插件也能用同一套能力（`Starveil.time()` / `Starveil.network()`），
> 见 [联网 / 时间 / IP 属地](network.md)。剧情脚本里直接用 `s.xxx()` 更省事。

#### 外挂与调试

| 方法 | 说明 |
| --- | --- |
| `run(Runnable)` / `call(Supplier)` | 在剧情线程上执行任意代码 |
| `onFx(Runnable)` / `onFx(Supplier)` | 在 JavaFX 线程上同步执行（需要碰 UI 时用） |
| `log(message)` | 写入剧情日志，DebugWindow「剧情」页可见 |

#### 屏幕特效（遮罩层）

遮罩层是覆盖在整屏之上的一层透明窗口，**随游戏启动自动显示**，不需要手动创建。

想关掉它，在 `DataManager` 里设：

```java
DataManager.setBoolean(GameConstants.SETTING_OVERLAY_ENABLED, false);   // overlay.enabled
```

（默认开启。关掉后故障花屏 / 伪蓝屏 / 降帧等特效全部不可用。）

这些方法**只改变遮罩层显示的内容**，不改动任何系统 / 驱动设置。

| 方法 | 说明 |
| --- | --- |
| `frameThrottle("50%")` / `frameThrottle("30")` | 降帧保持：把遮罩层下方的画面捕获到遮罩层并按目标帧率刷新，压低可见帧率 |
| `clearFrameThrottle()` | 关闭降帧，遮罩层重新透出实时画面 |
| `glitch(0.8)` / `glitch(0.8, x, y, w, h)` | 花屏（像素级撕裂 / 错位 / RGB 色散 / 扫描线） |
| `clearEffects()` | 清除花屏 |
| `blueScreen()` / `dismissBlueScreen()` | 伪蓝屏 |

**捕获的是什么**：遮罩层覆盖整屏，所以它下面不止是游戏窗口，还有任务栏和后面的其它窗口。
Windows 上走 GDI `BitBlt` 抓整个屏幕，因此降帧和花屏作用在「真实的屏幕画面」上。
其它平台（或 Windows 10 2004 以前）自动退化为只捕获游戏窗口内容。

> 抓屏前会先用 `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` 把遮罩层自己
> 从捕获中排除，否则会抓到自己刚贴上去的那张图。这个 API 需要 Windows 10 2004+；
> 不支持时会打一条 WARNING 并降级。

**注意：降帧只影响「画面变化的频率」。** 捕获的是 1:1 的屏幕截图，所以画面
本身静止时（例如站着不动看对话），30fps 和 60fps 看起来是一样的。要在
文字逐字显示、角色走动、通知滑出这类**有运动**的地方才看得出来。想快速
确认效果，直接把目标压到 `"4"`。

**鼠标仍然可用**：一旦贴上不透明截图，遮罩层会自动加 `WS_EX_TRANSPARENT`
把点击让给下层游戏窗口，所以降帧期间照样能点对话、点物品。
清除降帧后自动恢复。

**光标也会一起降帧**：光标由 DWM 合成在**所有窗口之上**，抓屏拿不到它、
遮罩层也盖不住它。所以做法是：抓屏时把光标按「抓屏那一刻」的位置画进画面，
同时把真实光标隐藏起来 —— 屏幕上唯一可见的光标就变成冻结图里的那个，
跟着同一个帧率一起跳。清除降帧时自动恢复真实光标。

> 副作用：目标帧率很低时，画出来的光标会明显滞后于你的实际操作位置
> （4fps 时最多滞后 250ms）。点击判定用的仍然是真实位置，所以会出现
> 「看到的光标和点到的位置对不上」——这本来就是卡顿该有的样子。
> 这个行为跟着「是否有截图在显示」自动开关，不会残留。

**性能**：抓屏在 JavaFX 线程上做。2560×1600 单帧约 23ms，30fps（帧间隔 33ms）
下占七成 —— 撑得住，但也会连带把游戏拖慢（这本来就符合"降帧"的意图）。
如果嫌重，不改代码即可降开销：

```bash
-Dstarveil.overlay.captureMaxDim=1920     # 代价：画面会略软
```

日志里会报 `抓屏耗时=xx.x ms`，超过帧间隔 60% 时会额外给一条 WARNING。

**自检**：`run --args="-no-admin -debug -overlay-test"` 会依次把画面压到
30 → 10 → 4 fps、跑一段花屏、再恢复，并把每一步的实际状态写进日志，
用来确认整屏抓取、捕获排除、鼠标穿透是否都生效。

降帧的硬规则：**只允许降低，不允许提升**（基准是游戏内部的实测帧率）。

```java
s.frameThrottle("30");     // 30fps
s.frameThrottle("4");      // 4fps，效果一眼可见（调试时用这个确认）
s.frameThrottle("50%");    // 当前实测帧率的 50%
s.frameThrottle(null);     // 非法/缺省 -> 只输出 WARNING，不生效
s.frameThrottle("120");    // 游戏内部实测约 900fps，所以 120 是合法降帧；
                           // 但它高于显示器刷新率，视觉上没有变化
```

连续花屏：

```java
for (int i = 0; i < 24; i++) {
    s.glitch(0.7 + i * 0.01);
    s.delay(45);
}
s.clearEffects();
```

---

### 4. 文本占位符

剧情文本里可以直接写：

| 占位符 | 替换为 |
| --- | --- |
| `{NAME}` | 玩家当前名字 |
| `{TITLE}` / `{window.title}` | 游戏标题 |

旧的富文本标记照常可用：`<more>`（点击推进分段）、`<green>…</green>`（染色）等。

---

### 5. 等别的系统发生事情

任何地方都可以触发信号，脚本侧 `await`：

```java
// 脚本里
s.await("boss.defeated");

// 别处（任意线程 / 任意回调）
EventCallbackManager.getInstance().signal("boss.defeated");
```

信号是**一次性闩锁**：先 `signal` 后 `await` 也不会漏，`await` 会立即通过。
需要复用时调用 `EventCallbackManager.resetSignal(name)`。

内置信号：

| 常量 | 触发时机 |
| --- | --- |
| `StorySignals.TUTORIAL_COMPLETED` | 新手教程完成（`TutorialManager`） |
| `StorySignals.WORLD_LOADED` | 世界加载完成（`EventCallbackManager.triggerWorldLoaded`） |

---

### 6. 让玩家自由活动

```java
s.say("霁雾", "你去把那个箱子打开。");

s.giveControl();                    // 交还操作权：显示 HUD、恢复输入
s.await("chapter2.chest_opened");   // 脚本仍在这里等
s.takeControl();                    // 收回操作权，继续演出

s.say("霁雾", "干得不错。");
```

`awaitTutorial()` 就是这套写法的封装。

---

### 7. 注册方式

**方式一：挂在地图事件上（推荐）**

地图 JSON：

```json
{
  "event": [
    { "on": "join", "id": "author_chapter2", "condition": {} }
  ]
}
```

章节构造函数里绑定：

```java
StoryScripts.bind(this, "author_chapter2");
```

事件触发时脚本自动入队，事件状态由框架处理。重复注册是安全的——
同一个 `章节id#事件id` 只会保留最后一次注册。

**方式二：手动跑一次**

```java
StoryScripts.run(new Chapter2());
```

---

### 8. 从旧写法迁移

| 旧写法 | 新写法 |
| --- | --- |
| `worldMap.addEventListener(id, (e, a) -> new Thread(() -> { … }).start())` | `StoryScripts.bind(chapter, id)` |
| `new SeqBuilder().addDialog(...).build()` | `s.say(...)` |
| `.insertChain(obj -> { if (...) return new SeqBuilder()...; })` | 直接写 `if` / `switch` |
| `.addReturnStep("anchor")` + `.setAnchor("anchor")` | 直接写 `while` / `continue` |
| `.withNextCallback(_ -> doSomething())` | 下一行直接 `doSomething()` |
| `EventCallbackManager.registerTutorialCompletedCallback(_ -> new Thread(...))` | `s.awaitTutorial()` |
| `sleep(ms)` / `LockSupport.parkNanos` | `s.delay(ms)` |
| `opsManager.showInfo(title, msg)` | `s.dialog(title, msg)` |
| `SaveDataManager.getInt("x", 0)` / `setInt` | `s.counter("x")` / `s.addCounter("x", 1)` |

`DialogSequence` / `SeqBuilder` 仍然保留但已标记 `@Deprecated`，仅为兼容旧内容。

---

### 9. 常见错误

| 现象 | 原因 |
| --- | --- |
| `IllegalStateException: StoryScript 的交互方法只能在剧情线程调用` | 在 FX 线程或普通回调里直接调了 `s.say(...)`。用 `StoryScripts.run(...)` 投递脚本，或在回调里 `EventCallbackManager.signal(...)` 唤醒脚本 |
| 脚本永久卡住 | 在等一个永远不会触发的信号。用 `await(signal, timeoutMillis)` 加超时，或在退出路径调用 `StoryScripts.resetRuntime()` |
| 章节整章重播 | 存档里该地图事件的 `triggered` 状态没有落盘；确认章节是通过 `StoryScripts.bind` 而不是手动监听注册的 |

## 动画键与玩法模式

### 1. 动画键语法

```
<方向>[:<变体>][:<效果>][:<角度>]
```

| 段 | 必填 | 取值 | 含义 |
|---|---|---|---|
| 方向 | 是 | `LEFT` / `RIGHT` / `UP` / `DOWN` | 该贴图用于哪个朝向 |
| 变体 | 否 | 任意非纯数字字符串（如 `LENGTHWAYS`） | 省略 = 普通（NORMAL）模型集 |
| 效果 | 否 | `IDLE` / `WALK` / `SKID` | 省略 = 该朝向的**基础姿态** |
| 角度 | 否 | 整数度数 | 重力方向专用贴图；省略或 0 = **自动旋转**基础贴图 |

实例：

```
LEFT                        普通模式的 LEFT 基础姿态
LEFT:WALK                   普通模式的 LEFT 行走
LEFT:LENGTHWAYS             重力模式的 LEFT 基础姿态
LEFT:LENGTHWAYS:90          重力方向 90° 时的 LEFT 专用贴图
LEFT:LENGTHWAYS:WALK        重力模式的 LEFT 行走
LEFT:LENGTHWAYS:WALK:90     重力方向 90° 时的 LEFT 行走专用贴图
```

#### 纯数字段一定是角度

`LEFT:90` 里的 90 既可能是变体也可能是角度，必须有一条硬规则消歧。
取「**纯数字 = 角度**」后：

- `LEFT:LENGTHWAYS:90` → 变体 + 角度
- `LEFT:LENGTHWAYS:WALK` → 变体 + 效果

代价是**变体名不能是纯数字**（`registerVariant("90")` 会被拒绝），
这与「变体名不能占用 `WALK` 等内置效果名」是同一类约束 —— 都是为了让语法保持无歧义。

角度归一化到 `[0,360)`，且 **0° 等价于不写角度**（`LEFT:WALK:0` 与 `LEFT:WALK` 是同一个键）。

#### 配置写法

`player.json` 的 `texture` 对象、NPC def 的 `texture` 都支持：

```json
{
  "default": {
    "texture": {
      "LEFT": "starveil:textures/player/left.png",
      "LEFT:WALK": "starveil:textures/player/left_walk.png",
      "LEFT:LENGTHWAYS": "starveil:textures/player/left_prone.png",
      "LEFT:LENGTHWAYS:WALK": "starveil:textures/player/left_prone_walk.png"
    }
  }
}
```

旧的 `idle` / `walk` / `default` / 裸朝向写法继续有效，无需迁移。

### 1.1 `@` 同级引用

同一个 JSON 对象里，值可以写成 `"@某个同级键"` 来复用：

```json
{
  "LEFT": "starveil:textures/player/left.png",
  "LEFT:LENGTHWAYS": "@LEFT",
  "LEFT:WALK": "starveil:textures/player/left_walk.png",
  "LEFT:LENGTHWAYS:WALK": "@LEFT:WALK"
}
```

- 只在**同级**查找，不跨层
- 支持链式引用 `A→B→C`，一路跟到底
- **循环引用会被检出并报错**（返回 null），不会无限递归把栈打爆
- 引用目标不存在 → 按「没有配置」处理（返回 null，走正常的贴图回退）

实现见 `infrastructure/persistence/ConfigReferences.java`，全部取值点都已接入。

#### 为什么变体名不得占用 `WALK` 等内置名

`LEFT:WALK` 是**二段键**，靠「第二段是不是内置效果名」来区分「效果」与「变体」。
若允许存在一个叫 `WALK` 的变体，这个键就同时有两种解释，语法立刻失去无歧义性。
因此 `AnimState` 的名字是引擎保留名：

- `AnimKey.parse` 遇到 `LEFT:WALK:WALK` 直接抛异常；
- `AnimKeyRegistry.registerVariant("WALK")` 同样抛异常。

同理，玩法模式声明的变体（`LENGTHWAYS`）也是引擎自带变体，第三方不得抢注。

### 2. 读取 API

```java
AnimKey key = AnimKey.parse("LEFT:LENGTHWAYS:WALK");
key.facing();   // Facing.LEFT
key.variant();  // "LENGTHWAYS"
key.effect();   // AnimState.WALK

// 实体贴图查找（内部会按变体 × 效果 × 朝向逐级回退）
AnimationSheet sheet = sprite.resolveSheet(Facing.LEFT);
```

回退顺序（`AnimKeyRegistry.resolve`），**变体整体优先于效果**：

```
(变体, 效果) → (变体, 基础姿态) → (普通, 效果) → (普通, 基础姿态)
```

例如某变体只定义了 `LEFT:LENGTHWAYS`，那么 `LEFT:LENGTHWAYS:WALK` 会命中它，
而**不会**跳到普通模型集的 `LEFT:WALK`。

效果各自的回退：

| 请求 | 回退链 |
|---|---|
| `IDLE` | `IDLE` → 基础姿态 |
| `WALK` | `WALK` → `IDLE` → 基础姿态 |
| `SKID` | `SKID` → 基础姿态（**不复用行走贴图**） |

### 3. 玩法模式

世界有两种玩法模式，由**地图文件**声明（不是章节声明 —— 玩法是地图的天然属性，
同一章可以先后进不同玩法的图）：

```json
{
  "gameplayMode": "GRAVITY",
  "gravityDirection": 0
}
```

| 模式 | 含义 |
|---|---|
| `NORMAL`（缺省） | 俯视玩法，无重力 |
| `GRAVITY` | 把 `y` 轴当作高度，实体沿 `gravityDirection` 自动下落 |

`gravityDirection` 角度约定：**`0°` 向下**，顺时针为正 ——
`90°` 向右、`180°` 向上、`270°` 向左。任意角度都支持（例如 `45` 斜向下），
方向向量为 `(sin θ, cos θ)` —— 屏幕坐标 y 轴向下，所以是 `(sin, cos)` 而非 `(cos, sin)`。

无法识别的 `gameplayMode` 会打 WARNING 并回退到 `NORMAL`。
地图一解析出这两个字段就写进 ECS 世界，随后创建的实体（玩家、NPC）在生成时
就按当前模式决定要不要挂 `Gravity` 组件 —— 所以**重力图里的 NPC 也会下落**。
想让它飘着（漂浮物、半空装饰），生成后把它的 `Gravity.enabled` 置 `false`。

##### 两种形状：矩形与多边形

```json
// 1) 矩形（老写法，仍然支持）
{ "x": 0, "y": 860, "x_to": 450, "y_to": 884 }

// 2) 多边形：任意顶点，凸凹都行 —— 斜坡、三角台阶、洞穴都用它
{ "polygon": [[450,740],[900,700],[900,756],[450,756]] }
```

矩形其实就是「4 个顶点的多边形」，所以碰撞只有一条代码路径。

**没有圆形。** 圆看着好用，实际做地形基本用不上：地面、台阶、斜坡、洞穴口
都是有边的形状；圆既拼不出边，判定又多一套（最近点距离），
还让「站立面」这个概念对一半形状失效（圆没有水平顶面）。
要圆形台子就用多边形画 —— 多写几个顶点而已：

```json
// 看着像圆的七边形台子
{ "polygon": [[1000,200],[1050,155],[1110,175],[1120,235],[1075,280],[1015,260],[990,215]] }
```

> 老地图里的 `circle` 写法现在会被**明确拒绝并打 WARNING**（不会静默忽略）——
> 静默忽略等于原地少一块碰撞，玩起来就是「这里怎么掉下去了」。

任何形状都能带贴图，会**平铺在形状内部并用形状裁剪**：

```json
{ "polygon": [[0,860],[200,700],[400,860]],
  "texture": "starveil:textures/tiles/rock.png",
  "textureSize": 50 }
```

任何形状也都能加 `"oneWay": true` 变成**单向平台**：

```json
{ "x": 300, "y": 640, "x_to": 480, "y_to": 656, "oneWay": true }
```

> **为什么不做「用一堆小矩形拼斜坡」**：既难写又会在接缝处抖。
> 而且外接矩形近似会让斜坡旁边的空白区域也挡人 —— 角色明明能从那儿过去却被拦住，
> 这种 bug 很难解释给玩家听。真实几何判定则不会。

> **台面别做太薄。** `GravitySystem` 单帧最多前进
> `maxFallSpeed * deltaTime ≈ 1200/60 = 20px`，所以 8px 厚的墙会被高速下落直接跨过去
> （表现为「穿过地板掉下去」）。台面给到 16px 以上，或者调小 `Gravity.maxFallSpeed`。
> 下落本身是**子步进**的（每小步最多 2px），所以薄墙不会「半穿」，
> 但整片跨过去仍然会发生 —— 那是判定次数解决不了的问题。

`texture` 是纯视觉的：**没有贴图的形状是隐形的**（这正是「空气墙」这个名字的由来）。

##### 怎么看见这些碰撞体（F3）

按 **F3** 打开调试层，空气墙会按类型描边：

| 颜色 | 含义 |
|---|---|
| **青色** | 实心空气墙（哪个方向都挡） |
| **金色** | 单向平台（只在落下来时接住你） |

两者几何上一模一样，只有「落下来会不会被接住」不同 —— 用同一个颜色等于没显示。
切地图时如果 F3 是开着的，新图的形状会自动接着显示（不需要按两下）。
代码里也可以直接调 `WorldMap.showAirWalls(true)` 或 `WorldMap.getAirWalls()`
（只读视图，返回的正是实际加载进去的形状）。

#### 跳跃

重力模式下按**向上键**（默认 `W`，跟随按键绑定）跳跃：

- **只认「按下的那一瞬间」** —— 按住不放不会连跳；
- **向上、横向都不受单向平台影响** —— 能从下面跳穿一块单向平台；
- 跳起来之后 `W` / `S` **不再**当方向键用，所以按住 `W` 不会一边跳一边往上飘。

起跳初速度与加速度在 {@code Gravity} 组件上：

| 字段 | 默认 | 说明 |
|---|---|---|
| `jumpSpeed` | `700` | 起跳初速度（像素/秒），方向与重力相反 |
| `acceleration` | `1800` | 重力加速度（像素/秒²） |
| `coyoteTime` | `0.10` | **土狼时间**：离开地面后还能起跳的宽容时间（秒） |
| `jumpBuffer` | `0.12` | **跳跃缓冲**：落地前按下的跳跃键最多记住多久（秒） |

跳跃高度约 `v² / (2a) = 700² / 3600 ≈ 136px`。想改手感改这几个即可。

> 注意**速度是带符号的标量**：正 = 沿重力方向（下落），负 = 逆重力（上升）。
> 所以「撞天花板」和「落地」是同一套判定逻辑。

##### 土狼时间与跳跃缓冲

这两个参数是给玩家的「宽容度」，解决的是**按键时机稍微偏一点就被吞掉**的问题：

| | 解决的糟糕体验 | 现在的行为 |
|---|---|---|
| 土狼时间 | 在台子边缘按跳，这一帧其实已经走出边缘，`grounded` 刚好变 `false` → 明明按了却没跳 | 离开地面 0.1 秒内仍算「刚离开地面」，照跳 |
| 跳跃缓冲 | 快落地时提前按跳，落地那一帧又没按到位 → 明明按了却没跳 | 按下的那一下最多记 0.12 秒，落地当帧立刻起跳 |

两者都不会导致二段跳：**一旦真的起跳，缓冲与土狼窗口会一起作废**。
「长按不放」也不会连跳 —— 按键边沿只在真正按下那一下产生一次，
之后按住多久都只是「键还按着」，不会再进缓冲。

剧情脚本想让角色直接跳一下，用 `gravity.requestJump()`（跳过输入缓冲，但仍然受土狼时间约束）。

##### 落地不悬停

下落是**子步进**的：一帧要走多远就拆成不超过 2px 的小步，每一步判一次碰撞，
停在最后一个没被挡住的位置（`GravitySystem.MAX_SUB_STEP`）。

不做子步进会怎样：终端速度下一帧要走 20px，只判「整帧终点」的话，
被挡住时只能原地不动 —— 角色会停在离地面最多 20px 的半空，
随后几帧再一小段一小段挪下来，看起来就是**落地时悬停再抖几下**。
（有回归测试盯着这条：`landingDoesNotHoverOrStutter`。）

#### 单向平台（`"oneWay": true`）

「只从上面挡」的薄板，也就是 2D 平台游戏里的阶梯/浮空台：

| 你的动作 | 结果 |
|---|---|
| 从上面落下来 | **接住**（脚底触到台面就站住） |
| 从下面往上跳 | **穿过去** —— 上升时它等于不存在，只管往上飞 |
| 横着走过去 | **不挡** —— 侧面是空气 |
| 站在上面按**向下键**（默认 `S`） | **穿下去**，落回下面的台子 |

判定细节：

- 台面高度取形状的**最高顶点**。所以单向平台请写成水平的矩形/多边形，
  **斜的单向平台不支持** —— 斜面没有唯一的「台面」，标了 `oneWay` 会得到一条悬空的判定线，
  走在斜面中段会直接穿下去。
- 非竖直重力（`gravityDirection` 不是 `0`）下「水平台面」没有意义，
  单向平台会退化成普通空气墙。
- 按「下」穿下去时会开一个 0.2 秒的窗口（`Gravity.dropThroughTime`），
  窗口内忽略单向平台；窗口结束后脚底已经落到台面下方，之后自然继续往下掉。
- 只有玩家会做方向判定。NPC 与剧情脚本走的仍是 `WorldMap.isBlockedByAirWall`
  （不分方向），对它们来说单向平台等同于普通空气墙。
  想让 NPC 也享受单向语义，把调用换成 `isBlockedSideways` / `isBlockedFalling` 即可。
- 代码里单向平台是个**装饰器**（`AirWall.OneWay`），包住原来的形状 ——
  所以 `wall instanceof AirWall.Poly` 对它是 `false`。
  要拿几何信息就取里层：`((AirWall.OneWay) wall).delegate()`。
- 按 F3 时单向平台是**金色**的（实心墙是青色），见前面的调试层小节。

> **按键为什么分成 `W` 跳、`S` 穿下去**：一键两用（站在单向平台上时 `S` 兼作跳）
> 对玩家是纯粹的惊吓来源。分开之后 `S` 只在真的踩着单向平台时才生效，
> 站在实心地面上按它什么都不发生。

#### 当前重力玩法的边界（别当成 bug）

- **空中也能水平移动**：`PlayerControlSystem` 不看 `grounded`。
  这是有意的 —— 否则跳起来之后完全无法微调落点。
- **斜的单向平台、非竖直重力下的单向平台**：见上一节的说明。
- **实心墙仍然可能「停在墙上方一小段」**：子步进把误差压到 2px 以内，
  但没有做「吸附到表面」—— 那需要知道表面的精确高度，而碰撞判据只是个布尔询问。
  2px 在 60FPS 下看不出来。
- **地图边界**（`width`/`height`）会夹住角色，被夹住时也算落地，所以在底边可以起跳。

也就是说现在拿到的是「自由落体 + 四方向水平移动 + 跳跃 + 单向平台 +
土狼时间 + 跳跃缓冲」这一整套平台游戏手感。

### 4. 惯性与急停

```java
GameplayMode.NORMAL   // 原有俯视玩法，无重力
GameplayMode.GRAVITY  // 把 y 当作 z，竖直方向自动下落
```

#### 代码覆盖

地图是默认来源，代码可以随时覆盖：

```java
gameInstance.getGameplayMode();
gameInstance.setGameplayMode(GameplayMode.GRAVITY);

gameInstance.setGravityDirection(90);   // 精确到角度
gameInstance.getGravityDirection();
```

- 重力模式下**带位置的实体都会获得 `Gravity` 组件**（玩家、NPC、掉落物），
  沿重力方向加速下落，直到被空气墙或地图边界挡住（`grounded`）。
  规则只有这一条 —— 只给玩家挂的话，重力图里的 NPC 会浮在半空。
  碰撞复用玩家移动的同一套判定，因此**不需要新增地形数据**。
- 想飘着就自己挂组件再关掉：`gravity.enabled = false`。
- 模式决定全局模型变体，由 `SpriteSyncSystem` 每帧同步到所有精灵。
- 重力方向存在 ECS 世界上（不是组件里），改方向**不需要重建实体**。
- `GravitySystem` 在 `NORMAL` 下空转，因此可以无条件注册，不必在切模式时增删系统。

#### 贴图与旋转

`SpriteSyncSystem` 每帧按「变体 × 效果 × 重力角度」查表：

| 情况 | 结果 |
|---|---|
| 有该角度的专用贴图（如 `LEFT:LENGTHWAYS:WALK:90`） | 直接用它，**不再旋转** |
| 只有普通贴图 | 用它，并**整体旋转**重力角度 |
| 一张都没有 | 兜底贴图 + 旋转重力角度 |

变体仍然整体优先于角度：某个变体只定义了基础姿态时，
不会因为「另一个模型集有 90° 专用图」而被抢走。

#### 手感参数

在 `Gravity` 组件上（每个实体独立）：`acceleration`（默认 1800 px/s²）、
`maxFallSpeed`（默认 1200 px/s，防止一帧穿过墙体）、`jumpSpeed`（默认 700 px/s）、
`coyoteTime`（默认 0.10s）、`jumpBuffer`（默认 0.12s）、`dropThroughTime`（默认 0.20s）。
`maxFallSpeed` **必须**设置合理，否则高速下落会直接跳过整片空气墙。
另外 `GravitySystem.MAX_SUB_STEP`（2px）控制下落子步进的粒度，见「落地不悬停」。

跳跃与单向平台的完整规则见 [玩法模式 → 跳跃](#跳跃) 与
[玩法模式 → 单向平台](#单向平台oneway-true)。

### 4. 惯性与急停

惯性由数据键控制，**默认开启**：

```java
FrameworkDataKeys.INERTIA.get();     // 默认 true
FrameworkDataKeys.INERTIA.set(false); // 关闭
```

- **开启**：移动带加减速（`PlayerController.accelRate` / `decelRate`），松开方向键会滑行一小段。
- **关闭**：恢复改动前的瞬时启停，且不再触发急停。

> **撞墙就把速度吃掉。** 被挡住的那一轴，速度会**立刻归零**
> （撞空气墙、被地图边界夹住都算）。不能留着 —— 留着的话「撞墙」会变成「蓄力」：
> 疾跑跳起来撞在墙上，位置不动、速度却一直攒着，等落回地面或从墙边滑开，
> 那股攒下来的速度会一次性把玩家推出去（按 `decelRate = 1100` 算能滑两百多像素）。
> 玩家的直觉是「撞上之后力就该没了」。
>
> 只清被挡住的那一轴，所以**斜着撞墙仍然能沿墙滑行**；
> 代价是撞墙那一下不会播急停动画（那本来就是「减速到停」的动画，
> 而撞墙是「瞬间停」）。回归测试：`hittingAWallInMidAirKillsHorizontalMomentum`。

急停（`SKID`）在**疾跑减速到静止**期间播放，可被其他动作打断：

| 事件 | 行为 |
|---|---|
| 疾跑刚结束且仍在滑行 | 进入急停 |
| 速度降到触发时的 15% 以下 | 结束急停 |
| 重新按下方向键 | 立即打断 |
| 开始蓄力 | 立即打断 |
| 惯性关闭 | 永不触发 |

结束阈值取「触发时速度」的**比例**而非绝对值 —— 速度单位取决于
`moveSpeed` 与 `getMovementVector` 的实现约定，写死绝对值会在换单位后悄悄失效。

### 5. 键空间规范

#### 5.1 数据键必须先注册

任何键在使用前都要先声明，声明时写清**命名空间、键名、默认值**；
值的 java 类型决定键的类型。读写只用句柄，不需要在每个调用点重复填默认值。

```java
// 声明一次（通常作为 static final 字段）
public static final DataKey<Integer> KILLS =
        DataManager.defineInt("chapter3", "kills", 0, DataKeyFlag.PER_SAVE);

// 之后到处都用它
KILLS.set(5);
int v = KILLS.get();          // 完整键名 chapter3:kills
```

- 内置键一律带 `starveil:` 前缀：`starveil:cant_exit`、`starveil:inertia`
- 第三方用**自己的命名空间**：`myplugin:some_key`
- `starveil:` 是引擎保留命名空间
- 无前缀的裸名会被拒绝
- 类型不符的写入会被拒绝（数值之间只允许无损拓宽）

`DataKeyFlag.PER_SAVE` 表示这是**存档作用域**的键 ——
既有 `data.dat` 默认值，又能被当前存档改写。
详见 [数据键](data-keys.md)。

> **没有历史迁移。** 框架仍在测试阶段，老存档与老配置直接删掉即可。
> 裸名 `CantExit` 既不是合法键、也不会被映射到 `starveil:cant_exit`。

#### 5.2 存档变量（`SaveDataManager`）

存档变量与数据键共用同一套注册规则，只是加了 `PER_SAVE` 标记：

```java
public static final DataKey<Integer> CHAPTER3_KILLS =
        DataManager.defineInt("chapter3", "kills", 0, DataKeyFlag.PER_SAVE);

CHAPTER3_KILLS.set(5);
int v = CHAPTER3_KILLS.get();   // 实际键: chapter3:kills
```

- 未注册的键**写不进去**，读只会拿到默认值。
- `clear()`（新游戏）清变量值但**保留键注册**：注册是模式，不是数据。

##### 剧情状态键：别写成点号（这是个真踩过的坑）

剧情脚本里的 `s.flag` / `s.bool` / `s.counter` / `s.set` / `s.setCounter` /
`s.addCounter` / `s.setFlag` / `s.data` / `s.setData` 都要求键**已注册**，
且完整键名的分隔符是**冒号**：

```java
// ✗ 点号：在框架看来是个未注册的裸键
if (s.counter("jiwu.patience") >= 3) { ... }

// ✓ 注册一次，之后用句柄
public static final DataKey<Integer> JIWU_PATIENCE =
        DataManager.defineInt("jiwu", "patience", 0, DataKeyFlag.PER_SAVE);

if (s.counter(JIWU_PATIENCE) >= 3) { ... }
s.addCounter(JIWU_PATIENCE, 1);
```

以前这种写法只会打一条 WARNING 然后照常跑：读到的永远是默认值、写入被丢掉，
于是**那个分支永远不会成立**，而脚本看不出任何异常（第一章的
「写错名字三次就放过玩家」就是这么静默失效的）。现在未注册的键会直接抛
`StoryScriptException`，并在消息里点出该注册成什么、以及「点数写错了、
应该是冒号」：

```
剧情状态键 'jiwu.patience' 未注册，无法读取。… 命名空间分隔符是冒号 ':'（不是点号），
看起来你想写的是 'jiwu:patience'。 请先用 DataManager.defineInt("ns", "name", 默认值,
DataKeyFlag.PER_SAVE) 声明，再用注册出来的键：s.counter(KEY) / s.addCounter(KEY, 1)…
```

收 `DataKey` 的那组重载是首选：命名空间与类型都由声明处保证，写不错。
两种重载读写的是同一个变量，所以 `s.counter(KEY)` 与 `s.counter("jiwu:patience")` 等价。

还有一种更省事的写法：用 `SaveKey` 值对象承载「命名空间 + 名字」两个字段。
之所以不做成两个 `String` 参数，是因为本类已有
`getString(String key, String defaultValue)`，再叠加
`getString(String namespace, String name)` 会因为参数类型完全相同而无法重载。

```java
SaveDataManager.SaveKey key = new SaveDataManager.SaveKey("chapter3", "123");
```


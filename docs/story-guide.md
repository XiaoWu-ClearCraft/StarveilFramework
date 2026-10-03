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
| 成就定义 | `assets/starveil/data/config/achievements.json` | 缺失则成就列表留空 |
| 任务 / 提示 / 致谢 | `assets/starveil/data/config/*.json` | |
| 贴图 | `assets/starveil/textures/**` | |
| 音频 | `assets/starveil/sounds/**` | |
| 字体 | `assets/starveil/fonts/**` | |
| 角色贴图配置 | `assets/starveil/data/entities/player.json` | 动画键写法见下 |

资源路径统一用命名空间写法：`starveil:<类型>/<路径>`，
例如 `starveil:textures/icons/app-icon.png`。

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

从 **NORMAL 切到 VISUAL_NOVEL** 时，走的是<b>和切换地图完全相同的过场</b>，
唯一的区别是<b>不加载新地图</b>：

```
1. lockControls(LOCK_MAP_TRANSITION)
2. 渐入黑幕                      worldMap.fadeToBlack()
3. 停顿 500ms
4. 卸下世界                      worldMap.unloadWorld()
   - 清空地图、销毁全部实体、连玩家视图也摘掉
   - playerEntity = -1，worldLoaded = false，视口变纯黑
5. 停顿 300ms
6. 渐出黑幕                      worldMap.fadeFromBlack()
7. unlockControls(LOCK_MAP_TRANSITION)
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

`enterWorld` 内部走的是与 `switchMap` 相同的过场：

```
lockControls → fadeToBlack → 500ms → mountWorld() → 300ms → fadeFromBlack → unlockControls
```

它返回时世界已经挂好（地图加载、玩家创建、法阵/拾取提示/调试 UI 重建、
相机居中），章节直接接着往下写即可。

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
| `narrate(text)` | 旁白 |
| `multi(speaker, part1, part2, …)` | 合成一次点击推进的多段文本 |
| `standee(path)` / `hideStandee()` | 只切立绘，不显示对话 |
| `image(path)` / `hideImage()` | 剧情图 |

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

```java
GameplayMode.NORMAL   // 原有俯视玩法，无重力
GameplayMode.GRAVITY  // 把 y 当作 z，竖直方向自动下落
```

#### 由地图声明（默认来源）

**纵向 / 平面模式默认由地图定义**，代码只在需要时覆盖。地图 JSON 加两个字段即可：

```json
{
  "name": "gravity-test",
  "gameplayMode": "GRAVITY",
  "gravityDirection": 90
}
```

| 字段 | 缺省 | 说明 |
|---|---|---|
| `gameplayMode` | `NORMAL` | `NORMAL` = 平面（无重力）；`GRAVITY` = 纵向（有重力） |
| `gravityDirection` | `0` | 重力方向，单位**度**。0° 向下，顺时针为正 |

无法识别的 `gameplayMode` 会打 WARNING 并回退到 `NORMAL`。

`mountWorld` 在地图加载后读取这两个字段并写入 ECS 世界，随后给玩家挂上 `Gravity` 组件。

#### 重力方向约定

```
0°   → 下 (+y)      90°  → 右 (+x)
180° → 上 (-y)      270° → 左 (-x)
```

方向向量为 `(sin θ, cos θ)` —— 屏幕坐标 y 轴向下，所以是 `(sin, cos)` 而非 `(cos, sin)`。

任意角度都支持（例如 45° 斜向下），重力沿该方向加速下落。

#### 代码覆盖

地图是默认来源，代码可以随时覆盖：

```java
gameInstance.getGameplayMode();
gameInstance.setGameplayMode(GameplayMode.GRAVITY);

gameInstance.setGravityDirection(90);   // 精确到角度
gameInstance.getGravityDirection();
```

- 重力模式下玩家会获得 `Gravity` 组件，沿重力方向加速下落，直到被空气墙或地图边界挡住（`grounded`）。
  碰撞复用玩家移动的同一套判定，因此**不需要新增地形数据**。
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
`maxFallSpeed`（默认 1200 px/s，防止一帧穿过墙体）。
`maxFallSpeed` **必须**设置合理，否则高速下落会直接跳过整片空气墙。

### 4. 惯性与急停

惯性由 `DataManager` 的特殊键控制，**默认开启**：

```java
DataManager.isInertiaEnabled();               // 默认 true
DataManager.setBoolean(GameConstants.INERTIA_KEY, false);  // 关闭
```

- **开启**：移动带加减速（`PlayerController.accelRate` / `decelRate`），松开方向键会滑行一小段。
- **关闭**：恢复改动前的瞬时启停，且不再触发急停。

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

#### 5.1 特殊键（`SpecialKeys`）

「特殊键」指会被当前存档覆盖的全局配置键（既有 data.dat 默认值，又能被存档改写）。

- 内置键一律带 `starveil:` 前缀：`starveil:cant_exit`、`starveil:inertia`
- 第三方注册**必须**带自己的命名空间：`myplugin:some_key`
- `starveil:` 是引擎保留命名空间，第三方不得占用
- 无前缀的裸名会被拒绝

历史遗留的裸名 `CantExit` 由迁移表兜住，配置加载时自动改写成 `starveil:cant_exit`，
老玩家的「禁止退出」设置不会丢。

#### 5.2 存档变量（`SaveDataManager`）

非特殊键会自动补上章节前缀，且**必须提前登记**：

```java
SaveDataManager.SaveKey kills = new SaveDataManager.SaveKey("chapter3", "123");
SaveDataManager.getInstance().registerKey(kills);   // 必须先登记

SaveDataManager.getInstance().setScopedInt(kills, 5);
int v = SaveDataManager.getInstance().getScopedInt(kills, 0);   // 实际键: chapter3:123
```

- 特殊键（如 `starveil:inertia`）保持自身，**不加**章节前缀。
- 未登记的键直接抛异常 —— 把「拼错键名」从「静默丢数据」变成「启动即报错」。
  存档变量一旦写错名字，玩家只会看到进度莫名消失。
- `clear()`（新游戏）清变量值但**保留键登记**：登记是模式，不是数据。

之所以用 `SaveKey` 值对象而不是两个 `String` 参数：本类已有
`getString(String key, String defaultValue)`，再叠加
`getString(String namespace, String name)` 会因为参数类型完全相同而无法重载。


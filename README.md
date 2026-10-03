# Starveil

**一个 Java / JavaFX 的「视觉小说 + 2D 探索」游戏引擎。**

框架只提供引擎能力，**不含任何游戏内容**：没有章节、地图、贴图、字体、音频。
游戏内容属于**另一个项目**，通过框架预留的接口接进来 —— 见 [如何开发游戏](#如何开发游戏)。

> **基于本框架开发游戏时，请不要修改框架源码。**
> 你需要的每个扩展点都是预留好的（内容初始化入口、资源重定向、数据键注册、
> 插件与注入点）。一旦改了框架，就失去「框架可独立升级」这个前提 ——
> 以后框架发新版，你的改动会变成合并不了的补丁。
> 详见 [构建指南](docs/build-guide.md)。

框架单独运行会**拒绝启动**并提示「缺少游戏内容」。这不是故障，是刻意设计：
与其让你对着一个玩不了的空壳猜测少了什么，不如明确告诉你。

---

## 功能

### 启动与生命周期

- 可插拔的启动参数处理器（`-debug` / `-no-admin` / `-no-content` 等），
  插件也能注册自己的参数。
- 单实例锁、异常关闭检测、权限提升、平台支持性检查。
- **无资源降级**：内容什么都不提供也能启动 —— 字体回退系统字体、
  背景纯黑、音频静默、窗口用系统默认图标。想自检这条路径：

  ```bat
  gradlew.bat run --args="-no-admin -debug -no-content"
  ```

  该模式下主菜单可显示但**禁止开始游戏**，成就列表留空。

### 内容与框架分离

- **内容初始化入口** `com.xiaowu.game.starveil.content.init.init`：
  框架在启动最早期探测并调用它，这是内容唯一的非框架初始化位置。
- **资源重定向**：框架代码里写的默认资源路径会被引到内容指定的路径，
  因此框架那几十处硬编码调用一行都不用改。
- **命名空间资源**：`starveil:<类型>/<路径>` → `assets/starveil/<类型>/<路径>`，
  类型含 `textures` / `fonts` / `sounds` / `data` / `lang`。
- **数据键注册制**：键必须先声明（命名空间、键名、默认值、类型、作用域），
  默认值只有一份，类型不符的写入会被拒绝。见 [数据键](docs/data-keys.md)。

### 章节与剧情

- **章节调度**：`ChapterDirector` 按章节类驱动，起始章节可由内容指定。
- **章节模式**：`NORMAL`（有世界，可自由行动）与 `VISUAL_NOVEL`（纯对话）。
- **阻塞式剧情脚本** `StoryScript` —— 剧情作者唯一需要接触的 API：
  每个交互方法阻塞到玩家做完为止，于是 `while` / `if` / `try-finally`
  直接就是剧情控制流，不需要维护「步骤索引 / 插入位置 / 回调时机」三套心智模型。
- 打字机文本、选项分支、输入询问、聊天记录（含跨章节保留开关）、
  屏幕特效（故障花屏 / 伪蓝屏 / 降帧 / 终端文本）、通知与弹窗。

### 世界与游戏系统

- 自研 ECS（实体 / 组件 / 系统）与渲染管线，固定逻辑画布 + 等比缩放。
- 2D 探索：重力、碰撞、惯性加减速与急停、动画键机制、可交互物。
- 物品与背包、容器、拾取提示、任务系统、成就系统。

### 存档、设置与安全

- **存档变量**（随存档）与**全局配置**（跨存档）分层，按数据键的作用域自动分流。
- **增量存档**（`save.dat`）与读档、多存档槽、备份与恢复。
- **文件加密**：`data.dat` / `save.dat` 走 AES-128-CBC，密钥与 IV 可由内容
  自定义 —— 不换的话，任何人拿到本框架就能解开所有基于它的游戏存档。
- 设置界面：画面比例、全屏方式、音量、文字速度、按键绑定、**界面语言**。

### 国际化

- 语言表 `starveil:lang/<代码>.json`，支持嵌套 JSON 展平、运行时注入覆盖、
  以及把某个语言整份指向内容自己的文件。
- **可选语言由内容提供**，内容不提供时设置界面不显示语言切换。

### 插件与平台注入点

- 插件在 `INIT`（内容初始化之前）或 `LAUNCHER`（启动器之前）两个时机加载，
  用 ByteBuddy 做运行期类改写。
- **平台注入点**（`PlatformInjectPoints`）：平台相关能力（隐藏数据目录、
  窗口操作、用户管理等）的默认实现可以被平台适配插件替换。
- 本版本**只打包 Windows 的 JavaFX 原生库**；其它平台靠平台适配插件提供。

---

## 项目结构

```
Starveil/                       ← 本仓库：框架
├── src/main/java/com/xiaowu/game/starveil/
│   ├── launcher/               启动入口（AppEntry）与启动器
│   ├── startup/                启动参数处理器注册表
│   ├── config/                 常量与启动配置
│   ├── infrastructure/         资源解析、内容配置、日志、音频、存档、i18n
│   ├── render/                 渲染引擎（JavaFX 实现 + 抽象接口）
│   ├── game/                   章节、剧情、世界、ECS、任务、成就、物品
│   ├── ui/                     主菜单、设置、背包、存档界面、对话、覆盖层
│   ├── plugin/                 插件加载、清单、兼容性、平台注入点
│   ├── platform/               平台抽象与 Windows 实现
│   ├── api/                    给内容/插件用的门面
│   └── debug/                  调试窗口
├── src/main/resources/assets/starveil/lang/zh_cn.json   ← 框架自带的文案
├── docs/                       文档（见下）
├── TestGame/                   gradlew run 的工作目录（运行时数据）
└── BuildOutput/                构建产物
```

游戏内容放在**同级目录**（约定为 `../StarveilContent`），是独立项目。

---

## 编译

### 环境

| 需要 | 版本 | 说明 |
|---|---|---|
| **JDK** | **25** | 必须正好是 25：JavaFX 25 依赖 `jdk.jsobject`，该模块在 JDK 26 已被移除 |
| Gradle | 9.5（用仓库里的 wrapper 即可） | 不需要单独安装 |

`gradle.properties` 里指定了 `org.gradle.java.home`，请按你的安装路径修改：

```properties
org.gradle.java.home=C:/Program Files/Zulu/zulu-25
```

### 构建框架 jar

```bat
cd Starveil
gradlew.bat buildAll
```

产物：`BuildOutput/ClearCraft.jar` —— 纯引擎 jar，**不含任何游戏内容**。
要得到可游玩 / 可执行的产物，请构建内容项目。

开发期只想快点出 jar：

```bat
gradlew.bat buildFast
```

### 跑测试

```bat
gradlew.bat test
```

### 单独运行框架（自检）

```bat
gradlew.bat run --args="-no-admin -debug -no-content"
```

---

## 如何开发游戏

内容项目是独立项目，**依赖方向永远是「内容 → 框架」**：框架不认识内容，
内容才认识框架。

```bat
:: 1) 先构建框架 jar
cd Starveil
gradlew.bat buildAll

:: 2) 再构建内容项目（示例路径）
cd ..\StarveilContent
gradlew.bat buildAll
```

两种接入方式，**都不需要改框架一行代码**：

| | 合包（shadowJar） | 运行时注入 |
|---|---|---|
| 做法 | 框架 jar 解包后与内容重新打成**一个胖 jar** | 框架 jar 与内容 jar 各自独立 |
| 产物 | 1 个 jar | 框架 jar + 内容 jar |
| 框架独立升级 | 需重新合包 | 直接换 jar |
| 内容迭代 | 每次重打大包 | 只重打内容 jar |
| 适合 | 面向玩家的成品分发 | 开发期、多内容复用同一框架 |

两者的 `build.gradle` 写法见 [构建指南](docs/build-guide.md)。

### 内容需要提供什么

- 一个入口类 `com.xiaowu.game.starveil.content.init.init`（静态 `init()`），
  在里面用 `ContentConfig` 声明外观资源、语言、加密密钥，并注册自己的数据键。
- 章节类 `com.xiaowu.game.starveil.content.chapter<N>.Chapter<N>`。
- 可选的贴图 / 字体 / 音频 / 地图 / 物品 / 成就定义，放在
  `assets/starveil/<类型>/` 下。

框架对内容没提供的东西一律**静默降级**（回退系统字体、纯黑背景、静默音频、
隐藏成就入口、不显示语言切换），因此可以先跑起来再一点点补齐。

---

## 文档

| 文档 | 内容 |
|---|---|
| [构建指南](docs/build-guide.md) | 内容怎么接入框架、两种打包方式、产物结构 |
| [剧情开发指南](docs/story-guide.md) | 章节怎么写、剧情脚本 API、动画键、世界与玩法系统 |
| [数据键](docs/data-keys.md) | 键怎么注册、类型与作用域规则、每个内置键的作用 |

目录索引见 [docs/README.md](docs/README.md)。

---

## 许可

本框架以 **GNU Affero General Public License v3.0**（AGPL-3.0）开源，
全文见 [LICENSE](LICENSE)。

简短说明（**不构成法律意见**）：

- 你可以自由使用、修改、再分发本框架，包括商用。
- 分发本框架（含修改版）时，必须提供完整对应源码，并以 AGPL-3.0 授权。
- AGPL 第 13 条：如果你把修改版作为**网络服务**提供给用户交互，
  必须向这些用户提供对应源码。

**关于「用本框架做的游戏要不要开源」**：这是一个需要想清楚的问题，
AGPL 对它的答案比多数人预期的严格得多 —— 尤其是「框架与内容打进同一个 jar」
这种打包方式。完整分析、可选做法与替代协议见
[docs/licensing.md](docs/licensing.md)。

# 构建指南 —— 如何把内容接入框架

> **先说结论：不建议更改框架源码。**
>
> 框架（本仓库）提供的是引擎能力：启动流程、渲染、ECS、章节调度、插件与注入点、
> 资源解析、设置与存档。**游戏内容**（章节脚本、贴图、音频、字体、地图、物品与成就定义）
> 属于另一个项目。
>
> 内容项目通过下面两种方式接入，**都不需要动框架一行代码**。
> 一旦你去改框架源码，就失去了「框架可独立升级」这个前提 ——
> 以后框架发新版，你的改动会成为合并不了的补丁。

---

## 前置：先产出框架 jar

框架只产出**引擎 jar**（不含任何美术/音频资源）：

```bat
cd Starveil
gradlew.bat buildFast
```

产物：`BuildOutput/ClearCraft.jar`

框架**单独运行会拒绝启动**并提示「缺少游戏内容」—— 这不是故障，是刻意设计：
框架本身不是一款可玩的游戏，与其让你对着空壳猜测，不如明确告诉你少了什么。

调试框架本身时可以用 `-no-content` 强制跑起来，检查缺资源时各处降级是否正常：

```bat
gradlew.bat run --args="-no-admin -debug -no-content"
```

该模式下主菜单背景为纯黑、字体回退系统字体、不播音频、用系统默认窗口图标，
并且**禁止开始游戏**。它只是自检工具，不是可玩状态。

> **跑内容项目时记得带 `-no-admin -debug`**：`-no-admin` 去掉「非管理员模式」提示框，
> `-debug` 跳过显卡优化提示、也不请求提权。两个都不带的话，启动会被一个模态对话框
> 挡住 —— 看起来像「卡在硬件信息那一行不动了」，其实只是在等你点确定。
>
> **游戏还在跑的时候不要重新构建框架 jar。** `shadowJar` 会直接覆盖
> `BuildOutput/ClearCraft.jar`，而运行中的 JVM 已经把那个文件映射进内存了 ——
> 覆盖之后类加载器读到的内容与映射不一致，表现是随机的
> `NoClassDefFoundError`（连 `javafx.scene.input.MouseEvent` 这种框架自带类都会
> 「找不到」），严重时 JVM 直接 `EXCEPTION_ACCESS_VIOLATION` 崩掉。
> 先关掉游戏再构建。

---

## 方式一：合包（shadowJar）

把内容与框架打进**同一个胖 jar**，适合做单文件分发。

内容项目的 `build.gradle`：

```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '9.3.0'
    id 'edu.sc.seis.launch4j' version '4.0.0'   // 需要 exe 时
}

// 框架 jar 作为普通依赖引入 —— 不要复制框架源码
def frameworkJar = file('../Starveil/BuildOutput/ClearCraft.jar')
dependencies {
    implementation files(frameworkJar)
}
if (!frameworkJar.exists()) {
    throw new GradleException("找不到框架 jar：${frameworkJar}\n请先在 ../Starveil 执行 gradlew.bat buildFast")
}

shadowJar {
    // 把框架 jar 解包后与内容一起重新打包 —— 最终只有一个胖 jar
    destinationDirectory.set(file("${projectDir}/BuildOutput"))
    manifest {
        attributes 'Main-Class': 'com.xiaowu.game.starveil.launcher.AppEntry'
    }
    mergeServiceFiles()
}
```

```bat
cd StarveilContent
gradlew.bat buildAll        :: 胖 jar + exe
```

**优点**：只有一个产物，分发最简单。
**代价**：框架与内容被焊死在一个 jar 里，框架升级必须重新合包；
每次改内容都要重新打 100+ MB 的包。

---

## 方式二：运行时注入（框架 jar 不动）

**框架 jar 保持原样**，内容单独打成 jar，两者只在**运行时的 classpath 上相遇**。

```groovy
// 内容项目：只打自己的 jar，不引入 shadow
jar {
    archiveFileName = 'content.jar'
}
```

启动时让 JVM 同时看到两个 jar：

```bat
java -cp "ClearCraft.jar;content.jar" com.xiaowu.game.starveil.launcher.AppEntry
```

框架在启动最早期就会探测 `com.xiaowu.game.starveil.content` 包是否存在，
所以内容 jar **必须从一开始就在 classpath 上**，不能等到运行时再挂载。

### 为什么入口 exe 建议叫 `launch`

做成 exe 时，请把可执行文件命名为 **`launch.exe`**（而不是某个具体游戏名）。

原因是这份 exe 的角色是**启动器**，不是游戏本体：它只负责把框架 jar 与内容 jar
一起交给 JVM。用中性名字的好处是：

- 换内容不需要换启动器 —— 同一个 `launch.exe` 可以拉起不同的内容 jar；
- 用户拿着 `launch.exe` 就知道「这是入口」，而不是以为它绑定了某款游戏；
- 框架与内容可以各自独立升级，只要 classpath 指对了就行。

**优点**：框架可独立升级，内容可独立替换，不用反复重打 100+ MB 的胖包。
**代价**：产物是两个（或更多）文件，分发时不能只给一个 jar。

---

## 两种方式怎么选

| | 合包（方式一） | 运行时注入（方式二） |
|---|---|---|
| 产物数量 | 1 个胖 jar | 框架 jar + 内容 jar |
| 框架单独升级 | 需重新合包 | 直接换 jar 即可 |
| 内容迭代 | 每次重打大包 | 只重打内容 jar |
| 分发复杂度 | 低 | 略高 |
| 适合 | 面向玩家的成品分发 | 开发期、多内容复用同一框架 |

两者**不冲突**：开发期用方式二迭代，发布时用方式一合包。

---

## 内容需要提供什么

框架对内容的要求只有一条硬约定：

```
com.xiaowu.game.starveil.content.init.init
```

一个类，含静态 `init()`（或静态 `main(String[])`）。框架在启动最早期调用它，
这是内容**唯一**的非框架初始化入口。

在里面用 `ContentConfig` 声明本作的外观与语言：

```java
package com.xiaowu.game.starveil.content.init;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;

public final class init {
    private init() {}

    public static void init() {
        // 游戏名 —— 框架不预设自己跑的是哪个游戏，默认是 "My Game"。
        // 这个名字会出现在：窗口标题、{TITLE} / {window.title} 占位符、
        // s.dialog(...) 的说话人（旁白）、主菜单/暂停菜单的标题、教程欢迎语。
        ContentConfig.setGameName("My Game");

        // 窗口标题 —— 不设则自动拼：主菜单「游戏名 - 主菜单」、游戏内就是游戏名；
        // 设了就整串照用（框架不再加后缀）。
        // ContentConfig.setWindowTitle("My Game");

        // 字体 —— 框架不带任何字体，不设就用系统字体
        ContentConfig.setBodyFont("starveil:fonts/my-body.ttf");
        ContentConfig.setTitleFont("starveil:fonts/my-title.ttf");
        ContentConfig.setDecorFont("starveil:fonts/my-decor.ttf");

        // 窗口图标 —— 不设就用系统默认图标
        ContentConfig.setAppIcon("starveil:textures/icons/app-icon.png");

        // 主菜单 —— 不设则背景纯黑、静默无音乐
        ContentConfig.setMenuBackground("starveil:textures/backgrounds/main-menu.png");
        ContentConfig.setMenuMusic("starveil:sounds/music/theme.mp3");

        // 主题色
        ContentConfig.setPrimaryColor("#FF69B4");

        // 可选语言（不提供则设置界面完全不显示语言切换）
        // 每个语言还要有一份文案：starveil:lang/<代码>.json
        // 框架自带 zh_cn；en_us 放在本项目的 assets/starveil/lang/ 下
        ContentConfig.addLanguage("zh_cn", "简体中文");
        ContentConfig.addLanguage("en_us", "English");

        // 数据文件加密密钥（不提供则用框架默认值；换密钥会作废旧存档）
        ContentConfig.setDataCryptoKey("MyGame_SecretKey", null);

        // 起始章节（不提供则从第 1 章开始）
        com.xiaowu.game.starveil.game.story.ChapterDirector.setStartChapter(1);
    }
}
```

内容自己新增的数据键也在同一个入口声明（详见 [数据键](data-keys.md)）：

```java
import com.xiaowu.game.starveil.infrastructure.persistence.DataKey;
import com.xiaowu.game.starveil.infrastructure.persistence.DataKeyFlag;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;

/** 本作的数据键，声明一次，全工程只用句柄读写。 */
public final class MyGameKeys {
    /** 主角好感度：随存档，默认 0。 */
    public static final DataKey<Integer> AFFECTION =
            DataManager.defineInt("mygame", "affection", 0, DataKeyFlag.PER_SAVE);

    /** 是否已看过开场：全局，默认 false。 */
    public static final DataKey<Boolean> SEEN_PROLOGUE =
            DataManager.defineBool("mygame", "seen_prologue", false);

    /** 最近到过的地图：没有默认值 → 读到 null。 */
    public static final DataKey<String> LAST_MAP =
            DataManager.define("mygame", "last_map", null, String.class);
}
```

类型是**声明出来的**，不靠默认值推断：默认值非 `null` 时必须与类型一致，
否则注册即报错 —— 这样「默认值写错」在启动阶段就暴露，而不是等到某次读取
因为解析失败悄悄回退。详见 [数据键](data-keys.md)。

也可以重定义框架内置键的出厂默认值（例如改掉默认文字速度）：

```java
DataManager.defineInt("starveil", "setting.text_speed", 30);
```

框架对「内容没提供」的一律**静默降级**，而不是报错：

| 内容没提供 | 框架行为 |
|---|---|
| 字体 | 回退系统字体 |
| 贴图 / 主菜单背景 | 纯黑 |
| 音频 | 静默，不播 |
| 窗口图标 | 系统默认图标 |
| 成就定义 | 成就列表留空，主菜单隐藏成就入口 |
| 语言列表 | 设置界面不显示语言切换 |

---

## 章节命名约定（强制）

```
com.xiaowu.game.starveil.content.chapter<N>.Chapter<N>
```

不合规会在解析时**直接报错并指出期望的类名**，而不是静默跳过 ——
后者会让人对着「过章节死活不触发」毫无线索。

---

## 相关文档

- [剧情开发指南](story-guide.md) —— 内容怎么写：章节、剧情脚本、动画键
- [数据键](data-keys.md) —— 键怎么注册，以及每个内置键的作用

# 生命周期事件

框架在关键执行点**广播**事件，谁关心谁监听。

> **没有监听者时，广播就是一次空操作** —— 不报错、没有任何副作用。
> 所以框架可以放心地在各处加发布点，内容也可以放心地只听自己关心的那几个。

---

## 为什么用它

原本框架里是「到点了直接调某个类的方法」。每多一个关心这件事的东西，就得回去改那个调用点；插件想插一脚更是只能改框架源码。

改成广播之后两边解耦：

| 角色 | 需要知道什么 |
|---|---|
| **发布方**（框架） | 只管广播，不需要知道有谁在听 |
| **监听方**（内容 / 插件 / 框架自己的模块） | 只管监听，不需要知道是谁广播的 |

框架内部已经这么用了一处：章节调度器广播 `ChapterChanged`，**聊天记录自己去监听**并决定要不要清空 —— 调度器不再需要 import 聊天记录。

---

## 怎么监听

```java
import com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents;

// 一直听
LifecycleEvents.onMenuShown(event ->
        LoggerManager.Logger("INFO", "主菜单出来了: " + event.title()));

// 只听第一次
LifecycleEvents.onceMenuShown(event ->
        LoggerManager.Logger("INFO", "首次进入游戏"));

// 不想听了就取消（句柄直接丢掉也完全可以，监听依然有效）
Lifecycle.Subscription<LifecycleEvents.MenuShown> sub =
        LifecycleEvents.onMenuShown(event -> { /* ... */ });
sub.cancel();
```

### 规则

- **同步分发**：监听者在发布线程上被直接调用，顺序即注册顺序。
  需要切线程请自己在监听者里切（例如 `Platform.runLater`）。
- **一个监听者出错不影响别人**，也不会把广播方拖垮 —— 只记一条 ERROR 日志。
- **发布方不等待、不收集返回值**，事件是「通知」而不是「请求」。
- 事件是**不可变对象**（record），监听者改不到别人看到的内容。
- 同一个监听者重复注册会被重复调用；需要去重就自己保存句柄。

---

## 可用事件

| 事件 | 什么时候发 | 携带什么 |
|---|---|---|
| `FrameworkReady` | 配置、内容初始化、插件都就绪，界面还没建 | `frameworkVersion` |
| `MenuShown` | 主菜单真正显示出来（启动提示也结束了） | `title` |
| `GameStarted` | 一局游戏开始（新游戏或读档都会发） | `startChapter`、`fromSave` |
| `ChapterChanged` | 章节已确定、脚本**即将**开始跑 | `chapter`、`mode` |
| `GameSaved` | 已保存到某个槽位 | `slot`（0 基） |
| `GameLoaded` | 已从某个槽位读档完成 | `slot`（0 基） |
| `SettingsOpened` | 设置界面打开 | — |
| `SettingsClosed` | 设置界面关闭 | `applied`（确定 vs 取消） |
| `LanguageChanged` | 界面语言已切换 | `languageCode` |
| `TutorialCompleted` | 教程完成 | — |

每个事件都有对应的 `onXxx(...)` 监听方法，`MenuShown` 另外有 `onceMenuShown(...)`。

### 几个时机上的注意点

- `ChapterChanged` 广播时**章节脚本还没开始跑**。想「等这一章真正开始之后」再做点什么，
  在监听者里 `Platform.runLater(...)`。
- `GameStarted` 广播时**世界可能还没加载** —— 章节是 `NORMAL` 还是 `VISUAL_NOVEL`
  决定了要不要世界。不要在 `GameStarted` 里假设玩家已经站在地图上。
- `SettingsClosed` 的 `applied` 为 `false` 表示点了取消或直接关掉。注意
  **界面语言是「选中即刻生效」的**，取消不会回滚语言。

---

## 自己广播事件

`Lifecycle.publish(...)` 是公开的，内容与插件可以用同一套机制广播自己的事件 ——
发布者和监听者一样，都只依赖事件类型本身：

```java
/** 自定义事件：同样用不可变 record 承载信息。 */
public record BossDefeated(String bossId, int attempts) { }

// 广播（没人听就是空操作）
Lifecycle.publish(new BossDefeated("tide_lord", 3));

// 在别处监听
Lifecycle.on(BossDefeated.class, e ->
        LoggerManager.Logger("INFO", "打过了 " + e.bossId()));
```

> **一个约定**：一个事件类型只由一类发布方广播。如果两个地方都在发同一种事件，
> 监听者就分不清消息来自谁了 —— 那种情况应该定义两个事件类型。

---

## 和另外两个事件机制的分工

框架里有三套事件机制，别混用：

| 机制 | 生命周期 | 用途 |
|---|---|---|
| `infrastructure.event.Lifecycle`（本页） | **进程级**，跨局存在 | 生命周期广播：「某件事发生了，谁关心谁处理」 |
| `game.ecs.EventBus` | **单局**，每局重建 | 实体之间的 ECS 事件，例如「NPC 死亡」 |
| `game.event.EventCallbackManager` | 单局 / 剧情级别 | 剧情脚本要 `await` 的信号 |

判断方法：**问「这件事属于进程、属于这一局、还是属于这段剧情」。**

---

## 相关文档

- [数据键](data-keys.md) —— 存值（与事件互补：事件是「发生了」，数据键是「存着什么」）
- [剧情开发指南](story-guide.md) —— 章节与剧情脚本
- [构建指南](build-guide.md) —— 内容怎么接入

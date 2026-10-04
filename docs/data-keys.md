# DataManager 数据键

本文说明**数据键**的定义方式与每个键的作用。

> 想改数据结构或新增键时**先看这一页**。键名、类型、默认值、作用域全部在注册处一次写清；
> 读写只用句柄，不需要在每个调用点重复填默认值。

---

## 一、键必须先注册

任何键在使用前都要先声明，声明时写清四件事：

| 要素 | 说明 |
|---|---|
| **命名空间** | 内置用 `starveil`（引擎保留），第三方用自己的前缀 |
| **键名** | 不带前缀的短名，最终形成 `namespace:name` |
| **默认值** | 「没设过值时读到什么」；传 `null` 表示**没有默认值**（读取返回 `null`） |
| **类型** | 显式写明。默认值非 `null` 时必须与类型一致，否则注册即报错 |
| **标记**（可选） | 存档作用域 / 只读 / 临时，见下 |

```java
// 最简：字符串键，默认 "无名"，完整键名 mymod:hero_name
public static final DataKey<String> HERO =
        DataManager.defineStr("mymod", "hero_name", "无名");

// 整数键，默认 0
public static final DataKey<Integer> AFFECTION =
        DataManager.defineInt("mymod", "affection", 0);

// 显式写类型（基本类型 / 包装类型都行），并声明为存档作用域
public static final DataKey<Boolean> CANT_EXIT =
        DataManager.define("mymod", "cant_exit", false, boolean.class, DataKeyFlag.PER_SAVE);

// 没有默认值：读到就是 null（区分「没值」与「值是空串」）
public static final DataKey<String> LAST_MAP =
        DataManager.define("mymod", "last_map", null, String.class);

// 只读：锁定在默认值，写入被拒绝
public static final DataKey<String> VERSION =
        DataManager.defineStr("mymod", "version", "1.0.0", DataKeyFlag.READ_ONLY);

// 临时：不落盘，进程结束即消失
public static final DataKey<Boolean> LOADING =
        DataManager.defineBool("mymod", "loading", false, DataKeyFlag.TEMPORARY);
```

可用的便捷方法：`defineStr` / `defineBool` / `defineInt` / `defineLong` / `defineDouble` /
`defineFloat`。需要直接写类型（例如 `int.class`）时用
`DataManager.define(namespace, name, 默认值, 类型, 标记...)`
或 `DataKey.of(namespace, name, 默认值, 类型, 标记...)`。

### 默认值与类型不符会当场报错

类型是**声明的**，不是从默认值猜出来的。两者不一致时注册那一刻就抛异常：

```java
DataManager.define("mymod", "flag", "true", Boolean.class);
// → IllegalArgumentException：键 'mymod:flag' 声明类型为 BOOLEAN，
//   但默认值 'true' (String) 不是这个类型
```

这样「默认值写错」会在启动阶段暴露，而不是等到某次读取时因为解析失败悄悄回退 ——
那时已经离出错的地方很远了。数值之间也不做隐式拓宽：`1` 不能当 `Long` 键的默认值。

### 读写

```java
HERO.set("霁雾");
String name = HERO.get();        // 类型已由注册决定，返回 String

AFFECTION.set(3);
int v = AFFECTION.get();         // 需要基本类型时用 getInt()/getBool()/...
boolean set = AFFECTION.isSet(); // 是否显式设过值
AFFECTION.remove();              // 删除值 → 回到默认值
```

> **为什么强制注册**
>
> 1. **默认值只有一份。** 旧写法 `getInt(key, 50)` 让同一个键在不同文件里可能带着
>    不同默认值（一处 50、一处 30），读出来的结果取决于先执行哪一处，极难定位。
> 2. **拼错会当场报错**，而不是静默落进一个默认值。
> 3. **归属一眼可辨**：键名自带命名空间。
>
> 未注册的键：**读**返回默认值并记 WARNING，**写**直接拒绝并记 ERROR。

### 值类型不符会被拒绝

键的类型写在注册处，调用点不需要（也无法）再选类型：

```java
DataManager.defineInt("mymod", "level", 1);   // 完整键名 mymod:level

DataManager.set("mymod:level", "abc");        // → WARNING，写入被拒
DataManager.setBoolean("mymod:level", true);  // → WARNING，写入被拒
```

数值之间只允许**无损拓宽**（`int` 写进 `long`/`double` 键可以，反过来不行）。

---

## 二、三种标记

### `PER_SAVE` —— 存档作用域

全局有基准值，但**允许当前存档覆盖**：

> 读取时，若当前存档里也写入了同一个键且值不同，则**优先取存档里的值**。

典型用途：某个存档需要「暂时不要退出」，但不想把这份设置带回主菜单影响其它存档。
没有活跃存档时（还在主菜单），读写都落到全局配置。

### `READ_ONLY` —— 只读

锁定在默认值。写入会被拒绝并记 WARNING，删除也无效。用来表达
「框架告知内容、但内容不该改」的信息，例如框架版本号。

### `TEMPORARY` —— 临时

**不落盘**，只活在当前进程里，进程结束即消失。

它的价值是替掉「写进配置、用完再主动清理」那套做法。后者有两个毛病：
崩溃或被强杀时清理不掉，标记就永久残留在配置里（比如「异常关闭」标记）；
而且清理逻辑散落在启动流程各处，容易漏。改成进程内状态后**根本不需要清理**。

---

## 三、读取优先级

```
临时键  >  内存覆盖（调试窗口）  >  当前存档（仅 PER_SAVE 键）  >  data.dat
```

- **临时键**是本次运行的权威状态，且刻意不落盘，即便配置文件没加载成功也要能读到。
- **内存覆盖**是调试窗口的逃生口，也是**唯一允许使用未注册键**的入口 ——
  调试时它需要能压住任意键，包括还没声明的。
- **代码写入会顶掉内存覆盖**：只要代码显式写同一个键，覆盖立即失效。
  这是刻意的 ——「代码设定的值」永远比「调试时手动改的值」权威。

---

## 四、内置键一览

### 剧情用键（存档作用域）

| 键 | 默认 | 作用 |
|---|---|---|
| `starveil:cant_exit` | `false` | **禁止退出**。见下 |
| `starveil:inertia` | **`true`** | 移动惯性开关 |
| `starveil:chat_history_keep_chapters` | `false` | 聊天记录是否跨章节保留 |
| `starveil:magic_circle_enabled` | `false` | 法阵（魔力）系统开关 |
| `starveil:overlay_enabled` | **`true`** | 屏幕特效遮罩层开关 |
| `starveil:tutorial_completed` | `false` | 教程是否已完成，作用域见 `tutorial_persist` |

#### `starveil:cant_exit`

**剧情用的「禁止退出」标记。** 默认：`false`。

它是**为剧情保留**的键：剧情在需要强化代入感的段落里设置它，
让玩家在那一刻**无法退出游戏** —— 而不是随时点一下退出就把眼前的情境丢下。

比如一段不容打断的独白、一次无法回头的抉择，退出按钮的存在本身就会削弱张力；
关闭它，玩家才会真正「被困在那个场景里」。

**行为**：设为 `true` 时，**主菜单**与 **ESC 暂停菜单**都会移除「退出按钮」；
设回 `false` 或移除该键即恢复。
暂停菜单每次打开都会重建，所以剧情中途改变标记会**立刻**反映到界面上。

它是**存档作用域**的键：某个存档可以只在当下禁掉退出，不影响别的存档。

```java
FrameworkDataKeys.CANT_EXIT.set(true);
// ……那段不能被打断的独白……
FrameworkDataKeys.CANT_EXIT.set(false);
```

#### `starveil:inertia`

**移动惯性开关。** 默认：**开启**。

- **开启**：移动带加减速（松开方向键会滑行一小段），疾跑减速到静止期间播放**急停**贴图；
- **关闭**：恢复瞬时启停，且不再触发急停。

代码里用 `DataManager.isInertiaEnabled()` 读取。手感参数（加速度 / 减速度 / 急停阈值）
在各实体的 `PlayerController` 上，不在这个键里。

#### `starveil:magic_circle_enabled`

**法阵（魔力）系统开关。** 默认：**关闭**。

关闭时魔力值一律返回 0、GameUI 不显示魔力条、Q 键法阵无效。

#### `starveil:overlay_enabled`

**屏幕特效遮罩层开关。** 默认：**开启**（随游戏启动自动显示）。

设为 `false` 时不创建全屏遮罩层，故障花屏 / 伪蓝屏 / 降帧等特效随之不可用。

### 教程进度

| 键 | 默认 | 作用 |
|---|---|---|
| `starveil:tutorial_completed` | `false` | 教程是否已完成 |
| `starveil:tutorial_persist` | `false` | 上面那个键是否**跨存档** |

**默认（`persist=false`）**：进度记在**当前存档**里 —— 每个存档各自记录，
开新档重新走教程。

**`persist=true`**：进度当作全局键，全局只记一次，之后所有存档都不再重复教程。

作用域在**存档会话开始时确定一次**，之后不再变动 ——
这样玩家中途改开关不会让同一个键一半在存档里、一半在全局里。

```java
DataManager.isTutorialCompleted();
DataManager.setTutorialCompleted(true);   // 按当前模式自动路由
```

### 框架元信息

| 键 | 默认 | 作用 |
|---|---|---|
| `starveil:framework_version` | `1.0.0` | **只读**。框架版本号 |

内容可以读它来适配不同框架版本，但**改不动**（写入被拒绝并告警）。

### 全局设置

| 键 | 默认 | 作用 |
|---|---|---|
| `starveil:player_name` | `""` | 玩家名字（剧情做文本替换） |
| `starveil:setting.aspect_ratio` | `16:9` | 画面比例 |
| `starveil:setting.fullscreen` | `false` | 是否全屏 |
| `starveil:setting.fullscreen_mode` | `无边框窗口` | 全屏实现方式 |
| `starveil:setting.bgm_volume` | `0.8` | 背景音乐音量 |
| `starveil:setting.sfx_volume` | 见常量 | 音效音量 |
| `starveil:setting.bgm_interval` | `0` | 背景音乐间隔（毫秒） |
| `starveil:setting.text_speed` | `50` | 打字机速度（每字毫秒） |
| `starveil:setting.language` | `zh_cn` | 界面语言。见下 |
| `starveil:key_bind.*` | `0` | 八个按键绑定（`move_up`、`interact` 等） |
| `starveil:plugin_consent_given` | `false` | 用户是否已同意加载插件 |
| `starveil:compatibility_warning_shown` | `false` | 兼容性提示是否已被忽略 |
| `starveil:gpu_check_ignored` | `false` | 显卡检查是否已被忽略 |
| `starveil:chapter_fade` | **`true`** | 章节之间是否做「落幕 → 卸下世界 → 亮幕」过场 |
| `starveil:illegally_shutdown` | `false` | 上次是否异常关闭（**临时键**） |

#### `starveil:chapter_fade`

章节之间的过场默认**开启**：它把上一章与下一章的场景在视觉上切开，
也给卸下 / 加载世界留出时间。

有些内容希望章节之间无缝衔接（例如一章拆成两半来写），把它设为 `false` 即可。

它只控制**过场与卸下世界**：下一章要不要世界仍然由该章的 `mode()` 决定 ——
需要世界时该加载还是会加载，不会因为关掉过场就把世界一起省掉。

### 界面语言

**可选语言由内容提供，框架不规定一款游戏支持哪些语言。**

```java
// content.init.init() 里
ContentConfig.addLanguage("zh_cn", "简体中文");
ContentConfig.addLanguage("en_us", "English");
```

- 一个都不登记 → 设置界面里**语言那一行整体不显示**，而不是显示一个只有一项的下拉框；
- 登记了什么就列出什么，显示名就是第二个参数；
- 玩家选中后**立即生效并持久化**到 `starveil:setting.language`，界面随之重建；
- 启动时读这个键恢复上次的选择；存的语言已经不在列表里（内容删掉了那份文案）则
  回落到列表里的第一个，并记一条 WARNING。

每个语言都要有一份文案文件，路径 `starveil:lang/<代码>.json`
（框架自带 `zh_cn`，内容把自己的放在自己的资源目录里）。找不到的键会回落到
框架默认语言，所以内容不必一次翻全 —— 但**键名必须与框架一致**，
否则回退的是框架原文案，看起来像「语言没切换」。

内容也可以把某个语言直接指向自己的文件：

```java
I18n.registerLanguageResource("en_us", "starveil:lang/en_us.json");
I18n.inject("en_us", "framework.setting.title", "Options");   // 少量改写
```

---

> `starveil:illegally_shutdown` 以前是「写进配置、下次启动时清理」的。
> 改成临时键后**不需要任何清理逻辑**：生命周期由进程本身保证，
> 被强杀也不会留下残留标记。

---

## 五、关于历史键迁移

**没有迁移。** 框架仍在测试阶段，不做历史数据迁移 ——
老存档与老配置**直接删掉即可**，不需要担心兼容。

`starveil:cant_exit` 曾经是无前缀的裸名 `CantExit`，现在裸名既不是合法键、
也不会被映射。刻意不写迁移逻辑，是为了不让「裸名曾经合法」这件事继续流传 ——
那只会让后来的人以为无前缀键是可接受的写法。

---

## 六、数据文件加密密钥

`data.dat` / `save.dat` 用 **AES-128-CBC** 加密。密钥与 IV 可以由内容在
`content.init.init()` 里换成自己的一套：

```java
ContentConfig.setDataCryptoKey("MyGame_SecretKey", null);          // 只换密钥
ContentConfig.setDataCryptoKey("MyGame_SecretKey", "MyGame_InitVec16"); // 密钥 + IV
```

| 项 | 默认值 | 长度 |
|---|---|---|
| 密钥 | `Starveil_DataKey` | **必须正好 16 字节**（UTF-8 编码后计） |
| IV | `1234567890123456` | 16 字节 |

长度由算法决定（AES-128），不是代码偏好。长度不对会记 ERROR 并**保持默认值**，
不会中断启动 —— 密钥写错属于配置失误，让游戏带着明确报错继续跑比直接崩在启动阶段更好。

**为什么要换**：不换的话，任何人拿到本框架源码就能解开所有基于它的游戏存档。

**注意**：换密钥会**作废已有存档**（旧文件解不开），需要删掉重来。

---

## 七、第三方如何注册

直接用同一套 API，把命名空间换成自己的：

```java
public final class MyPluginKeys {
    public static final DataKey<Integer> KILLS =
            DataManager.defineInt("myplugin", "kills", 0, DataKeyFlag.PER_SAVE);
    public static final DataKey<String> LAST_MAP =
            DataManager.define("myplugin", "last_map", null, String.class);
}
```

注册会被**拒绝**的情况：

- 没有命名空间前缀（裸名）；
- 键名里出现第二段命名空间（`a:b:c`）；
- 类型不受支持（只支持 String / Boolean / Integer / Long / Double / Float）；
- **默认值类型与声明类型不符**（`null` 除外，它表示「没有默认值」）；
- 同一个键名被声明成两种类型（几乎一定是两处各自声明了同名键，必须炸掉）。

`starveil:` 是引擎保留命名空间，但**内容可以重定义内置键的默认值** ——
例如改掉 `starveil:setting.text_speed` 的出厂值。重定义不会取消它的内置身份。

> 已发出的 `DataKey` 句柄是**不可变**的：即使后来有人重新注册了同一个键名，
> 旧句柄仍持有当初的定义，不会出现「同一个键在两次读取之间换了默认值」这种半边生效的状态。

---

## 相关文档

- [构建指南](build-guide.md) —— 内容如何接入框架、打包、分发
- [剧情开发指南](story-guide.md) —— 内容怎么写、存档变量怎么用

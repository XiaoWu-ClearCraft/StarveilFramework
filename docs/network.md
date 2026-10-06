# 联网 / 时间 / IP 属地

框架提供四件事，都在 `com.xiaowu.game.starveil.infrastructure.net`：

| 能力 | 入口 | 触发方式 |
|---|---|---|
| 是否联网 | `NetworkStatus` | Windows 系统联网状态（本地）+ 接口事件 + 兜底轮询 |
| 断网提示（强制） | `ui.overlay.OfflinePrompt` | 需要内容开启 |
| 可信时间 | `TrustedTime` | 启动/需要时联网同步一次 |
| IP 属地（市级） | `IpLocation` | 启动时查一次，只放内存 |

**框架不联网也能跑**：四项能力都有「没网时怎么办」的兜底（见下），
没有任何一项会阻塞启动或在断网时抛异常。

---

## 1. 是否联网：`NetworkStatus`

```java
NetworkStatus.start();                 // 注册接口事件 + 起兜底轮询（幂等）
NetworkStatus.checkNow();              // 立刻测一次（异步）
boolean online = NetworkStatus.isOnline();
NetworkStatus.isEventDriven();         // 是否正在用系统接口事件

NetworkStatus.addListener(state -> {   // 状态变化（在探测线程上，碰 UI 请 runLater）
});
```

### 触发方式：接口事件为主，轮询兜底

Windows 上框架用 JNA 调 `NotifyIpInterfaceChange`（iphlpapi）注册一个回调：
**插拔网线、开关 WiFi 这类接口变化会立刻触发重测**，不用等轮询周期。

但仍然保留一个**兜底轮询**，原因是「接口事件」不等于「能上互联网」：
最典型的是**路由器活着但 WAN 断了**（网线还插着、WiFi 还连着）—— 这时接口没有任何变化、
事件不会触发，只看事件的话「断网提示」要等到玩家真的发请求失败才出现。

轮询间隔默认是**自动**的，取决于判定来源（下面一节）：问 Windows 时 5 秒一次
（纯本地调用，几乎不花钱），自己发 TCP 探测时 30 秒一次（要发网络包，不能太勤）。

```java
ContentConfig.setNetworkEndpoints("223.5.5.5:443", "1.1.1.1:443");  // 只在退回 TCP 探测时用
NetworkStatus.setIntervalSeconds(30);      // 想写死就写死
NetworkStatus.setIntervalSeconds(0);       // 关掉轮询，只靠接口事件
NetworkStatus.setIntervalSeconds(NetworkStatus.AUTO_INTERVAL);   // 默认值：自动
```

非 Windows 平台、或系统联网状态拿不到时，自动退回纯 TCP 轮询，
日志里会写清楚当前用的是哪一种（`联网判定来源: ...`）。

### 怎么判断「能上互联网」

**优先问 Windows 自己**：`WindowsInternetState` 通过 COM 拿
`INetworkListManager`（网络列表管理器）的结论 —— 就是任务栏那个
「有网 / 无 Internet」图标背后的数据源（NCSI）。它能区分
**「有网络但没互联网」**：连着路由器或热点、但出不去，这种情况
「网卡有地址」和「TCP 连不上」是两种不同的解释，系统给出的答案是明确的。

这是个**本地 COM 调用，不发网络包**，所以可以问得比 TCP 探测勤得多（自动模式下 5 秒一次）。

拿不到系统结论时（非 Windows、COM 创建失败），才退回
**真的去 TCP 连一个公网地址**：默认端点是公网 DNS 的 443 端口
（`223.5.5.5` / `1.1.1.1` / `8.8.8.8`）：纯 IP 不需要先解析域名
（断网时那一步会先失败，反而看不出是谁的问题），任何一个能连上就算在线，
国内国外都可用。

> **它是「Windows 的判定」，不是绝对真理**：NCSI 自己也有探测周期，刚断的瞬间可能还显示有网
> （任务栏图标同理）；反过来，公司代理 / 强制门户环境下 NCSI 可能判「无 Internet」，
> 而玩家的游戏其实连得上。所以框架把它当**首选信号**而不是唯一信号。

> **首次探测出结果前 `isOnline()` 返回 `true`。** 宁可先当「有网」，
> 也不要在还没测出来的那几百毫秒里给玩家弹一个「你没网」。

### 给内容用的门面

内容与插件走 `com.xiaowu.game.starveil.api` 这一层，不必碰内部实现类：

```java
import com.xiaowu.game.starveil.api.Starveil;

Starveil.network().isOnline();        // 现在能不能上互联网（首次结果前为 true）
Starveil.network().state();           // UNKNOWN / ONLINE / OFFLINE
Starveil.network().isKnown();         // 已经测出过结果没有（区分「在线」和「还没测」）
Starveil.network().checkNow();        // 立刻重测（异步，平时不用手动调）
Starveil.network().source();          // 当前判定来源的可读描述，排查假阴性时写日志用
Starveil.network().addListener(state -> { /* 在探测线程上，碰 UI 请 runLater */ });
Starveil.network().removeListener(listener);   // 传注册时的同一个实例
```

## 2. 断网提示：`OfflinePrompt`

```java
ContentConfig.setOfflinePromptEnabled(true);   // 默认关闭
```

开启后：判定断网 → 盖住整个画面的提示；**联网后自动消失**。
没有「知道了」按钮、也没有跳过键 —— 这是给「不联网就没法正常玩」的游戏用的，
让玩家点掉继续玩一个坏掉的游戏没有意义。
（内容若想自己掌握开关，比如调试菜单里临时放行，运行期再调一次
`ContentConfig.setOfflinePromptEnabled(false)` 即可。）

提示挂在**场景根**上（不是画布里的 modalHost），所以主菜单阶段也会提示。

### 「强制」是怎么做到的

只挂一个遮罩节点是不够的 —— 挡不住点击和按键（踩过：断网状态下视觉小说
还能点/按回车翻页）。现在做了三件事：

1. **拦在窗口级，不是场景级。** 按键过滤器按注册先后执行，框架的 `InputHandler`
   在场景建好时就注册了，遮罩后注册就永远排在它后面 —— 拦不住回车/空格。
   窗口在事件链上更靠外（实测窗口过滤器先于场景过滤器执行），所以在窗口上拦才可靠：
   显示期间鼠标、按键、滚轮全部 consume。
2. **清掉「断开瞬间正按着」的按键**，并按来源锁住 `InputHandler` 的操作。
3. **锁住剧情推进**（`ChatManager.lockAdvance(owner)`，按来源记账）：
   视觉小说的翻页判定本来就是「暂停菜单 / 教程」共用那个开关，断网提示也加入其中，
   所以就算有事件从别的路径漏进去，对话框也不会翻页。

> 还有个坑：`GameManager` 接管新场景时会 `rootContainer.getChildren().clear()`，
> 遮罩节点会被摘掉。所以每次显示都会确认节点还挂在宿主上，不在就补挂回去 ——
> 否则会出现「状态以为在挡、画面上却什么都没有」。

**怎么测**：拔网线 / 关 WiFi / 断路由器的 WAN，然后看任务栏图标 ——
两者应该同时变（框架问的就是同一个数据源）。日志里会有
`联网状态: OFFLINE（已断开）`，启动时还会写一行 `联网判定来源: ...`。
不想拔网线也行：调试时调 `NetworkStatus.setDebugOverride(false)` 就当断网
（`null` 恢复真实判定，**正式发布前记得去掉**）。

## 3. 可信时间：`TrustedTime`

内容侧用门面取（内部类 `infrastructure.net.TrustedTime` 也可以用，但门面更稳）：

```java
import com.xiaowu.game.starveil.api.Starveil;

Instant now = Starveil.time().trustedNow();          // 可信时间（该用这个）
LocalDateTime local = Starveil.time().trustedNowLocal();
ZonedDateTime zoned = Starveil.time().trustedNowZoned();
long millis = Starveil.time().trustedEpochMillis();  // 存档 / 比大小的便捷写法

Instant raw = Starveil.time().systemNow();           // 本机时钟：玩家能改，别做判定
boolean trustworthy = Starveil.time().isSynced();    // 可信时间到底可不可信
long offset = Starveil.time().offsetMillis();        // 可信时间 − 本机时间
String from = Starveil.time().source();              // 取时用的地址，没同步过是 null
Starveil.time().syncAsync();                         // 需要时重新同步（异步，平时不用调）
```

框架层（启动流程里已经在用）多一个「基准时间戳」的读法：

```java
TrustedTime.loadFromConfig();          // 启动时恢复上次的基准（AppEntry 已经调了）
TrustedTime.baseTrustedMillis();       // 基准时间戳本身
```

> **没同步过时 `trustedNow()` 返回本机时钟** —— 游戏不该因为取不到时间就跑不动。
> 要判断这个值值不值得信，只看 `isSynced()`（首次启动 + 没网时为 `false`）。

### 时间戳 ⇄ 日期时间

存档、冷却里存的都是毫秒时间戳，要显示或判断「现在几点」得转过来 ——
不用自己拼 `Instant`/`ZoneId`：

```java
long t = Starveil.time().trustedEpochMillis();

LocalDateTime dt = Starveil.time().toLocalDateTime(t);   // 2026-10-06T08:30
LocalDate day    = Starveil.time().toLocalDate(t);
LocalTime clock  = Starveil.time().toLocalTime(t);
int hour         = Starveil.time().hourOfDay(t);         // 8 ← 问候语最常用
String text      = Starveil.time().format(t, "M月d日 HH:mm");
long back        = Starveil.time().toEpochMillis(LocalDateTime.of(2026, 10, 6, 6, 0));

// 和「现在」有关的快捷写法
int nowHour = Starveil.time().hourOfDay();
ZoneId zone = Starveil.time().zone();                    // 玩家机器的时区
```

时区一律用**玩家机器的时区**（`ZoneId.systemDefault()`）：玩家的表是几点，
问候语就该说几点。

剧情脚本里有更短的写法（`s.hour()` / `s.time("HH:mm")` / `s.isOnline()` …，
连问候语的分档示例见 [剧情开发指南](story-guide.md) 的「时间与网络」）。

### 为什么是「基准时间戳 + 单调时钟」

本机时钟由玩家说了算，改一下系统时间就能跳过冷却、刷新每日奖励、伪造存档时间戳。
但**只记一个「偏差值」是不够的**：玩家一拨系统时间，偏差值立刻失真。
所以同步时成对记两样东西：

- **基准可信时间**：联网要回来的那个时间戳；
- **基准单调时间**：`System.nanoTime()` —— **不受系统时间调整影响**。

之后 `现在 = 基准可信时间 + (当前单调时间 − 基准单调时间)`。
于是**程序运行期间，玩家怎么改系统时间都不影响它** ——
剧情里如果需要玩家自己去改系统时间，游戏看到的时间仍然是对的。

**跨重启**：`nanoTime` 只在一次进程内有效，所以把「基准可信时间 + 同步那一刻的本机时钟」
一起写进全局配置（`starveil:trusted_time_base` / `..._base_wall`），
下次启动用「本机时钟走了多久」补上中间的时间差。这一步只能相信本机时钟。
结论：**一次运行内改时间无效；跨重启改则有效**，直到重新联网同步。

### 怎么取

读 HTTP 响应的 **`Date` 头**（RFC 7231 规定由服务器生成）。相比 NTP：
走 443/80 几乎不会被防火墙拦，实现几十行、没有额外依赖；代价是精度到秒。
三种历史格式都认（RFC 1123 / RFC 850 / asctime）。

```java
ContentConfig.setTrustedTimeUrls("https://www.baidu.com", "https://www.bing.com");
```

> 对时间可信度要求极高的场景（防作弊积分榜之类），HTTP Date 只到秒、
> 且依赖你对那个站点的信任 —— 那种场合应当用自己服务端签名的时间。

## 4. IP 属地（市级）：`IpLocation`

```java
IpLocation.prefetchAsync();            // 启动时调一次（AppEntry 已经调了）
String city = IpLocation.city();       // 「南昌」；查不到就是 null
IpLocation.Result r = IpLocation.current();   // city / region / country
boolean ok = IpLocation.isAvailable();
```

语义，三条：

1. **启动时查一次，只放内存，不写盘。** IP 会变（换网络、换城市），
   把上一次的属地存下来接着用等于骗自己。
2. **查不到就返回 `null`，不猜。** 市一级信息只存在于 IP 库里，纯离线拿不到；
   按系统时区猜出来的「上海」对南昌玩家是错的 —— **错的值比没有更糟**。
   所以调用方必须自己处理 `null`（例如「不显示城市」而不是「显示一个错的」）。
3. **整个运行期只发这一次请求**：`city()` 命中缓存直接返回，不会每次都联网。
   想重新查用 `IpLocation.refreshAsync()`。

联网查询会依次请求若干公共接口，认常见的字段名（`city` / `pro` / `addr` …），
任一个成功就用；只给整串地址的那种（`addr = "江西省南昌市"`）会切出市名。
默认接口国内可用（含一个 GBK 的），内容可以换成自己的：

```java
ContentConfig.setIpLocationUrls("https://my-api.example.com/geo");
```

城市名归一化：去掉后缀「市」（`南昌市 → 南昌`）、去掉空白；
**自治州的「州」不去**（`湘西土家族苗族自治州` 去掉「州」就废了）。

> 如果你希望**完全离线也能精确到市**，唯一的办法是随内容带一份 IP 库：
> 那属于内容侧的数据文件（框架按约定不携带任何数据），
> 需要的话可以加一个「内容提供 IP 库路径」的接口，框架只负责查表。

---

## 内容初始化里的完整例子

```java
// 断网就挡住（不联网没法正常玩的游戏再打开）
ContentConfig.setOfflinePromptEnabled(true);

// 探测端点 / 取时地址 / 属地接口（都可选，不设用框架默认）
ContentConfig.setNetworkEndpoints("223.5.5.5:443", "1.1.1.1:443");
ContentConfig.setTrustedTimeUrls("https://www.baidu.com");
ContentConfig.setIpLocationUrls("https://whois.pconline.com.cn/ipJson.jsp?json=true");
```

运行期读取（内容/插件的日常用法）：

```java
boolean online = Starveil.network().isOnline();
Instant now = Starveil.time().trustedNow();
```

## 线程与退出

- 探测、同步、属地查询都在**守护线程**上跑：不阻塞 JavaFX 线程，也不会拦住游戏退出。
- `Starveil.network().addListener` 的回调在探测线程上触发，碰 UI 要自己 `Platform.runLater`。
- `NetworkStatus.stop()` 会注销系统回调；一般不需要调（进程退出时系统自己清理）。


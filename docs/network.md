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
> 而玩家的游戏其实连得上。所以框架把它当**首选信号**而不是唯一信号，
> 并且给断网提示留了特殊键（见下一节）。

> **首次探测出结果前 `isOnline()` 返回 `true`。** 宁可先当「有网」，
> 也不要在还没测出来的那几百毫秒里给玩家弹一个「你没网」。

## 2. 断网提示：`OfflinePrompt`

```java
ContentConfig.setOfflinePromptEnabled(true);   // 默认关闭
ContentConfig.setOfflineBypassKey("F8");       // 可选，默认就是 F8
```

开启后：探测到断网 → 盖住整个画面的提示；**联网后自动消失**。
没有「知道了」按钮 —— 这是给「不联网就没法正常玩」的游戏用的，
让玩家点掉继续玩一个坏掉的游戏没有意义。

**留了一个特殊键**（默认 `F8`）：按一次，本次会话不再提示（恢复联网后再断开仍会提示）。
理由很实际：网络判断会有假阴性（公共 WiFi 的强制门户、公司代理、探测端点刚好被墙），
一旦判断错了，玩家就被一个永不消失的弹窗关在门外 —— 那比断网本身严重得多。

提示挂在**场景根**上（不是画布里的 modalHost），所以主菜单阶段也会提示。

**怎么测**：拔网线 / 关 WiFi / 断路由器的 WAN，然后看任务栏图标 ——
两者应该同时变（框架问的就是同一个数据源）。日志里会有
`联网状态: OFFLINE（已断开）`，启动时还会写一行 `联网判定来源: ...`。

## 3. 可信时间：`TrustedTime`

```java
TrustedTime.loadFromConfig();          // 启动时恢复上次的基准（AppEntry 已经调了）
TrustedTime.syncAsync();               // 联网同步一次（异步）
Instant now = TrustedTime.now();       // 可信时间
LocalDateTime local = TrustedTime.nowLocal();
boolean synced = TrustedTime.isSynced();
long base = TrustedTime.baseTrustedMillis();   // 基准时间戳
```

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
ContentConfig.setOfflineBypassKey("F8");

// 探测端点 / 取时地址 / 属地接口（都可选，不设用框架默认）
ContentConfig.setNetworkEndpoints("223.5.5.5:443", "1.1.1.1:443");
ContentConfig.setTrustedTimeUrls("https://www.baidu.com");
ContentConfig.setIpLocationUrls("https://whois.pconline.com.cn/ipJson.jsp?json=true");
```

## 线程与退出

- 探测、同步、属地查询都在**守护线程**上跑：不阻塞 JavaFX 线程，也不会拦住游戏退出。
- `NetworkStatus.addListener` 的回调在探测线程上触发，碰 UI 要自己 `Platform.runLater`。
- `NetworkStatus.stop()` 会注销系统回调；一般不需要调（进程退出时系统自己清理）。


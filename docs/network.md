# 联网 / 时间 / IP 属地

框架提供四件事，都在 `com.xiaowu.game.starveil.infrastructure.net`：

| 能力 | 入口 | 线程 |
|---|---|---|
| 是否联网 | `NetworkStatus` | 后台探测，查询不阻塞 |
| 断网提示（强制） | `ui.overlay.OfflinePrompt` | 需要内容开启 |
| 可信时间 | `TrustedTime` | 同步在后台，`now()` 随时可读 |
| IP 属地（市级） | `IpLocation` | 联网查询在后台 |

**框架不联网也能跑**：四项能力都有「没网时怎么办」的兜底（见下），
没有任何一项会阻塞启动或在断网时抛异常。

---

## 1. 是否联网：`NetworkStatus`

```java
NetworkStatus.start();                 // 起后台探测（幂等；isOnline() 也会按需拉起）
NetworkStatus.checkNow();              // 立刻测一次（异步）
boolean online = NetworkStatus.isOnline();

NetworkStatus.addListener(state -> {   // 状态变化（在探测线程上）
    // 注意：要碰 UI 请自己 Platform.runLater
});
```

**怎么判断**：真的去 **TCP 连一个公网地址**。不这么做就没有可靠办法 ——
网卡有地址只说明连着路由器，`InetAddress.isReachable` 又经常被防火墙静默丢掉。
默认端点是公网 DNS 的 443 端口（`223.5.5.5` / `1.1.1.1` / `8.8.8.8`）：
纯 IP 不需要先解析域名（断网时那一步会先失败，反而看不出是谁的问题），
任何一个能连上就算在线，国内国外都可用。

```java
ContentConfig.setNetworkEndpoints("223.5.5.5:443", "my-server.example.com:443");
```

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

## 3. 可信时间：`TrustedTime`

```java
TrustedTime.loadFromConfig();          // 启动时读回上次的校正量（AppEntry 已经调了）
TrustedTime.syncAsync();               // 联网同步一次（异步）
Instant now = TrustedTime.now();       // 可信时间
LocalDateTime local = TrustedTime.nowLocal();
long skew = TrustedTime.offsetMillis();// 可信时间 − 本机时间
boolean synced = TrustedTime.isSynced();
```

**为什么需要**：本机时钟由玩家说了算，改一下系统时间就能跳过冷却、刷新每日奖励、
伪造存档时间戳。需要「真的几点」时得问网络。

**怎么取**：读 HTTP 响应的 **`Date` 头**（RFC 7231 规定由服务器生成）。
相比 NTP：走 443/80 几乎不会被防火墙拦，实现几十行、没有额外依赖；
代价是精度到秒 —— 冷却、每日重置这类用途够用。

```java
ContentConfig.setTrustedTimeUrls("https://www.baidu.com", "https://www.bing.com");
```

三种历史格式都认（RFC 1123 / RFC 850 / asctime，后两种几乎没有服务器会发，
但规范要求接收方支持）。取回的是**校正量**而不是一次性时间戳：
断网期间 `now()` 继续用这个差值校正，不会因为一次同步失败就退回裸的本机时钟。
校正量还会写进全局配置（`starveil:trusted_time_offset`），下次启动先沿用；
不过本机时钟被改得很大时旧差值也会失准，所以每次启动都值得同步一次。

> 对时间可信度要求极高的场景（防作弊积分榜之类），HTTP Date 只到秒、
> 且依赖你对那个站点的信任 —— 那种场合应当用自己服务端签名的时间。

## 4. IP 属地（市级）：`IpLocation`

```java
IpLocation.Result r = IpLocation.resolveOnce();   // 阻塞；UI 用 resolveAsync()
String city = r.city();                           // 「南昌」（已去掉「市」字）
String region = r.region();                       // 「江西」
boolean exact = r.isExact();                      // 是不是真按 IP 查出来的

IpLocation.city();                                // 快捷方式：有联网结果用结果，否则给时区猜测
```

> ### ⚠️ 纯离线拿不到准确的「市」
>
> 市一级信息只存在于 IP 归属地库里：要么联网查，要么随游戏带一份几十 MB 的数据。
> 框架按约定**不携带任何数据文件**，所以离线只能给一个**粗略猜测** ——
> 按系统时区（`Asia/Shanghai` → 上海）。
> 在国内大家都用 `Asia/Shanghai`，所以这个猜测对很多人是**错的**
> （在南昌会猜成上海）。
>
> 因此返回值带 `Source`：只有 `Source.IP` 才是查出来的，`Source.TIMEZONE`
> 只是兜底猜测。**按城市发限定奖励这类用途不能用猜测值** —— 用 `isExact()` 挡一下。

联网查询会依次请求若干公共接口，认常见的字段名（`city` / `pro` / `addr` …），
任一个成功就用；`addr` 那种一整串「江西省南昌市」会用
`cityFromAddress` 切出市名。默认接口国内可用（含一个 GBK 的），
内容可以换成自己的：

```java
ContentConfig.setIpLocationUrls("https://my-api.example.com/geo");
```

城市名归一化规则：去掉后缀「市」（`南昌市 → 南昌`）、去掉空白；
**自治州的「州」不去**（`湘西土家族苗族自治州` 去掉「州」就废了）。

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
- `NetworkStatus.stop()` 可以停掉探测；一般不需要调。

# 许可与「我的游戏要不要开源」

> **这不是法律意见。** 下面是按 AGPL-3.0 / LGPL-3.0 / MPL-2.0 条文本身写出的分析，
> 帮你把问题问对、把选项摆清楚。真要做商业决策，请咨询律师。

---

## 一、现状

框架以 **AGPL-3.0** 开源（见 [LICENSE](../LICENSE)）。AGPL-3.0 第 5(c) 条是最关键的一句：

> You must license the entire work, as a whole, under this License to anyone who
> comes into possession of a copy. This License will therefore apply ... to the
> whole of the work, **and all its parts, regardless of how they are packaged**.

「无论怎么打包」这几个字是重点：**是否分成两个 jar、是否用反射加载，都不影响判断**。
判断依据是「两者是否结合成一个更大的程序」，而不是文件怎么摆。

---

## 二、AGPL 下，游戏作者有什么选择

设某位开发者用本框架做了一款游戏，想把游戏**以二进制形式**发给玩家（免费或收费），
但不想公开游戏源码。在纯 AGPL 下只有这几种可能：

| # | 做法 | 结果 |
|---|---|---|
| 1 | 公开游戏源码，以 AGPL 授权 | 合规，但不是他想要的 |
| 2 | 游戏与框架结合成一个程序后分发 | **必须**把整个程序（含游戏代码）以 AGPL 授权 |
| 3 | 只分发游戏代码（不含框架），并让玩家自己装框架 | 需要论证「独立作品」。但游戏代码按设计**必须**调用框架 API 才能运行，很难论证它不是衍生作品 |
| 4 | 从版权方（你）另行取得授权 | 合规 —— 你拥有版权，可以双授权 |

**结论：纯 AGPL 下没有「打包自己代码而无需开源」的干净做法。**
唯一稳妥的两条路是「全部 AGPL」或「另行取得授权」。

还有一点容易忽略：即便不谈源码，AGPL 第 6 条对**消费者产品**（User Product）
还要求随目标码提供 *Installation Information* —— 让接收者能把自己修改过的框架
版本装进这个产品。对游戏来说这可能是很重的义务。

---

## 三、你手里有什么牌

**你是版权持有人。** 这一条决定了：你可以单方面给下游额外许可
（AGPL-3.0 第 7 条明确允许 *additional permissions*），也可以随时改成别的协议。
下游开发者没有这个自由 —— 他们只能接受你给的条款。

所以要回答「能不能让其它开发者打包自己的代码而无需开源」，
答案取决于**你愿意给什么**，而不是协议本身能不能。

---

## 四、四种做法

### 做法 A：AGPL-3.0 + 游戏内容例外（推荐，如果你要保留 AGPL 的强 copyleft）

保留框架的 AGPL 授权不变，另外给「用它做的游戏」一个明确的链接例外。
这正是 GNU Classpath / GCC runtime library 那套 **Classpath Exception** 的思路：
库本身 copyleft（改了库必须开源），但链接它的程序不受传染。

`LICENSE` 里追加一段（放在 AGPL 全文之后，或在 `LICENSE-EXCEPTION` 单列一个文件）：

```
Starveil Framework — Game Content Exception
Version 1.0

As a special exception to the GNU Affero General Public License, the copyright
holders of the Starveil Framework give you permission to link or combine this
framework with independent works that constitute game content (chapter scripts,
story data, maps, textures, audio, fonts, and other assets) and to convey the
resulting combined work under terms of your choice, provided that:

  a) you do not modify the framework itself, or if you do modify it, you license
     your modified version of the framework under the GNU Affero General Public
     License as required by section 5 of that License; and

  b) you do not remove or alter this exception notice.

The game content and the framework are otherwise independent works. This
exception does not grant permission to relicense any part of the framework
under terms other than the GNU Affero General Public License.
```

**效果**：

| 谁 | 义务 |
|---|---|
| 只写游戏内容、不改框架 | 游戏可以闭源、可以收费、可以任意协议 |
| 改了框架 | 改动部分必须以 AGPL 开源（这正是你想要的：改进回流） |
| 把框架用于网络服务 | 框架的 Copyleft 仍然生效 |

**代价**：这已经不是「纯 AGPL-3.0」了，SPDX 标识要写成
`AGPL-3.0-only WITH Starveil-Game-Content-Exception`，而自定义例外会让一些
公司 / 发行平台的法务多问几句（但比纯 AGPL 好通过得多）。
另外，各 Linux 发行版通常不接受带自定义例外的包 —— 不过一个游戏引擎本来也不进发行版。

### 做法 B：换成 LGPL-3.0

- **框架本身**：改动必须开源（copyleft 保住了）。
- **用它做的游戏**：可以闭源，**前提是保持「库」与「应用」的分离** ——
  即框架以独立 jar 提供、游戏以独立 jar 提供、运行时链接。

**优点**：是 OSI / FSF 的标准协议，法务和发行平台都熟，不需要自定义例外。
**代价**：
- 你对「游戏代码必须开源」没有要求 —— 但听起来你本来也不想要这个要求。
- 静态链接 / 打包进同一个 jar 时，LGPL 第 4(d)(0) 条会要求你让用户能够替换
  LGPL 部分的修改版（重新链接的权利）。**所以用 LGPL 就必须保持两 jar 分离**，
  这对框架的 `shadowJar 合包` 方式是个限制 —— 好在 [构建指南](build-guide.md)
  里的「方式二：运行时注入」本来就是分离的。
- LGPL 没有 AGPL 第 13 条的网络条款。

### 做法 C：换成 MPL-2.0

- **文件级** copyleft：框架**源码文件**被修改后，这些文件的开源义务保留；
  但把框架与闭源游戏结合、以及新写的文件，不受影响。
- **优点**：比 LGPL 更简单（没有「必须能重新链接」那套要求），
  和闭源代码混合最省心，商业友好度高，法务几乎没有阻力。
- **代价**：保护比 AGPL/LGPL 弱 —— 别人可以把你的框架改名换皮闭源发布
  （只要不改你原来的那些文件）。

### 做法 D：换成 Apache-2.0 或 MIT

- 最宽松，下游几乎无义务（Apache 多一个专利授权与保留声明的要求）。
- **代价**：完全放弃 copyleft。别人改好框架后可以闭源、可以不再回馈。
  如果你希望「框架的改进回流到社区」，不要选这个。

---

## 五、怎么选

| 你的优先级 | 建议 |
|---|---|
| 强制框架改进回流 + 允许游戏闭源 | **做法 A**（AGPL + 游戏内容例外） |
| 强制框架改进回流 + 用标准协议、不想要自定义条款 | **做法 B**（LGPL-3.0） |
| 想让人放心用、愿意接受保护弱一些 | **做法 C**（MPL-2.0） |
| 最大化采用率，不在乎别人闭源改框架 | 做法 D（Apache-2.0） |
| 就是要「用它做的东西也必须开源」 | 保持纯 AGPL-3.0，不做例外 |

注意一件事：**你自己那款游戏不受这个选择影响。** 你是框架的版权持有人，
可以给自己任意授权（包括闭源、包括商用），也可以双授权给其他人。
协议是给别人用的门槛，不是给你自己上的锁。

---

## 六、无论选哪个协议，都值得做的技术准备

这些做法能让「框架」和「游戏」在事实上就是两个独立作品，
从而在**任何**协议下都让下游更清楚自己的位置：

1. **保持两 jar 分离**（构建指南的「方式二」）。
   框架 jar 原样分发、游戏 jar 单独分发，运行时在 classpath 上相遇。
2. **不要把框架源码复制进游戏项目。** 复制源码这个动作本身就会让
   「独立作品」的论证变得困难。
3. **让内容侧只依赖公开 API。** 框架已经很注意这一点
   （`api/` 门面、`ContentConfig`、`DataKey`、插件注入点）；
   下游也应当只碰这些，而不是去继承框架内部类。
4. **别改框架。** 需要新能力时提交 issue / PR 让框架本身支持 ——
   这既是许可上的澄清，也是工程上唯一能持续升级的路。
5. **在 README 里给下游一段明确的许可说明**（本文件的第二、三节可以直接引用），
   让他们不必自己解读 AGPL。

---

## 七、当前状态与待办

- [x] `LICENSE` —— AGPL-3.0 全文（与 gnu.org 官方文本逐字一致）
- [ ] 决定是否采用**做法 A** 的游戏内容例外
- [ ] 若采用，追加例外文本并把 SPDX 标识改为
      `AGPL-3.0-only WITH Starveil-Game-Content-Exception`
- [ ] 若改用 LGPL-3.0 / MPL-2.0，替换 `LICENSE` 并同步 README

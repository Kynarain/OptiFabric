# 发布用文案(简要描述 / 详细描述)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文(GitHub 的话,README 本身就是详细介绍)。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件把英文放在前两节、中文放后两节;往 CF 粘贴时,每个字段里都先贴英文、再贴中文即可(只想用英文就只贴英文那节)。
> 另:简介(S) 建议不超过一句,用每个语言里的"一句版"最稳。

---

## 一、简要描述(English)

> Run OptiFine on Fabric. Put OptiFabric and your own OptiFine 1.20.6 jar into `mods/` — OptiFabric unpacks, remaps and patches OptiFine at startup so it works alongside Fabric API. Singleplayer, multiplayer, models, chunks and shaders verified in game.

**One-liner:**

> OptiFine on Fabric 1.20.6, with Fabric API loaded alongside.

---

## 二、详细描述(English)

### OptiFabric — OptiFine on Fabric

A Fabric mod that brings **OptiFine** to Fabric. Put OptiFabric and **your own OptiFine 1.20.6 jar** into `mods/` and it takes care of the rest.

> ℹ️ OptiFine is **not** bundled or redistributed. Get OptiFine 1.20.6 from the official site (only preview builds exist for 1.20.6 so far).

### Why it is needed

OptiFine is built for vanilla (and Forge): its patches are compiled against the **official obfuscated** names, while Fabric runs in the **intermediary** namespace. Fabric API also injects into many of the same classes. Put together, the two disagree in ways that are hard to diagnose — different constructor shapes, different synthetic field names, helper methods inlined away, object creation replaced by OptiFine's own subclasses, and so on.

### What it does

At the earliest point of startup (the loader's `preLaunch`), OptiFabric will:

1. run OptiFine's own installer to extract its patches for the game classes;
2. drop the volde-ification and **remap the patches from official to intermediary**;
3. repair the known structural conflicts between OptiFine and Fabric API (each one traced down to the bytecode level);
4. hand the repaired classes to the Fabric Loader's class transformer and cache the result under `<game dir>/.optifine/<version>/`, so later launches reuse it instead of doing the work again.

### Installation

1. Install a 1.20.6 client with **Fabric Loader 0.19.3 or newer**.
2. Put **OptiFabric** and **your own OptiFine 1.20.6 jar** into `.minecraft/mods/`.
   The file is named like `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar` — dropping it in is enough, you do **not** need to run its installer first.
3. Start the game. The OptiFine version appears in the top left of the title screen when it works.

Fabric API can be loaded alongside (this port is adapted for it specifically).

### Requirements

| | |
|---|---|
| Minecraft | 1.20.6 |
| Fabric Loader | 0.19.3 or newer |
| Java | 21+ (tested on Java 25) |
| Side | client |
| Optional | Fabric API (supported) |

### Compatibility issues that are fixed

All of these were found through real crashes and traced to the bytecode:

- Fabric API's `ShaderProgramMixin` injected before `super()` → OptiFine's delegating constructor is rewritten;
- fields OptiFine left obfuscated with a mismatching descriptor (e.g. the particle factory table) → realigned by name, type and stored value;
- private helpers OptiFine inlined away while recompiling → the vanilla method body is restored so injections have a target again;
- methods OptiFine turned into forwarders to its own overloads (chunk building, model baking) → vanilla bodies restored (this was behind "every model fails to bake" and invisible blocks);
- object creation OptiFine redirects to its own subclass (the `ChunkOF` chunk object) → an inert marker puts the injection point back;
- a ported fixer dropped a field initialisation together with the constructor it replaced → the constructor is no longer replaced (this one disconnected multiplayer sessions after two seconds).

The full list (symptom / cause / fix) is in the changelog.

### Known issues

- **Conflicts with Sodium** — both are renderers; do not install them together.
- **Incompatible with RyoamicLights** — OptiFine replaces the whole video settings screen (including its superclass), which makes that mod's injection fail and crashes as soon as the screen is opened. OptiFine has **built-in dynamic lights** (Video Settings → Quality → Dynamic Lights), so it is not needed.
- **Mods that rely on FRAPI/indigo** no longer get indigo's custom rendering; terrain is rendered by OptiFine.
- **OptiFine cannot see resources inside Fabric mods** — you will see `Unknown resource pack type: ...ModNioResourcePack` in the log. This is a limitation on OptiFine's side.
- Shader packs that do not match your OptiFine version log `[Shaders] Invalid program name: ...`; that comes from the shader pack.

### Troubleshooting

**Where is the cache / how do I force a rebuild?**
`<game dir>/.optifine/<OptiFine version>/`. Delete the `.optifine/` folder to force a rebuild.

**Why can't I find `[OptiFabric]` in the log?**
Its output goes to the **launcher console**, not to `logs/latest.log`.

**The game jar cannot be found / I want to point at it manually**
Add `-Doptifabric.mc-jar=<path to the vanilla 1.20.6 client jar>`.

**I want to inspect the patched classes**
Add `-Doptifabric.extract=true`; the remapped OptiFine classes are unpacked to `.optifine/<version>/optifine-classes/`.

### Reporting a problem

Please attach:

- `logs/latest.log` (plus the matching file from `crash-reports/` if it crashed — it ends with an `-- OptiFabric --` section listing the OptiFine version, jar status and remapped jar path);
- your `mods/` folder listing;
- your OptiFine version (e.g. `OptiFine_1.20.6_HD_U_J1_pre18`).

### License and credits

A port of [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric) by Modmuss50 and Chocohead, licensed under **MPL-2.0**.

---

## 三、简要描述(中文)

> OptiFabric 让 OptiFine 与 Fabric 共存。把它和自备的 OptiFine 1.20.6 一起放进 `mods/`,启动时自动完成解包、重映射与兼容性修补。已专门适配 Fabric API;单人、多人、光影与区块渲染均已在真机验证。

**更短的一句版**(GitHub About / 列表摘要):

> 在 Fabric 1.20.6 上运行 OptiFine。与 Fabric API 同时加载也正常。

---

## 四、详细描述(中文)

### OptiFabric — 让 OptiFine 在 Fabric 上跑起来

这是一个 Fabric 模组,它把 **OptiFine** 接进 Fabric 环境。把 OptiFabric 与你**自备的 OptiFine 1.20.6** 一起放进 `mods/`,剩下的交给它。

> ℹ️ 本项目**不包含、也不分发 OptiFine 本体**,请自行从 OptiFine 官网获取 1.20.6 版本(目前只有 preview 构建)。

### 为什么需要它

OptiFine 是为原版(以及 Forge)编写的:它的补丁针对**官方混淆名**编译,而 Fabric 使用 **intermediary** 命名空间;再加上 Fabric API 会往同一批类里注入代码,两者直接放在一起会以各种难以定位的方式崩溃 —— 构造器形状不同、合成字段名不同、方法被内联掉、对象创建被换成 OptiFine 自己的子类,等等。

### 它做了什么

在游戏启动的最早阶段(loader 的 `preLaunch`),OptiFabric 会:

1. 运行 OptiFine 自带的安装器,取出它对原版类的补丁;
2. 去掉 volde 化痕迹,并把补丁从官方混淆名**重映射到 intermediary**;
3. 修正 OptiFine 与 Fabric API 之间已知的结构冲突(逐个定位到字节码层面);
4. 把修好的类交给 Fabric Loader 的类变换器,并在 `<游戏目录>/.optifine/<版本>/` 缓存 —— 二次启动直接复用,不再重算。

### 安装

1. 用 **Fabric Loader 0.19.3 或更高**安装一个 1.20.6 客户端。
2. 把 **OptiFabric** 和**你自备的 OptiFine 1.20.6 jar** 一起放进 `.minecraft/mods/`。
   OptiFine 1.20.6 的文件名形如 `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar`,**直接放进去即可**,不需要先运行它的安装器。
3. 启动游戏。标题界面左上角出现 OptiFine 版本号就说明生效了。

Fabric API 可以一起加载(本模组专门针对它做过适配)。

### 依赖

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.6 |
| Fabric Loader | 0.19.3 或更高 |
| Java | 21 及以上(实测运行于 Java 25) |
| 环境 | 客户端 |
| 可选 | Fabric API(已适配,可同时加载) |

### 已修复的兼容问题(均来自真机崩溃,逐个定位到字节码)

- Fabric API 的 `ShaderProgramMixin` 注入点落在 `super()` 之前 → 重写 OptiFine 的委托构造器;
- 被 OptiFine 留成混淆名、描述符不符的字段(如粒子工厂表)→ 按映射表对齐名字、类型与存入值;
- 被 OptiFine 重编译时内联掉的私有助手方法 → 把原版方法体补回,让注入点重新存在;
- 被 OptiFine 改成"转发给自己重载"的瘦包装方法(区块构建、模型烘焙)→ 恢复原版方法体(方块全消失、56,042 次模型烘焙失败就是这个问题);
- 被 OptiFine 换成自己子类的对象创建(区块对象 `ChunkOF`)→ 插入惰性标记让注入点重新存在;
- 移植修复器替换构造器时丢掉了字段初始化 → 改为不替换构造器(多人服务器两秒后掉线就是这个原因)。

完整清单(症状 / 根因 / 处理)见更新日志。

### 已知问题

- **与 Sodium 冲突**:两者都是渲染器,请勿同时安装。
- **与 RyoamicLights 不兼容**:OptiFine 把视频设置界面整个换成了自己的实现(连父类都换掉),该模组注入失败会导致开界面即崩。OptiFine **自带动态光源**(视频设置 → 品质 → 动态光源),不需要它。
- **依赖 FRAPI/indigo 的模组**不再获得 indigo 的自定义渲染(地形由 OptiFine 渲染)。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现 `Unknown resource pack type: ...ModNioResourcePack`,这是 OptiFine 侧的限制。
- 光影包与 OptiFine 版本不完全匹配时会有 `[Shaders] Invalid program name: ...` 报错,属光影包自身问题。

### 常见问题

**缓存放在哪?想强制重建怎么办?**
`<游戏目录>/.optifine/<OptiFine 版本>/`。删掉 `.optifine/` 目录即可强制重新生成。

**为什么日志里搜不到 `[OptiFabric]`?**
它的输出走**启动器控制台**,不在 `logs/latest.log` 里(loader 只把 log4j 的输出写进日志文件)。

**找不到原版 jar / 想手动指定?**
加启动参数 `-Doptifabric.mc-jar=<原版 1.20.6 client jar 路径>`。

**想排查补丁结果?**
加 `-Doptifabric.extract=true`,重映射后的 OptiFine 类会解包到 `.optifine/<版本>/optifine-classes/`。

**进游戏后模型全没了 / 开存挡报"网络协议错误"?**
这类问题是补丁没生效或 OptiFine 版本不匹配,请附日志反馈(见下)。

### 反馈问题时请附上

- `logs/latest.log`;若崩溃,再附 `crash-reports/` 里对应的报告(其末尾有一段 `-- OptiFabric --`,包含 OptiFine 版本、jar 状态与重映射 jar 路径);
- `mods/` 文件夹的文件列表;
- 你的 OptiFine 版本(例如 `OptiFine_1.20.6_HD_U_J1_pre18`)。

### 许可与致谢

本项目是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**。
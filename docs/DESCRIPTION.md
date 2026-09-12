# 发布用文案(简要描述 / 详细描述)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文(GitHub 的话,README 本身就是详细介绍)。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件把英文放在前两节、中文放后两节;往 CF 粘贴时,每个字段里都先贴英文、再贴中文即可(只想用英文就只贴英文那节)。
> 另:简介(S) 建议不超过一句,用每个语言里的"一句版"最稳。
> 本文件对应 **Minecraft 1.21.11** 版本(`OptiFabric-1.0.0+mc1.21.11.jar`)。

---

## 一、简要描述(English)

> Run OptiFine on Fabric. Put OptiFabric and your own OptiFine jar into `mods/` — OptiFabric unpacks, remaps and patches OptiFine at startup so it works alongside Fabric API. One jar per Minecraft release, for every 1.21.x version OptiFine has a build for (1.21 – 1.21.11). Verified in game on 1.21.11: singleplayer, multiplayer, models, chunks, shaders and the F3 debug screen.

**One-liner:**

> OptiFine on Fabric 1.21 – 1.21.11, with Fabric API loaded alongside.

---

## 二、详细描述(English)

### OptiFabric — OptiFine on Fabric (1.21 – 1.21.11)

A Fabric mod that brings **OptiFine** to Fabric. Put OptiFabric and **your own OptiFine jar** into `mods/` and it takes care of the rest.

> ℹ️ OptiFine is **not** bundled or redistributed. Get the build matching your Minecraft version from the official site (e.g. `OptiFine_1.21.11_HD_U_J9.jar`) and drop it in — you do **not** need to run its installer.

### Supported versions

One jar per release (each carries that release's mappings), for every 1.21.x version OptiFine ships a build for:

| Minecraft | OptiFabric file | OptiFine build |
|---|---|---|
| 1.21 | `OptiFabric-1.0.0+mc1.21.jar` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` (preview only) |
| 1.21.1 | `OptiFabric-1.0.0+mc1.21.1.jar` | `OptiFine_1.21.1_HD_U_J1.jar` |
| 1.21.3 | `OptiFabric-1.0.0+mc1.21.3.jar` | `OptiFine_1.21.3_HD_U_J2.jar` |
| 1.21.4 | `OptiFabric-1.0.0+mc1.21.4.jar` | `OptiFine_1.21.4_HD_U_J3.jar` |
| 1.21.6 | `OptiFabric-1.0.0+mc1.21.6.jar` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` |
| 1.21.7 | `OptiFabric-1.0.0+mc1.21.7.jar` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` |
| 1.21.8 | `OptiFabric-1.0.0+mc1.21.8.jar` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` |
| 1.21.9 | `OptiFabric-1.0.0+mc1.21.9.jar` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` |
| 1.21.10 | `OptiFabric-1.0.0+mc1.21.10.jar` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` |
| 1.21.11 | `OptiFabric-1.0.0+mc1.21.11.jar` | `OptiFine_1.21.11_HD_U_J9.jar` |

(OptiFine never shipped a build for 1.21.2 or 1.21.5, so there is no jar for those.) All ten pass the same offline verification — every patched class and every OptiFine class loaded and checked with the JVM verifier plus an ASM data-flow verifier, and five scanners on top; live-verified on 1.21.11 so far.

### Why it is needed

OptiFine is built for vanilla (and Forge): its patches are compiled against the **official obfuscated** names, while Fabric runs in the **intermediary** namespace. Fabric API also injects into many of the same classes. Put together, the two disagree in ways that are hard to diagnose — different constructor shapes, different synthetic field names, helper methods inlined away, object creation replaced by OptiFine's own subclasses, and so on.

### What it does

At the earliest point of startup (the loader's `preLaunch`), OptiFabric will:

1. run OptiFine's own installer to extract its patches for the game classes (1.21.11 ships them as xdelta diffs, handled the same way);
2. drop the volde-ification and **remap the patches from official to intermediary**, with the game jar on the remapper's classpath so overrides in subclasses resolve;
3. repair the known structural conflicts between OptiFine and Fabric API (each one traced down to the bytecode level);
4. hand the repaired classes to the Fabric Loader's class transformer and cache the result under `<game dir>/.optifine/<version>/`, so later launches reuse it instead of doing the work again.

### Installation

1. Install a 1.21.x client with **Fabric Loader 0.19.5 or newer**.
2. Put the **OptiFabric jar for that release** and **your own OptiFine jar for that release** into `.minecraft/mods/`.
   The OptiFine file is named like `OptiFine_1.21.11_HD_U_J9.jar` — dropping it in is enough, you do **not** need to run its installer first.
3. Start the game. The OptiFine version appears on the title screen when it works.

Fabric API can be loaded alongside (this port is adapted for it specifically; verified with the Fabric API release of each version, e.g. 0.141.6+1.21.11).

### Requirements

| | |
|---|---|
| Minecraft | 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 or 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Java | 21+ (tested on Java 25) |
| Side | client |
| Optional | Fabric API (supported, tested with each release's own build) |
| You also need | the OptiFabric jar **and** the OptiFine jar for that same release |

### Compatibility issues that are fixed

All of these were found through real crashes and traced to the bytecode (nine of them on 1.21.11 alone):

- Fabric API's injection targets that OptiFine's recompiled classes no longer contain (methods inlined away, or renamed lambdas with a different signature) → the vanilla method body is restored so injections have a target again. On the releases before 1.21.6 that also covers helpers Fabric API injects into which OptiFine's build for those releases drops (the model baker's deserialisation helper, three InGameHud layers, `Keyboard.method_1454`);
- a Fabric hook that reads a world render context OptiFine's pass structure never fills in → the hook is moved onto code that is never called, so the game stops crashing and the block outline is still drawn;
- the same trick for the moving-blocks renderer hook, whose caller lives in *another* class (those call sites are redirected too, otherwise the hook fires from the injected copy);
- Fabric API's renderer registry being empty while `contains_renderer` keeps Indigo away → an inert placeholder renderer is registered, which also keeps the **F3 debug screen** from crashing;
- item models failing to bake (every item texture missing) → the vanilla body of the item-render method is restored;
- OptiFine's region constructor needing a section position the restored vanilla builder never passes → it is passed through, so chunk rendering no longer NPEs;
- fields OptiFine left obfuscated with a mismatching descriptor → realigned by name, type and stored value;
- synthetic `this$0` / `val$…` fields (several of them with the same type) → paired by declaration order and renamed to what mods shadow;
- object creation OptiFine redirects to its own subclass (the `ChunkOF` chunk object) → an inert marker puts the injection point back.

The full list (symptom / cause / fix, per release) is in the changelog and in `docs/DEVELOPMENT.md`.

### Verified state

Offline, for **every supported release**, each class is loaded and linked in a single loader (the same way the game does it) and checked with the JVM verifier plus an ASM data-flow verifier — 425 to 570 patched game classes and 773 to 874 OptiFine classes per release, 0 failures, 0 verifier problems, plus five scanners (mixin member references, `@At` points, abstract contracts/overrides/references and invokedynamic handles) with nothing left but the injection points of the deliberately disabled Indigo. In game (1.21.11): startup, title screen, singleplayer, **multiplayer server**, block/chunk/item rendering, **shaders** (`ComplementaryReimagined` loaded), F3 debug screen — with 0 `[ERROR]` lines and no crash report in the final session.

### Known issues

- **Conflicts with Sodium** — both are renderers; do not install them together.
- **Incompatible with RyoamicLights** — OptiFine replaces the whole video settings screen (including its superclass), which makes that mod's injection fail and crashes as soon as the screen is opened. OptiFine has **built-in dynamic lights** (Video Settings → Quality → Dynamic Lights), so it is not needed. (Confirmed on the 1.20.6 port; declared the same way here.)
- **Mods that rely on FRAPI/indigo** no longer get indigo's custom rendering; terrain is rendered by OptiFine. Fabric's renderer API is present but backed by a placeholder, so the F3 "Renderer:" line shows `OptifineRendererPlaceholder`.
- **Two Fabric API hooks are intentionally inert**: the `BEFORE_BLOCK_OUTLINE` event does not fire (the outline is still drawn), and the Fabric renderer's moving-block hook is bypassed (moving blocks are drawn by the vanilla path).
- **OptiFine cannot see resources inside Fabric mods** — you will see `Unknown resource pack type: ...ModNioResourcePack` in the log. This is a limitation on OptiFine's side.
- Shader packs log warnings like `Unknown macro value: IRIS_VERSION` or `ParseException: Model variable not found: ...`; those come from the shader pack, not from this mod.

### Troubleshooting

**Where is the cache / how do I force a rebuild?**
`<game dir>/.optifine/<OptiFine version>/`. Delete the `.optifine/` folder to force a rebuild.

**Why can't I find `[OptiFabric]` in the log?**
Its output goes to the **launcher console**, not to `logs/latest.log`.

**The game jar cannot be found / I want to point at it manually**
Add `-Doptifabric.mc-jar=<path to the vanilla 1.21.11 client jar>`.

**I want to inspect the patched classes**
Add `-Doptifabric.extract=true`; the remapped OptiFine classes are unpacked to `.optifine/<version>/optifine-classes/`.

### Reporting a problem

Please attach:

- `logs/latest.log` (plus the matching file from `crash-reports/` if it crashed — it ends with an `-- OptiFabric --` section listing the OptiFine version, jar status and remapped jar path);
- your `mods/` folder listing;
- your OptiFine version (e.g. `OptiFine_1.21.11_HD_U_J9`).

### License and credits

A port of [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric) by Modmuss50 and Chocohead, licensed under **MPL-2.0**. OptiFine itself is not included or redistributed.

---

## 三、简要描述(中文)

> OptiFabric 让 OptiFine 与 Fabric 共存。把它和自备的、同版本的 OptiFine 一起放进 `mods/`,启动时自动完成解包、重映射与兼容性修补。**每个 Minecraft 版本一个 jar**,覆盖 OptiFine 出过构建的全部 1.21.x(1.21 ~ 1.21.11)。已专门适配 Fabric API;1.21.11 已真机验证单人、多人、光影、区块与物品渲染、F3 调试屏。

**更短的一句版**(GitHub About / 列表摘要):

> 在 Fabric 1.21 ~ 1.21.11 上运行 OptiFine。与 Fabric API 同时加载也正常。

---

## 四、详细描述(中文)

### OptiFabric — 让 OptiFine 在 Fabric 上跑起来(1.21 ~ 1.21.11)

这是一个 Fabric 模组,它把 **OptiFine** 接进 Fabric 环境。把**对应版本的** OptiFabric 与你**自备的同版本 OptiFine** 一起放进 `mods/`,剩下的交给它。

> ℹ️ 本项目**不包含、也不分发 OptiFine 本体**,请自行从 OptiFine 官网获取与你游戏版本一致的那份(如 1.21.11 用 `OptiFine_1.21.11_HD_U_J9.jar`),**直接放进去即可**,不需要先运行它的安装器。

### 支持的版本

**每个 Minecraft 版本一个 jar**(每个 jar 里打包的是该版本的 `official→intermediary` 映射表,`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本),覆盖 OptiFine 出过构建的全部 1.21.x:

| Minecraft | OptiFabric 文件 | OptiFine 构建 |
|---|---|---|
| 1.21 | `OptiFabric-1.0.0+mc1.21.jar` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar`(只有 preview) |
| 1.21.1 | `OptiFabric-1.0.0+mc1.21.1.jar` | `OptiFine_1.21.1_HD_U_J1.jar` |
| 1.21.3 | `OptiFabric-1.0.0+mc1.21.3.jar` | `OptiFine_1.21.3_HD_U_J2.jar` |
| 1.21.4 | `OptiFabric-1.0.0+mc1.21.4.jar` | `OptiFine_1.21.4_HD_U_J3.jar` |
| 1.21.6 | `OptiFabric-1.0.0+mc1.21.6.jar` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` |
| 1.21.7 | `OptiFabric-1.0.0+mc1.21.7.jar` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` |
| 1.21.8 | `OptiFabric-1.0.0+mc1.21.8.jar` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` |
| 1.21.9 | `OptiFabric-1.0.0+mc1.21.9.jar` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` |
| 1.21.10 | `OptiFabric-1.0.0+mc1.21.10.jar` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` |
| 1.21.11 | `OptiFabric-1.0.0+mc1.21.11.jar` | `OptiFine_1.21.11_HD_U_J9.jar` |

(OptiFine 没出过 1.21.2 / 1.21.5 的构建,所以这两版没有对应 jar。)10 个版本都通过了同一套离线校验(每个补丁类与每个 OptiFine 类都在与游戏一致的单一加载器里用 JVM 验证器 + ASM 数据流验证器双向检查,再加 5 个扫描器);真机验收目前只有 1.21.11 完成。

### 为什么需要它

OptiFine 是为原版(以及 Forge)编写的:它的补丁针对**官方混淆名**编译,而 Fabric 使用 **intermediary** 命名空间;再加上 Fabric API 会往同一批类里注入代码,两者直接放在一起会以各种难以定位的方式崩溃 —— 构造器形状不同、合成字段名不同、方法被内联掉、对象创建被换成 OptiFine 自己的子类,等等。

### 它做了什么

在游戏启动的最早阶段(loader 的 `preLaunch`),OptiFabric 会:

1. 运行 OptiFine 自带的安装器,取出它对原版类的补丁(1.21.6 起的 OptiFine 用 xdelta 差分包,处理方式一致);
2. 去掉 volde 化痕迹,并把补丁从官方混淆名**重映射到 intermediary**(重映射时把游戏 jar 一起放进 classpath,否则子类里覆写的方法继承不到映射);
3. 修正 OptiFine 与 Fabric API 之间已知的结构冲突(逐个定位到字节码层面);
4. 把修好的类交给 Fabric Loader 的类变换器,并在 `<游戏目录>/.optifine/<版本>/` 缓存 —— 二次启动直接复用(1–2 秒)。

### 安装

1. 用 **Fabric Loader 0.19.5 或更高**安装一个 1.21.x 客户端。
2. 把**对应这个版本的 OptiFabric jar** 和**你自备的、同版本的 OptiFine jar** 一起放进 `.minecraft/mods/`。
   OptiFine 的文件名形如 `OptiFine_1.21.11_HD_U_J9.jar`,**直接放进去即可**,不需要先运行它的安装器。
3. 启动游戏。标题界面出现 OptiFine 版本号就说明生效了。

Fabric API 可以一起加载(本模组专门针对它做过适配;每个版本都用该版本自己的 Fabric API 测过,例如 1.21.11 用 0.141.6+1.21.11)。

### 依赖

| 项目 | 要求 |
|---|---|
| Minecraft | 1.21 / 1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 |
| Fabric Loader | 0.19.5 或更高 |
| Java | 21 及以上(实测运行于 Java 25) |
| 环境 | 客户端 |
| 可选 | Fabric API(已适配,每个版本用该版本自己的构建测过) |
| 另需 | **同一个版本**的 OptiFabric jar 与 OptiFine jar |

### 已修复的兼容问题(均来自真机崩溃,逐个定位到字节码)

仅 1.21.11 移植就修了 9 类:

- 被 OptiFine 重编译后**消失的注入目标**(方法被内联掉,或 lambda 改名且签名多了参数)→ 把原版方法体补回,让注入点重新存在。**1.21.5 及更早**还要额外补上该版本 Fabric API 会注入、而 OptiFine 的对应构建丢掉的助手方法(模型烘焙器的反序列化助手、InGameHud 的三个层、`Keyboard.method_1454`);
- Fabric 的方块描边钩子读的渲染上下文 OptiFine 从不填充 → 把钩子挪到**没人调用**的代码上,不再崩,描边照画;
- 同一招用在**移动方块**的渲染钩子上会失效:它的调用者在**另一个类**里,按名字调到的是被注入的副本 → 连调用点一起改到真实方法体上;
- `contains_renderer` 让 Indigo 退场后,Fabric 的渲染器注册表是空的 → 注册一个惰性占位渲染器,顺便修掉**一按 F3 就崩**;
- 物品模型全部烘焙失败(表现为**所有物品贴图丢失**)→ 恢复物品渲染方法的原版方法体;
- OptiFine 的区域构造器需要 section 位置,而恢复出的原版构建方法从不传 → 补上,区块渲染不再 NPE;
- 被 OptiFine 留成混淆名、描述符不符的字段 → 按映射表对齐名字、类型与存入值;
- 合成字段 `this$0`/`val$…`(其中几个类型完全相同)→ 按声明顺序配对并改成模组能 shadow 的名字;
- 被 OptiFine 换成自己子类的对象创建(区块对象 `ChunkOF`)→ 插入惰性标记让注入点重新存在。

另外,`StubInjectionTargetFix` / `CallSiteRedirectFix` 这类"按名字 + 描述符"匹配的修复器改成了**按名字匹配、描述符可选** —— 同一个方法在不同版本描述符不同(1.21.8 的 `method_62210` 收 `Camera`,1.21.11 收 `Vec3d`),写死描述符会让修复器在别的版本上静默失效。

完整清单(症状 / 根因 / 处理,按版本)见更新日志与 `docs/DEVELOPMENT.md`。

### 验证状态

离线:支持的全部 10 个版本,每一版都把**与游戏一致的单一加载器**里的所有类逐个加载+链接,并用 JVM 验证器与 ASM 数据流验证器双向检查 —— 每个版本 425~570 个被补丁的游戏类、773~874 个 OptiFine 自身的类全部通过,0 失败、0 验证器问题;再加 5 个扫描器(mixin 成员引用、`@At` 注入点、抽象契约/覆写/引用、invokedynamic 句柄),除了**已被有意停用**的 indigo 的注入点以外没有遗留。真机(目前只有 1.21.11 完成):启动、主界面、单人世界、**多人服务器**、方块/区块/物品渲染、**光影**(`ComplementaryReimagined` 加载成功)、F3 调试屏;最近一轮会话 `[ERROR]` 0 条、无崩溃报告。

### 已知问题

- **与 Sodium 冲突**:两者都是渲染器,请勿同时安装。
- **与 RyoamicLights 不兼容**:OptiFine 把视频设置界面整个换成了自己的实现(连父类都换掉),该模组注入失败会导致开界面即崩。OptiFine **自带动态光源**(视频设置 → 品质 → 动态光源),不需要它。(该结论来自 1.20.6 移植的实测,1.21.11 沿用同样的声明。)
- **依赖 FRAPI/indigo 的模组**不再获得 indigo 的自定义渲染(地形由 OptiFine 渲染);Fabric 的渲染器 API 由一个占位实现顶着,F3 的 `Renderer:` 一行会显示 `OptifineRendererPlaceholder`。
- **两个 Fabric API 钩子被有意中和**:`BEFORE_BLOCK_OUTLINE` 事件不再触发(描边照画);移动方块的 FRAPI 渲染钩子失效(移动方块由原版路径正常渲染)。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现 `Unknown resource pack type: ...ModNioResourcePack`,这是 OptiFine 侧的限制。
- 光影包会打印 `Unknown macro value: IRIS_VERSION`、`ParseException: Model variable not found: ...` 之类的警告,属光影包自身与 OptiFine 版本的匹配问题。

### 常见问题

**缓存放在哪?想强制重建怎么办?**
`<游戏目录>/.optifine/<OptiFine 版本>/`。删掉 `.optifine/` 目录即可强制重新生成。

**为什么日志里搜不到 `[OptiFabric]`?**
它的输出走**启动器控制台**,不在 `logs/latest.log` 里(loader 只把 log4j 的输出写进日志文件)。

**找不到原版 jar / 想手动指定?**
加启动参数 `-Doptifabric.mc-jar=<原版 1.21.11 client jar 路径>`。

**想排查补丁结果?**
加 `-Doptifabric.extract=true`,重映射后的 OptiFine 类会解包到 `.optifine/<版本>/optifine-classes/`。

**物品贴图全丢 / 区块渲染崩 / 按 F3 崩?**
这几个在 1.21.11 上都已修复(见上)。若仍遇到,请附日志反馈(见下)。

### 反馈问题时请附上

- `logs/latest.log`;若崩溃,再附 `crash-reports/` 里对应的报告(其末尾有一段 `-- OptiFabric --`,包含 OptiFine 版本、jar 状态与重映射 jar 路径);
- `mods/` 文件夹的文件列表;
- 你的 OptiFine 版本(例如 `OptiFine_1.21.11_HD_U_J9`)。

### 许可与致谢

本项目是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**。OptiFine 本体不包含、也不随本项目分发。

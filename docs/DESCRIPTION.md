# 发布用文案(简要描述 / 详细描述)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文(GitHub 的话,README 本身就是详细介绍)。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件把英文放在前两节、中文放后两节;往 CF 粘贴时,每个字段里都先贴英文、再贴中文即可(只想用英文就只贴英文那节)。
> 另:简介(S) 建议不超过一句,用每个语言里的"一句版"最稳。
> 本文件覆盖 **26.x** 这一条线,目前只有 **26.1.2** 一个产物:`OptiFabric-Reforged-2.0.0+mc26.1.2.jar`。26.1 起 Minecraft **未混淆**(官方名即运行名),而且**要求 Java 25**;另一条线(1.21.x,混淆名 + yarn/intermediary,十个 MC 版本)在自己的分支上,两边的 jar 不能互相替代。

---

## 一、简要描述(English)

> Run OptiFine on Fabric. Put OptiFabric and your own OptiFine jar into `mods/` — OptiFabric patches OptiFine into the game at startup so it works alongside Fabric API. Built for **Minecraft 26.1.2**, which ships unobfuscated and needs Java 25. Verified in game: singleplayer, multiplayer, models, chunks, shaders and anti-aliasing.

**One-liner:**

> OptiFine on Fabric for Minecraft 26.1.2, with Fabric API loaded alongside.

---

## 二、详细描述(English)

### OptiFabric Reforged — OptiFine on Fabric (26.1.2)

A Fabric mod that brings **OptiFine** to Fabric. Put OptiFabric and **your own OptiFine jar** into `mods/` and it takes care of the rest.

> ℹ️ OptiFine is **not** bundled or redistributed. Get the 26.1.2 build from the official site (currently `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`) and drop it in — you do **not** need to run its installer.

### Supported versions

**This jar supports Minecraft 26.1.2 only.** Minecraft 26.1 and newer ships **unobfuscated** — the official names are the runtime names — so this line carries no `official → intermediary` mappings at all and does not remap OptiFine:

| Minecraft | OptiFabric file | OptiFine build |
|---|---|---|
| **26.1.2** | `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` (**Java 25**) |

26.1.2 is the only release OptiFine has a build for on this line (26.1, 26.1.1, 26.1.3, 26.2, 26.2.1 and 26.3 have empty build lists), and each new release would need its own port — no jar of this line runs on another one. The other line, 1.21.x (obfuscated, ten releases), lives on its own branch and its jars are not interchangeable with this one.

### Why it is needed

OptiFine is built for vanilla (and Forge): its patches are compiled against one particular build of the game's classes, while Fabric API injects into many of the same classes. Put together, the two disagree in ways that are hard to diagnose — helper methods inlined away, a vanilla method hollowed into a thin wrapper with its body moved into an overload of OptiFine's own, object creation replaced by OptiFine's own subclasses, and so on.

On **26.1.2** the mapping half of the usual story disappears — the game ships unobfuscated, so there is nothing to remap and that whole stage is skipped — but the structural half does not: OptiFine still recompiles every class it patches, and Fabric API still injects into the same methods under the same official names.

### What it does

At the earliest point of startup (the loader's `preLaunch`), OptiFabric will:

1. run OptiFine's own installer to extract its patches for the game classes;
2. drop the volde-ification (no remap stage on this line: the patches are already in the runtime namespace, which is the official one, and the jar carries no mappings at all);
3. repair the known structural conflicts between OptiFine and Fabric API (each one traced down to the bytecode level);
4. hand the repaired classes to the Fabric Loader's class transformer and cache the result under `<game dir>/.optifine/<version>/`, so later launches reuse it instead of doing the work again.

### Installation

1. Install the **26.1.2** client with **Fabric Loader 0.19.5 or newer**, on **Java 25** (26.1.2's own hard requirement).
2. Put `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` and **your own OptiFine 26.1.2 jar** into `.minecraft/mods/`.
   The OptiFine file is named like `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` — dropping it in is enough, you do **not** need to run its installer first.
3. Start the game. The OptiFine version appears on the title screen when it works.

Fabric API can be loaded alongside (this port is adapted for it specifically; verified with 0.155.3+26.1.2).

### Requirements

| | |
|---|---|
| Minecraft | **26.1.2** (no other release) |
| Fabric Loader | 0.19.5 or newer |
| Java | **25** (required by 26.1.2 itself) |
| Side | client |
| Optional | Fabric API (supported, tested with 0.155.3+26.1.2) |
| You also need | the OptiFabric jar **and** the OptiFine jar for 26.1.2 |

### Compatibility issues that are fixed

All of these were found through real crashes and traced to the bytecode. Nearly every conflict sits in the renderer, because that is where OptiFine and Fabric API overlap most — and because the pipeline moved on again in 26.1:

- OptiFine hollows `LevelRenderer.extractBlockOutline` into a thin wrapper and moves the body into an overload of its own, leaving **two methods of one name**. A Fabric mixin that names it without a descriptor then resolves neither and the whole class fails to transform. The vanilla body is restored, and the **overloads vanilla no longer has are dropped** (keeping them makes the injection fail with `LVTGeneratorError` while it looks for method metadata);
- the same trick is harder on **chunk building**: `SectionCompiler.compile`'s public overload is still called from outside and must not be removed → it is **renamed and its call site redirected**, which keeps the contract and puts the injection point back on the real body;
- the new feature pipeline (`BlockFeatureRenderer`) splits block submission into a moving-blocks and a block-models pass, while OptiFine looks for the old names → stub methods are added and both call sites redirected, so moving blocks are submitted from the mod's own phase;
- OptiFine also hollows out `ModelManager`'s bake lambda, `ScreenEffectRenderer.getViewBlockingState` and `CuboidItemModelWrapper.update` → their vanilla bodies are restored (`update` too needs its vanilla-absent overload dropped);
- the chunk object is redirected to OptiFine's own `ChunkOF` → an inert marker is inserted **under the official name** so injections that depend on `LevelChunk` have a target again (without it, opening a world ends in a network protocol error);
- the Fabric renderer API moved into `api.client.renderer.v1` on 26.1, so the placeholder renderer was never registered (the lookup failed and returned quietly) and the first `Renderer.get()` crashed the game. The placeholder also no longer throws: Fabric API's own rendering hooks call it in the middle of ordinary frames, and it answers with inert objects of the right shape instead;
- the `fabric-renderer-api-v1:contains_renderer` key inherited from the 1.21.x line was switching off exactly the rendering plug-in Fabric API keeps asking for. Indigo is not a terrain renderer on 26.1.2 any more (Fabric API moved the terrain and submit-node integration into `fabric-renderer-api-v1` itself, leaving Indigo one item mixin and two accessors), so the key had nothing left to keep away: mods' meshes went into the inert placeholder — invisible, and silent about it. This line no longer declares the key and Indigo registers its own `IndigoRenderer`; the fallback class reads the key back and only steps in where it is declared;
- the anti-aliasing chain is read from `post_effect/` here, so OptiFine's own post-chain files are left exactly as they ship;
- and the one that makes FRAPI mods actually render: Fabric's terrain hook injects into the vanilla chunk-build loop's `BlockPos.betweenClosed` iteration, and OptiFine's own compile overload has no such loop at all, so that hook ran nowhere and a model's own geometry — better grass, anything that has to look at the world around a block — was silently absent. The block tesselation call inside OptiFine's method now goes to our bridge: the quads are produced by Fabric's renderer (ambient occlusion, tint and light included) and handed to the `BlockQuadOutput` OptiFine passes in, so OptiFine still writes every vertex, with its vertex format, layers, lighting and shader attributes. Writing them into the section buffers instead — what Fabric's own hook does on a Fabric client — corrupts them here (a second `BufferBuilder` over the same `ByteBufferBuilder`), which shows up in game as see-through blocks.

The full list (symptom / cause / fix) is in the changelog and in `docs/PORT_26.x.md`.

### Verified state

Offline, every class is loaded and linked in a single loader (the same way the game does it) and checked with the JVM verifier plus an ASM data-flow verifier — **567 patched game classes and 879 OptiFine classes**, 0 failures, 0 verifier problems, plus five scanners (mixin member references, `@At` points, abstract contracts/overrides/references and invokedynamic handles) with **every one of them clean**. (Two OptiFine classes implement NeoForge's SPI and are never loaded on Fabric, so they are excluded.) In game: startup, title screen, singleplayer, chunk rebuilds, block/item/entity rendering, **anti-aliasing**, **shaders** and **multiplayer** all work, with no crash report — and Indigo registers its own renderer (`[Indigo] Registering Indigo renderer!`) with Fabric API's hooks drawing through it. With LambdaBetterGrass installed, its better grass renders and its connected textures are correct, shaders on.

### Known issues

- **Conflicts with Sodium** — both are renderers; do not install them together.
- **26.1.2 requires Java 25** — starting it on Java 21 fails before the game window appears. That is the game's own requirement, not this mod's.
- **Only 26.1.2 is supported** — the other 26.1 patch versions and 26.2+ have no OptiFine build and need their own port.
- **Incompatible with RyoamicLights** — OptiFine replaces the whole video settings screen (including its superclass), which makes that mod's injection fail and crashes as soon as the screen is opened. OptiFine has **built-in dynamic lights** (Video Settings → Quality → Dynamic Lights), so it is not needed. (Confirmed on the 1.20.6 port; declared the same way here.)
- **Mods that rely on FRAPI/indigo** get Indigo's real renderer here, Fabric API's hooks draw through it, and a model's own render-time geometry is rendered — verified with LambdaBetterGrass (better grass and connected textures both correct, shaders on).
- **Two Fabric API hooks are intentionally inert**: the moving-block and block-model submit hooks are redirected to code nobody calls, so those two submits are drawn by the vanilla/OptiFine path; the block-breaking overlay path is untouched and does go through Fabric's renderer.
- **OptiFine cannot see resources inside Fabric mods** — you will see `Unknown resource pack type: ...ModNioResourcePack` in the log. This is a limitation on OptiFine's side.
- Shader packs log warnings like `Unknown macro value: IRIS_VERSION` or `ParseException: Model variable not found: ...`; those come from the shader pack, not from this mod.

### Troubleshooting

**Where is the cache / how do I force a rebuild?**
`<game dir>/.optifine/<OptiFine version>/`. Delete the `.optifine/` folder to force a rebuild.

**Why can't I find `[OptiFabric]` in the log?**
Its output goes to the **launcher console**, not to `logs/latest.log`.

**The game jar cannot be found / I want to point at it manually**
Add `-Doptifabric.mc-jar=<path to the vanilla 26.1.2 client jar>`.

**I want to inspect the patched classes**
Add `-Doptifabric.extract=true`; the prepared OptiFine classes are unpacked to `.optifine/<version>/optifine-classes/`.

### Reporting a problem

Please attach:

- `logs/latest.log` (plus the matching file from `crash-reports/` if it crashed — it ends with an `-- OptiFabric --` section listing the OptiFine version, jar status and runtime jar path);
- your `mods/` folder listing;
- your OptiFine version (e.g. `preview_OptiFine_26.1.2_HD_U_K1_pre2`).

### License and credits

A port of [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric) by Modmuss50 and Chocohead, licensed under **MPL-2.0**. OptiFine itself is not included or redistributed.

---

## 三、简要描述(中文)

> OptiFabric Reforged 让 OptiFine 与 Fabric 共存。把它和自备的、同版本的 OptiFine 一起放进 `mods/`,启动时自动完成解包与兼容性修补。为 **Minecraft 26.1.2** 构建 —— 那一版游戏**未混淆**(官方名即运行名),并且**要 Java 25**。已专门适配 Fabric API;真机验证:单人、多人、光影、抗锯齿、区块与物品渲染。

**更短的一句版**(GitHub About / 列表摘要):

> 在 Fabric Minecraft 26.1.2 上运行 OptiFine。与 Fabric API 同时加载也正常。

---

## 四、详细描述(中文)

### OptiFabric Reforged — 让 OptiFine 在 Fabric 上跑起来(26.1.2)

这是一个 Fabric 模组,它把 **OptiFine** 接进 Fabric 环境。把 **26.1.2 的 OptiFabric** 与你**自备的同版本 OptiFine** 一起放进 `mods/`,剩下的交给它。

> ℹ️ 本项目**不包含、也不分发 OptiFine 本体**,请自行从 OptiFine 官网获取 26.1.2 的那份(目前是 `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`),**直接放进去即可**,不需要先运行它的安装器。

### 支持的版本

**这个 jar 只支持 Minecraft 26.1.2。** 26.1 起游戏**未混淆**(官方名就是运行名),所以这一线**没有 `official→intermediary` 映射表要打包**,也不做重映射:

| Minecraft | OptiFabric 文件 | OptiFine 构建 |
|---|---|---|
| **26.1.2** | `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`(**要 Java 25**) |

26.1.2 是这一线上 OptiFine 出过构建的唯一版本(26.1 / 26.1.1 / 26.1.3 / 26.2 / 26.2.1 / 26.3 的构建列表都是空的),而且每再来一个版本都要各自重新移植 —— **别拿这个 jar 去顶别的版本**。另一条线是 1.21.x(混淆名,十个 MC 版本),在自己的分支上,两边的 jar 不能互相替代。

### 为什么需要它

OptiFine 是为原版(以及 Forge)编写的:它的补丁是照着**某一个具体版本的游戏类**编译的,而 Fabric API 会往同一批类里注入代码,两者直接放在一起会以各种难以定位的方式崩溃 —— 方法被内联掉、原版方法被掏成一层薄壳、真实实现被挪进 OptiFine 自己的重载、对象创建被换成 OptiFine 自己的子类,等等。

**26.1.2 起,"名字对不上"这半边消失了。** 这一版 Minecraft 不再混淆(官方直接发真实名字),Fabric 也不再提供 intermediary,运行时命名空间就是 `official` —— 于是"OptiFine 按混淆名编译"那层错位没有了,重映射整段跳过。但结构层面的修补照旧要做:OptiFine 仍会重编译它补丁的每一个类,而 Fabric API 仍会往同名的方法里注入。

### 它做了什么

在游戏启动的最早阶段(loader 的 `preLaunch`),OptiFabric 会:

1. 运行 OptiFine 自带的安装器,取出它对原版类的补丁;
2. 去掉 volde 化痕迹(这一线**没有重映射那一步**:运行时命名空间就是官方名,jar 里也不打包任何映射表);
3. 修正 OptiFine 与 Fabric API 之间已知的结构冲突(逐个定位到字节码层面);
4. 把修好的类交给 Fabric Loader 的类变换器,并在 `<游戏目录>/.optifine/<版本>/` 缓存 —— 二次启动直接复用(1–2 秒)。

### 安装

1. 用 **Fabric Loader 0.19.5 或更高**安装 **26.1.2** 的客户端,并且**必须用 Java 25**(游戏本身的硬要求,不是本模组的要求)。
2. 把 `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` 和**你自备的 26.1.2 的 OptiFine jar** 一起放进 `.minecraft/mods/`。
   OptiFine 的文件名形如 `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`,**直接放进去即可**,不需要先运行它的安装器。
3. 启动游戏。标题界面出现 OptiFine 版本号就说明生效了。

Fabric API 可以一起加载(本模组专门针对它做过适配;实测 0.155.3+26.1.2)。

### 依赖

| 项目 | 要求 |
|---|---|
| Minecraft | **26.1.2**(不支持别的版本) |
| Fabric Loader | 0.19.5 或更高 |
| Java | **25**(该版本本身的硬要求) |
| 环境 | 客户端 |
| 可选 | Fabric API(已适配,实测 0.155.3+26.1.2) |
| 另需 | 26.1.2 的 OptiFabric jar 与 OptiFine jar |

### 已修复的兼容问题(均来自真机崩溃,逐个定位到字节码)

冲突几乎全集中在渲染器上 —— 那正是 OptiFine 与 Fabric API 重叠最多的地方,而 26.1 的渲染管线又往前迈了一步:

- OptiFine 把 `LevelRenderer.extractBlockOutline` 掏成一层空壳、实现挪进自己的重载 → 补回原版方法体,同时**丢掉原版已经不存在的那几个重载**(留着它们,注入点会因找不到方法元数据而直接报 `LVTGeneratorError`);
- 同一招用到**区块构建**上更棘手:`SectionCompiler.compile` 的公开重载仍被外部调用,**不能删** → 改成**改名 + 把调用点一起重定向**,契约保住了,注入点也回到了真实方法体上;
- 新渲染特性管线(`BlockFeatureRenderer`)把方块提交拆成"移动方块 / 方块模型"两段,OptiFine 却按老名字找注入点 → 补上占位方法,并把两段调用点分别重定向,移动方块改由模组自己的阶段提交;
- OptiFine 还掏空了 `ModelManager` 的烘焙 lambda、`ScreenEffectRenderer.getViewBlockingState`、`CuboidItemModelWrapper.update` → 一律补回原版方法体(`update` 同样要顺带丢掉原版已删的重载);
- 区块对象被换成 OptiFine 自己的 `ChunkOF` → 按**官方名**插入惰性标记,让依赖 `LevelChunk` 的注入点重新存在(没有它就是**开存档时的网络协议错误**);
- Fabric 的渲染器 API 这一版搬到了 `api.client.renderer.v1` → 新旧两个包名依次回退;占位实现也改成**生成惰性桩**(链式调用返回 `this`、getter 返回惰性对象),不再一个方法一个方法地补,杜绝"渲染到一半抛异常";
- **最安静的一条(它从不崩)**:从 1.21.x 继承过来的 `fabric-renderer-api-v1:contains_renderer` 让位键,在这一线关掉的正是 Fabric API 一直要用的那个渲染器 —— 26.1.2 的 indigo 已经不是地形渲染器(地形与提交节点的整合搬进了 `fabric-renderer-api-v1` 自己,它只剩一条物品 mixin 和两个 accessor),键没有东西可"让"了,于是模组生成的网格进了惰性占位:**看不见,而且不报错**。现在这一线不再声明该键,由 Indigo 注册自己的 `IndigoRenderer`;占位类把这个键读回来,只在它确实被声明时兜底;
- **抗锯齿**:这一线的链从 `post_effect/` 解析,所以 OptiFine 自带的那份 post chain 文件一律原样保留;
- **让 FRAPI 模组真的能画出来的那一条**:Fabric 的地形钩子注入在原版区块构建循环的 `BlockPos.betweenClosed` 上,而 OptiFine 自己的 `compile` 里**根本没有那个循环**,于是钩子无处执行,模型**实时生成**的几何(更好的草,以及任何需要看周围方块的几何)**静默消失**。现在 OptiFine 方法里那次方块 tessellate 调用指向我们的桥:几何由 Fabric 的渲染器产出(含 AO、染色与光照),再交给 OptiFine 传进来的 `BlockQuadOutput` —— **顶点仍由 OptiFine 写**(顶点格式、层级、光照、光影属性都是它的)。反过来直接写区块缓冲(Fabric 自己的钩子在纯 Fabric 上就是这么做的)在这里会把数据写坏:同一个 `ByteBufferBuilder` 上出现第二个 BufferBuilder,真机表现为**方块透明**。

完整清单(症状 / 根因 / 处理)见更新日志与 `docs/PORT_26.x.md`。

### 验证状态

离线:把**与游戏一致的单一加载器**里的所有类逐个加载+链接,并用 JVM 验证器与 ASM 数据流验证器双向检查 —— **567 个被补丁的游戏类**与 **879 个 OptiFine 自身的类**全部通过,0 失败、0 验证器问题;再加 5 个扫描器(mixin 成员引用、`@At` 注入点、抽象契约/覆写/引用、invokedynamic 句柄),**全部为 0**。(OptiFine 有 2 个类实现的是 **NeoForge** 的 SPI,在 Fabric 上永远不会加载,已排除。)真机:启动、主界面、单人世界、区块重建、方块/物品/生物渲染、**抗锯齿**、**光影**、**多人**全部正常,无崩溃报告;进世界时 Indigo 注册了真正的渲染器(`[Indigo] Registering Indigo renderer!`),Fabric API 的钩子从它上面绘制。装 LambdaBetterGrass 实测:**"更好的草"正常、连接纹理正确(光影开启)**。

### 已知问题

- **与 Sodium 冲突**:两者都是渲染器,请勿同时安装。
- **26.1.2 必须用 Java 25**:用 Java 21 启动会在游戏窗口出现之前就失败。这是游戏本身的要求、不是本模组的限制。
- **只支持 26.1.2**:26.1 的其他小版本与 26.2+ 没有 OptiFine 构建,需要各自重新移植。
- **与 RyoamicLights 不兼容**:OptiFine 把视频设置界面整个换成了自己的实现(连父类都换掉),该模组注入失败会导致开界面即崩。OptiFine **自带动态光源**(视频设置 → 品质 → 动态光源),不需要它。(该结论来自 1.20.6 移植的实测,这里沿用同样的声明。)
- **依赖 FRAPI/indigo 的模组**在这一线上拿到的是**真正的渲染器**:Indigo 会注册自己的渲染器、Fabric API 的钩子真的从它上面画过去,而且**模组自己实时生成的几何会被渲染**(用 LambdaBetterGrass 实测:更好的草与连接纹理都正常,光影开启)。
- **两个 Fabric API 钩子被有意中和**:"移动方块提交"与"方块模型提交"两处做了重定向,这两类提交由原版/OptiFine 路径绘制;方块破坏裂纹那条没有动,它确实走 Fabric 的渲染器。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现 `Unknown resource pack type: ...ModNioResourcePack`,这是 OptiFine 侧的限制。
- 光影包会打印 `Unknown macro value: IRIS_VERSION`、`ParseException: Model variable not found: ...` 之类的警告,属光影包自身与 OptiFine 版本的匹配问题。

### 常见问题

**缓存放在哪?想强制重建怎么办?**
`<游戏目录>/.optifine/<OptiFine 版本>/`。删掉 `.optifine/` 目录即可强制重新生成。

**为什么日志里搜不到 `[OptiFabric]`?**
它的输出走**启动器控制台**,不在 `logs/latest.log` 里(loader 只把 log4j 的输出写进日志文件)。

**找不到原版 jar / 想手动指定?**
加启动参数 `-Doptifabric.mc-jar=<原版 26.1.2 client jar 路径>`。

**想排查补丁结果?**
加 `-Doptifabric.extract=true`,准备好的 OptiFine 类会解包到 `.optifine/<版本>/optifine-classes/`。

**开存档报网络协议错误 / 物品贴图全丢 / 依赖 FRAPI 的模组几何不显示?**
这几个在 26.1.2 上都已修复(见上)。若仍遇到,请附日志反馈(见下)。

### 反馈问题时请附上

- `logs/latest.log`;若崩溃,再附 `crash-reports/` 里对应的报告(其末尾有一段 `-- OptiFabric --`,包含 OptiFine 版本、jar 状态与运行期 jar 路径);
- `mods/` 文件夹的文件列表;
- 你的 OptiFine 版本(例如 `preview_OptiFine_26.1.2_HD_U_K1_pre2`)。

### 许可与致谢

本项目是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**。OptiFine 本体不包含、也不随本项目分发。

# GitHub Release notes — tag `v2.0.0`(`OptiFabric-Reforged-2.0.0+mc26.1.2.jar`)

> 复制下面 `---` 之间的内容到 GitHub Release 的说明框里(标题用第一行)。英文在前,末尾附中文摘要。
> 标签是**版本号本身**(`v2.0.0`,不带 `+mc`),与已发的 `v1.2.0` 一致;这一行要手改,
> 其余版本号由 `release\version.ps1` 统一改写。

---

## OptiFabric Reforged 2.0.0+mc26.1.2 — OptiFine on Fabric 26.1.2

Run **OptiFine** and **Fabric** in the same 26.1.2 client. Drop OptiFabric and your own OptiFine jar into `mods/`; at startup OptiFabric runs OptiFine's installer, repairs its patches against the structural conflicts with Fabric API, and hands the result to Fabric Loader's class transformer. There is no remapping step: 26.1 and newer ship **unobfuscated**, so the official names are the runtime names.

**Minecraft 26.1.2 requires Java 25** — that is the game's own requirement, and this jar is built and tested on it.

**OptiFine is not bundled or redistributed** — bring your own `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` (OptiFine has only preview builds for 26.1.2).

### New in 2.0.0 — the mod renamed itself, and FRAPI geometry renders again

**The mod id changed: `optifabric` → `optifabric_reforged`**, and the jar is now `OptiFabric-Reforged-…`. That is why this is a major version (SemVer §8: anything that `depends` or `breaks` the old id is affected). Delete the old `OptiFabric-1.2.0+mc26.1.2.jar` before installing this one.

The id is not cosmetic: mods that declare `"breaks": {"optifabric": "*"}` (LambdaBetterGrass does) refuse the 1.21.x line, and Fabric Loader matches on the **id**, not the display name — the only way to be a different mod to them is to be a different mod id. Measured: the unmodified upstream `lambdabettergrass-2.7.2+26.1.1.jar` now starts alongside this mod, better grass and connected textures included.

**Geometry that a mod generates per block position is rendered again.** Fabric's terrain hook injects into the vanilla chunk-build loop, and OptiFine's own compile method has no such loop — the hook ran nowhere, so a model's `emitQuads` was never asked for anything and its geometry was silently absent (better grass, and anything that has to look at the world around a block). The block tesselation call inside OptiFine's method now goes to a bridge: the quads are produced by Fabric's renderer (ambient occlusion, tint and light included) and handed to the `BlockQuadOutput` OptiFine passes in, so OptiFine still writes every vertex — its vertex format, layers, lighting and shader attributes. Only models whose `emitQuads` is declared outside the game are routed; vanilla blocks stay on OptiFine's path.

**The Fabric renderer API is a real renderer again.** 26.1 moved it into `api.client.renderer.v1`, and the lookup that failed quietly left the placeholder unregistered, so the first `Renderer.get()` took the game down; the placeholder also no longer throws — Fabric API's own hooks call it in the middle of ordinary frames, and it answers with inert objects of the right shape. More fundamentally, the `fabric-renderer-api-v1:contains_renderer` key inherited from the 1.21.x line had nothing left to keep away on 26.1.2 (Indigo is not a terrain renderer there any more — Fabric API moved the terrain and submit-node integration into `fabric-renderer-api-v1` itself, leaving Indigo one item mixin and two accessors), so declaring the key switched off the very plug-in Fabric API keeps asking for. This line no longer declares it and Indigo registers its own `IndigoRenderer`.

### What it took for 26.1.2

26.1.2 is not "the same patches at a different version number": being unobfuscated brings its own conflicts, and the render pipeline moved on again. Every one of them was found through a real crash and traced down to the bytecode (the whole record is in [`docs/PORT_26.x.md`](PORT_26.x.md)):

- **Injection points** — OptiFine's recompiler hollows a vanilla method into a thin wrapper and moves the body into an overload of its own, while Fabric API names its target **without a descriptor**: two methods of one name left in the class means MixinExtras cannot build the local-variable context and the whole class fails to transform. Resolved one by one for `LevelRenderer` (`extractBlockOutline`), `ScreenEffectRenderer`, `ModelManager` and `CuboidItemModelWrapper` (the vanilla body comes back, and the overload vanilla no longer has goes away with it), and for `SectionCompiler` the public overload that is still called from outside is **renamed and its call site redirected** instead of removed.
- **The chunk object** — OptiFine creates its own `ChunkOF` instead of a `LevelChunk`, so Fabric's `@At(value = "NEW", …)` point is gone and opening a world ended in a network protocol error; an inert marker puts it back.
- **Anti-aliasing** — the chain id is resolved from `post_effect/` on this release, so OptiFine's own post-chain files are left exactly as they ship. (The 1.21.x line's old repair did the opposite — it deleted them — which is what broke AA there; that repair is gone as of 1.1.2 on both lines.)
- **Two Fabric API submit hooks are intentionally inert**, and redirected the same way as on the 1.21.x line: the moving-block and block-model submits are drawn by the vanilla/OptiFine path. The block-breaking overlay path is untouched and does go through Fabric's renderer.

### Requirements

| | |
|---|---|
| Minecraft | 26.1.2 (**this jar supports no other release**) |
| Fabric Loader | 0.19.5 or newer |
| Java | **25** (required by 26.1.2 itself) |
| Side | client |
| OptiFine | your own 26.1.2 build (tested: `preview_OptiFine_26.1.2_HD_U_K1_pre2`) |
| Fabric API | optional — supported, tested with 0.155.3+26.1.2 |

### Install

1. Install a 26.1.2 Fabric client (Loader 0.19.5+, **Java 25**).
2. Put `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` **and** your OptiFine 26.1.2 jar into that version's `mods/` folder. Do **not** run OptiFine's installer — dropping the file in is enough.
3. Start the game with the **Fabric** profile. The first launch spends a few seconds patching (cached afterwards under `<game dir>/.optifine/<version>/`).

### Verified

- Offline, every class is loaded and linked in a single loader (the same way the game does it) and checked with the JVM verifier plus an ASM data-flow verifier: **567/567 patched game classes** and **879/879 OptiFine classes**, 0 failures, 0 verifier problems. (Two OptiFine classes implement NeoForge's SPI and are never loaded on Fabric; they are excluded.)
- Mixin member references, `@At` injection points, abstract contracts, virtual overrides, member references and invokedynamic handles: **all zero** — cleaner than 1.21.11.
- In game: startup, title screen, singleplayer, chunk rebuilds, block/item/entity rendering, **anti-aliasing**, **shaders**, multiplayer — no crash report. Indigo registers its own renderer (`[Indigo] Registering Indigo renderer!`) and Fabric API's hooks draw through it. With LambdaBetterGrass installed, its better grass renders and its connected textures are correct, shaders on.

### Known limits (deliberate)

- **Conflicts with Sodium** (declared); `no_fog`, `thallium`, `xradiation`, `ryoamiclights` are declared incompatible.
- **Only 26.1.2 is supported.** The other 26.1 patch versions and 26.2+ need their own port — there is no OptiFine build for them, and no jar of this line will run on them.
- **Java 25 is required.** Starting 26.1.2 on Java 21 fails before the game window appears.
- **Mods that rely on FRAPI/Indigo** get Indigo's real renderer here, and a mod's own runtime geometry is rendered; only the moving-block and block-model submit hooks are intentionally inert.
- OptiFine cannot see resources inside Fabric mods (`Unknown resource pack type: ...ModNioResourcePack`) — a limitation on OptiFine's side.
- Shader packs log warnings such as `Unknown macro value: IRIS_VERSION` or `ParseException: Model variable not found: …`; those come from the shader pack, not from this mod.

### Files

| File | SHA-256 |
|---|---|
| `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` (177166 bytes) | `FBB432C2D9C8B0E7E06F0FDA4A0C1B6A8F302D5D09ABD7CE67F13CBE04A5CF60` |

This line builds **exactly one** Minecraft release: `.\gradlew build` → `OptiFabric-Reforged-<version>+mc26.1.2.jar` (the target comes from `gradle.properties`; there is no version switch in the build). The other line — 1.21.x, obfuscated, ten releases — lives on its own branch and its jars are not interchangeable with this one.

Full changelog: [`CHANGELOG.md`](CHANGELOG.md) · Usage, troubleshooting and known issues: [`README.md`](README.md) · Porting record: [`docs/PORT_26.x.md`](docs/PORT_26.x.md) · Verification tooling: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)

### Credits and license

A port of [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric) by Modmuss50 and Chocohead, licensed under **MPL-2.0**. OptiFine itself is neither included nor redistributed; get it from the official site (in China the `bmclapi2.bangbang93.com/optifine/26.1.2/HD_U_K1/pre2` mirror works — note the four-segment path, the patch number carries the `K1` prefix).

---

## 中文摘要

把 OptiFine 接进 Minecraft **26.1.2** 的 Fabric —— 26.1 起游戏**未混淆**,官方名就是运行名,所以这一线没有 yarn、也没有可重映射的 intermediary(26.1.2 只发布占位 `intermediary:0.0.0`),jar 里不含映射表。把本 jar 与自备的 `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` 一起放进 `mods/`,用 Fabric 版本启动即可(**不需要**先运行 OptiFine 安装器);首次启动多花几秒做补丁,之后走缓存。

- **2.0.0 改了两件大事**:①mod id 从 `optifabric` 改成 **`optifabric_reforged`**(产物名也变成 `OptiFabric-Reforged-…`),所以是主版本号 —— 升级前请**删掉旧的 `OptiFabric-1.2.0+mc26.1.2.jar`**;②依赖 Fabric 渲染器 API 的模组**实时生成的几何现在真的会被画出来**(更好的草这类),顶点仍由 OptiFine 写
- 同时修掉:注入点被 OptiFine 的重编译掏空(`LevelRenderer` / `SectionCompiler` / `CuboidItemModelWrapper` / `ScreenEffectRenderer` / `ModelManager`)、区块对象被换成 `ChunkOF`、Fabric 渲染器 API 搬家后占位器注册不上、抗锯齿后处理链
- 需要:Fabric Loader ≥ 0.19.5、**Java 25**(26.1.2 本身的硬要求,与 1.21.x 的 Java 21 不同)、客户端;Fabric API 可选(实测 0.155.3+26.1.2)
- 离线校验:被补丁的 **567** 个游戏类与 OptiFine 自身的 **879** 个类全部通过 JVM + ASM 双向校验,5 个扫描器**全 0**
- 真机已验证:启动、主界面、单人世界、区块重建、方块/物品/生物渲染、**抗锯齿**、**光影**、**多人**;Indigo 注册真正的渲染器,装 LambdaBetterGrass 实测"更好的草"正常、连接纹理正确
- 已知限制:**只支持 26.1.2**(其余 26.1 小版本与 26.2+ 需要各自重新移植);与 Sodium 冲突;26.1.2 的 OptiFine 目前只有 preview 构建
- **不包含、也不分发 OptiFine 本体**

# GitHub Release notes — tag `v1.1.1`(`OptiFabric-1.1.1+mc1.21.11.jar`)

> 复制下面 `---` 之间的内容到 GitHub Release 的说明框里(标题用第一行)。英文在前,末尾附中文摘要。
> 标签是**版本号本身**(`v1.1.1`,不带 `+mc`),与已发的 `v1.1.0` / `v1.2.0` / `v2.0.0` 一致;这一行要手改,
> 其余版本号由 `release\version.ps1` 统一改写。

---

## OptiFabric 1.1.1+mc1.21.11 — OptiFine on Fabric 1.21.11

Run **OptiFine** and **Fabric** in the same 1.21.11 client. Drop OptiFabric and your own OptiFine jar into `mods/`; at startup OptiFabric runs OptiFine's installer, remaps its patches into Fabric's namespace, repairs the structural conflicts with Fabric API, and hands the result to Fabric Loader's class transformer.

**OptiFine is not bundled or redistributed** — bring your own `OptiFine_1.21.11_HD_U_J9.jar` (or another 1.21.11 build).

### New in 1.1.1

Switching to a shader pack no longer fails the resource reload (`Resource not found: minecraft:post_effect/fxaa_of_2x.json`, sometimes with `Could not find post chain with id: minecraft:fxaa_of_2x`). It was our own anti-aliasing repair: OptiFine builds since 1.21.8 stopped shipping the old `shaders/post/fxaa_of_*.json` location, so the pipeline wrote that file back and **deleted** the game's `post_effect/fxaa_of_2x.json`. On 1.21.6–1.21.10 OptiFine really does read the old location (measured), but on 1.21.11 the post chain is resolved by **the game's own loader**, which looks for `minecraft:fxaa_of_2x` under `post_effect/` — and that file was gone. 1.1.1 keeps OptiFine's own file exactly as shipped on 1.21.11, and every other release keeps the previous behaviour. The jar is built from the same source tree as the 26.x line, so that work is compiled in as well — it is gated on the runtime namespace and on what a release actually has, and on 1.21.11 (intermediary) the pipeline behaves as before, which the offline verification below re-checks in full.

This jar is versioned on its own: the other nine Minecraft releases of this line stay at 1.1.0, because their content did not change (`v1.1.0` stays as published).

### Requirements

| | |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Java | 21+ (tested on Java 25) |
| Side | client |
| OptiFine | your own 1.21.11 build (tested: HD_U J9, build `20260205-175838`) |
| Fabric API | optional — supported, tested with 0.141.6+1.21.11 |

### Install

1. Install a 1.21.11 Fabric client (Loader 0.19.5+).
2. Put `OptiFabric-1.1.1+mc1.21.11.jar` **and** your OptiFine 1.21.11 jar into that version's `mods/` folder. Do **not** run OptiFine's installer — dropping the file in is enough.
3. Start the game with the **Fabric** profile. The first launch spends a few seconds patching and remapping (cached afterwards under `<game dir>/.optifine/<version>/`).

### What it took for 1.21.11

OptiFine's 1.21.11 build ships its class patches as xdelta diffs, and its recompiled classes no longer line up with what Fabric API injects into. Every conflict below was found through a real crash and traced down to the bytecode; the fixers are in `patcher/fixes` and the whole story is in [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md):

- **Remapping needs the game on the classpath** — member mappings are recorded per declaring class, so overrides in subclasses silently kept OptiFine's names (35 unmapped methods in `class_1308` alone → 281 broken abstract contracts, 254 lost virtual overrides).
- Injection targets OptiFine's recompiler erased (inlined helpers, renamed lambdas with a different signature) — the vanilla method bodies are restored so Fabric's injections have a target again.
- A Fabric hook that reads a render context OptiFine's pass structure never fills in — the hook is moved onto code that is never called, so the game stops crashing and the block outline is still drawn.
- The same for the **moving-blocks** renderer hook, whose caller lives in *another* class — those call sites are redirected too, otherwise the hook fires from the injected copy (this one crashed multiplayer after ~30 seconds).
- Fabric's renderer registry being empty while `contains_renderer` keeps Indigo away — an inert placeholder renderer is registered, which also stops the **F3 debug screen** from crashing.
- Item models failing to bake (every item texture missing), chunk rendering NPEs from OptiFine's region constructor, obfuscated fields with mismatching descriptors, same-typed synthetic `this$0`/`val$…` fields, and `ChunkOF` object creation replacing Fabric's injection point.
- Stack map frames were being recomputed for **every** patched class (the global override fixer always reports a change, so the patcher could not tell), and the merge degraded a local to `java/lang/Object` in classes as unrelated to Fabric as `ShoulderParrotFeatureRenderer` and `EntityRenderDispatcher` — the game rejects those with `VerifyError: Bad type on operand stack in putfield`. Classes no fixer modifies now keep OptiFine's own frames, and recomputation only happens where a fixer really changed the class.

### Verified

- Offline, every class is loaded and linked in a single loader (the same way the game does it) and checked with the JVM verifier plus an ASM data-flow verifier: **570/570 patched game classes** and **874/874 OptiFine classes**, 0 failures, 0 verifier problems.
- Mixin member references, `@At` injection points, `@Shadow` members and uninitialised-field scans: clean (the only 4 `@At` misses belong to the **disabled** Indigo).
- In game: startup, title screen, singleplayer, **multiplayer server**, block/chunk/item rendering, **shaders** (`ComplementaryReimagined` loaded), F3 debug screen — 0 `[ERROR]` lines and no crash report in the final session.

### Known limits (deliberate)

- **Conflicts with Sodium** (declared); `no_fog`, `thallium`, `xradiation`, `ryoamiclights` are declared incompatible.
- **Mods that rely on FRAPI/Indigo** do not get Indigo's custom rendering — OptiFine renders the terrain. Fabric's renderer API exists but is backed by a placeholder (F3 shows `OptifineRendererPlaceholder`), and it explains itself if something really tries to draw through it.
- Two Fabric API hooks are intentionally inert: the `BEFORE_BLOCK_OUTLINE` event does not fire (the outline is still drawn), and the Fabric renderer's moving-block hook is bypassed (moving blocks are drawn by the vanilla path).
- OptiFine cannot see resources inside Fabric mods (`Unknown resource pack type: ...ModNioResourcePack`) — a limitation on OptiFine's side.
- Shader packs log warnings such as `Unknown macro value: IRIS_VERSION` or `ParseException: Model variable not found: …`; those come from the shader pack.

### Files

| File | SHA-256 |
|---|---|
| `OptiFabric-1.1.1+mc1.21.11.jar` (876446 bytes) | `9E78C98FC0FC568C453ACA880FE167545192021D16C4A2DA0E4127AC5F3A9143` |

The same `v1.21.x` project also builds the other Minecraft releases OptiFine ships a 1.21.x build for — 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.6, 1.21.7, 1.21.8, 1.21.9 and 1.21.10 — with `.\gradlew -p v1.21.x build "-Pmc=<version>"`, and each of them passes the same offline verification (see [`docs/DEVELOPMENT.md`](DEVELOPMENT.md)).

Full changelog: [`CHANGELOG.md`](CHANGELOG.md) · Usage, troubleshooting and known issues: [`README.md`](README.md) · Verification tooling: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)

### Credits and license

A port of [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric) by Modmuss50 and Chocohead, licensed under **MPL-2.0**. OptiFine itself is neither included nor redistributed; get it from the official site (in China the `bmclapi2.bangbang93.com/optifine/1.21.11/HD_U/J9` mirror works — 302 to the official maven distribution).

---

## 中文摘要

把 OptiFine 接进 Minecraft **1.21.11** 的 Fabric。把本 jar 与自备的 `OptiFine_1.21.11_HD_U_J9.jar` 一起放进 `mods/`,用 Fabric 版本启动即可(**不需要**先运行 OptiFine 安装器);首次启动多花几秒做补丁+重映射,之后走缓存。

- **1.1.1 修复**:换光影包时不再弹"重载资源失败"(旧版会报 `Resource not found: minecraft:post_effect/fxaa_of_2x.json`),
  见上面英文段的 "New in 1.1.1";这一个 jar 单独用 1.1.1 号,1.21.x 其余九个版本仍是 1.1.0
- 需要:Fabric Loader ≥ 0.19.5、Java 21+、客户端;Fabric API 可选(实测 0.141.6+1.21.11)
- 离线校验:被补丁的 **570** 个游戏类与 OptiFine 自身的 **874** 个类全部通过 JVM + ASM 双向校验
- 真机已验证:启动、主界面、单人、**多人服务器**、方块/区块/物品渲染、**光影**、F3 调试屏,`[ERROR]` 0 条
- 已知限制:与 Sodium 冲突;依赖 FRAPI/indigo 的模组不再有 indigo 渲染;`BEFORE_BLOCK_OUTLINE` 事件与移动方块的 FRAPI 钩子被有意停用(渲染本身正常)
- **不包含、也不分发 OptiFine 本体**

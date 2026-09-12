# GitHub Release / CurseForge 文案 —— 1.21 ~ 1.21.10

本文件是 **1.21 ~ 1.21.10 这 9 个版本**的可直接粘贴文案(1.21.11 的详细版在 [`RELEASE_NOTES.md`](RELEASE_NOTES.md))。

用法:发某个版本时,把这个版本的小节整体粘进 GitHub Release 说明框(第一行作标题)以及 CurseForge 对应文件的 changelog;两个平台都支持 Markdown。想只留英文就把末尾的「中文」一行删掉。

所有版本共用同一份源码(分支 `mc1.21.x`),构建方式:

```powershell
.\gradlew build "-Pmc=1.21.10"      # PowerShell 里必须给参数加引号
```

---

## 全系列共用段落(可选,放 Release 正文开头)

**OptiFabric — OptiFine on Fabric for Minecraft 1.21 – 1.21.11**

Run **OptiFine** and **Fabric** in the same client. Drop this jar and your own OptiFine jar into `mods/`; at startup OptiFabric runs OptiFine's installer, remaps its patches into Fabric's namespace, repairs the structural conflicts with Fabric API, and hands the result to Fabric Loader's class transformer. One jar per Minecraft release — each carries that release's mappings, for every 1.21.x version OptiFine ships a build for.

**OptiFine is not bundled or redistributed.** Bring your own build for the release you play; you do **not** need to run its installer.

What this series needed on top of the 1.21.11 work (all of it in `patcher/fixes`, and inert on releases that do not have the ids):

- the fixers that move a method aside for a Fabric hook matched by **name and descriptor**; descriptors differ between releases (`LevelRenderer.method_62210` takes a `Camera` on 1.21.8 and a `Vec3d` on 1.21.11), so they now match by name with the descriptor optional — otherwise the block outline hook went back to injecting into live code on 1.21.8, which cannot work;
- the stub those hooks inject into now carries the **vanilla** body: mixins are written against the vanilla shape, and OptiFine's recompiled body no longer contains the calls an instruction-level injection point needs;
- helpers Fabric API injects into, which OptiFine's builds for **1.21.5 and older** drop, are put back: the model baker's deserialisation helper (`class_1088.method_65737` on 1.21.4, `method_61072` on 1.21.1), three `InGameHud` layers and `Keyboard.method_1454`;
- `KeyboardFix` no longer throws when a release does not have the methods it reverts (1.21.6+ rewrote the key dispatch), so one fixer serves 1.21 through 1.21.11;
- a new scanner (`LambdaScan`) checks the method handles behind every `invokedynamic` — neither the JVM verifier nor ASM's verifier resolves those, so a lambda left pointing at a method the patched class no longer has would only have shown up in game.

Every release below is expected to behave exactly like 1.21.11 does (which is live-verified: startup, singleplayer, multiplayer, block/chunk/item rendering, shaders and the F3 debug screen, 0 errors).

---

## v1.0.0+mc1.21.10

> Run OptiFine on Fabric **1.21.10**. Drop this jar and your own `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` into `mods/` and start the Fabric profile; the first launch unpacks, remaps and repairs OptiFine (cached afterwards under `<game dir>/.optifine/<OptiFine version>/`). Optional: Fabric API `0.138.4+1.21.10`.

**Verified offline:** 553 patched game classes and 836 OptiFine classes loaded and linked in a single loader and checked with the JVM verifier plus an ASM data-flow verifier — 0 failures, 0 verifier problems — and five scanners clean (mixin member references, `@At` injection points, abstract contracts/overrides/references, and invokedynamic handles: nothing dangling). The only `@At` findings left belong to Indigo, which steps aside for OptiFine on purpose.

**Asset:** `OptiFabric-1.0.0+mc1.21.10.jar` · SHA-256 `8E88F1893839AC012C79454C4779D8ACD4390FD26BED629DB870E0AF29A9D960`

中文:`1.21.10` 版 —— 把本 jar 与自备的 `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` 一起放进 `mods/`,用 Fabric 版本启动即可(不需要先运行 OptiFine 安装器)。离线校验 553 个补丁类 + 836 个 OptiFine 类全绿,5 个扫描器无遗留。

---

## v1.0.0+mc1.21.9

> Run OptiFine on Fabric **1.21.9**. Drop this jar and your own `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.134.1+1.21.9`.

**Verified offline:** 519 patched game classes and 832 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.9.jar` · SHA-256 `7B0C3E0A6C5D70F532B9FC354148BCAED4DBFBB6224D968B527A667BD2D9C69B`

中文:`1.21.9` 版 —— 配 `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar`。离线校验 519 + 832 全绿。

---

## v1.0.0+mc1.21.8

> Run OptiFine on Fabric **1.21.8**. Drop this jar and your own `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.136.1+1.21.8`.

This is the release where the descriptor mismatch in two of the fixers showed up: `LevelRenderer.method_62210` takes a `Camera` here (a `Vec3d` on 1.21.11), so the fixers that move methods aside for Fabric hooks now match by name — with the descriptor pinned to 1.21.11 the block outline hook would inject into live code and the game would fail on the class.

**Verified offline:** 516 patched game classes and 831 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.8.jar` · SHA-256 `D43DC3A1AF5190ECFED25798071DC6666E191C488CA2AD79FC6FF7BF1261BDAD`

中文:`1.21.8` 版 —— 配 `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar`。这一版正是"写死描述符"问题的现场(此处 `method_62210` 收 `Camera`),相关 fixer 已改成按名字匹配。离线校验 516 + 831 全绿。

---

## v1.0.0+mc1.21.7

> Run OptiFine on Fabric **1.21.7**. Drop this jar and your own `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.129.0+1.21.7`.

**Verified offline:** 500 patched game classes and 823 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.7.jar` · SHA-256 `8CA52985C41A7484783CE83DAA6B97B16DB7DD5B81F51378C3DF1A7543D2501C`

中文:`1.21.7` 版 —— 配 `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar`。离线校验 500 + 823 全绿。

---

## v1.0.0+mc1.21.6

> Run OptiFine on Fabric **1.21.6**. Drop this jar and your own `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.128.2+1.21.6`.

**Verified offline:** 487 patched game classes and 820 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.6.jar` · SHA-256 `F4D91B16DB126AB0AECB0991DD01FDFD14F01EE84CF23E2E5E84A3C7A4233D37`

中文:`1.21.6` 版 —— 配 `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar`。离线校验 487 + 820 全绿。

---

## v1.0.0+mc1.21.4

> Run OptiFine on Fabric **1.21.4**. Drop this jar and your own `OptiFine_1.21.4_HD_U_J3.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.119.4+1.21.4`.

This release and older ones needed extra repairs, because Fabric API injects into helpers OptiFine's build for them drops: here that is the model baker's deserialisation helper `ModelBakery.method_65737`. Without it the mixin cannot find its target and the model loading class fails to transform.

**Verified offline:** 474 patched game classes and 812 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.4.jar` · SHA-256 `CEB24D17A7EA9076DB683034C51A41DBADAF5C09E3F7E13F7B3767D5FAE72B3A`

中文:`1.21.4` 版 —— 配 `OptiFine_1.21.4_HD_U_J3.jar`。这一版起还要补回被 OptiFine 丢掉的助手方法(此处是模型烘焙器的反序列化助手 `class_1088.method_65737`)。离线校验 474 + 812 全绿。

---

## v1.0.0+mc1.21.3

> Run OptiFine on Fabric **1.21.3**. Drop this jar and your own `OptiFine_1.21.3_HD_U_J2.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.114.1+1.21.3`.

**Verified offline:** 440 patched game classes and 816 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.3.jar` · SHA-256 `4CB4B534833DBF23C8ECF19BD99A061EB1FD301ECD2D5B11F2E8FE7C1E4BB18C`

中文:`1.21.3` 版 —— 配 `OptiFine_1.21.3_HD_U_J2.jar`。离线校验 440 + 816 全绿。

---

## v1.0.0+mc1.21.1

> Run OptiFine on Fabric **1.21.1**. Drop this jar and your own `OptiFine_1.21.1_HD_U_J1.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.116.9+1.21.1`.

Two Fabric API hooks need their targets put back on this release: the model baker's helper (`ModelBakery.method_61072`) and `Keyboard.method_1454`, which OptiFine's build drops and `fabric-screen-api-v1` injects into.

**Verified offline:** 425 patched game classes and 783 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.1.jar` · SHA-256 `751915FE89A9EDCC101266D358477B4C0C514007C888286C0E0CB3BD57793D0B`

中文:`1.21.1` 版 —— 配 `OptiFine_1.21.1_HD_U_J1.jar`(正式版)。这一版补回了 `class_1088.method_61072` 与 `Keyboard.method_1454`。离线校验 425 + 783 全绿。

---

## v1.0.0+mc1.21

> Run OptiFine on Fabric **1.21**. Drop this jar and your own `preview_OptiFine_1.21_HD_U_J1_pre9.jar` into `mods/` and start the Fabric profile. Optional: Fabric API `0.99.5+1.21`. (OptiFine only published preview builds for 1.21.)

`Keyboard.method_1454` and three `InGameHud` layers are put back here: Fabric API injects into them and OptiFine's build for this release drops them.

**Verified offline:** 440 patched game classes and 773 OptiFine classes, JVM + ASM clean; five scanners clean.

**Asset:** `OptiFabric-1.0.0+mc1.21.jar` · SHA-256 `F69032EED2B4534871DFF10D6010FCE9EA286919EAF803F91A2A2A9B3FAC2B7F`

中文:`1.21` 版 —— 配 `preview_OptiFine_1.21_HD_U_J1_pre9.jar`(1.21 只有 preview 构建)。这一版补回了 `Keyboard.method_1454` 与 InGameHud 的三个层。离线校验 440 + 773 全绿。

---

## 每个版本都要注意的事(可放 Release 末尾)

- **一个 jar 只对应一个 Minecraft 版本**:jar 里打包的是该版本的 `official→intermediary` 映射表,放错版本会在启动时报版本不匹配。
- 需要 **Fabric Loader ≥ 0.19.5**、Java 21+(实测 Java 25)、客户端。
- **与 Sodium 冲突**(两者都是渲染器);`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 声明为不兼容。
- 依赖 FRAPI/indigo 的模组不再获得 indigo 的自定义渲染(地形交给 OptiFine);Fabric 的渲染器 API 由占位实现顶着(F3 显示 `OptifineRendererPlaceholder`)。
- 两个 Fabric API 钩子被有意中和:`BEFORE_BLOCK_OUTLINE` 事件不再触发(描边照画),移动方块的 FRAPI 渲染钩子失效(移动方块由原版路径渲染)。
- 缓存位于 `<游戏目录>/.optifine/<OptiFine 版本>/`;删掉 `.optifine/` 可强制重建。
- `[OptiFabric]` 的输出走**启动器控制台**,通常不在 `logs/latest.log` 里。

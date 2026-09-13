# 更新日志

## 1.1.2+mc1.21.3 … 1.1.2+mc1.21.11 — 抗锯齿全线修复,并纠正 1.1.1 里的错误结论

> **这一版覆盖 8 个产物**:1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11(都叫 1.1.2)。
> **1.21 与 1.21.1 不受影响,继续停在 1.1.0** —— 那两版的 OptiFine 只带老位置的后处理链,当时的修复根本没碰过它。
> 每个 jar 的版本号只描述它自己那份产物(SemVer §3),所以这是"8 个产物一起升到 1.1.2",不是把整条线重发;
> 1.21.11 那一份在**行为上**与 1.1.1 相同(它那时已经修好),但字节不同(管线不再做那次后处理链修复),
> 因此同样按新版本发布。

1.1.1 修的是 1.21.11 换光影包时的"重载资源失败"。当时判断问题只出在这一版,并写下"1.21.6–1.21.10 上 OptiFine
读老位置、实测可用"。**逐版本真机实测证明那句话是错的。**

### 实测:缺陷覆盖 1.21.3 起的几乎所有版本

每个版本都用**生产 jar**跑一遍(抗锯齿 2x + `ComplementaryReimagined_r5.9.1.zip` + 进世界,75–140 秒后读
`logs/latest.log`):

| MC | `Resource not found: minecraft:post_effect/fxaa_of_*` | 同会话里的 `Failed to load post chain` |
|---|---|---|
| 1.21 / 1.21.1 | —(修复没碰过这两版) | — |
| 1.21.3 | **2 条** | 未出现(那次没切抗锯齿) |
| 1.21.4 | **2 条** | 同上 |
| 1.21.6 | 未测到(无光影时不出现此警告) | 无光影:0 条;**启用光影则启动阶段崩溃**(见下方注) |
| 1.21.7 | **2 条** | 未出现 |
| 1.21.8 | **2 条** | **2 条**(用户 09-12 的会话) |
| 1.21.9 | **2 条** | 未出现 |
| 1.21.10 | **2 条** | **2 条**(用户 09-12 的会话) |
| 1.21.11 | 0(1.1.1 已修) | 0 |

> **1.21.6 那一行的更正(2026-09-13 本机复现)**:当时"量不到"是因为那台机器上这两版**启用光影即崩**,
> 而不是它们根本起不来 —— 用 `1.1.2` 生产 jar 复测:**不开光影时可以正常启动**(标题界面正常渲染、无崩溃报告),
> 只有**选了光影包**才会在启动阶段崩于 OptiFine 自身的 `NullPointerException ... "multiTex" is null` at
> `net.optifine.shaders.ShadersTex.initDynamicTextureNS`(把 `shaderPack=` 清空即恢复;只开 `ofAaLevel:2` 不崩)。
> 详见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) 的"仍待办的两项 A"。

代码层面它对得上:OptiFine 补丁过的 `ShaderManager`(`class_10151`)里注册 FXAA 链的方法,在
1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 上**逐条指令、逐条常量完全一致**,字符串配方就是 `post_effect/\u0001.json`;
这套代码**从 1.21.3 起就有**。而当时的修复**从 1.21.3 起一直在删那个文件** —— 所以它从一开始就是错的,
只是 1.21.11 先被真机撞到。

> 1.21.9 / 1.21.10 那条"抗锯齿黑屏已修好"的结论同样是错的:删掉链之后链**加载失败**,抗锯齿不再被应用,
> 于是不黑屏了 —— 看起来像修好,其实是**静默失效**。真正的原因是顶点着色器,见下。

### 本版做了什么

1. **不再碰 OptiFine 的后处理文件**:`OptifinePostChainFixer` 整个删除,两条线一视同仁。链本来就该由游戏按
   `minecraft:fxaa_of_2x` 从 `post_effect/` 解析(OptiFine 把 `ShaderManager` 改成在那儿注册),OptiFine 自带的
   那份就是它要的:补写老位置文件多余,删掉新位置文件是错的。这一条同时消掉"每次资源重载刷警告"与
   "一动抗锯齿就弹重载资源失败"。
2. **`OptifineJarFixer` 增加顶点着色器修复**:游戏从 1.21.9 起改用 `gl_VertexID` 生成全屏三角形、**不再提供
   `Position` 顶点属性**,而 OptiFine 1.21.9 / 1.21.10 那份 `fxaa_of_*.vsh` 还在读 `Position`(顶点塌成一点 ——
   这才是当初"一开抗锯齿整屏黑"的真正原因)。管线把这两个 `.vsh` 改写成同一套全屏三角形写法,
   `SamplerInfo` / `FxaaConfig` 两个 uniform 块与 FXAA 的 `posPos` 计算**原样保留**。判据是**文件内容**
   (游戏那份 `core/screenquad.vsh` 是否用 `gl_VertexID` + OptiFine 那份是否还在读 `Position`),所以
   1.21.3–1.21.8 的 `.vsh` 与 1.21.11 那份(sp614x 自己已经修好)**都不动**。

缓存格式 25 -> **26**。

> 日志里会重新出现一对 `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` —— 那是 OptiFine 每次
> 还去 1.21.6 之前的**老位置**探一次它的链(1.21.8 起的构建早就不带那个文件),纯探测警告:链走的是
> `post_effect/` 那条,不补写老位置文件反而少一个没人读的资源。用户已确认抗锯齿正常的 1.21.11(1.1.1)会话里
> 同样有这两条。

### 离线校验(每个受影响版本一条命令)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-version.ps1 -Version 1.21.10 -ModVersion 1.1.2
```

| MC | 补丁类(JVM+ASM) | OptiFine 类(JVM+ASM) | ASM 问题 | @At 注入点 | 其余扫描器 |
|---|---|---|---|---|---|
| 1.21.3 | 440 / 440 | 816 / 816 | 0 | 2(已停用 indigo) | 全 0 |
| 1.21.4 | 474 / 474 | 812 / 812 | 0 | 2 | 全 0 |
| 1.21.6 | 487 / 487 | 820 / 820 | 0 | 4 | 全 0 |
| 1.21.7 | 500 / 500 | 823 / 823 | 0 | 4 | 全 0 |
| 1.21.8 | 516 / 516 | 831 / 831 | 0 | 4 | 全 0 |
| 1.21.9 | 519 / 519 | 832 / 832 | 0 | 4 | 全 0 |
| 1.21.10 | 553 / 553 | 836 / 836 | 0 | 4 | 全 0 |
| 1.21.11 | 570 / 570 | 874 / 874 | 0 | 4 | 全 0 |

真机:八个版本各用修好的 jar 重跑探针(`test-downloads\probe-1.21-aa.ps1`),
`Resource not found: minecraft:post_effect/fxaa_of_*` **全部为 0 条**,全部进到标题界面。

八个产物各自的字节数与 SHA-256 见 `dist/README.txt` 与对应版本的 `release/notes/mc<版本>.md`。

## 1.1.1+mc1.21.11 — 只覆盖 1.21.11 的一版(抗锯齿后处理链)

> **为什么只跳一格、而且只覆盖一个 MC 版本**:1.21.x 这条线是"一份源码、每个 MC 版本一个 jar",每个 jar 的版本号
> 描述的是**它自己那份产物的内容**(SemVer §3:已发布的版本号不能改内容,只能发新版本)。这次的行为改动只发生在
> 1.21.11 上(判据按版本取值),其余九个 1.1.0 的 jar 内容没变、不需要重建,所以只有 1.21.11 从 1.1.0 升到 1.1.1,
> `v1.1.0` 那个发布条目原样不动。逐 MC 版本升版只用一条命令,见 [`docs/VERSIONING.md`](docs/VERSIONING.md)。

**修复:切换光影包时提示"重载资源失败"**(`Resource not found: minecraft:post_effect/fxaa_of_2x.json`,
可能还带 `Could not find post chain with id: minecraft:fxaa_of_2x`)。

根因在**我们自己那条抗锯齿修复**上:1.21.8 起的 OptiFine 构建不再带老位置的
`assets/minecraft/shaders/post/fxaa_of_2x.json`,于是管线按老办法**补写**它、并把游戏新位置的
`assets/minecraft/post_effect/fxaa_of_2x.json` **删掉**(1.21.6–1.21.10 上 OptiFine 确实读老位置的那条链,
实测可用)。但 1.21.11 的后处理链已改由**游戏自己的加载器**解析 —— 它按 `minecraft:fxaa_of_2x` 去
`post_effect/` 取文件,而那个文件被我们删了:每次资源重载日志里都留一条 `Resource not found`,
需要这条链时(开抗锯齿 / 选光影包)直接失败并弹窗。

修法:1.21.11 上**原样保留** OptiFine 自带的新位置文件 —— 不补写老位置的那个,也不删它
(`OptifinePostChainFixer.fix(jar, keepGameChains)` 的新分支);1.21.6–1.21.10 保持老做法不变。
这个版本列表是**逐版本实测**得出的(静态特征不可用:每个 1.21.x 原版 jar 里都只有 `post_effect/` 一种位置,
OptiFine 的类里也没有路径字面量),依据写在 `OptifineSetup.POST_EFFECT_RELEASES` 旁边。
缓存格式号同时升到 `25`(管线产物变了),升级后首次启动会重建 `.optifine/`。

**离线校验**(`powershell -File test-downloads\verify-version.ps1 -Version 1.21.11 -ModVersion 1.1.1`):
被补丁的游戏类 **570 / 570**、OptiFine 自身的类 **874 / 874** 通过 JVM 校验,ASM 数据流验证器 **0 问题**;
扫描器:注入点 4 条(全部属于**已停用**的 indigo)、mixin 成员引用缺失 0、契约/覆写/引用 0/0/0、invokedynamic 句柄 0 悬空。

**真机确认**(2026-09-13,把 1.1.0 换成 1.1.1 并清空 `.optifine/`):启动、资源重载、切光影包、开关抗锯齿都正常;
1.1.0 上每次资源重载都出现的 `Resource not found: minecraft:post_effect/fxaa_of_2x.json` 与
`Failed to load post chain: minecraft:fxaa_of_2x` **都不再出现**,整轮 `[ERROR]` 0 条。

产物:`OptiFabric-1.1.1+mc1.21.11.jar` — 876446 字节
`SHA-256: 9E78C98FC0FC568C453ACA880FE167545192021D16C4A2DA0E4127AC5F3A9143`

> 这一版的范围判断("问题只在 1.21.11")与上面"1.21.6–1.21.10 读老位置、实测可用"那句是**错的**,1.1.2 已经纠正;
> 这一节保留原样,作为当时的记录。已发布的 `1.1.1+mc1.21.11` 内容不变(§3)。

## 2.0.0+mc26.1.2 — 26.x 线的第二版(mod id 改名 + 实时几何)

> **主版本号递增的依据**(SemVer §8,规则见 [`docs/VERSIONING.md`](docs/VERSIONING.md)):这一版把 mod id 从
> `optifabric` 改成 `optifabric_reforged`,对任何 `depends`/`breaks` 那个 id 的东西都是**不兼容修改** ——
> 升级时请**删掉旧的 `OptiFabric-1.2.0+mc26.1.2.jar`**,换成 `OptiFabric-Reforged-2.0.0+mc26.1.2.jar`。
> 同一版里那些"向下兼容的新功能"被主版本号一并吸收(§8:主版本号递增时次版本号与修订号归零)。

**Minecraft 26.1.2** —— 26.1 起游戏**未混淆**,这是一条与 1.21.x 完全独立的线,两边的 jar **不能互相替代**。

**这一线的 mod id 与显示名也是它自己的**:`optifabric_reforged` / **OptiFabric Reforged**(1.21.x 仍为 `optifabric`,已发布的 1.1.0 不动)。理由是实际的:有模组声明 `"breaks": {"optifabric": "*"}`(LambdaBetterGrass 就是),而 Fabric Loader 按 **id** 匹配 —— 只改显示名无效;独立 id 之后这类声明不再拦 26.x。实测上游**未修改**的 LBG jar 可与本模组共存,更好的草与连接纹理正常。

官方名就是运行名,既没有 yarn 也没有真正的 intermediary 可重映射(26.1.2 只发布占位 `intermediary:0.0.0`)。
因此 26.x 用 Loom 的**非重映射** flavour(`net.fabricmc.fabric-loom`)、不写 `mappings`、运行期命名空间是
`official` 而不是 `intermediary`;1.21.x 那套按 `class_XXXX` 注册的 fixer 判据在这一线指向的类**根本不存在**,
所以另建了一张"官方名"注册表。

```powershell
.\gradlew -p v26.x build        →  OptiFabric-Reforged-2.0.0+mc26.1.2.jar
```

### 本版修复

- **注入点(最主要的一类)**:OptiFine 重编译时把原版方法**削成薄壳**、真正的实现搬进它自己加的重载,
  而 Fabric API 用**不带描述符**的 `method = "..."` 指名目标 —— 恢复原版方法体之后类里出现两个同名方法,
  MixinExtras 建不出局部变量上下文,整个类变换失败。逐处消歧:`LevelRenderer`、`SectionCompiler`、
  `CuboidItemModelWrapper`、`ScreenEffectRenderer`、`ModelManager`;
- **渲染器(FRAPI)**:26.1 把 Fabric 渲染器 API 挪进了 `api.client.renderer.v1`,按旧名字查找失败曾让占位
  **从未注册**,第一个 `Renderer.get()` 就把游戏带走;更根本的是,从 1.21.x 继承来的
  `fabric-renderer-api-v1:contains_renderer` 让位键在这一线**本来就是多余的** —— 26.1.2 的 indigo 已经不是
  地形渲染器(地形与提交节点的整合搬进了 `fabric-renderer-api-v1` 自己,它只剩 1 条物品 mixin + 2 个 accessor),
  键一声明就把 Fabric API 唯一能问到的那个渲染器关掉,模组生成的网格进了惰性占位 —— **不报错,也不显示**。
  本版**不再声明该键**,`RendererApiFallback` 把它读回来、只在确实被声明时才补占位器,否则由 Indigo 注册自己的
  `IndigoRenderer`(F3 的 `Renderer:` 一行因此显示 `IndigoRenderer`,而不再是 `OptifineRendererPlaceholder`);
- **实时几何(FRAPI)**:Fabric 的地形渲染钩子注入在原版 `compile` 的 `BlockPos.betweenClosed` 循环上,而 OptiFine
  的区块构建方法里那个循环**一次都不出现** —— 钩子只落在没人调用的补丁方法里,注入成功、永不执行,于是需要按位置
  实时生成几何的模型(例如 LambdaBetterGrass 的"更好的草")`emitQuads` 一次都没被问过、几何**静默消失**。
  新增 `FrapiTesselateBridgeFix` + `OptifineFrapiBridge`:把 OptiFine 循环里那次方块 tessellate 调用改到桥上,
  几何由 Fabric 的渲染器产出(AO / 染色 / 光照齐),**顶点交给 OptiFine 传进来的 `BlockQuadOutput` 写** ——
  顶点格式、层级缓冲、光照与光影属性全归 OptiFine,桥上不碰任何顶点缓冲(第一版直接写区块缓冲,会把整层数据写成垃圾,
  表现为"一切方块透明",原因见 `docs/PORT_26.x.md` 第 5 节)。只路由 `emitQuads` 声明在游戏之外的模型,
  原版方块一律留在 OptiFine 的路由上(否则光影下会把 OptiFine 的额外顶点属性弄丢:实测发黑/光照怪);
- **抗锯齿**:26.x 从 `post_effect/` 读后处理链,而 1.21.x 那套修复的做法是**删掉该文件**、补写老位置的链 ——
  在这一线正好是反的。现在按版本线分开处理;
- **渲染路径**:移动方块与普通方块模型这两处 Fabric API 钩子仍改为惰性(由原版/OptiFine 路径绘制);方块破坏
  裂纹那条没有动,它现在真的走 Indigo 的渲染器。

### 要求与实测

| 项 | 值 |
|---|---|
| Minecraft | 26.1.2(**只支持这一个版本**) |
| Fabric Loader | >= 0.19.5 |
| Java | **25**(与 1.21.x 的 Java 21 不同,26.1.2 本身要求 25) |
| OptiFine | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`(目前只有 preview) |
| Fabric API | 0.155.3+26.1.2 |

实测:启动、进世界、方块/物品/生物渲染、抗锯齿、光影、多人全部正常;进世界时 Indigo 注册真正的渲染器
(`[Indigo] Registering Indigo renderer!`),Fabric API 自己的渲染钩子从它上面绘制;**装 LambdaBetterGrass 实测:
"更好的草"正常、连接纹理正确(光影开启)**。

产物:`OptiFabric-Reforged-2.0.0+mc26.1.2.jar` — 177166 字节
`SHA-256: FBB432C2D9C8B0E7E06F0FDA4A0C1B6A8F302D5D09ABD7CE67F13CBE04A5CF60`

## 1.2.0+mc26.1.2 — 26.x 线的第一版(已发布 2026-09-12)

[GitHub release](https://github.com/Kynarain/OptiFabric-Reforged/releases/tag/v1.2.0) ——
`OptiFabric-1.2.0+mc26.1.2.jar`,163164 字节,
`SHA-256: 672F3895AB656FACDA42C93218F885BA21487D929A92C9E05D542A4D0A20B64A`

当时的状态:26.x 走通了"未混淆 + 官方名"的独立构建与运行期路径,能构建出一个可玩的 jar;但 mod id 仍是
`optifabric`,Fabric 的渲染器由一个**惰性占位**顶着(Indigo 让位),**需要按方块位置实时生成的几何画不出来**
—— 设置与取舍见 [`docs/PORT_26.x.md`](docs/PORT_26.x.md) 第 2、3 节。`2.0.0` 修掉的正是后者。

## 1.0.0+mc1.21 … 1.0.0+mc1.21.11 — 1.21.x 全系列

**一份源码,覆盖 OptiFine 出过 1.21.x 构建的全部 10 个版本**,每个版本一个 jar:

```
.\gradlew build "-Pmc=1.21.8"     →  OptiFabric-1.0.0+mc1.21.8.jar
```

（PowerShell 里必须给参数加引号,否则 `1.21.8` 会被拆成 `1`。不带参数则构建 `gradle.properties` 里的默认版本。）

每个 jar 都绑定了**自己那个版本**的 `official→intermediary` 映射表(官方混淆名每版不同,用错版本会把 OptiFine 重映射成乱码),`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本。

### 各版本与实测搭配

| MC | 产出的 jar | OptiFine 构建 | 建议 Fabric API |
|---|---|---|---|
| 1.21 | `OptiFabric-1.0.0+mc1.21.jar` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar`(只有 preview) | 0.99.5+1.21 |
| 1.21.1 | `OptiFabric-1.0.0+mc1.21.1.jar` | **`OptiFine_1.21.1_HD_U_J1.jar`** | 0.116.9+1.21.1 |
| 1.21.3 | `OptiFabric-1.0.0+mc1.21.3.jar` | **`OptiFine_1.21.3_HD_U_J2.jar`** | 0.114.1+1.21.3 |
| 1.21.4 | `OptiFabric-1.0.0+mc1.21.4.jar` | **`OptiFine_1.21.4_HD_U_J3.jar`** | 0.119.4+1.21.4 |
| 1.21.6 | `OptiFabric-1.0.0+mc1.21.6.jar` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` | 0.128.2+1.21.6 |
| 1.21.7 | `OptiFabric-1.0.0+mc1.21.7.jar` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` | 0.129.0+1.21.7 |
| 1.21.8 | `OptiFabric-1.0.0+mc1.21.8.jar` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` | 0.136.1+1.21.8 |
| 1.21.9 | `OptiFabric-1.0.0+mc1.21.9.jar` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` | 0.134.1+1.21.9 |
| 1.21.10 | `OptiFabric-1.0.0+mc1.21.10.jar` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` | 0.138.4+1.21.10 |
| 1.21.11 | `OptiFabric-1.0.0+mc1.21.11.jar` | **`OptiFine_1.21.11_HD_U_J9.jar`** | 0.141.6+1.21.11 |

（OptiFine 没出过 1.21.2 / 1.21.5 的构建,所以这两版没有对应 jar。）

### 为什么一套源码够用,以及它需要什么

- **intermediary id 在 1.21.x 各版本之间是稳定的** —— 对比 1.21.10 与 1.21.11 的映射,本移植用到的类/方法 id 两边都在。fixer 全部以 intermediary 名义注册,于是能跨版本复用。
- fixer 的行为是"找到就修、找不到就跳过",而且 `RestoreVanillaMethodsFix` 这类读的是**当前版本游戏 jar 里的原版字节码**,不存在"把 1.21.11 的方法体塞进 1.21.4"的问题。版本差异退化为"某些 fixer 在某版本不触发"。
- 但**写死描述符的 fixer 会在别的版本上静默失效**:`class_761.method_62210` 在 1.21.8 收一个 `Camera`、在 1.21.11 收一个 `Vec3d`。`StubInjectionTargetFix` 与 `CallSiteRedirectFix` 因此改成**按方法名匹配、描述符可选**,并用"实际找到的那个方法"的描述符去改调用点 —— 否则 1.21.8 上的方块描边钩子会落回活代码,而它要的注入点在 OptiFine 重编译后的方法体里并不存在(真机必崩)。
- 同为"让不兼容的钩子注入进死代码"那招,**死代码副本现在用原版方法体**(Mixin 本来就是照着原版写的,OptiFine 会把方法体内的调用改写掉;副本是死代码,身体只需要"长得像原版")。
- 版本特有的成员缺失(仅 ≤1.21.5)由 `RestoreVanillaMethodsFix` 补:`class_1088.method_65737`(1.21.4)/`method_61072`(1.21.1)、`class_329.method_55806/55807/55808`、`class_309.method_1454`。
- `KeyboardFix`(1.20.6 线上用过)重新启用并**改成容错**:1.21/1.21.1 还有 `method_1454`(OptiFine 的构建把它丢了,而 fabric-screen-api-v1 往它里面注入),1.21.6 以后根本没有这个方法。上游找不到方法时**直接抛异常**,改成"只 revert 该版本确实存在的那些"之后,同一个 fixer 能同时服务 1.21 到 1.21.11。

### 验证(每个版本都跑完整离线链路)

| MC | 补丁类(JVM+ASM) | OptiFine 类(JVM+ASM) | @At 注入点 | mixin 成员引用 | 契约/覆写/引用 | invokedynamic 句柄 |
|---|---|---|---|---|---|---|
| 1.21 | 440/440 | 773/773 | 3(全是已停用 indigo) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.1 | 425/425 | 783/783 | 2(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.3 | 440/440 | 816/816 | 2(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.4 | 474/474 | 812/812 | 2(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.6 | 487/487 | 820/820 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.7 | 500/500 | 823/823 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.8 | 516/516 | 831/831 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.9 | 519/519 | 832/832 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.10 | 553/553 | 836/836 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |
| 1.21.11 | 570/570 | 874/874 | 4(同上) | 0 缺失 | 0/0/0 | 0 悬空 |

新增扫描器 **`LambdaScan`**:逐个检查补丁类里 `invokedynamic` 的 bootstrap 方法/字段句柄是否指向"游戏真正会加载的那份类"里还存在的方法。这是之前唯一没人查的一环 —— JVM 与 ASM 验证器都**不解析** invokedynamic 目标(它们只在首次执行时才链接,也就是在游戏里)。逐版本结果 **0 悬空**。

每个版本的验证都能一条命令重跑:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-version.ps1 -Version 1.21.8
```

真机验证只有 **1.21.11 完成**(启动、主界面、单人、多人、方块/区块/物品渲染、光影、F3,`[ERROR]` 0 条)。其余 9 个版本已通过全部离线校验并装机实测过,结果是:

- **1.21.3 / 1.21.8** 崩在 `VerifyError: Bad type on operand stack in putfield`(`EntityRenderDispatcher.renderHitbox`、`ShoulderParrotFeatureRenderer.render`)。根因不在 fixer 上,而在管线自己:**未被任何 fixer 改动**的类也被重算栈帧(`MissingOverrideFix` 是全局的,于是全部 ~500 个补丁类都走了 `COMPUTE_FRAMES`),合并分支类型时退化成 `java/lang/Object`,游戏拒绝加载该类。现在这类类原样保留 OptiFine(经 tiny-remapper)的栈帧,只有真被改动的类才重算;`getCommonSuperClass` 退化时会打日志,验证器也不再依赖类加载顺序。**已修,待复测。**
- **1.21 / 1.21.4** 崩在 `RuntimeException: Mixin transformation of net.minecraft.class_X failed`。Mixin 不把原因写进 `latest.log`,从注入点倒推出来是**注入点本身被改掉了**:
  - 1.21 的 `class_5944`(ShaderProgram):`DelegatingConstructorFix` 内联 OptiFine 的委托构造函数时,照抄了 OptiFine 的 `new Identifier(name)`,而原版构造函数用的是 `Identifier.ofVanilla(name)` —— Fabric API 的 `@WrapOperation` 包的正是后者。现在内联时先问原版类怎么转换,有静态工厂就照抄。**已修,待复测。**
  - 1.21.4 的 `class_329`(InGameHud):OptiFine 把构造函数里三条 layer 方法引用(`this::method_55806/55807/55808`)改写成了 `lambda$new$0/1/2`;那一版 Fabric API 的 `LayerInjectionPoint` 是**按引导方法句柄**匹配注入点的,句柄对不上就等于注入点不存在。新增 `LambdaMethodRefFix`,把这些 lambda 改名回游戏使用的方法名(保留 OptiFine 的身体,它在准星那一层比原版多了 QuickInfo)。**已修,待复测。**

- **1.21.6 / 1.21.7 / 1.21.9** 的"光影没有任何效果",三个原因都定位到了(见下),其中两个已在本项目里修好;
- **1.21.8 / 1.21.10** 光影加载成功但渲染不对:光影包 `photon_v1.2a.zip` 使用了 OptiFine 不认识的程序名(`gbuffers_entities/particles/block_translucent`、`gbuffers_all_translucent` 与 Distant Horizons 的 `dh_terrain`/`dh_water`),OptiFine 只报 `Invalid program name` 并跳过这些 pass。**这属于光影包与 OptiFine 不匹配,不是补丁能修的**(换 OptiFine 专用包即可验证)。

**第二轮复测(同日 12:03–12:11)后又修掉的三处**:

- **1.21 / 1.21.3 / 1.21.4 进世界十几秒后崩**(`NullPointerException: Cannot invoke "net.optifine.override.ChunkCacheOF.renderStart()" because "regionIn" is null`):`RegionSectionPosFix` 在 1.21–1.21.4 上没生效 —— 那些版本的 region 构建器收的是 `ChunkSectionPos` 对象,1.21.6 起才是打包 long,fixer 只处理后者就整段跳过,于是 region 用原版构造器建出来、内部的 `ChunkCacheOF` 为 null。现在两种形状都支持。**已修,待复测。**
- **1.21.1 崩在 `Mixin transformation of net.minecraft.class_5944 failed`**:与 1.21 同一处注入点,但 OptiFine 在那里用的是**静态工厂**委托(`this(provider, Identifier.ofVanilla(id), type)`),内联 fixer 原来只认 `new Identifier(...)`,于是注入点留在 `this()` 之前(Mixin 拒绝实例 handler 落在 `super()` 前)。两种形状现在都识别。**已修,待复测。**
- **1.21.6 / 1.21.7 光影完全不加载**:OptiFine 那两个预览构建(`J6_pre3`、`J6_pre7`)的 `Shaders.loadShaderPack()` 在检查之前写死了 `cancelled = true`(1.21.8 起是读取检查结果),于是 `getShaderPack()` 永远不会被调用,选任何光影包都是 `[Shaders] No shaderpack loaded.`。新增的 `OptifineJarFixer` 在映射后的 jar 上删掉那两条指令;**同一组件还修好了 1.21.9**:OptiFine 自带的 `post_effect/fxaa_of_{2,4}x.json` 把 `minecraft:post/blit` 当顶点着色器,而 1.21.9 起游戏只有 `post/blit.fsh`(顶点阶段是 `core/screenquad`),后处理管线编译失败会连带光影初始化失败;顺带把 1.21.6 / 1.21.7 那份用了 `"program"` 键(那两版的解析器只认 `vertex_shader`/`fragment_shader`)的 JSON 一并改写。**已修,待复测。**

逐条推导、日志证据与复跑口径见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) 的"第二轮真机反馈"。

---

## 1.0.0+mc1.21.11 — 移植到 Minecraft 1.21.11

把 OptiFine 接进 Minecraft 1.21.11 的 Fabric,并处理掉 OptiFine 与 Fabric API 之间的全部已知结构冲突。
实测搭配:**OptiFine 1.21.11 HD_U J9**(build `20260205-175838`)+ **Fabric Loader 0.19.5** + **Fabric API 0.141.6+1.21.11**,运行于 Java 25。

### 功能

- 在 `preLaunch` 阶段对 OptiFine jar 自动执行:跑 `optifine.Patcher` 解包(1.21.11 的 OptiFine 换成 xdelta 差分包,用法不变)→ 去 volde 化 → official→intermediary 重映射 → 应用字节码修复 → 注入 Fabric Loader 的类变换器。
- 重映射时把**游戏 jar 一起放进重映射器的 classpath 与输入**。这是 1.21.11 移植最关键的一处修正:TinyRemapper 的成员映射按**声明类**记录,看不见游戏类层次时,子类里覆写的方法会留成 OptiFine 的名字(`class_1308` 一个类就漏了 35 个方法 → 281 处破坏的抽象契约、254 处丢失的虚方法覆写)。
- 结果缓存到 `<游戏目录>/.optifine/<OptiFine 版本>/`(含缓存格式版本号,当前 `13`),二次启动直接复用(1–2 秒)。
- 缺 OptiFine / jar 损坏 / 版本不匹配 / 放了多份 OptiFine / 内部错误时,标题界面弹错误对话框,并在崩溃报告里追加 `OptiFabric` 一节。
- `-Doptifabric.mc-jar=<原版 client jar>` 可显式指定原版 jar;`-Doptifabric.extract=true` 会把重映射后的类解包出来便于排查。

### 与 Fabric API 的兼容修复(全部来自真机崩溃,逐个定位到字节码)

| 症状 | 根因 | 处理 |
|---|---|---|
| 启动崩 `Mixin transformation of net.minecraft.class_761 failed` | OptiFine 把 lambda 体重编译成 `lambda$addMainPass$1` 且**多一个参数**,Mixin 按名字+描述符找不到 `method_62214` | 恢复原版方法体(同类另有 `class_3898.method_60440`、`class_1092.method_65750`) |
| `class_1088` 的 `@WrapOperation` 找不到 `method_68018/68019` | 重编译后方法消失(同上) | 恢复原版方法体;另给 `class_775.method_3347` 补回被干掉的注入点(`InjectionCallPointFix`) |
| 启动崩 `@Local class_2338$class_2339` 校验失败 | 原版在 `ARETURN` 处作用域内有 `MutableBlockPos`,补丁后没有(MixinExtras 在**注入点**上判别局部变量) | `RestoreVanillaMethodsFix(true, "method_24225")` |
| 进世界 4 秒后崩 `ChunkCacheOF.renderStart() ... regionIn is null` | OptiFine 的区域构造器只有**六参数**版本会写 section 位置,恢复出的原版构建方法调用的是五参数那个 | `RegionSectionPosFix`:改调六参数构造器,用该方法本就收到的打包 long 生成第六个参数 |
| 进世界 4 秒后崩 `WorldRenderContextImpl.worldState()` 为 null | Fabric 的方块描边钩子读的上下文由 `LevelRenderer.method_22710` 头部准备,而 OptiFine 用 `RenderPass` + 自己的 lambda 替换了那条流程 | `StubInjectionTargetFix`:钩子目标改名并留同名副本,让注入落在**没人调用**的代码上(代价:`BEFORE_BLOCK_OUTLINE` 事件不再触发,描边照画) |
| **所有物品贴图丢失** | `class_10430.method_65584` 上的 `@Inject(at = RETURN)` 需要重编译后不再存在的局部变量 → **每个**物品模型都烘焙失败 | `RestoreVanillaMethodsFix(true, "method_65584")` |
| 进多人服务器约 30 秒崩 `Attempted to retrieve active rendering plug-in before one was registered` | 同上那招对**移动方块**钩子失效:它的调用者在**另一个类**(`class_11684.method_73002`)里,按名字调到的是被注入的副本 | `CallSiteRedirectFix`:调用方一并接管,调用点改到改名后的真实方法体 |
| 一按 **F3** 就崩(同一异常) | 声明 `contains_renderer` 只是让 Indigo 退场;Fabric API 自己的 F3 调试条目仍要 `Renderer.get()`,而没有任何渲染器被注册 | `RendererApiFallback`:注册惰性占位渲染器(F3 显示 `Renderer: OptifineRendererPlaceholder`) |
| 启动即崩 `NoSuchMethodError: class_2680.getBlockStateBaseCacheClass()` | 上一项第一版在 preLaunch 调了 `Class.getMethods()` 读 Fabric 接口,而接口方法签名里全是游戏类型 → 这些类被**提前加载成原版**,整局都停在原版上(`class_2680`=BlockState 少了 OptiFine 加的方法) | 生成器改为 ASM 只读接口 class 文件(不解析任何类型);注册改用 `MethodHandles.findStatic`;注册时机挪到补丁类注入之后 |

此外还有一批**离线推导**出来的结构问题:合成字段 `this$0`/`val$…` 同名同类型时按声明顺序配对并改成模组可 shadow 的名字(`SyntheticFieldFix` 扩展);被 OptiFine 重编译掉的原版方法桥接(`MissingOverrideFix`);被换成 OptiFine 子类的对象创建(`ChunkOF`)补回惰性 `NEW` 标记;被读取但从未被赋值的字段(`UnsetFieldScan` 报 0)。

### 兼容性

- 需要 **Fabric Loader ≥ 0.19.5**、Java 21+(实测运行在 Java 25)、客户端。
- 与 `sodium` 冲突(已声明);`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 声明为不兼容。
- **不包含、也不分发 OptiFine 本体**;OptiFine 需自行获取后放进 `mods/`(1.21.11 有正式发布版,如 HD_U J9)。
- 依赖 FRAPI/indigo 的模组不再获得 indigo 的自定义渲染(地形交给 OptiFine);Fabric 的渲染器 API 由占位实现顶着。
- 两个 Fabric API 钩子被有意中和:`BEFORE_BLOCK_OUTLINE` 事件不再触发;移动方块的 FRAPI 渲染钩子失效(移动方块由原版路径渲染)。
- OptiFine 看不到 Fabric 模组内部的资源(它不认 Fabric 的资源包类型)。

### 验证

- 离线字节码校验(与游戏一致的单一加载器):被补丁的 **570 / 570** 个游戏类通过 JVM 校验,OptiFine 自身的 **874 / 874** 个类通过,ASM 数据流验证器 **0 问题**。
- 扫描器:mixin 成员引用缺失 **0**、破坏的抽象契约/丢失的虚方法覆写/无法解析的成员引用 **0/0/0**;`@At` 注入点仅剩 4 条,全部属于**已被停用**的 indigo。
- 真机(final session):启动、主界面、单人世界、**多人服务器**、方块/区块/物品渲染、光影(`ComplementaryReimagined` 加载成功)、F3 调试屏;`[ERROR]` 0 条、无崩溃报告。
- 过程、每一类的根因与修法、可复现工具见 [`docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md)。

---

## 1.0.0+mc1.20.6 — 首个发布版

把 OptiFine 接进 Minecraft 1.20.6 的 Fabric,并处理掉 OptiFine 与 Fabric API 之间的全部已知结构冲突。

### 功能

- 在 `preLaunch` 阶段对 OptiFine jar 自动执行:跑 `optifine.Patcher` 解包 → 去 volde 化 → official→intermediary 重映射 → 应用字节码修复 → 注入 Fabric Loader 的类变换器。
- 结果缓存到 `<游戏目录>/.optifine/<OptiFine 版本>/`(含缓存格式版本号),二次启动直接复用。
- 缺 OptiFine / jar 损坏 / 版本不匹配 / 放了多份 OptiFine / 内部错误时,标题界面弹错误对话框,并在崩溃报告里追加 `OptiFabric` 一节。
- `-Doptifabric.mc-jar=<原版 client jar>` 可显式指定原版 jar;`-Doptifabric.extract=true` 会把重映射后的类解包出来便于排查。

### 与 Fabric API 的兼容修复(全部来自真机崩溃,逐个定位到字节码)

| 症状 | 根因 | 处理 |
|---|---|---|
| 启动崩 `@ModifyArg handler before this() invocation must be static` | OptiFine 的 `ShaderProgram` 委托构造器在 `this()` 之前创建 `Identifier`,Fabric 的注入点落在 `super()` 前 | 内联委托构造器,把标识符创建挪到 `super()` 之后(保留原参数布局) |
| `@Shadow field field_3835 was not located` | OptiFine 把字段留成混淆名且描述符不符 | 按映射表对齐字段名、类型与构造器里存入的值 |
| 校验错 `Bad type on operand stack` / `Bad local variable type` | 构造器重写的槽位搬移破坏了描述符与调用点的一致性 | 改用末尾空闲槽位,并用反汇编逐条核对 |
| 缺注入目标 `EntityRenderers.method_32174/32175`、`ThreadedAnvilChunkStorage.method_17227/18843` | OptiFine 重编译时把私有助手内联掉了 | 恢复原版方法体 |
| 缺 `@Shadow` 字段 `field_27735` / `field_40571` / `field_20839` | 合成外部实例引用被 javac 命名为 `this$0` / `this$1`,映射表里没有这种名字 | 按描述符匹配并重命名合成字段 |
| 区块构建崩 | `ChunkRendererRegionBuilder.build` 被改成转发给自己重载的瘦包装,局部变量搬走 | 恢复原版方法体 |
| 56,042 次模型烘焙失败(方块全部消失) | `ModelLoader$BakerImpl.bake` 同样被改成转发,三条注入点搬进了重载 | 恢复原版方法体 |
| 开存档报"网络协议错误" | OptiFine 用 `net.optifine.ChunkOF` 取代 `WorldChunk`,Fabric 的 `@At(value="NEW", target="WorldChunk")` 匹配不到 | 在 OptiFine 创建子类处前面插入惰性 `NEW; POP` 标记 |
| 进多人服务器两秒后掉线(协议错误) | 移植的 `ParticleManagerFix` 把构造器换成原版,连带丢掉了 OptiFine 对 `renderEnv` 字段的唯一初始化 → 破坏方块粒子时 NPE 抛在网络包处理里 | 不再替换构造器(类型对齐由映射层完成) |
| 区块构建时崩(提前拦住的隐患) | indigo 注入 `BlockPos.iterate`,而 OptiFine 重写了那段循环 | 声明 `fabric-renderer-api-v1:contains_renderer`,让 indigo 按 Fabric 的机制让位 |

### 兼容性

- 需要 **Fabric Loader ≥ 0.19.3**、Java 21+(实测运行在 Java 25)、客户端。
- 与 `sodium` 冲突(已声明);`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 声明为不兼容。
- **不包含、也不分发 OptiFine 本体**;OptiFine 需自行获取后放进 `mods/`。
- 依赖 FRAPI/indigo 的模组不再获得 indigo 的自定义渲染(地形交给 OptiFine)。
- OptiFine 看不到 Fabric 模组内部的资源(它不认 Fabric 的资源包类型)。

### 验证

- 离线字节码校验:与游戏一致的单一加载器下 **425 / 425 通过**,ASM 数据流验证器 **0 问题**。
- 真机:带 Fabric API 进主界面、开单人存档、进多人服务器、模型与区块渲染、光影生效。
- 过程与可复现工具见 [`docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md)。

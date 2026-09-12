# 26.x 移植:起点与关键结论

> 用户已确认:**26.x 及其之后的 Minecraft 版本是未混淆的**(官方名即运行名)。

## 已验证的事实(Fabric 元数据实测,同一时刻对照)

| 查询 | 结果 |
|---|---|
| `meta.fabricmc.net/v2/versions/yarn/1.21.11` | 正常,返回 `1.21.11+build.1` ~ `build.6`(与本仓库 `build.gradle` 的 `yarnBuilds` 一致) |
| `meta.fabricmc.net/v2/versions/yarn/26.1.2` | **空数组** —— 没有 yarn 构建 |
| `meta.fabricmc.net/v2/versions/intermediary/26.1.2` | 只有 `net.fabricmc:intermediary:**0.0.0**` —— Fabric 对"无需 intermediary 重映射"的占位 |

结论与用户确认一致:**26.1.2 不需要重映射**,类名就是官方名(`net.minecraft.client.Minecraft` 等)。

本地环境:`versions\26.1.2-Fabric 0.19.5\` 已就位(Fabric Loader 0.19.5 + `fabric-api-0.155.3+26.1.2.jar`),
但**磁盘上没有任何 26.x 的 OptiFine jar** —— 这是开工前的硬阻塞。

## 这件事改变了什么(不是加一行版本号)

1. **`patcher/fixes/*` 整套会失效**:全部按 `class_XXXX`(intermediary)注册,未混淆版本没有这些名字。
   需逐个改写为官方名注册;每个 fixer 的判据字符串(方法名、字段名、描述符)也要跟着换成官方名。
   示例:`registerFix("class_5944", new DelegatingConstructorFix())` -> 用 `net.minecraft.world.level.block.entity.BlockEntity` 之类的官方名。
2. **官方名 -> intermediary 重映射阶段变成恒等操作**:可以直接跳过(少一步、少一类错误点)。
   `OptifineSetup.getRuntime()` 里的 `Patcher.process(...)` 需要按"未混淆"分支处理。
3. **构建侧换映射**:`build.gradle` 的 `yarnBuilds` 表对 26.x 不适用,改用官方映射(Loom 的 official mappings 路径),
   并把 `gradle.properties` 的 `minecraft_version` 指到 26.1.2。
4. **`RemappingUtils` 需要改造**:它现在做 intermediary <-> yarn 的名字/成员映射(`getClassName` 还会无条件加 `net.minecraft.`);
   未混淆版本下这些映射应当是恒等,且该前缀行为要按名字形态判断(见 `docs/DEVELOPMENT.md` 里 A 那次失败的记录)。
5. **校验脚本的假设同样要改**:`test-downloads/verify-version.ps1` 与 `VerifyPatched` 的断言里有按 `class_XXXX` 统计的列,
   未混淆版本应按官方名统计;`Prepared N patched classes (0 skipped, 0 failed)` 这类口径保持不变。
6. **OptiFine 侧形态待确认**:它的 patcher 原本面向混淆 jar,拿到 26.x 构建后要先看它自己的类和补丁形态。

## 开工前需要的

1. **26.1.x 的 OptiFine jar**(路径即可)—— 唯一硬阻塞;
2. 目标版本 id 确认为 `26.1.2`(本地实例目录名与 FAPI 版本串均为 `26.1.2`)。

## 开工顺序

1. 读官方客户端 jar,确认"未混淆"并据此在管线里分叉(重映射步骤跳过);
2. 加 26.1.2 的构建入口(官方映射 + `minecraft_version`),先只求"能编译、能跑通离线管线";
3. 跑基线校验,收集"哪些 fixer 没命中"的清单 —— 这就是本轮移植的待办表;
4. 按官方名重写 fixer(本轮主要工作量),逐项验证,纪律与 1.21.x 相同:
   `Prepared … (0 skipped, 0 failed)`、`verified OK`、`ASM verifier problems: 0`、扫描器三列 0、不得出现 `Failed to prepare` / `define failed`;
5. 真机跑通后再做逐版发布材料(沿用 `release/` 下的框架)。

## 分支

`26.x` 从 `mc1.21.x` 建立。1.21.x 的成果不受影响:八个版本可用、发布材料在 `release/`,
`mc1.21.x` 分支保持不动。
## 已确认的事实(实测,可直接作为动手起点)

### 1. 26.1.2 确实是未混淆的(读真实 jar 得到)

```
versions\26.1.2-Fabric 0.19.5\26.1.2-Fabric 0.19.5.jar
  net/minecraft/client/Minecraft.class        <- 官方名
  net/minecraft/client/Camera.class
  net/minecraft/client/AttackIndicatorStatus.class
  ...
  class_ 形态的条目数: 0                        <- 没有任何 intermediary 名
```

### 2. OptiFine 26.1.2 已就位(BMCLAPI 镜像下载)

```
文件      preview_OptiFine_26.1.2_HD_U_K1_pre2.jar   (另有 pre1)
大小      7797229 字节
SHA-256   F8EB9026E4DA2444E18D5601D3DEDE2BD19CF514D02095FFCDB0E101687C2172
镜像      https://bmclapi2.bangbang93.com/optifine/26.1.2/HD_U_K1/pre2
          (URL 规律:/optifine/<MC 版本>/<类型>/<补丁号>)
落位      test-downloads\  +  versions\26.1.2-Fabric 0.19.5\mods\
```

镜像列表用 `https://bmclapi2.bangbang93.com/optifine/versionList` 查(497 条,含 mcversion/type/patch/filename)。

它**同时带了新式与旧式的 FXAA 资源**(`post_effect/fxaa_of_2x.json` 与 `shaders/post/fxaa_of_2x.{vsh,fsh}`),
也就是说 `OptifinePostChainFixer` 的前提条件成立 —— 1.21.9/1.21.10 那个抗锯齿黑屏的修法应可平移,不必重踩。

### 3. 构建侧第一处要改的地方(当前会直接抛错)

`build.gradle` 现在这样:

```groovy
def yarnBuilds = [ "1.21" : "1.21+build.9", ..., "1.21.11" : "1.21.11+build.6" ]
def targetYarn = yarnBuilds[targetMc]
if (targetYarn == null) throw new GradleException("No yarn build is listed for Minecraft " + targetMc + ...)
```

26.1.2 没有 yarn(实测元数据为空),所以 `-Pmc=26.1.2` 会**立刻抛这个异常**。要做的分叉:

1. 26.x 分支改用**官方映射**(下文实测更正:不是"Loom 的 official mappings 路径",而是换一个**插件**),
   不走 yarn;
2. **跳过"把 intermediary mappings 打进 jar"**那一步(未混淆下不重映射,`Patcher.process(...)` 的 official->intermediary 阶段成为恒等);
3. `gradle.properties` 的 `minecraft_version` 指到 `26.1.2`;
4. 之后再谈 `patcher/fixes/*` 按官方名重写(本轮主要工作量,逐个 fixer 的注册名与判据字符串都要换)。

## 已完成:两个项目分离(实测通过)

两条线不能共用一份源码 —— 同一份 mixin 不可能既是 yarn 名又是官方名 —— 所以按用户决定把
**1.21.x 与 26.x 拆成两个完全独立的 Gradle 项目**,各自有自己的 `gradle.properties`:

```
OptiFabric/
  common/src/main/            共享源码:patcher、fixer、mod、util 与资源(与版本无关,两边一起编译)
  v1.21.x/                    1.21.x 项目:fabric-loom + yarn + intermediary,Java 21
    settings.gradle  gradle.properties(minecraft_version=1.21.11)  build.gradle
    src/main/java/.../mixin/  yarn 名的两个 mixin
  v26.x/                      26.x 项目:net.fabricmc.fabric-loom,无 mappings,Java 25
    settings.gradle  gradle.properties(minecraft_version=26.1.2)   build.gradle
    src/main/java/.../mixin/  官方名的两个 mixin
  gradlew(.bat)  gradle/      共用的 wrapper(根目录不再是 Gradle 项目)
```

构建(两条线互不干扰):

```
.\gradlew.bat -p v1.21.x build "-Pmc=1.21.11" --offline   # -> BUILD SUCCESSFUL(含 remapJar/remapSourcesJar)
.\gradlew.bat -p v26.x build                              # -> BUILD SUCCESSFUL
```

**1.21.x 未被触动**:`v1.21.x/build/libs/OptiFabric-1.1.0+mc1.21.11.jar` 与已发布的
`dist/OptiFabric-1.1.0+mc1.21.11.jar` **逐字节相同**(SHA-256 `B3148876…BE71D7`,865253 字节)。
`v1.21.x` 仍保留 `yarnBuilds` 表与 `-Pmc=` 覆盖,所以十个 1.21.x 版本照旧从这一个项目构建。

**26.x 现在也能出 jar 了**:`v26.x/build/libs/OptiFabric-1.1.0+mc26.1.2.jar`,156769 字节。
`v26.x` 没有 `-Pmc`:它的目标版本就是 `v26.x/gradle.properties` 里那一个值。

> 本机 shell 会把**未加引号**的 `-Pmc=1.21.11` 拆成 `-Pmc=1` + `.21.11`(实测 `cmd /c echo -Pmc=1.21.11`
> 输出 `-Pmc=1 .21.11`),Gradle 于是收到 `mc=1` 并抛出"没有 yarn 构建"。加引号写成 `"-Pmc=1.21.11"` 即可。
> 这是执行环境的参数解析问题,不是仓库问题。

### 实测更正:Fabric 官方 26.1.2 移植文档给出的真实做法

`https://docs.fabricmc.net/26.1.2/develop/porting/` 与 `fabric-example-mod` 的 `26.1` 分支写明:

| 项 | 1.21.x(混淆) | 26.1+(未混淆) |
|---|---|---|
| Loom 插件 id | `fabric-loom`(遗留 id,即 remap 版) | **`net.fabricmc.fabric-loom`**(非重映射版) |
| `mappings` 依赖 | `net.fabricmc:yarn:<ver>:v2` | **整行删掉**(非重映射 Loom 没有 mappings 概念) |
| Loader 依赖 | `modImplementation` | `implementation` |
| Java | 21 | **25** |
| 打包任务 | `remapJar`(+ `remapSourcesJar`) | 只有 `jar` |

也就是说 PORT_26.x.md 原先"改用官方映射"的说法不准确:26.x 不是"用官方 mappings",而是**换插件 + 不要 mappings**。
intermediary 元数据也印证了这点:`maven.fabricmc.net/net/fabricmc/intermediary/26.1.2/` 是 **404**,
26.1.2 只发布占位 `net.fabricmc:intermediary:0.0.0`。

### 26.1.2 产物实测(不是推断)

```
v26.x\build\libs\OptiFabric-1.1.0+mc26.1.2.jar        156769 字节
  fabric.mod.json        minecraft "26.1.2", fabricloader ">=0.19.5"
  mappings/mappings.tiny  不存在          <- intermediary 打包在 v26.x 里根本不注册
  Optifabric.class        主版本 69       <- Java 25
  optifabric.mixins.json  compatibilityLevel "JAVA_25"
```

`compatibilityLevel` 必须跟着编译版本走:Mixin 拒绝套用"比 config 声明的 Java 版本更新"的 mixin 类。
共享的 `optifabric.mixins.json` 里放的是 `${mixin_compatibility_level}` 占位符,由各项目的
`processResources` 展开 —— 实测 `v1.21.x` 得到 `JAVA_21`、`v26.x` 得到 `JAVA_25`。

### 两个 mixin 的官方名对照(逐个 `javap` 读 26.1.2 真实 jar 得到)

按用户决定,**1.21.x 那两个 mixin 保持 yarn 名不动**(优先保障 1.21.x,且已验证产物逐字节相同);
26.x 的对应实现写在 `v26.x/src/main/java/.../mixin/`。下表就是 `v26.x` 里那份用的对照:

`mixin/CrashReportMixin.java`:

| 1.21.x (yarn) | 26.1.2 (官方) |
|---|---|
| `net.minecraft.util.crash.CrashReport` | `net.minecraft.CrashReport` |
| `net.minecraft.util.crash.CrashReportSection` | `net.minecraft.CrashReportCategory` |
| `CrashReportSection.add(name, value)` | `CrashReportCategory.setDetail(name, value)` |
| `CrashReportSection.addStackTrace(StringBuilder)` | `CrashReportCategory.getDetails(StringBuilder)` |
| `CrashReport.addDetails(StringBuilder)` | `CrashReport.getDetails(StringBuilder)` |

注意最后一行:26.1.2 的 `CrashReport` **同时**有 `getDetails()`(无参,返回 String)和 `getDetails(StringBuilder)`,
所以 `@Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", ...)` 必须写描述符,光写名字会有歧义。

`mixin/MixinTitleScreen.java`:

| 1.21.x (yarn) | 26.1.2 (官方) |
|---|---|
| `client.gui.screen.TitleScreen / Screen / ConfirmScreen` | `client.gui.screens.*` |
| `client.gui.DrawContext` | **`client.gui.GuiGraphicsExtractor`** |
| `Screen.render(DrawContext, int, int, float)` | **`Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`** |
| `Screen#textRenderer` / `#client` | `Screen#font` / `#minecraft` |
| `TitleScreen#doBackgroundFade` / `#backgroundFadeStart` | `TitleScreen#fading` / `#fadeInStart` |
| `text.Text` | `network.chat.Component` |
| `Text.literal(x).formatted(F)` | `Component.literal(x).withStyle(F)` |
| `util.Formatting` | `ChatFormatting` |
| `util.math.MathHelper` | `util.Mth` |
| `DrawContext#drawTextWithShadow(font, s, x, y, colour)` | `GuiGraphicsExtractor#text(font, s, x, y, colour)` |
| `Util.getOperatingSystem().open(x)` | `Util.getPlatform().openUri(String)` / `.openFile(File)` |
| `Util.getMeasuringTimeMs()` | `Util.getMillis()` |
| `MinecraftClient#keyboard` | `Minecraft#keyboardHandler` |

**尚未在游戏内验证**:26.1 把"往 draw context 里画"改成了"抽取渲染状态(render state)+ 独立渲染器",
所以版本号角标是从 `extractRenderState` 里加进去,而不是原来的 `render`。这是对新 API 的忠实读法,
但要真的看到字出现在屏幕上才算数。

**另一条好消息**:当初 26.x 编译失败时,37 个错误**只出在这两个 mixin 文件里**。`mod/*`、`patcher/*`、
`patcher/fixes/*`、`util/*` 全部在官方名命名空间下**原样编译通过** —— 因为 fixer 是用**字符串**
写类名/方法名的(`class_XXXX` 只是字符串常量),不依赖编译期的游戏类型。也就是说
`patcher/fixes/*` 的官方名重写是**运行期行为**问题(注册名与判据字符串要换),不是编译问题。

## 运行期管线分叉(已完成)

### 关键发现:26.x 的 runtime namespace 是 `official`,不是 `intermediary`

这条此前只是推测,现在从 Fabric Loader 0.19.5 的字节码里读实了。`MappingConfiguration.computeRuntimeNamespace()`:

```java
String ns = "official";                                  // 没有映射时的默认值
if (hasAnyMappings()) {                                  // = getMappings().getClasses().isEmpty() 取反
    ns = launcher.isDevelopment() ? "named" : "intermediary";
    if (!getNamespaces().contains(ns)) ns = "official";
}
return gameProvider.getRuntimeNamespace(ns);
```

26.1.2 只发布占位的 `intermediary:0.0.0`(空映射),所以 `hasAnyMappings()` 为 **false**,命名空间落在 **`official`**。

**这直接推翻了两处原有假设**:

1. `OptifineSetup` 原来的守卫是 `if (!"intermediary".equals(namespace)) throw ...` —— 在 26.x 上会**直接抛异常**,
   OptiFine 根本load不起来;
2. 原计划写的"official -> intermediary 重映射成为恒等,可以留着不管"也不对:**不能留**。
   jar 是为两个世界之一构建的,26.x 的产物**根本不带 mappings**,重映射器没有东西可读。

### 实际改动(`common/`,运行期判断,不分目录)

| 位置 | 改动 |
|---|---|
| `OptifineSetup.getRuntime()` | 守卫接受 `intermediary` **或** `official`;`official` 时**整段跳过** `remapOptifine(...)`,直接拿 de-volderfy 之后的 jar 往下走 |
| `OptifineMappings.hasBundledMappings()` | 新增:`/mappings/mappings.tiny` 在不在 |
| `OptifineMappings.findFieldRenames(...)` | 没有自带映射时直接返回空表 |

第三处是必须的:`OptifineInjector` **每次运行都会调** `findFieldRenames`(它修的是 OptiFine 把某些字段
留成混淆名的情况,比如粒子工厂表 `k` vs `field_3835`)。26.x 下 OptiFine 的补丁本来就是按官方名编的,
没有"遗留混淆名"可修,语义上返回空表才对;同时这也避开了那条会去读不存在映射的路径。

### 还没做的,以及为什么

**`RemappingUtils` 这轮故意没动。** 它服务于 `patcher/fixes/*` 的判据字符串(`getClassName("class_437")` 之类),
只有等 fixer 换成官方名之后,改它才有可观测效果、也才可验证。现在改属于无法验证的瞎动 —— 而且
`docs/DEVELOPMENT.md` 的"A. 1.21.6/1.21.7 启动崩溃"一节正好记着这块前缀行为踩过的坑
(`registerFix` 内部会过一遍 `getClassName`)。

好消息是它**不会炸**:`MappingResolverImpl.mapClassName` 对未知名字是**原样返回**(只在名字含 `/` 时报格式错),
所以 26.x 下 fixer 注册表能正常初始化,只是每个 fixer 的判据都匹配不上 —— 也就是"找到就跳过",
正好是这一步留给下一步的基线状态。

### 1.21.x 的行为未变(推理 + 实跑回归验证)

新增分支只在 `namespace == "official"` 时生效;1.21.x 是 `intermediary`,所以守卫、重映射、`findFieldRenames`
三条路径的走向与改动前**逐条相同**。注意:这次**产物哈希必然变了**(改的是源码,不是构建配置),
所以先前"与 dist 逐字节相同"那条验收标准在这一步**不适用** —— 真实验收是仓库自己的离线校验,已实跑:

```
powershell -File test-downloads\verify-version.ps1 -Version 1.21.11 -SkipBuild
  Prepared 570 patched classes (0 skipped, 0 failed)
  verified OK: 570        FAILED: 0        ASM verifier problems: 0
  OptiFine 自己的类: 874 全过,FAILED 0,ASM problems 0
  RefmapScan      MISSING members: 0
  RuntimeContractScan  broken 0 / lost 0 / unresolvable 0
  LambdaScan      DANGLING handles: 0
  AtTargetScan    PROBLEMS: 4
```

`AtTargetScan PROBLEMS: 4` **不是回归**:未被我碰过的 `verify-1.21.10.log` 里是**一字不差的同样 4 条**
(`class_776` 两条、`class_9810` 两条),整个 1.21.8–1.21.11 家族的基线都是 4(1.21.1/1.21.3/1.21.4 是 2,1.21 是 3)。
这是已知基线,不是这次改动引入的。

> 跑校验时踩到一处**搬家留下的坑**,已修:`test-downloads/harness-cp.txt` 里把 mod 的编译产物
> 硬编码成了 `<根>\build\classes\java\main` 与 `<根>\build\resources\main`。根目录不再是 Gradle 项目后
> 这两条已失效,`VerifyPatched` 直接 `NoClassDefFoundError: OptifineSetup`。现已指向
> `v1.21.x\build\classes\java\main` / `v1.21.x\build\resources\main`。
> 另外首次跑之前要清掉 `test-downloads\mc<版本>\game\.optifine` 缓存,否则管线走的是缓存、**不会**执行改过的代码。

## 26.x 离线校验通道(已完成,一条命令)

`test-downloads\verify-26.ps1` —— 1.21.x 那套 `verify-version.ps1` 的 26.x 对应物:

```
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-26.ps1
```

与 1.21.x 版的四处不同,每一处都是未混淆这件事逼出来的:

| 项 | 1.21.x | 26.x |
|---|---|---|
| 映射文件 | 该版本的 yarn tiny | **没有**(26.1.2 不发 yarn),所以给扫描器一个**不存在的路径** |
| 游戏 jar 与校验 jar | 混淆客户端 vs intermediary 客户端 | **同一份**(official 命名空间下两者就是一个) |
| 校验 classpath | Loom 产出的未签名 jar | 自行去签名的客户端 jar(见下) |
| 传递命名空间 | 默认 `intermediary` | `-Dharness.namespace=official` |

**两个坑值得记住**:

1. **扫描器会静默加载 `test-downloads\yarn-mappings.tiny`。** 那个文件是仓库里 1.21.x 的一份遗留,
   扫描器只在"给的路径存在"时才加载 —— 不显式给一个不存在的路径,它就会拿 1.21.x 的 yarn 表去解释
   26.x 的官方名,把每个引用都解析错。脚本里显式传不存在的路径,让扫描器进入"名字已经是运行期名字"模式。
2. **校验 classpath 必须是未签名的客户端 jar。** 直接拿 Mojang 那份签名 jar,`VerifyLoader` 会抛
   `SecurityException: signer information does not match`——我们的补丁类没签名,不能和已签名类同包。
   这不是 mod 的问题(Fabric Loader 在真实启动里会剥掉签名),是 harness 口径问题;
   脚本会自动去签名(`META-INF/*.SF|RSA|DSA` + 重写 `MANIFEST.MF`),产出的状态与 Loom 为 1.21.x 产出的那份一致。

### fixer 的命名空间分叉

`RemappingUtils.hasOfficialNames()`(运行时问 `getCurrentRuntimeNamespace()` 是不是 `official`)
现在是两条 fixer 注册表的总开关:

- `registerIntermediaryNameFixes()` —— **原有那一整块,一字未改**(`class_XXXX` 键),1.21.x 走它;
- `registerOfficialNameFixes()` —— 26.x 走它,**刻意很短**,由基线驱动而不是照着翻译。

为什么不照翻译:把整张 intermediary 表在 26.x 上留成死表跑基线,得到的已经是
`Prepared 566 (0 skipped, 0 failed)` + RefmapScan `MISSING 0` + RuntimeContractScan `0/0/0`
+ LambdaScan `DANGLING 0`。也就是说 1.21.x 那些条目所修的冲突**在 26.1.2 上大多根本不存在**,
把名字照搬过去等于给不存在的字节码形状注册修补。只有扫描器真正报出来的才登记。

### 26.1.2 基线(实测)

```
powershell -File test-downloads\verify-26.ps1
  [OptiFabric] Minecraft 26.1.2 ships unobfuscated (runtime namespace "official"), so OptiFine needs no remapping
  [OptiFabric] Restored vanilla net/minecraft/client/resources/model/ModelManager.lambda$loadBlockModels$2(...)
  [OptiFabric] Wrote assets/minecraft/shaders/post/fxaa_of_2x.json / _4x.json
  [OptiFabric] Dropped assets/minecraft/post_effect/fxaa_of_2x.json / _4x.json
  Prepared 566 patched classes (0 skipped, 0 failed)
  verified OK: 566   FAILED: 0   ASM verifier problems: 0
  OptiFine 自己的类: 881 个,879 全过,FAILED 0,not applicable(NeoForge)2,ASM 0
  AtTargetScan PROBLEMS 0 / RefmapScan MISSING 0 / RuntimeContractScan 0-0-0 / LambdaScan DANGLING 0
```

对照 1.21.11(`Prepared 570`、`AtTargetScan PROBLEMS 4`),26.x **全绿且问题更少**。

### 基线里唯一一个真实问题(已修)

唯一的 `AtTargetScan` 问题:

```
[NO INSTRUCTION] net/minecraft/client/resources/model/ModelManager.lambda$loadBlockModels$2(Ljava/util/Map$Entry;)
                 Lcom/mojang/datafixers/util/Pair; has no INVOKE of Pair.of(...)
  -> net.fabricmc.fabric.mixin.client.model.loading.ModelManagerMixin.actuallyDeserializeModel
```

这就是 1.21.x 那条 `class_1092 / method_65750` 的同一个冲突、换了形状:那边 OptiFine 重编译把 lambda
**改了名**,这边 lambda **名字与描述符都在**,但重编译把 mixin 的 `@At` 需要的 `Pair.of` 调用**删掉了**。
两边 jar 都实测过(不是推断):

```
javap -c <原版>  ... lambda$loadBlockModels$2 ...  invokestatic com/mojang/datafixers/util/Pair.of   (1 处)
javap -c <补丁>  ... lambda$loadBlockModels$2 ...  Pair.of 出现次数 0
```

于是 `registerOfficialNameFixes()` 里登记 `RestoreVanillaMethodsFix(true, "lambda$loadBlockModels$2")`,
用原版方法体盖掉 OptiFine 那份 —— 这个 fixer 只按名字+描述符匹配,不含任何 intermediary 字符串,
换官方名直接用。修完 `PROBLEMS: 0`。

### FXAA 那条路也是对的(顺带核过)

26.1.2 的 OptiFine 只带新式 `assets/minecraft/post_effect/fxaa_of_*.json`,老式
`assets/minecraft/shaders/post/fxaa_of_*.json`(它自己按老位置去找的那个)没有:

- `OptifineJarFixer` **正确地没动**:那份新式 json 用的是 `"vertex_shader"`/`"fragment_shader"` 键、
  blit pass 的顶点级是 `minecraft:core/screenquad`,形状本来就是这个版本能读的(它修的是 1.21.6/1.21.7/1.21.9 那几种坏形状);
- `OptifinePostChainFixer` **按设计生效**:补写老式链、并把新式 effect 拿掉,让抗锯齿只走 OptiFine 自己那条链。

### harness 口径 bug(已修)

`VerifyPatched --verify-jar` 把 26.1.2 OptiFine 里两个 **NeoForge-only** 类记成了 FAILED
(`optifine.OptiFineClassProcessor` 实现 `net.neoforged.neoforgespi.*`、`optifine.VirtualJarContents` 实现
`net.neoforged.fml.jarcontents.*`)。两个原因:

1. `externalOnly` 只认 `net/minecraftforge/` 与 `cpw/mods/`,不认 `net/neoforged/`(NeoForge 是 Forge 的后续,
   26.x 的 OptiFine 构建按它写 Forge 集成);
2. 更要紧的是**计数与打印口径不一致**:打印 `define:` 时传了失败原因文本,计数时却只传类名 + `null`,
   而类名本身完全看不出 NeoForge(`optifine.OptiFineClassProcessor` 看着就是个普通 OptiFine 类)。

两处都修了,现在 `FAILED 0 / not applicable 2`。—— 这类"看着像缺陷其实不是"的口径问题必须修掉,
否则下一轮会有人去追一个不存在的问题。

## 剩下的事:必须真机(游戏内)测试

离线能查的已经查完了。以下几条**静态查不出来**,需要在 26.1.2 + Fabric Loader 0.19.5 + OptiFine HD_U_K1_pre2
的真实启动里看:

1. **两个 mixin 能不能套上**。`MixinTitleScreen` 用的是 `@Inject(method = "init")`(没写描述符)
   和 `extractRenderState`;`CrashReportMixin` 用 `getDetails(Ljava/lang/StringBuilder;)V`。
   Mixin 解析失败会**整个类失败**——离线校验看不到。
2. **版本号角标画不画得出来**。26.1 把"往 draw context 里画"改成了"抽取渲染状态 + 独立渲染器",
   我们把角标加在 `extractRenderState` 里。这是对新 API 的忠实读法,**但没在屏幕上见过**。
3. **OptiFine 真的跑起来**——shaderpack 能加载、抗锯齿、区块渲染、多人。
   即 1.21.x 那条"单机 + 光影 + 抗锯齿 + 多人全部正常"的验收,在 26.x 上重跑一遍。
4. **崩溃报告里那节 `OptiFabric` 分类**是否出现(`CrashReportMixin` 的效果)。

在此之前不要发布 26.x 的 jar。1.21.x 不受影响,它的八个版本照旧可用。

## 26.x 已经能跑起来(实机,本机启动)

```
Prepared 566 patched classes (0 skipped, 0 failed)
mixin 变换失败: 0     Minecraft has crashed: 0     管线失败: 0
[OptiFine] Disable Forge light pipeline   <- OptiFine 真的加载了
标题界面(panorama + ResourceManager)已到达
```

### 关键工具:在自己机器上复现 + 看 Mixin 的真实报错

`latest.log` **看不到真因**:stdout 没写进日志,而崩溃报告也写不出来 —— 写报告会跑 OptiFine 的 `Reflector`,
正好去 load 那个刚失败的类,于是 `Caused by` 链条被 `... 3 more` 吃掉。

`test-downloads/launch-26.ps1` 解决这件事:从版本 json 重建启动命令(7 个 Fabric 库没有 `downloads` 块,
按坐标解析)加 `-Dmixin.debug.verbose=true` 直接起游戏,输出落到 `test-downloads/launch-26.log`。
**这是这一轮最重要的能力**:排查不再需要用户跑一次。

### 修掉的四类冲突

`registerOfficialNameFixes()` 现在有 6 条注册,对应实测出来的冲突:

| 类 | 修法 | 1.21.x 对应条目 |
|---|---|---|
| `ModelManager` | `RestoreVanillaMethodsFix(true, "lambda$loadBlockModels$2")` | `class_1092 / method_65750` |
| `ClientChunkCache` | `ObjectCreationPointFix(".../LevelChunk", "net/optifine/ChunkOF", "replaceWithPacketData")` | `class_631 / method_16020` |
| `ScreenEffectRenderer` | `RestoreVanillaMethodsFix(true, "getViewBlockingState")` | `class_4603 / method_24225` |
| `LevelRenderer` | restore `extractBlockOutline` + drop 掉 OptiFine 的多余重载 | `class_761` |
| `CuboidItemModelWrapper` | restore `update` + drop 掉 9 参重载 | `class_10430 / method_65584` |
| `SectionCompiler` | restore `compile` + **rename** OptiFine 重载 + 重定向调用者 | `class_6850` 同族 |

### 这一类冲突的根因(值得记住)

OptiFine 重编译时会把**原版方法削成薄壳**,把真正的实现搬进**自己加的一个重载**:
`extractBlockOutline(Camera, LevelRenderState)` 只转发给 `extractBlockOutline(..., boolean)`,
`update(7 参)` 只转发给 `update(9 参)`,`compile(4 参)` 只转发给 `compile(..., ChunkCacheOF, III)`。

于是:原版方法体连同 Fabric API 注入点需要的调用一起没了。而 Fabric API 的写法是
`method = "extractBlockOutline"` —— **不带描述符**。恢复原版方法体之后类里就有两个同名方法,
MixinExtras 建局部变量上下文时**找不到方法**,报:

```
LVTGeneratorError: Could not locate method metadata for update generating LVT in .../CuboidItemModelWrapper
```

`Scanned 0 target(s)` 是同一件事的另一种表现。**所以 restore 必须配一个"消歧"**,而消歧有两种:

- **drop**(删掉 OptiFine 的多余重载):只有当**没有任何类**还在调它时才安全;
- **rename**(改名 + 把调用者重定向过去):当还有调用者时必须走这条。

`DropVanillaAbsentOverloadsFix` 就是干这个的,规则是**按可见性定可靠性**:`private` 方法只有本类能调,
所以类内扫描是完备的;非 `private` 的方法可能被**另一个单独变换的类**调用,所以默认拒绝,
要显式传 `allowNonPrivate = true` —— 而且只在核实过"这个名字+描述符在整个游戏 jar 里都不出现"之后才传。

**这条规则不是理论**:`SectionCompiler.compile(SectionPos, ChunkCacheOF, ...)` 是 public 且被
`SectionRenderDispatcher$RenderSection$RebuildTask.doTask` 调用,当时"类内没人调"就 drop 了,
**离线扫描器逮住了它**:

```
[patched caller] SectionRenderDispatcher$RenderSection$RebuildTask.doTask -> SectionCompiler.compile(...)
```

这是世界加载后第一次区块重建就会踩的 `NoSuchMethodError`。正确做法是 rename + `CallSiteRedirectFix` 重定向,
现在两者都在,`unresolvable member references: 0`。

### 顺带修掉的一个真 bug:`RemappingUtils` 的前缀

`getClassName` 无条件加 `net.minecraft.`,于是 `CallSiteRedirectFix` 传完整官方名时抛:

```
IllegalArgumentException: Class names must be provided in dot format:
net.minecraft.net/minecraft/client/renderer/chunk/SectionCompiler
```

整个 fixer 表在静态初始化阶段就炸了(`ExceptionInInitializerError`),游戏"继续但不带 OptiFine" ——
**表面看 0 崩溃、0 mixin 失败,其实是没加载**。这类假成功是最危险的,判断标志是日志里
`[OptiFabric] Failed to set up OptiFine, the game will continue without it` 和没有 `[OptiFine]` 行。

修法按 PORT_26.x.md 原本的建议:前缀**按名字形态判断**(短 id 加前缀,已经是全路径的不加);
但描述符里的类名要另走一条路 —— `CLASS_FINDER` 捕获的是 `Lnet/minecraft/` **之后**的部分,
在未混淆版本里那是 `core/SectionPos` 这样的片段,必须无条件补回前缀,否则会拼出 `Lcore/SectionPos;`。
1.21.x 的短 id 不含 `/`,两条路径的结果都与改动前逐字节一致(已用离线校验确认)。

### 26.1.2 最终基线

| 检查 | 1.21.11 | **26.1.2** |
|---|---|---|
| Prepare | 570 (0 skipped, 0 failed) | **566 (0 skipped, 0 failed)** |
| JVM verify | 570 OK / 0 FAILED | **566 OK / 0 FAILED** |
| ASM verifier | 0 | **0** |
| AtTargetScan PROBLEMS | 4(已知基线) | **0** |
| RefmapScan MISSING | 0 | **0** |
| RuntimeContractScan | 0 / 0 / 0 | **0 / 0 / 0** |
| LambdaScan DANGLING | 0 | **0** |

## 还需要用户实际用一遍的部分

离线全绿 + 启动成功之后,剩下的是**玩法层面**的确认(我这边只能到"起来了"):

1. **进世界**:区块重建走的是 `SectionCompiler.optifine$compile` 那条被重定向的路,标题界面碰不到它;
2. **光影**:shaderpack 能加载(抗锯齿走 `OptifinePostChainFixer` 补写的那条老式链);
3. **物品模型 / 生物 / 方块渲染**:`CuboidItemModelWrapper.update` 与 `LevelRenderer.extractBlockOutline`
   都用原版方法体盖掉了 OptiFine 的实现,OptiFine 自己那条渲染路径是否还完整需要看画面;
4. **多人**;5. 崩溃报告里 `OptiFabric` 那节。

## 进世界之后暴露的问题(已修)

标题界面碰不到渲染管线,所以真机进世界才是这一轮的试金石。用 `launch-26.ps1 -World <世界名>`
(`--quickPlaySingleplayer`)直接进世界,逐个修掉:

### 1. Fabric 渲染器占位注册不上(第一次进世界就崩)

```
UnsupportedOperationException: Attempted to retrieve active rendering plug-in before one was registered.
  at net.fabricmc.fabric.impl.client.renderer.RendererManager.getRenderer
  at net.fabricmc.fabric.api.client.renderer.v1.Renderer.get
  at ...BlockFeatureRenderer.handler$znj000$fabric-renderer-api-v1$beforeInitBlockRenderer
```

`RendererApiFallback` 查的是 `net.fabricmc.fabric.api.renderer.v1.Renderer`,而 **26.1 把接口和它的注册表
一起挪进了 client 包**(`api.client.renderer.v1.Renderer` / `impl.client.renderer.RendererManager`)。
查不到就 `ClassNotFoundException`,而那条路径原本是"没有 Fabric API"的正常分支 —— 于是**静默返回**,
占位从来没注册过。现在按新→旧顺序尝试两个位置。1.21.x 走旧名字,行为不变。

### 2. 占位自己抛异常(第二个崩点)

注册成功之后,第一个画方块的帧就崩在**我们自己的占位**上:

```
UnsupportedOperationException: ...（OptifineRendererPlaceholder 那段话）
  at ...OptifineRendererPlaceholder.quadEmitter
  at ...BlockFeatureRenderer.renderBreakingBlockModelSubmits
  at ...BlockFeatureRenderer.renderTranslucent
  at ...GameRenderer.renderItemInHand
```

`BlockFeatureRenderer` **不是 OptiFine 打的补丁类**,所以那些调用是 **Fabric API 自己的代码**被注入进原版方法后
在普通绘制路径上跑的 —— 1.21.x 时代只有 F3 那条调试行会碰它,"抛异常"才是诚实的做法;26.x 不是。

先按 1.21.x 的老办法把 `renderMovingBlockSubmits` / `renderBlockModelSubmits` 用 `StubInjectionTargetFix`
改成死代码,但**这条路走不通**:Fabric API 的调用是内联在多个方法体里的,逐个堵是打地鼠。

所以改成让占位**返回形状正确的惰性对象**:`RendererApiStubGenerator` 现在会递归生成返回类型对应的接口
占位(实测 7 个类),fluent 接口(抽象方法返回自身)直接把 `this` 还回去,其余返回默认值。
依然是"只读 class 文件、绝不解析参数类型"那套(见该文件头部:曾经因为 `getMethods()` 提前加载
游戏类而崩在 `getBlockStateBaseCacheClass`)。

代价是明确的:**Fabric API 想画的 quad 哪儿也不去,世界由 OptiFine 自己画** —— 这正是
`contains_renderer: true` 声明的东西。

### 3. Indigo 在 26.x 上根本不是地形渲染器(所以那个键是多余的)

上面那句"Fabric API 想画的 quad 哪儿也不去"是当时诚实的描述,但它把 **1.21.x 的取舍带到了这一线**。
两条线上的 indigo 不是同一个东西:

| | 1.21.11(Fabric API 0.141.6) | 26.1.2(Fabric API 0.155.3) |
|---|---|---|
| indigo | **5.0.3**,11 条 mixin(`SectionCompilerMixin`、`ModelBlockRendererMixin`、`RenderSectionRegionMixin`、`BlockRenderDispatcherMixin`、`SubmitNode*` + 物品两条) | **8.1.5**,只剩 3 条:`BlockModelLighterAccessor`、`ItemFeatureRendererAccessor`、`ItemFeatureRendererMixin` |
| 它还是地形渲染器吗 | 是 | **不是** |

26.1 把地形与提交节点的整合搬进了 `fabric-renderer-api-v1` 自己(`SectionCompilerMixin`、
`BlockFeatureRendererMixin`、`VanillaBlockModelPartEncoder`、`QuadConsumers`、`FabricSubmitNodeCollection`…),
而**这些 mixin 不受 `contains_renderer` 影响,本来就一直在生效**。于是这一线的实际情况是:`Renderer.get()`
一直有人在问(Fabric API 自己的钩子),而键一声明,**唯一能回答它的渲染器就被关掉了** —— 拿到的是我们的惰性占位,
mod 生成的网格**静默消失**(不报错、不显示),F3 那行 `Renderer: OptifineRendererPlaceholder` 就是它。

**改法**:26.x 的 `fabric.mod.json` 不再声明该键(两条线的 metadata 因此在构建期分叉:`custom` 块由
`expand` 注入,见 `v26.x/build.gradle` 与 `v1.21.x/build.gradle`);`RendererApiFallback` 把这个键**读回来**,
只有它被声明时才注册占位器,否则让 Indigo 自己注册 `IndigoRenderer.INSTANCE`(26.x 的 `Renderer` 一共只有
3 个抽象方法,Indigo 自己的实现就是完整答案)。1.21.x 的 jar 照旧声明,那一线的行为一字未变。

**先量后改**:indigo 那 3 条 mixin 的目标类 OptiFine **全都补丁了**(OptiFine 26.1.2 的 jar 里
`patch/srg/net/minecraft/client/renderer/feature/ItemFeatureRenderer.class.xdelta` 与
`.../client/renderer/block/BlockModelLighter.class.xdelta` 都在),所以先在补丁产物上逐个核对成员:
`ItemFeatureRenderer.renderSolid` / `renderTranslucent` 仍在(描述符与 indigo 的 `@Inject` 一致),
`getFoilBuffer` / `computeFoilDecalPose` 仍在(`@Invoker` 的目标,私有无妨 —— Mixin 会放宽),
`BlockModelLighter.CACHE` 仍是 `ThreadLocal<BlockModelLighter$Cache>`(`@Accessor` 的目标)。
(离线 `AtTargetScan` 在 26.x 上本来就是 `PROBLEMS 0`:那句 `[indigo: disabled by ...]` 只是标注文案,
扫描本身一直包含 indigo。)

**真机结果**(`launch-26.ps1 -World OptiTest`,世界里有光影):

```
[OptiFabric] Indigo is present and nothing declared fabric-renderer-api-v1:contains_renderer,
             so it registers Fabric's rendering plug-in itself - not registering a placeholder
[Render thread/INFO]: [Indigo] Registering Indigo renderer!
[Render thread/INFO]: Mixing ItemFeatureRendererAccessor … into net.minecraft.client.renderer.feature.ItemFeatureRenderer
[Render thread/INFO]: Mixing ItemFeatureRendererMixin … into net.minecraft.client.renderer.feature.ItemFeatureRenderer
[Render thread/INFO]: Mixing BlockModelLighterAccessor … into net.minecraft.client.renderer.block.BlockModelLighter
[Render thread/INFO]: Renaming @Accessor method fabric_getCACHE()Ljava/lang/ThreadLocal; …
```

进世界、区块与实体渲染、光影全部照常;mixin 变换失败 0、异常 0(上面第 2 节那个"占位自己抛异常"的崩点
从此不存在)。**`[Indigo] Different rendering plugin detected` 这行不该再出现** —— 它才是"让位生效"的标志。

**仍然有意保持惰性的两处**:`BlockFeatureRenderer.renderMovingBlockSubmits` / `renderBlockModelSubmits` 照旧
改名(`optifabric$movingBlocks` / `optifabric$blockModels`)并把调用者一起改过去,即这两条 Fabric 钩子仍然
收不到调用、由原版方法体绘制(见 `OptifineFixer.registerOfficialNameFixes`)。渲染器现在是真的了,理论上
可以把它们放回去、让那些提交走 Indigo —— 但那是与**已实测的绘制路径**不同的另一条路,要单独测过再动。

### 4. 抗锯齿的 post chain 被我们弄坏了(第三次进世界的日志)

```
ShaderManager$CompilationException: Could not find post chain with id: minecraft:fxaa_of_2x
  at ShaderManager.getPostChain -> GameRenderer.render
```

`OptifinePostChainFixer` 的整个前提在 26.x 上是**反的**:它为 1.21.x 写(那些版本从
`assets/minecraft/shaders/post/` 读链),所以它**补写老式文件、并把新式的 `post_effect/*.json` 删掉**。
而 26.1.2 读的正是 `post_effect/` —— 删掉它才是链加载失败的原因,补写的老式文件根本没人读。
现在这一支只在混淆线(intermediary)执行,未混淆线原样保留 OptiFine 自带的 `post_effect/`。

### 5. 实时生成的几何:把 Fabric 的 FRAPI 钩子搬进 OptiFine 的循环(本轮)

第 3 节让 Indigo 注册了真渲染器,但**渲染器存在 ≠ 几何会被画**:Fabric 的地形 FRAPI 入口是
`fabric-renderer-api-v1` 的 `SectionCompilerMixin`,它对 `compile` 下两个注入 ——

```
@Inject  method="compile"  at=INVOKE Lnet/minecraft/core/BlockPos;betweenClosed(...)   → 建立 altBlockRenderer / altQuadOutput
@Redirect method="compile" at=INVOKE ModelBlockRenderer.tesselateBlock(...)            → 把模型交给 AltModelBlockRenderer
```

而 OptiFine 的 `optifabric$compile` 里 **`betweenClosed` 出现 0 次**(整个循环被它换成了自己的实现),所以那两个注入
只落在我们补回的、**没有任何调用者**的原版方法里:mixin 注入成功、不报错、永不执行。需要按方块位置看邻居来生成几何的模型
(LBG 的"更好的草"就是),`emitQuads` 一次都没被问过,几何**静默消失**。

**第一版(错在哪)**:照抄 Fabric 的做法,把 quad 直接写进区块层缓冲 —— 用 `SectionCompiler.getOrBeginLayer(map, pack, layer)`
拿 BufferBuilder 然后 `quad.buffer(...)`。真机上表现为**一切方块透明**,而且症状与光影无关(关掉光影照旧)。原因在链路上:

```
[OptiFabric] … failed on …LBGLayerBakedModel: layer CUTOUT has no buffer in the section's empty started-layers map
```

**那张 map 是空的** —— OptiFine 不用 `startedLayers` 存放它的层级缓冲,它把缓冲留在自己的 `BlockQuadOutput` lambda
(`RenderEnv.setCompileParams`)里。于是 `getOrBeginLayer` 在同一个 `ByteBufferBuilder` 上**又造了一个 BufferBuilder**,
两个写入者互相覆盖顶点 → 整层数据成垃圾 → 方块全透明。

**第二版(现在的做法)**:几何由 Fabric 产出,**顶点交给 OptiFine 写**。

1. `AltModelBlockRenderer.tesselateBlock(emitter, 0,0,0, level, pos, state, model, seed)` —— 偏移传 0,让 quad 保持
   方块局部坐标(这正是 `BakedQuad` 的坐标系),AO / 染色 / 光照由 Indigo 的这套实现算好;
2. 每个 quad:`QuadView.toBakedQuad(sprite)`(Fabric 自己的默认方法)转成原版 `BakedQuad`,精灵从**方块图集**里查;
3. `QuadInstance` 填 `color(i)` / `lightmap(i)` / `NO_OVERLAY`;
4. 交给**OptiFine 传进来的那个 `BlockQuadOutput`**:顶点格式、层级缓冲、光照、光影属性全归 OptiFine 自己
   —— 这里一次都没有碰过顶点缓冲。

踩到的三个坑(都已修,记下来省下一次):

- **图集 id 变了**:`AtlasManager.getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS)` 直接抛
  `IllegalArgumentException: Invalid atlas id: minecraft:textures/atlas/blocks.png` —— 26.x 的图集键不是这个。
  改成用 `AtlasManager.forEach` 按图集**自己报告的 `location()`** 找,并且每 256 个 quad 复查一次(资源重载会换掉图集对象);
- **调用的描述符**:OptiFine 那个 `ModelBlockRenderer.tesselateBlock` 是**静态**方法、**渲染器本身是它的第一个参数**。
  我用"匹配用的描述符"去构造替换调用,于是渲染器那个值留在了栈上 → 管线给改动过的类重算栈帧时在 ASM 里
  `ArrayIndexOutOfBoundsException: Frame.merge`,整个 `SectionCompiler` 被丢弃(`Prepared 566 (0 skipped, 1 failed)`)——
  比没有功能严重得多。正确做法是匹配用 9 参、替换用 10 参+额外状态;
- **只接管该接管的模型**:Fabric 的钩子对**每个**方块都替换 tessellate 调用,但在 OptiFine 下这不能照搬 ——
  OptiFine 的 `ModelBlockRenderer` 会写它光影管线要用的额外顶点属性(它 `setMidBlock` 的那套),把所有方块都交给
  Fabric 就会"画面变了但不对"(实测:发黑 / 光照怪)。所以只路由**`emitQuads` 声明在游戏之外**的模型(模组模型),
  原版模型一律留在 OptiFine 的路由上。

**真机结果**(光影开启,用户确认):**LBG 的更好的草正常、连接纹理正确**;日志顺序为

```
[OptiFabric] …LBGBakedModel emits quads of its own, so those blocks are tesselated through Fabric's renderer …
[OptiFabric] OptiFine's chunk build reaches Fabric's block renderer through the block bridge … the compile loop was carrying 0 started layer buffer(s)
[OptiFabric] Fabric's first block quad was handed to OptiFine's own quad output, so its vertex format, layers and lighting stay OptiFine's
```

没有任何 `Falling back`,也没有崩点。离线校验同日复跑:`Prepared 567 (0 skipped, 0 failed)`、`verified OK 567`、
ASM 0、OptiFine 879/0、五个扫描器全 0。

**仍然有意保持惰性的两处**:`BlockFeatureRenderer.renderMovingBlockSubmits` / `renderBlockModelSubmits` 照旧改名成死代码
(移动方块与"方块模型提交"仍由原版/OptiFine 路径绘制)。这条桥只解决**地形**(区块构建)这一路。

### 6. 26.x 用独立 mod id 绕开"被声明的"不兼容

`fabric.mod.json` 里 `"breaks": {"optifabric": "*"}`(LambdaBetterGrass 就有)是**加载器层面**的硬拦,发生在任何代码
运行之前,而且它匹配的是 **mod id**、不是显示名 —— 于是有两条路:让上游去掉它,或者这一线不做 `optifabric`。

选了后者:26.x 用 **`optifabric_reforged` / "OptiFabric Reforged"**(`v26.x/gradle.properties` 的
`archives_base_name`、`build.gradle` 里注入 metadata 的 `optifabric_id`/`optifabric_name`,产物名
`OptiFabric-Reforged-1.2.0+mc26.1.2.jar`);1.21.x 仍是 `optifabric`,已发布的 1.1.0 与 `dist/` 里那十个 jar 一个字节都不动。

效果与代价:

- 不止 LBG —— **所有**声明 `breaks: optifabric` 的模组都不再拦 26.x;而 1.21.x 那条线(Indigo 仍需让位、FRAPI 几何确实
  不渲染)保留原 id,上游当年的声明在那里依然成立;
- 我们自己的逻辑不受影响:`RendererApiFallback` 判断 `contains_renderer` 是**遍历所有模组的 metadata**,与 id 无关;
- 实测:上游**未经修改**的 `lambdabettergrass-2.7.2+26.1.1.jar`(SHA-256 `CEC0CDD7CDAF1730C9DE6866764FB474D6EFD1418FB51E375EBA9CA782B1AC40`)
  与本模组一起启动成功,更好的草与连接纹理正常;不再需要改别人的 jar。

### 进世界的最终判定

```
mixin 变换失败 0 | crash 0 | 未捕获异常 0 | post chain 失败 0 | 干净退出(Stopping!)
```

### 26.1.2 基线(增加一个类:我们接管的 BlockFeatureRenderer)

| 检查 | 1.21.11 | **26.1.2** |
|---|---|---|
| Prepare | 570 (0 skipped, 0 failed) | **567 (0 skipped, 0 failed)** |
| JVM verify / ASM | 0 / 0 | **0 / 0** |
| AtTargetScan PROBLEMS | 4(已知) | **0** |
| RefmapScan MISSING | 0 | **0** |
| RuntimeContractScan | 0 / 0 / 0 | **0 / 0 / 0** |
| LambdaScan DANGLING | 0 | **0** |

## 真机验收:通过

用户在自己的 26.1.2 实例里实际用了一遍,**确认完全没问题**。这份清单就是当时的验收项:

1. 启动、OptiFine 加载、标题界面、进入世界;
2. 方块 / 物品 / 生物渲染;
3. 抗锯齿(链能加载之后,画面效果也对);
4. 光影(shaderpack 加载并渲染);
5. 多人;
6. 崩溃报告里 `OptiFabric` 那节。

至此 26.x 这一轮的移植闭环。1.21.x 未受影响:八个版本照旧可用(见 [`DEVELOPMENT.md`](DEVELOPMENT.md) 的最终成绩),
`v1.21.x` 项目的离线校验在改动前后数字完全一致。

发布材料(`release/` 那一套、`docs/PUBLISHING.md` 的流程)这一轮**没有**为 26.x 准备;
要把 26.1.2 正式发出去,还需要按 1.21.x 的先例补:版本备注、`dist/` 里的 jar、`release/publish.ps1` 的版本列表。

## 之前顺带改掉的路径引用

分离之后,原来指向"根目录就是唯一项目"的地方都已更新:`test-downloads/verify-version.ps1`(构建命令与产物路径)、
`test-downloads/version-setup.ps1`(提示语)、`test-downloads/CheckFixerIds.ps1`(源码目录 → `common/`)、
`test-downloads/harness-cp.txt`(mod 编译产物 → `v1.21.x/build/...`)、
`docs/PUBLISHING.md`、`docs/DEVELOPMENT.md`、`docs/RELEASE_NOTES*.md` 的构建命令。

**注意**:根目录不再有 `build.gradle` / `settings.gradle` / `gradle.properties`,所以裸敲 `.\gradlew build`
会失败 —— 必须带 `-p v1.21.x` 或 `-p v26.x`。IntelliJ 里也需要把这两个文件夹重新作为两个 Gradle 项目导入。

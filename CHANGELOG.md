# 更新日志

## 1.2.0+mc26.1.2 — 26.x 线的第一版(未混淆)

**Minecraft 26.1.2** —— 26.1 起游戏**未混淆**,这是一条与 1.21.x 完全独立的线,两边的 jar **不能互相替代**。

官方名就是运行名,既没有 yarn 也没有真正的 intermediary 可重映射(26.1.2 只发布占位 `intermediary:0.0.0`)。
因此 26.x 用 Loom 的**非重映射** flavour(`net.fabricmc.fabric-loom`)、不写 `mappings`、运行期命名空间是
`official` 而不是 `intermediary`;1.21.x 那套按 `class_XXXX` 注册的 fixer 判据在这一线指向的类**根本不存在**,
所以另建了一张"官方名"注册表。

```powershell
.\gradlew -p v26.x build        →  OptiFabric-1.2.0+mc26.1.2.jar
```

### 本版修复

- **注入点(最主要的一类)**:OptiFine 重编译时把原版方法**削成薄壳**、真正的实现搬进它自己加的重载,
  而 Fabric API 用**不带描述符**的 `method = "..."` 指名目标 —— 恢复原版方法体之后类里出现两个同名方法,
  MixinExtras 建不出局部变量上下文,整个类变换失败。逐处消歧:`LevelRenderer`、`SectionCompiler`、
  `CuboidItemModelWrapper`、`ScreenEffectRenderer`、`ModelManager`;
- **渲染器占位**:26.1 把 Fabric 渲染器 API 挪进了 `api.client.renderer.v1`,按旧名字查找失败使占位
  **从未注册**,第一个 `Renderer.get()` 就把游戏带走;并且占位**不再抛异常** —— Fabric API 自己的渲染钩子
  会在普通帧里调用它,现在返回形状正确的惰性对象(Fabric API 想画的 quad 哪儿也不去,世界由 OptiFine 绘制);
- **抗锯齿**:26.x 从 `post_effect/` 读后处理链,而 1.21.x 那套修复的做法是**删掉该文件**、补写老位置的链 ——
  在这一线正好是反的。现在按版本线分开处理;
- **渲染路径**:移动方块与普通方块模型这两处 Fabric API 钩子改为惰性(世界仍由 OptiFine 绘制)。

### 要求与实测

| 项 | 值 |
|---|---|
| Minecraft | 26.1.2(**只支持这一个版本**) |
| Fabric Loader | >= 0.19.5 |
| Java | **25**(与 1.21.x 的 Java 21 不同,26.1.2 本身要求 25) |
| OptiFine | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`(目前只有 preview) |
| Fabric API | 0.155.3+26.1.2 |

实测:启动、进世界、方块/物品/生物渲染、抗锯齿、光影、多人全部正常。

产物:`OptiFabric-1.2.0+mc26.1.2.jar` — 163164 字节
`SHA-256: 672F3895AB656FACDA42C93218F885BA21487D929A92C9E05D542A4D0A20B64A`

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

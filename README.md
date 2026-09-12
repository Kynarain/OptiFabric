# OptiFabric — Minecraft 1.21.x 移植版 (Fabric)

#!!!此模组由deepseek编写并验证请小心用于生产环境!!!#

让 **Fabric Loader** 与 **OptiFine** 在同一客户端共存。把 OptiFine 的 jar 丢进 `mods/`,OptiFabric 会在游戏启动时给原版客户端打补丁、重映射命名空间,并把结果接到 Fabric 的类加载流程里。

- 目标版本: **Minecraft 1.21 ~ 1.21.11**(OptiFine 出过构建的全部 10 个版本), Fabric Loader **≥ 0.19.5**, Java 21 及以上
- 实测搭配(1.21.11): **Fabric API 0.141.6+1.21.11**、**OptiFine 1.21.11 HD_U J9**(build `20260205-175838`)、Java 25
- 产物: 每个版本一个 jar,`build/libs/OptiFabric-1.0.0+mc<版本>.jar`
- 许可: **MPL-2.0**(`LICENSE.txt`),核心机制移植自 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)
- 开发/验证记录(逐轮崩溃的根因、每个版本的差异、可复现的离线校验工具):[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)

### 支持的版本

| Minecraft | 产出的 jar | OptiFine 构建 | 真机验证 |
|---|---|---|---|
| 1.21 | `OptiFabric-1.0.0+mc1.21.jar` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` | **已实测正常** |
| 1.21.1 | `OptiFabric-1.0.0+mc1.21.1.jar` | `OptiFine_1.21.1_HD_U_J1.jar` | **已实测正常** |
| 1.21.3 | `OptiFabric-1.0.0+mc1.21.3.jar` | `OptiFine_1.21.3_HD_U_J2.jar` | **已实测正常** |
| 1.21.4 | `OptiFabric-1.0.0+mc1.21.4.jar` | `OptiFine_1.21.4_HD_U_J3.jar` | **已实测正常** |
| 1.21.6 | `OptiFabric-1.0.0+mc1.21.6.jar` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` | **不推荐:该版 OptiFine 构建自身缺陷,启动即崩** |
| 1.21.7 | `OptiFabric-1.0.0+mc1.21.7.jar` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` | **不推荐:同上** |
| 1.21.8 | `OptiFabric-1.0.0+mc1.21.8.jar` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` | **已实测正常(多人崩溃已修)** |
| 1.21.9 | `OptiFabric-1.0.0+mc1.21.9.jar` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` | **已实测正常(含抗锯齿)** |
| 1.21.10 | `OptiFabric-1.0.0+mc1.21.10.jar` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` | **已实测正常(含抗锯齿)** |
| 1.21.11 | `OptiFabric-1.0.0+mc1.21.11.jar` | `OptiFine_1.21.11_HD_U_J9.jar` | **已实测正常** |

OptiFine 没出过 **1.21.2 / 1.21.5** 的构建,所以这两版没有对应 jar。10 个版本都已经跑过完整的离线校验(JVM + ASM 双向 + 5 个扫描器,逐版本数字见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md))。上表**真机一列是 2026-09-12 两轮装机实测的结果**,五处已定位到根因并修复:

1. **1.21.3 / 1.21.8 的 `VerifyError`** —— 补丁管线给**未被任何 fixer 改动**的类也重算了栈帧(`MissingOverrideFix` 是全局的),合并分支类型时退化成 `java/lang/Object`,游戏拒绝加载该类。现在这类类保留 OptiFine 自己的栈帧。
2. **1.21 / 1.21.4 的 Mixin 转换失败** —— 两类注入点被改掉了:`DelegatingConstructorFix` 内联 OptiFine 的委托构造函数时,把原版构造函数里那次 `Identifier.ofVanilla(name)` 换成了 OptiFine 的 `new Identifier(name)`(Fabric API 的 `@WrapOperation` 包的正是前者);1.21.4 上 OptiFine 把 `InGameHud` 构造函数里三条 layer 方法引用改写成了 `lambda$new$0/1/2`,而那一版 Fabric API 的自定义注入点是**按方法句柄**匹配的。现在内联照抄**原版**的转换调用,`LambdaMethodRefFix` 则把这些 lambda 改名回游戏使用的方法名(改名而非指回原版方法,是为了不丢掉 OptiFine 在这些 layer 里的附加逻辑,如 QuickInfo)。
3. **1.21.1 的 Mixin 转换失败** —— 同一处注入点,但 OptiFine 在那里用的是**静态工厂**委托(`this(provider, Identifier.ofVanilla(id), type)`),内联 fixer 原来只认 `new Identifier(...)` 那一种形状,于是注入点留在 `this()` 之前。两种形状现在都识别。
4. **1.21 / 1.21.3 / 1.21.4 进世界十几秒后崩**(`ChunkCacheOF.renderStart()` 收到 null)—— `RegionSectionPosFix` 在 1.21–1.21.4 上没生效:那些版本的 region 构建器收的是 `ChunkSectionPos` 对象,1.21.6 起才是打包 long,fixer 只处理后者就整段跳过了。现在两种形状都支持。
5. **1.21.6 / 1.21.7 光影完全没反应** —— OptiFine 那两个预览构建的 `Shaders.loadShaderPack()` 在检查前写死了 `cancelled = true`,于是 `getShaderPack()` 永远不被调用,选任何包都是 `No shaderpack loaded`(1.21.8 起的构建是正常写法)。新的 `OptifineJarFixer` 在映射后的 jar 上把这两条指令删掉;**同一组件还修好了 1.21.9**:OptiFine 自带的 `post_effect/fxaa_of_{2,4}x.json` 把 `minecraft:post/blit` 当成顶点着色器,而 1.21.9 起游戏只有 `post/blit.fsh`(顶点阶段是 `core/screenquad`),后处理管线编译失败连带光影初始化失败。

**唯一不属于补丁的一条**:1.21.8 / 1.21.10 上光影"加载了但渲染不对",是光影包 `photon_v1.2a.zip` 里用了 OptiFine 不认识的程序名(`gbuffers_entities/particles/block_translucent`、`gbuffers_all_translucent`,以及 Distant Horizons 用的 `dh_water`/`dh_terrain`),OptiFine 只报 `Invalid program name` 并跳过。换一个 OptiFine 专用包(例如 `ComplementaryReimagined_r5.9.1.zip`)即可验证。详见 DEVELOPMENT.md 的"第二轮真机反馈"。

**一个 jar 只能对应一个版本**:jar 里打包的是该版本的 `official→intermediary` 映射表(官方混淆名每版不同),`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本。构建任意版本:

```powershell
.\gradlew build "-Pmc=1.21.8"      # PowerShell 里必须加引号,否则 1.21.8 会被拆成 1
.\gradlew build                     # 不带参数 = gradle.properties 里的默认版本
```

---

## 1. 工作原理

OptiFine 不是 Fabric 模组:它的 jar 里是**针对原版(混淆名)Minecraft 类的字节码补丁** + OptiFine 自己的类。要让它在 Fabric 下跑起来,必须完成四件事,这也是本模组在 `preLaunch` 阶段做的:

```
mods/OptiFine_1.21.11_HD_U_J9.jar
        │  ① 用 OptiFine 自带的 optifine.Patcher 给原版(混淆)客户端 jar 打补丁
        │     (1.21.6 起的 OptiFine 用自己的 xdelta 差分包,但 Patcher.process 的用法没变)
        ▼
  打补丁后的 vanilla jar  (OptiFine 的补丁 + OptiFine 的类)
        │  ② LambdaRebuilder:补丁类里的 lambda(invokedynamic)指向已被搬走的原方法,需要重建
        │  ③ tiny-remapper:official(混淆) → intermediary 命名空间重映射
        │     **必须把游戏 jar 一起放进重映射器的 classpath**,否则子类里覆写的方法继承不到映射
        ▼
  Optifine-mapped.jar
        │  ④ 拆成两部分
        ├── 非 Minecraft 类(OptiFine 自己的类/资源)──► 加进游戏 classpath
        └── net/minecraft/** 打过补丁的类 ──────────► ClassCache(替换用)
```

替换通过 **Fabric Loader 自己的 GameTransformer** 完成:Minecraft 类被加载时,Loader 的 `KnotClassDelegate.getPreMixinClassByteArray` 会先问游戏 provider 的 `GameTransformer.transform(类名)` 有没有现成的字节码 —— 这一步**在 Mixin 之前**。所以 OptiFabric 在 preLaunch 阶段把打过补丁的 MC 类(先应用 `patcher/fixes` 的版本修正、再对齐访问级别)直接放进那个 transformer 的 `patchedClasses` 里,类加载时就会被顶替;Loader 自己补过的类(客户端 brand 等)保持 Loader 的版本不动。

这样做的好处是:不需要为每个补丁类动态生成 stub mixin,也不依赖 Mixin 的扩展 API —— 而且因为交出去的是 Mixin 的**输入**而不是输出,其它模组针对这些类的 mixin 仍然照常生效。

**一条硬性约束**(1.21.11 移植里踩过):在把补丁类交给 Loader **之前**,任何一次对游戏类的反射解析(例如 `Class.getMethods()`,它会把方法签名里的 `net.minecraft.*` 全部**加载**掉)都会把这些类永久钉成原版 —— 类一旦加载就不会再查转换器。所以这段代码里只允许拿**字节**(`getClassByteArray` / ASM),不允许拿 `Class` 对象。

所有中间产物缓存在 `<游戏目录>/.optifine/<OptiFine 版本>/`:

| 文件 | 内容 |
|---|---|
| `cache-format.txt` | 缓存格式版本(当前 `15`);数字与代码里不一致就整份重建 |
| `Optifine-mapped.jar` | 重映射后的 OptiFine(不含 MC 类),这就是加进 classpath 的 jar |
| `Optifine.classes.gz` | 打过补丁的 MC 类缓存(ClassCache),用于下次启动直接复用 |

**升级 OptiFabric 后如果行为没变化,先删掉 `<游戏目录>/.optifine/`**:缓存里存的是**打过补丁的字节码**,只要补丁管线改了(缓存的格式号就会被提升,正常情况下会自动重建),旧缓存就会让新 jar 看起来"没生效"。

---

## 2. 使用

1. **准备 OptiFine**:下载与你的 MC 版本**严格一致**的 OptiFine(对应构建见上表)。OptiFabric 会读取 jar 内 `optifine/Config` 的 `MC_VERSION` 校验,不一致会直接在标题界面报错。安装器形态(含 `patch/` 差分包,如 `OptiFine_1.21.11_HD_U_J9.jar`)和已解包的模组形态(含 `notch/<混淆名>.class`)都支持 —— 直接丢进 `mods/` 即可,**不需要**先运行它的安装器。
2. **编译**(需要联网下载依赖,或本地已有 Gradle/Loom 缓存):
   ```
   gradlew build "-Pmc=1.21.11"     # 换成你要的版本;不带 -Pmc 则构建默认版本
   ```
3. **安装**:把**对应版本**的 `build/libs/OptiFabric-1.0.0+mc<版本>.jar` 和 OptiFine 的 jar 一起放进 **该 Fabric 版本自己的 `mods` 目录**。**不要**同时放两份 OptiFine(会报 `DUPLICATED`),也不要放错版本的 OptiFabric jar(jar 里的映射表是绑定版本的)。
   - PCL2/HMCL 若开启了**版本隔离**,游戏目录是 `versions/<版本名>/`,mods 目录也在那里;`.optifine/` 缓存同样会建在版本目录下。没开隔离才是 `.minecraft/mods`。
   - 用 **Fabric 版本**启动,不要用启动器装的 `1.21.x-OptiFine_xxx` 版本(那个是启动器自己在启动时注入 OptiFine,会和本模组重复)。
4. **启动**:首次启动会多花几秒(实测 5–7 秒)做补丁+重映射(控制台里会看到 `[OptiFabric]` 前缀的输出),之后走缓存(1–2 秒)。成功的标志:标题界面出现 OptiFine 版本号,视频设置里出现 OptiFine 选项。

### 支持的启动环境

目前**只支持生产环境**(runtime namespace = `intermediary`),也就是用正常启动器(官方启动器 + Fabric Loader,或 Prism/MultiMC/PCL 等)启动装好的 jar。

在开发环境里直接 `gradlew runClient` 会明确报错并被拒绝(dev 的命名空间是 `named`,需要额外的 contextual mapping 层,见"已知限制")。

---

## 3. 与上游 OptiFabric 的差异

| 方面 | 上游 (≤1.20.4, Loader 0.15) | 本移植 (1.21.11, Loader 0.19.5) |
|---|---|---|
| 类替换挂钩 | Fabric-ASM / Manningham Mills(`mm:early_risers` 入口 + `ClassTinkerers` + 运行时生成 stub mixin) | **自实现**:注入 Loader 的 `GameTransformer.patchedClasses`(`GameTransformerHook`),全程不碰 Mixin API |
| OptiFine jar 上 classpath | Fabric-ASM 反射式 `addURL` | Fabric Loader 自带 API `FabricLauncherBase.getLauncher().addToClassPath(...)` |
| 重映射器 | 自己依赖 `net.fabricmc:tiny-remapper:0.8.11` | 直接用 **Loader 内嵌的 tiny-remapper**(`net.fabricmc.loader.impl.lib.tinyremapper`,0.14 API),不额外打包依赖;并显式把游戏 jar 放进 classpath 与输入 |
| 映射表 | 构建期把 mappings 打进 jar | 同样:构建期把 `net.fabricmc:intermediary:1.21.11:v2` 的 `mappings/mappings.tiny` 打进去 |
| 每 mod 兼容 mixin | 数十个(`compat/**`,针对 fabric-api / architectury / apoli …) | **未包含**(它们依赖 MM 的 early riser 机制) |
| contextual mapping | 有:人工维护的硬编码表,按版本手写(`this$0`/`this$1`/`field_3835` 等) | **改为规则推导**:`OptifineMappings` 按字段名形状 + 描述符匹配(含沿继承层次找覆写),自动对齐名字、类型与构造器里存入的值 |
| 版本特定补丁修正 | 面向 1.20.4 等 | **`patcher/fixes` 里 17 个 fixer**(其中 10 个为本移植新增,见下),全部用离线验证器(JVM + ASM 双向)与真机逐项验证 |

新增或重写的文件(其余文件为逐行移植,仅改包名与必要的 API 适配)。文件头的来源说明与这里一致,而且和上游逐个核对过:

- 上游**没有**对应文件的,注明 `New in the 1.20.6 port …` 或 `New in the 1.21.11 port …`(写明是哪一版写的);
- 上游**有**对应文件的,注明 `Ported from OptiFabric …, Adapted for Minecraft 1.20.6 and 1.21.11`。

```
kynarain/cn/optifabric/Optifabric.java                入口(preLaunch;上游的 OptifabricLoadGuard 是个空类,这个是干活的)
kynarain/cn/optifabric/mod/OptifabricRuntime.java     总调度:找 jar → 打补丁 → 挂 classpath → 注册替换
kynarain/cn/optifabric/mod/GameTransformerHook.java   把补丁类注入 Loader 的游戏 transformer(按字段类型反射定位)
kynarain/cn/optifabric/mod/OptifineMappings.java      取代上游硬编码 contextual mapping 的规则推导
kynarain/cn/optifabric/mod/OptifineRuntime.java       准备结果(remapped jar + ClassCache)
kynarain/cn/optifabric/mod/OptifabricSetup.java       仅保留 optifineRuntimeJar(供崩溃报告用)
kynarain/cn/optifabric/mod/RendererApiFallback.java   给 Fabric 的渲染器 API 注册一个惰性占位渲染器(见第 4 节)
kynarain/cn/optifabric/mod/RendererApiStubGenerator.java  在运行时用 ASM 生成上面那个类(不解析任何游戏类型)
kynarain/cn/optifabric/patcher/fixes/RestoreVanillaMethodsFix.java    把 OptiFine 重编译时丢掉的原版方法体补回来
kynarain/cn/optifabric/patcher/fixes/DelegatingConstructorFix.java    重写 OptiFine 的委托构造器
kynarain/cn/optifabric/patcher/fixes/SyntheticFieldFix.java           this$0/this$1 → 真实字段名(同类型时按声明顺序配对)
kynarain/cn/optifabric/patcher/fixes/ObjectCreationPointFix.java      补回被换掉的 NEW 注入点
kynarain/cn/optifabric/patcher/fixes/InjectionCallPointFix.java       保留 OptiFine 方法体,只补一个惰性调用点
kynarain/cn/optifabric/patcher/fixes/RegionSectionPosFix.java         给 OptiFine 的区域构造器补 section 位置
kynarain/cn/optifabric/patcher/fixes/StubInjectionTargetFix.java      改名 + 留同名副本,让语义不兼容的钩子注入进死代码
kynarain/cn/optifabric/patcher/fixes/CallSiteRedirectFix.java         把**跨类**的调用点也改到改名后的方法上
kynarain/cn/optifabric/patcher/fixes/LambdaMethodRefFix.java          OptiFine 把方法引用改成 lambda 时,把 lambda 改回方法名(自定义注入点按句柄匹配)
kynarain/cn/optifabric/patcher/fixes/MissingOverrideFix.java          全局:补回被 OptiFine 重编译掉的原版方法桥接
```

---

## 4. Fabric API 与 OptiFine 的结构冲突

### 4.1 离线推导出来的结构问题

| 症状 | 根因 | 处理 |
|---|---|---|
| 启动时 Mixin 成片 `Could not find` | 重映射器看不见游戏类层次,子类里覆写的方法继承不到映射表条目(`class_1308` 一个类就漏了 35 个方法) | 把游戏 jar 加进重映射器的 classpath 与输入 |
| `@Shadow field field_27735/field_40571/field_20839/field_61871…` 找不到 | 合成外部实例/捕获字段被 javac 命名为 `this$0`/`val$xxx`,映射表里没有这种名字,且**同名同类型**时只能按声明顺序配对 | `SyntheticFieldFix`(按位置配对)+ `OptifineMappings` 沿继承层次对齐字段引用 |
| 校验错 `Bad type on operand stack` / `Bad local variable type` | 构造器重写搬移槽位破坏了描述符与调用点的一致性 | 改用末尾空闲槽位,并用反汇编逐条核对 |
| 抽象契约被破坏 / 虚方法覆写丢失 | 同上(重映射不完整) | `RuntimeContractScan` 扫出后逐个修正(281 → 0) |
| 被读取但从未被赋值的字段(NPE 地雷) | 某个 fixer 替换了 OptiFine 的构造器,连带丢掉了字段唯一的初始化处 | `UnsetFieldScan` 扫出,改为不替换构造器(`renderEnv` 是唯一一处) |

### 4.2 真机上逐轮崩溃(9 类,全部定位到字节码)

| # | 症状 | 根因 | 修复 |
|---|---|---|---|
| 1 | 启动崩:`Mixin transformation of net.minecraft.class_761 failed` | OptiFine 把 lambda 体重编译成 `lambda$addMainPass$1` 且**多一个参数**,Mixin 按名字+描述符找不到 `method_62214` | 补回原版方法体(同类还有 `class_3898.method_60440`、`class_1092.method_65750`) |
| 2 | 启动崩:`class_1088` 的 `@WrapOperation` 找不到 `method_68018/68019` | 同类,重编译后方法消失 | 补回原版方法体;另给 `class_775.method_3347` 补回被干掉的调用点(`InjectionCallPointFix`) |
| 3 | 启动崩:`@Local class_2338$class_2339` 校验失败 | 原版在 `ARETURN` 处作用域内有 `MutableBlockPos`,补丁后没有(MixinExtras 在**注入点**上判别局部变量) | `RestoreVanillaMethodsFix(true, "method_24225")` |
| 4 | 进世界约 4 秒后崩:`ChunkCacheOF.renderStart() ... regionIn is null` | OptiFine 的 `RenderChunkRegion` 只有**六参数**构造器会写 section 位置,而恢复出的原版 `build()` 调用的是五参数那个 | 新增 `RegionSectionPosFix`:改调六参数构造器,用该方法本就收到的打包 long 生成第六个参数 |
| 5 | 进世界约 4 秒后崩:`WorldRenderContextImpl.worldState()` 为 null | Fabric 的 `BEFORE_BLOCK_OUTLINE` 钩子读的上下文由 `LevelRenderer.method_22710` 头部准备,而 OptiFine 用 `RenderPass` + 自己的 lambda 替换了那条流程 → 上下文没被填充。**这是渲染流程的语义不兼容,不是字节码形状问题** | 新增 `StubInjectionTargetFix`:把 `class_761.method_62210` 改名并留同名副本,让钩子注入进**没人调用**的代码 |
| 6 | 物品贴图全部丢失 | `class_10430.method_65584` 上的 `@Inject(at = RETURN)` 需要 OptiFine 重编译后不再存在的局部变量 → **每个**物品模型都烘焙失败 | `RestoreVanillaMethodsFix(true, "method_65584")` |
| 7 | 进多人服务器约 30 秒崩:`Attempted to retrieve active rendering plug-in before one was registered` | 第 5 项那招对**移动方块的钩子**失效:它的调用者在**另一个类**(`class_11684.method_73002`)里,按名字调到的是 Mixin 刚注入的那份"死代码"副本 | 新增 `CallSiteRedirectFix`:把调用方也接管,调用点改到改名后的真实方法体(`optifabric$movingBlocks`) |
| 8 | 一按 F3 就崩(同一个异常) | 声明 `contains_renderer` 只完成了"让 Indigo 退场";Fabric API 自己的 F3 调试条目仍要 `Renderer.get()`,而此时没有任何渲染器被注册 | `RendererApiFallback` 注册惰性占位渲染器(F3 显示 `Renderer: OptifineRendererPlaceholder`) |
| 9 | 启动即崩:`NoSuchMethodError: class_2680.getBlockStateBaseCacheClass()` | 第 8 项第一版在 preLaunch 调了 `Class.getMethods()` 去读 Fabric API 的接口,而接口方法签名里全是游戏类型 → 这些类被**提前加载成原版**,整局都停在原版上(`class_2680`=BlockState 少了 OptiFine 加的方法,`Reflector` 静态初始化失败) | 生成器改为 ASM **只读接口自己的 class 文件**(不解析任何类型);注册改用 `MethodHandles.findStatic`;注册时机挪到补丁类注入之后 |

### 4.3 为此付出的代价(有意接受的降级)

- **Fabric 的渲染器 API 只是"存在"**:OptiFine 不实现 FRAPI,依赖 indigo 的模组不会获得自定义渲染(地形由 OptiFine 渲染),`Renderer.get()` 拿到的是一个占位实现 —— 真去用它建网格会得到一句明确说明的 `UnsupportedOperationException`。
- **两个 Fabric API 钩子被有意中和**:`BEFORE_BLOCK_OUTLINE` 事件不再触发(方块描边仍照画);移动方块的 FRAPI 渲染钩子失效(移动方块由原版路径正常渲染)。
- **OptiFine 的 `BlockEntity` 补丁被应用**(上游是跳过):跳过它会留下 5 处悬空引用(OptiFine 给它加的 `hasCustomOutlineRendering` 与两个字段被它自己和重编译后的 `class_757` 调用),代价是日志里一条 `Failed to locate initialiser injection point in <init>(class_2591,...)`。

---

## 5. 已知限制与排查

### 验证情况

移植版是在真实游戏里逐轮排查出来的:每一处 Fabric API 与 OptiFine 的结构冲突,都先在**离线复现的补丁管线**上定位到具体字节码,再用 JVM 验证器与 ASM 数据流验证器双向确认,最后才交给真机验证。当前状态(1.21.11):

| 项目 | 结果 |
|---|---|
| 被补丁的游戏类(OptiFine 补丁 568 个 + 本移植额外接管 `class_11681`/`class_11684` 两个) | **570 / 570 通过 JVM 校验**,0 失败 |
| OptiFine 自身的类 | **874 / 874 通过 JVM 校验**,0 失败 |
| ASM 数据流验证器(上面 1444 个类) | **0 问题** |
| mixin 成员引用 / `@At` 注入点 / 局部变量捕获 / `@Shadow` 成员 / 未赋值字段扫描 | 全部通过(仅剩 4 条 `@At` 找不到的,全部属于**已被停用**的 indigo) |
| 真机验证 | 启动、主界面、**单人世界**、**多人服务器**、方块/区块/物品渲染、**光影加载**、F3 调试屏;整轮会话 `[ERROR]` 0 条、无崩溃报告 |

逐轮排查过程、每一类的根因与修法、以及可复现的离线校验工具(`VerifyPatched`、`RefmapScan`、`AtTargetScan`、`LocalsScan`、`ShadowScan`、`UnsetFieldScan`、`RuntimeContractScan`、`FapiRendererFallbackTest`)记录在 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)。

### 已确认的第三方模组不兼容

- **Sodium**:两者都是渲染器,`fabric.mod.json` 里已声明 `conflicts`。
- **RyoamicLights(动态光源)**:OptiFine 把原版视频设置界面(`class_446`)**整类替换成自己的实现,连父类都换掉**,往原版父类上注入的模组会因找不到委托构造器而崩(`Delegate constructor lookup failed`)。这条在 1.20.6 移植上实测确认,1.21.11 沿用同样的声明(`breaks`),未单独复测。OptiFine **自带动态光源**(视频设置 → 品质 → 动态光源),删掉它**不会损失功能**。
- `no_fog`、`thallium`、`xradiation` 同样声明为不兼容(与上游一致)。

更一般地:凡是往 OptiFine **整类替换**的界面/渲染类里注入的模组,都可能以同样方式失败。日志出现 `Delegate constructor lookup failed` 或 `Mixin transformation of <类> failed` 时,先看 OptiFine 是否换掉了那个类的父类。

### 已知限制

- 依赖 FRAPI/indigo 的模组不再有 indigo 的自定义渲染(地形交给 OptiFine)—— 见第 4.3 节。
- OptiFine 不认识 Fabric 的资源包类型(日志里成片的 `[OptiFine] Unknown resource pack type: ...ModNioResourcePack`),所以 **Fabric 模组内部的资源(贴图/CTM 配置等)OptiFine 看不到**。这是 OptiFine 侧的限制,不影响启动与运行。
- 光影包与 OptiFine 版本不完全匹配时会有 `[Shaders] ...` 报错,属光影包自身问题。

### 日志里的正常噪音(不影响运行,别被吓到)

| 日志 | 说明 |
|---|---|
| `[OptiFine] (Reflector) Class not present: net.minecraftforge.*` / `sun.misc.SharedSecrets` | OptiFine 在探测 Forge/旧 JDK 的类,Fabric 上本来就没有 |
| `Failed to locate initialiser injection point in <init>(class_2591,...)` | 第 4.3 节:应用 OptiFine 的 `BlockEntity` 补丁的代价 |
| `[Shaders] Unknown macro value: IRIS_VERSION` / `ANGELICA_VERSION` | 光影包在探测 Iris/Angelica,OptiFine 不认这两个宏 |
| `[Shaders] Invalid macro expression` / `Invalid argument type, function: ">", type: BOOL` | 光影包宏表达式写法与 OptiFine 的解析器不符(实测 Complementary 也有) |
| `[Shaders] ParseException: Model variable not found: BIOME_SULFUR_CAVES` 等 | 光影包引用了 OptiFine 还没有的生物群系/变量(1.21.11 新增内容) |
| `[OptiFine] Shaders: Block not found for name: minecraft:planks` / `double_plant` | 光影包里的旧方块别名 |
| `Skipping bad option: lastServer` | 选项文件里的旧字段 |

### 排查手段

- 日志过滤 `[OptiFabric]`,可以看到准备过程、准备了多少个补丁类、Loader 接管了多少个。
- 标题界面会弹错误对话框:缺 OptiFine / jar 损坏 / 版本不匹配 / 多份 OptiFine / 内部错误,并给出 "打开 mods 文件夹 / 复制堆栈 / 打开 issues" 按钮。
- 崩溃报告里会多出一节 `OptiFabric`,包含 OptiFine 版本、jar 状态、重映射 jar 路径、错误信息。
- 强制重建缓存:删掉 `<游戏目录>/.optifine/`。
- 找不到原版 jar 时(Loader 没有暴露 `fabric-loader:inputGameJar`)可以显式指定:
  `-Doptifabric.mc-jar=<原版 1.21.11 client jar 路径>`
- 调试:`-Doptifabric.extract=true` 会把重映射后的 OptiFine 类解包到 `.optifine/<版本>/optifine-classes/`。
- 注意:`[OptiFabric]` 的输出走**启动器控制台**,通常不在 `logs/latest.log` 里(loader 只把 log4j 的输出写进 latest.log)。所以"日志里没有 [OptiFabric]"不等于模组没跑。
- **模型/物品/贴图成片消失**、或日志里成片的 `Unable to bake ... model` 时,方向是某个 Fabric mixin 变换那个类失败了(最外层消息会把真实原因吃掉)。用 `AtTargetScan` 查注入点是否还在:OptiFine 常把原版方法改成"转发给自己重载"的瘦包装,注入点会随之搬走。物品贴图全丢(第 6 类)就是这么找到的。

**卡住了怎么定位**(画面停在 Mojang 图标/进度条不动):

1. 先看 `logs/latest.log` 的**最后一行**在做什么,以及文件是否还在增长(不增长说明真的停了,不是在慢慢算)。
2. 取**两次**线程转储对比(JDK 自带,只读):`jstack <pid>`,间隔十几秒。
   - 两次栈完全相同、进程 CPU 几乎不涨 → 是卡死,不是慢。
   - 栈顶在**原生调用**里(例如 `GLFW.glfwSwapBuffers`)→ 呈现层问题(vsync/驱动/窗口状态),不是 Java 死锁。再查 GPU 占用:`Get-Counter '\GPU Engine(*engtype_3D)\Utilization Percentage'`;若 GPU 也空闲,就是纯粹在等呈现 —— 可先关垂直同步(`options.txt` 里 `enableVsync:false`)或试全屏/移动窗口。
   - 停在 Java 锁上、且有两个线程互相等待 → 那才是死锁,转储里会写明各自持有的 monitor。
3. 对照时间线:如果转储里**已经没有资源重载工作线程**(`Worker-Main-*`),说明加载其实完成了 —— 此时画面还停在加载界面,通常是"移除加载界面"这一步没能执行(它排在卡住的 Render 线程队列里)。
4. **先查窗口是不是最小化了**(实测踩过一次):`IsIconic(hwnd)` 为真 + `WindowRect` 在 `-32000,-32000` 就是最小化。开着垂直同步时最小化窗口的 `SwapBuffers` 会一直阻塞,Render 线程整个卡在原生 `glfwSwapBuffers` 里,表现和"游戏卡死"完全一样。还原窗口后它会立刻继续跑帧。所以:加载期间**不要最小化游戏窗口**,或者关掉垂直同步。

### 与 OptiFine 的冲突(indigo 让位)

`fabric-renderer-indigo`(Fabric API 自带的地形渲染器)与 OptiFine 只能有一个在场,移植版用 Fabric 自己的机制让 indigo 让位:`fabric.mod.json` 里声明 `"custom": {"fabric-renderer-api-v1:contains_renderer": true}`。这个键本来就**是给"另一个渲染器"用的**(Sodium 用同一个键),而 OptiFine 本身就是地形渲染器。indigo 会打印 `[Indigo] Different rendering plugin detected; not applying Indigo.`。

- **代价**:依赖 FRAPI/indigo 的模组不再有 indigo 提供的自定义渲染(地形由 OptiFine 渲染)。
- **只声明这个键还不够**:Fabric 的渲染器模块不看这个键,它们查**注册表**,空的时候会抛 `Attempted to retrieve active rendering plug-in before one was registered`。所以本模组另外注册了惰性占位渲染器(第 4.2 节第 8、9 项)。
- **想换回 indigo**:删掉 `fabric.mod.json` 里那个 `custom` 键并重新构建 —— 但那样 `ChunkBuilder$BuiltChunk$RebuildTask` 一加载就会因为缺失注入点而崩(见 1.20.6 移植的记录)。

---

## 6. 国内镜像(实测)

| 用途 | 地址 | 实测 |
|---|---|---|
| OptiFine 版本列表 | `https://bmclapi2.bangbang93.com/optifine/1.21.11` | ✅ 200,列出 1.21.11 的全部构建:`HD_U J8`、**`HD_U J9`**(最新发布版)以及 J8/J9 的各 preview |
| OptiFine 下载 | `https://bmclapi2.bangbang93.com/optifine/<MC版本>/<type>/<patch>` | ✅ 302 跳到 `/maven/com/optifine/<MC>/OptiFine_<MC>_<type>_<patch>.jar`。注意路径是**三段**(`/1.21.11/HD_U/J9`),`/1.21.11/HD_U_J9` 是 404 |
| Fabric 安装信息(meta) | `https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader` | ✅ 200,BMCLAPI 代理了 fabric-meta |
| Fabric Maven 本体 | `https://maven.fabricmc.net/` | ✅ 200,国内可直连(慢,但可用);**Aliyun 公共仓库没有 Fabric 构件(404),SJTU/NJU 的 fabric-maven 路径也是 404,不要照抄网上的老地址** |
| Gradle 依赖 | 本项目已全部缓存在本机 `~/.gradle`,直接 `gradlew build --offline` 即可 | ✅ 构建成功 |

1.21.11 的 OptiFine **有正式发布版**:文件名形如 `OptiFine_1.21.11_HD_U_J9.jar`(8,045,105 字节,build `20260205-175838`),直接丢进 `mods/` 即可。

```powershell
# 一条命令拿到 1.21.11 OptiFine(国内直链,实测 302 → 官方 maven 分发)
curl.exe -L -o OptiFine_1.21.11_HD_U_J9.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.11/HD_U/J9"
```

---

## 7. 想继续完善的方向

1. **开发环境支持**:dev 的命名空间是 `named`,需要两段式重映射(official→intermediary→named)并补回上游的 contextual mapping 冲突修正。
2. **把被中和的 Fabric API 钩子换成"真能用"的实现**:例如给 `Renderer.get()` 提供一个能把网格落到原版渲染路径上的实现,而不是占位实现。
3. **把上游 `compat/**` 的每 mod 兼容搬回来**:需要重写 early riser 机制(现在最接近的替代是 `IMixinConfigPlugin#getMixins` 的动态 mixin 列表)。
4. **OptiFine 各项功能的具体效果**(连接纹理、缩放、动态光源、FPS 优化幅度)还没有逐项验证;启动、进世界、模型与区块渲染、光影、多人已确认工作。

---

## 8. 许可与致谢

- 本项目的核心逻辑是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**;移植文件保留了来源说明头。
- `reference/upstream/` 保存了移植所依据的上游源码快照,便于逐行比对。
- **不包含、也不分发 OptiFine 本体**:OptiFine 版权归 sp614x 所有,请自行从官网获取。

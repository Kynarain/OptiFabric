# 开发与验证记录

> 移植过程中的逐轮排查记录,以及可复现的离线校验方法。面向使用者的说明见仓库根目录 `README.md`。

## 实测验证状态

用**真实 OptiFine 1.20.6(HD U I9_pre1)+ 原版 1.20.6 混淆客户端 jar** 跑过以下步骤:

| 步骤 | 结果 |
|---|---|
| OptiFine jar 识别 | ✅ 该安装器把类放在 `notch/net/optifine/Config.class`,`OptifineVersion` 的回退分支正好命中;读到 `MC_VERSION=1.20.6`、`VERSION=OptiFine_1.20.6_HD_U_I9_pre1`,版本校验通过 |
| 安装器判定 | ✅ 含 4996 个 `patch/` 条目 → `JarType.OPTIFINE_INSTALLER`(会走 `optifine.Patcher`) |
| 运行 OptiFine 补丁器 | ✅ `process(mcJar, installerJar, out)`(与 `OptifineSetup.runInstaller` 一致)1.4 秒产出 6.8 MB jar:其中 **421 个 `notch/<混淆名>.class` 是打补丁生成的**(安装器本身为 0),另有 654 个 `notch/net/optifine/**`、1787 个 `assets/**`、67 个 `notch/net/minecraftforge/**`,以及 386 个 `srg/**`(移植的 de-volderfy 会丢弃,与上游一致) |
| `patcher/fixes` 硬编码 id | ✅ 24 个 intermediary 类名在 1.20.6 **全部存在**;成员签名核对 10 项,9 项吻合 |
| jar 内容与重映射注解 | ✅ 产物 jar 已打包 `mappings/mappings.tiny`(official→intermediary)、`fabric.mod.json` 占位符已展开;两个 mixin 的注解字符串已被 Loom 重映射成 intermediary(`init`→`method_25426`、`render`→`method_25394`) |

**唯一确认失效的**:`SpriteAtlasTextureFix`。它引用的 `class_1059$class_4007` 与 `method_18163` 在 1.20.6 已不存在(1.20.5+ 精灵图拼接被重构)。它找不到目标时**只会静默跳过、不会崩**,只是那项修正不再生效。

**已在真机跑完整条流水线**:第一次实测启动时,日志报错出现在 Mixin 准备 stub mixin 的阶段 —— 也就是说**它之前的全部步骤(PoptiFine 定位/版本校验 → `optifine.Patcher` 打补丁 → LambdaRebuilder → tiny-remapper 官方名→intermediary → ClassCache → OptiFine jar 加入 classpath)在真实游戏里都成功执行了**。那次崩溃的原因是旧的"运行时生成 stub mixin"方案在 Mixin 里不被接受,现已整体改为注入 Loader 的 `GameTransformer`,不再生成任何 mixin。

**第二次实测**则暴露出一个更隐蔽的问题:`VerifyError: Expecting a stackmap frame`。用真实 OptiFine jar 单独验证后定位到根因 —— 上游 de-volderfy 那一步用 `ASMUtils.readClass`(`SKIP_FRAMES`)读类、再用 `ClassWriter(0)` 写回,**把 OptiFine 自带的 StackMapTable 全丢了**:

```
notch/alf.class (Identifier):   原始 frames = 47
SKIP_FRAMES 读后写回:            frames = 0     ← 上游的写法(丢帧)
EXPAND_FRAMES 读后写回:          frames = 47    ← 已修复
tiny-remapper 重映射之后:        frames = 47    ← 重映射器会保留栈帧
```

上游没暴露这个 bug,是因为它把补丁类交给 Mixin 重写,而 Mixin 会重算栈帧;新方案里没有 mixin 指向的类不经过 Mixin,丢帧就直接是 VerifyError。现在改成读类时 `EXPAND_FRAMES`,让 OptiFine 原始栈帧全程保留;只有被 fixer 改写过描述符的那几个类才重算栈帧(见第 4 条)。缓存里若有旧流水线产物会通过 `cache-format.txt` 自动识别并重建。

**第四次实测(暂时移除 fabric-api)→ 成功进入游戏** ✅

日志确认:OptiFabric 接管 424 个补丁类、OptiFine 的 `Reflector` 正常解析、**`[Shaders]` 子系统初始化完成**(说明 `ShaderProgram` 等类必须保留 OptiFine 的补丁版本)、全程无 VerifyError、没有新的崩溃报告。

到这一步为止修掉的问题(都在本仓库内):

1. **运行时生成 stub mixin 被 Mixin 拒绝** → 改为把补丁类注入 Loader 自己的 `GameTransformer.patchedClasses`(在 Mixin 之前生效,不需要任何生成的 mixin)。
2. **de-volderfy 用 `SKIP_FRAMES` 读类,把 OptiFine 自带的 StackMapTable 全丢了** → 改为 `EXPAND_FRAMES`,栈帧全程保留(`tiny-remapper` 会保留栈帧)。上游没暴露这个问题,是因为它把补丁类交给 Mixin 重写,而 Mixin 会重算帧。
3. **OptiFine 用不同名字/描述符声明 MC 字段,导致成员重映射整条漏掉**(如 `k : java/util/Map` 对不上 `field_3835 : Int2ObjectMap`)→ 新增 `OptifineMappings`:从映射表推导出这类字段,把**名字、类型、构造器里存入的值**一起对齐(实测修好 6 个字段,其中 `class_702` 那个正是 `fabric-registry-sync` 崩溃的原因)。
4. **`KeyboardFix` 改写方法描述符(`Screen`→`Element`)后栈帧失效** → 对被 fixer 改过的类**重算栈帧**,并优先用游戏自身 classpath 解析继承关系。

配套的只读工具链(不参与模组运行):

- `test-downloads/VerifyPatched.java` —— 跑真实流水线,再用 **JVM 自带的验证器**逐个加载生成的类。当前 **423/425 通过、零 VerifyError**(剩下 2 个是工具侧类加载限制)。
- `test-downloads/PatchedConflictScan.java` —— 只读扫描 OptiFine 补丁类与原版的结构差异,并与 Fabric API 的 mixin 目标交叉匹配,输出 `test-downloads/scan/conflict-report.md`。

**Fabric API 兼容(进行中)**:Fabric API 的 `ShaderProgramMixin` 因为 OptiFine 给 `ShaderProgram` 加的委托构造器而注入失败(`@ModifyArg handler before this() invocation must be static`)。已排除两条捷径 —— 跳过该类会破坏 OptiFine 着色器(实测 `Shaders.class` 依赖 `useVanillaProgram()` 等新增成员),移除注入点会因 `defaultRequire: 1` 变成另一种崩溃。

**已实现修复**(`patcher/fixes/DelegatingConstructorFix`,注册在 `class_5944`):把 OptiFine 的委托构造器**内联**成原版形状 —— 参数类型改回 `String`、把真正的构造器体复制过来、局部变量槽位整体后移一位、在 `super()` 之后插入 `new Identifier(String)`。实测产物:

```
public class_5944(class_5912, String, class_293):
   1: invokespecial java/lang/Object.<init>()V   ← super() 在前
   4: new class_2960                              ← Identifier 在 super() 之后创建
   9: invokespecial class_2960.<init>(String)     ← 正是 Fabric 注入的目标指令
```

这样 Fabric 的实例注入处理器就落在合法位置,同时 OptiFine 自有的构造器与成员全部保留。

**带 fabric-api 的静态预检**(只读工具 `test-downloads/InjectionScan.java`,列出 Fabric API 的 mixin 目标并与补丁类比对):

- **构造器形态冲突只有一个**:全部 160 个被注入的类里,只有 `class_5944` 属于"OptiFine 加了构造器 + Fabric 注入 `<init>`"这一崩溃模式 —— 已修。
- 逐个人工核对确认无问题的两处:`WorldRenderer`(Fabric 注入的 7 个方法 `render`/`setupTerrain`/`drawBlockOutline`/`renderSky`/`renderClouds`/`renderWeather`/`reload` 在补丁版里**全部存在**);`ShaderProgram$1`(Fabric 注入的 `loadImport` 实际是父类 `GlImportProcessor.method_34233`,补丁版内部类保留了它)。
- 该工具报出的其余"缺失目标"多为**假阳性**:processedMods 里 mixin 注解用的是 Yarn 名(`render`、`setupTerrain`…),运行时靠 refmap 映射成 intermediary,直接按名字比对会误报。
- 结论:删掉 `class_5944` 那一项之外,静态层面已找不到同类冲突;但注入点级别的冲突(`@At` 目标在 OptiFine 改写后的方法体里不存在)无法静态预测,仍需实际启动确认。

**注入目标缺失(第二轮)**:把扫描结果逐条用 yarn 映射还原后(processedMods 里的注解是 Yarn 名),43 条"缺失目标"里只有两条是真的 —— OptiFine 重编译时把私有辅助方法内联掉了,而 Fabric API 仍往它们注入:

| 缺失方法 | 使用方 | 处理 |
|---|---|---|
| `class_5619.method_32174` / `method_32175`(EntityRenderers) | `fabric-rendering-v1` 的 `EntityRenderersMixin` | `RestoreVanillaMethodsFix` 把原版方法体搬回 ✓ |
| `class_3898.method_17227` / `method_18843`(ThreadedAnvilChunkStorage) | `fabric-lifecycle-events-v1` 的 `ThreadedAnvilChunkStorageMixin` | 同上 ✓ |

新增 `patcher/fixes/RestoreVanillaMethodsFix`:按方法名把原版方法**原样**复制回补丁类。选择复制方法体而不是塞空方法,是因为这样注入点真实存在;而 OptiFine 自己的代码已经不再调用这些被内联掉的方法,所以加回来不会改变行为(方法体内若引用了已不存在的成员也不影响:JVM 的成员解析是惰性的)。

**工具使用注意**:`InjectionScan` / `conflict-report.md` 分析的是 **fixer 之前**的重映射结果,而像 `KeyboardFix` 这类 fixer 本身会把原版方法搬回去(`class_309.method_1454` 就属于这种),所以扫描报出的"缺失"必须对照 fixer 之后的产物再确认一次。

**第三轮(按最终字节码复核)**:验证器现在会把 425 个**最终**类(即游戏真正会加载的字节码)导出到 `test-downloads/out/final`,`ResolveMissing.ps1` 用 yarn 映射逐条复核 Fabric API 的全部注入目标 —— 除"被跳过的 `class_2586`(BlockEntity,上游本就跳过、用原版)"和两处 javap 排版造成的误判(`<clinit>` 打印为 `static {}`、构造器打印为类名)之外,**全部存在**。至此 fixer 之后的字节码里已无缺失的注入目标;静态层面仅剩 `@At` 指令级注入点这一盲区(需要模拟 Mixin 的注入点解析,无法离线预测)。

验证器同时改用 **JDK 25**(与游戏运行时一致):新版校验更严,而之前用 JDK 21 跑漏掉了一个真实的 `Bad type on operand stack` 错误。

**第四轮与第五轮的修复(构造器重写的两个坑)**:

1. 移动局部变量槽位时条件写成 `var > slot`,导致 `Identifier` 参数自己没被移动 —— 方法体仍从旧槽位读它(那里已是 `String`)→ `VerifyError: Bad type on operand stack`。改为 `var >= slot`。
2. 但"把参数之上整体后移一位"本身也不对:**描述符里只有 3 个参数(槽 1/2/3),方法体却把 `type` 放到了槽 4** → 验证器眼里槽 4 是未初始化的 `top` → `VerifyError: Bad local variable type`。

最终做法:**参数布局完全不动**(只把该参数的类型换成 `String`),把创建出来的 `Identifier` 放进**方法末尾的空闲槽位**,只把方法体里对该参数的**读取**(`ALOAD`)改到新槽位 —— 并且如果发现该槽位被写入过(`ASTORE` 等,说明它被复用为临时变量)就放弃内联并记录日志。实测产物:

```
public class_5944(class_5912, String, class_293):
   0: aload_0
   1: invokespecial Object.<init>()V            ← super() 在前
   4: new class_2960                             ← Identifier 在 super() 之后创建
   8: aload_2                                    ← String 参数(布局未变)
   9: invokespecial class_2960.<init>(String)    ← Fabric 注入目标,位置合法
  12: astore 16                                   ← 存进末尾空闲槽位
  ...
  57: aload 16 → invokevirtual class_2960.method_12836   ← 方法体读取已全部指向新槽位
```

验证方面除了 JVM 验证器(JDK 25,与游戏一致)之外,又加了 **ASM 自己的数据流验证器**(`asm-util`)作为第二意见 —— 因为前两次真实缺陷都是在 Mixin 变换后才暴露的,JVM 验证器没能提前抓到。

**第六轮(查 `@Shadow` 成员)**:新增只读工具 `test-downloads/ShadowScan.java` —— 把每个 mod mixin 声明的 `@Shadow`/`@Accessor`/`@Invoker` 成员拿去和**最终**补丁类比对。结果查出三个真实缺失,而且正是上游用硬编码 contextual mapping 修过的那三条:

```
class_638$class_5612.field_27735   (ClientWorld$ClientEntityHandler.this$0)   ← fabric-lifecycle-events-v1
class_1088$class_7778.field_40571  (ModelLoader$BakerImpl.this$0)            ← fabric-model-loading-api-v1
class_846$class_851$class_4578.field_20839 (ChunkBuilder$BuiltChunk$RebuildTask.this$1) ← fabric-renderer-indigo
```

这些字段在 OptiFine 重编译后的类里被 javac 命名为 `this$0`/`this$1`,**映射表里没有这种名字的条目**(映射表只有混淆名 → `field_27735`),所以上一轮按"混淆形状名字"的规则找不到它们。新增 `patcher/fixes/SyntheticFieldFix`:**按描述符**匹配原版同类字段(唯一候选才动手),然后重命名声明并改写类内全部引用 —— 实测三处分别改写 9/4/27 个引用 ✓。这样比上游硬编码三条更通用。

`ShadowScan` 剩下的 8 条是 `@Accessor`/`@Invoker` 生成的访问器方法(该方法本身不存在于目标类,是 mixin 自己生成的),属工具假阳性。

**第七轮(注入目标的解析方式 + `@At` 调用点)**:这一轮先纠正了前两轮扫描器的**方法论错误**,再用它抓到并修掉两个真问题。

第一个错误是**跳过 refmap**。像 `fabric-rendering-v1` 里的 `@Inject(method = "render")`,注解里写的是**具名(Yarn)**名字,运行时由 jar 里的 refmap 翻译成 intermediary —— 例如 `client-fabric-rendering-v1-refmap.json` 里就明摆着:

```json
"WorldRendererMixin": { "render": "Lnet/minecraft/class_761;method_22710(...)V" }
```

旧扫描器直接拿 `render` 去补丁类里找,自然找不到,于是报出 60 多条"缺失"——绝大多数是假阳性(真问题只有 `method_32174/32175`、`method_17227/18843` 那几条已经是 intermediary 形式的)。新工具 `test-downloads/RefmapScan.java` **先解析 refmap 再校验**,并且把"最终补丁类"与"原版 intermediary jar"拼成完整类层次(沿父类/接口查找,未打补丁的类一律视为形状完好),把假阳性压到 0:

```
mixin classes scanned: 257
references resolved through a refmap: 480  (跳过未打补丁的类: 405)
constructor injections: 7
MISSING members: 0
```

其中 7 个构造器注入包含本目标的交付物本身 —— `class_5944.<init>(Lnet/minecraft/class_5912;Ljava/lang/String;Lnet/minecraft/class_293;)V` 在最终字节码里**存在**,即 Fabric API 的 `ShaderProgramMixin` 有落点。

第二个错误是**只看成员是否存在**。方法在,不代表它体内还有那条被注入的指令 —— 而 OptiFine 干的正是重写方法体。`test-downloads/AtTargetScan.java` 解析每个 `@At(target = ...)`(同样走 refmap),在最终方法体里**数匹配指令**并和 `ordinal` 比较:

```
@At points with an explicit target: 67
call sites counted: 30
PROBLEMS: 1
  [NO INSTRUCTION] class_846$class_851$class_4578.method_22785 里没有
                   class_2338.method_10097(BlockPos,BlockPos)Iterable 的调用
```

这就是真问题:`fabric-renderer-indigo` 的 `ChunkBuilderBuiltChunkRebuildTaskMixin` 要注入 `ChunkBuilder$BuiltChunk$RebuildTask.render` 里的 `BlockPos.iterate` 调用点(它还带 `LocalCapture.CAPTURE_FAILHARD`,依赖该处的局部变量表),而 OptiFine 把那段循环换成了自己的实现,调用点消失。indigo 的配置是 `"injectors": {"defaultRequire": 1}`,缺注入点是**致命**的。原版那个方法里确实有这条调用(实测 1 处),补丁类里 0 处 —— 双方都核对过了。

处理办法是让 indigo 按 Fabric 自己的机制让位。`IndigoMixinConfigPlugin` 的字节码写得很清楚:

```java
if (meta.containsCustomValue("fabric-renderer-api-v1:contains_renderer")) indigoApplicable = false;
else if (meta.containsCustomValue("fabric-renderer-indigo:force_compatibility")) forceCompatibility = true;
public boolean shouldApplyMixin(...) { return indigoApplicable; }        // 整套 mixin 都不应用
```

`Indigo.onInitializeClient` 同样受它保护:不成立就打印 `[Indigo] Different rendering plugin detected; not applying Indigo.` 并且**不注册**渲染器。所以移植版在自己的 `fabric.mod.json` 里声明:

```json
"custom": { "fabric-renderer-api-v1:contains_renderer": true }
```

关键点是**这个键必须由"另一个渲染器"声明**(Sodium 用的就是它),而 OptiFine 本身就是地形渲染器,所以语义上是诚实的,不是绕过检查。注意上一轮设的 `fabric-renderer-indigo:force_compatibility` **达不到这个效果** —— 它只切换 indigo 的兼容渲染路径,照样会应用在那条会崩的 mixin 上。代价是:依赖 FRAPI/indigo 的模组不再拿到 indigo 的自定义渲染,改由 OptiFine 渲染地形;需要换回去就删掉这个键,但那样 `class_846$class_851$class_4578` 一加载就会崩。

最后,用 `-ea` 打开断言跑了一遍管线(生产环境断言默认关闭),又发现一个隐患:`ChunkRendererFix` 是上游针对 **Forge** 的修复,它断言那段调用带的是 1.16–1.18 的 `IModelData`,而 1.20.6 的 OptiFine 用的是 1.19+ 改名后的 `ModelData`,断言直接不成立:

```
invokevirtual class_776.renderBatched:(...Z, Random,
    net/minecraftforge/client/model/data/ModelData, RenderLayer)V
```

断言被 JVM 关掉时它**照样改写**,只是恰好改写对了 —— 把 Forge 的 `renderBatched(..., ModelData, RenderLayer)` 换成 Fabric 上存在的原版 `renderBlock`,并按"后进先出"顺序删掉两个多余参数(这个顺序是对的:多余参数最后入栈,必须最后删)。所以线上行为没出错,但"前提不成立也照改"是地雷,已换成真实校验:接受 `IModelData`/`ModelData` 两种,形状不符就跳过并打日志。改完后 `-ea` 下:

```
[OptiFabric] Prepared 425 patched classes (1 skipped, 0 failed)     ← 之前是 424 + 1 failed
java.lang.AssertionError 出现次数: 0
verified OK: 423 / FAILED: 2(第八轮查明:这两个"失败"是验证器自己的 bug,不是字节码问题)
ASM verifier problems: 1(已知的 class_156 假阳性,第八轮一并消失)
```

```
good class          -> OK
corrupted (old bug) -> OK                                    ← JVM resolveClass 漏检 ✗
ASM check, good     -> OK
ASM check, corrupt  -> AnalyzerException: expected class_2960, but found String   ← ASM 正确报错 ✓
```

也就是说 `resolveClass`(靠 JVM 链接触发校验)在测试环境里**并不可靠**,它放过了两版真正有问题的构建;而 **ASM 的 `CheckClassAdapter.verify` 能准确抓到**。现在验证以 ASM 为准(425 个最终类里只有 1 条 `class_156`/`Util` 的误报 —— 工具类加载器看不到被替换的内部类所致),JVM 那一路只作补充。

**第三次实测**则暴露出上游另一处手工修补的缺口:

```
InvalidMixinException: @Shadow field field_3835 was not located in the target class net.minecraft.class_702
```

OptiFine 的补丁里,这个粒子工厂表被声明成了 `k : java/util/Map`(构造器里赋值的是 `new HashMap()`),而游戏里它是 `field_3835 : Int2ObjectMap`。成员映射按 **owner+name+描述符**精确匹配,描述符对不上就整条漏掉,字段名留在了混淆名 `k`,于是别的模组 `@Shadow field_3835` 直接失败 —— 这正是上游用 contextual mapping 硬编码修掉的 4 个已知 case 之一。

移植版现在用一条**从映射表推导**的通用规则替代那些硬编码(`OptifineMappings`):对每个补丁类,凡是"字段名还是混淆名、而映射表里该名字对应另一个真正的字段"的字段,就把**名字、类型和构造器里存入的值**一起对齐(类型对齐是必须的:移植的 `ParticleManagerFix` 会把 4 个方法换成原版实现,而原版实现按 `Int2ObjectMap` 访问它)。用真实 OptiFine jar 验证:

```
renames found : class_702.k -> field_3835  (目标类型 Lit/.../Int2ObjectMap;)
stored value  : java.util.HashMap -> it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
栈帧数量      : 76 -> 76(不变)
4 处引用(<init> / method_3043 / method_18834 / method_3055)名字与类型全部一致
```

**第八轮(局部变量捕获,以及第一次真机跑到渲染阶段)**:这一轮既有新发现的冲突,也纠正了前几轮一直挂在报告里的"已知失败"。

先纠正验证器自身:那两个从第五轮起就存在的 `FAILED: 2` **不是字节码问题**,是验证器的两个 bug:

1. 它把原版类放在父加载器、补丁类放在子加载器,于是 `class_2841`(子)实现 `class_2835`(父)时,同名包在不同加载器里算两个"运行时包",包私有父接口就抛 `IllegalAccessError` —— 真实游戏里 Knot 把两者放在**同一个**加载器,不会有这个问题。改成把整个游戏 classpath 放进同一个加载器后,假阳性消失。
2. `defineNow` 先 `pending.remove(name)` 再 `defineClass`,于是重试阶段一旦抛异常,那个类就被永久丢弃 —— 既不被重试也不被报告,只以"not defined"出现,连原因都看不到。

两处修好之后,验证结果从 `423 / 2 / ASM 1` 变成 **`425 / 0 / ASM 0`** —— 425 个补丁类在"与游戏一致的单一加载器"下全部定义并链接通过,ASM 的数据流验证器也再无一条报告。

然后是新的冲突面:**局部变量捕获**。fabric-api 里有 28 个处理器用 `LocalCapture.CAPTURE_FAILHARD`(其中 5 个的目标类被 OptiFine 打过补丁),而 Mixin 是按**槽位**把目标方法的局部变量交给处理器的 —— OptiFine 重新编译过这些类,javac 自己分配槽位,布局可能和 Fabric 编译时依据的原版不一样。新工具 `test-downloads/LocalsScan.java` 做差分:同一个方法在原版与补丁类里的局部变量类型序列是否一致,并把每个处理器声明的捕获参数一并列出。查出两处:

```
class_846$class_851$class_4578.method_22785   ← indigo(已被 contains_renderer 中和)
class_6850.method_39969                       ← 真问题
```

`class_6850`(ChunkRendererRegionBuilder)的情况:OptiFine 把原版 `build` 变成了瘦包装

```java
public ChunkRendererRegion method_39969(World w, BlockPos from, BlockPos to, int padding) {
    return createRegion(w, from, to, padding, true);   // OptiFine 自己的方法
}
```

循环体、4 个循环计数器以及 `Chunk[][]` 数组全部搬进了 `createRegion`,于是 `method_39969` 的局部变量表只剩 `this` 和 4 个参数。而 fabric-block-view-api-v2 的 `createDataMap` 正是以 `CAPTURE_FAILHARD` 捕获那 5 个局部变量来注册数据表 —— 一旦进世界渲染区块就会硬失败。

修法是把原版方法**整体换回**:`RestoreVanillaMethodsFix` 增加"替换"语义(`RestoreVanillaMethodsFix(true, "method_39969")`),原版布局恰好就是 Fabric 编译时对应的 `(int,int,int,int,Chunk[][])`,而 OptiFine 自己的 `createRegion` 仍然留给它自己的调用者。代价是这条路不再有 OptiFine"空区块区域直接返回 null"的提前退出(纯优化)。改完后 `LocalsScan` 的差异从 2 条降到 1 条(只剩被中和的 indigo 那条):

```
LocalsScan: methods whose local layout differs from vanilla: 1
VerifyPatched: Prepared 425 (0 failed) / verified OK 425 / FAILED 0 / ASM 0
```

**第一次真机跑到渲染阶段**:带 fabric-api 启动后,之前所有的崩溃点都过去了 —— OptiFine 的光影子系统正常工作(`[Shaders] Allocate texture map normal/specular`),方块图集逐个创建。随后画面停在 Mojang 图标+进度条不动,但这次**不是崩溃也不是 Java 死锁**:两次线程转储相隔 27 秒,`Render thread` 的栈完全相同(`glfwSwapBuffers` 原生调用),CPU 27 秒只涨 47ms,GPU 占用 1.4%,而且转储里**没有任何资源重载工作线程**(说明加载其实已经完成,只是"移除加载界面"这个任务要由卡住的 Render 线程执行)。这是呈现层(vsync/驱动)的空转,不是我们改的字节码造成的。**真实原因在第九轮查明:游戏窗口当时处于最小化状态。**开着垂直同步时,最小化窗口的 `SwapBuffers` 会一直阻塞,于是 Render 线程出不来、"移除加载界面"的排队任务永远不执行,画面就定格在最后画出的那一帧。九轮的定位与验证过程见下面的"排查手段"。

**第九轮(扫描器的两个盲区,以及"游戏起来了但所有模型都没加载")**:这一轮先补上了扫描器**静默跳过**的两类目标,再用补好的扫描器抓到当前真正的故障。

盲区一:**`@Mixin(targets = "...")` 里的类是具名的**。`@Mixin(value = SomeClass.class)` 在编译时就被重映射成了 intermediary 的 `Type`,但 `targets = "net.minecraft.client.render.model.ModelLoader$BakerImpl"` 是**字符串**,始终留在具名空间:

```
org.spongepowered.asm.mixin.Mixin( targets=["net/minecraft/client/render/model/ModelLoader$BakerImpl"] )
```

我的扫描器拿它去补丁类集合里查,查不到就 `removeIf` 丢掉 —— 于是**所有用字符串声明目标的 mixin 从来没有被检查过**。现在通过项目里自带的 yarn 映射(`test-downloads/yarn-mappings.tiny`)把具名类名、描述符和成员名都翻成 intermediary(新增 `test-downloads/NamedResolver.java`)。

盲区二:**`@At(target = "...")` 里的引用也可能是具名的**,而且**不在 refmap 里**。同一个 mixin 就有:

```
at=@org.spongepowered.asm.mixin.injection.At(
  value="INVOKE_ASSIGN"
  target="Lnet/minecraft/client/render/model/ModelLoader$BakerImpl;getOrLoadModel(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/model/UnbakedModel;")
```

refmap 里没有这个键,旧代码解析出的是具名 owner、在补丁类集合里查不到,于是**整条注入点被悄悄跳过**。另外 `INVOKE_ASSIGN` 这种注入点当时根本没建模,连"未支持"都没计数。两处都补上之后,覆盖率与结果立刻变化:

```
@At points with an explicit target: 67 -> 77
call sites counted: 30 -> 35
PROBLEMS: 1 -> 4
```

新查出来的三个全在**同一个方法**上:

```
net/minecraft/class_1088$class_7778.method_45873(...) 里没有
  INVOKE        class_1100.method_4753(...)        (@Redirect 的目标)
  INVOKE        class_793.method_3446(...)         (@Redirect 的目标)
  INVOKE_ASSIGN class_1088$class_7778.method_45872 (@ModifyVariable 的目标)
```

原因又是"OptiFine 把原版方法改成瘦包装":它的 `bake(id, settings)` 只负责转发给自己新增的三参数 `bake(id, settings, textureGetter)`,而 Fabric 要注入的那三条调用全在后者里。注入点缺失 → **整个类的 Mixin 变换失败** → 真机上一次运行里 **56,042 次模型烘焙失败**,并连带出现 `getModel(state)` 返回 null 的 NPE;表现就是"游戏起来了但模型全都不见了"。

修法沿用第八轮的"替换"模式恢复原版方法体。恢复是**行为等价**的:瘦包装转发时传的就是 `this.field_40572`,而原版方法体内部用的正是同一个字段(逐条指令核对过,只差一个槽位偏移)。

```
[OptiFabric] Restored vanilla class_1088$class_7778.method_45873(...) over OptiFine's version
AtTargetScan: PROBLEMS 4 -> 1(只剩被 contains_renderer 中和的 indigo 那条)
VerifyPatched: 425 prepared / 0 failed, verified OK 425 / FAILED 0 / ASM 0
```

两次"瘦包装"故障(`class_6850` 与 `class_1088$class_7778`)说明这是 OptiFine 打补丁的**常见手法**:把原版方法换成对自己重载的转发。判断标准很简单 —— 只要 Fabric 的注入目标方法是那种"取参数 → 调用另一个方法 → 返回"的转发体,就必须把原版方法体换回去。

**第十轮(把剩下的静态面收干净)**:这一轮没有新的补丁改动,做的是"确认没有下一个坑"。

- `RefmapScan` 也接上了 `NamedResolver`(它同样会静默跳过具名引用),结果仍是 `MISSING members: 0`。
- 把 fabric-api 里所有用 `@ModifyVariable(ordinal/index)` 定位局部变量的 mixin 都过了一遍(9 个),其中只有 3 个的目标类被 OptiFine 打过补丁:`class_1088`(ModelLoader 外层)、`class_775`(FluidRenderer)、`class_5944`(ShaderProgram)。逐个核对:
  - `class_5944` 的 `@ModifyVariable(method="loadShader", at=@At("STORE"), ordinal=1)` 已被真机反证 —— 那次运行光影正常加载,说明这个 mixin 应用成功了。
  - `class_1088` 的是 `at=@At("HEAD"), argsOnly=true`(按类型匹配,不用序号)。
  - `class_775.method_3347` 是把补丁类与原版逐条对比:方法体结构一致,`bipush 16`(CONSTANT 注入点)、`method_3348`(isSameFluid)、`method_26204`(getBlock,`shift=BY 2` 的目标)三个注入点在两边**都在**,数量也一致。
- 结论:静态能查的面(成员存在性、`@At` 调用点与序号、局部变量捕获、`@Shadow` 成员、构造器注入)目前全绿,只剩被 `contains_renderer` 有意中和的 indigo 那一条。

**第十一轮(进世界报"网络协议错误")**:第四轮那次真机运行里,游戏能进主界面、模型也正常了(第九轮的修复生效:模型烘焙失败从 **56,042 次降到 0**),但**打开单人存档立刻报"网络协议错误"**并退回主菜单。日志里终于有了完整堆栈:

```
java.lang.RuntimeException: Mixin transformation of net.minecraft.class_631 failed
Caused by: MixinTransformerError: An unexpected critical error was encountered
Caused by: InjectionError: Critical injection failure: Callback method onChunkUnload(...)V
   in fabric-lifecycle-events-v1.client.mixins.json:ClientChunkManagerMixin
   failed injection check, (0/1) succeeded. Scanned 0 target(s).
```

`class_631`(ClientChunkManager)整个类变换失败 → 客户端区块管理器加载不了 → 进世界即协议错误。要定位它得先读懂两句 Mixin 的内部信息:

- `Scanned 0 target(s)` 来自字段 `targetCount`,而它在 `InjectionInfo` 的构造器里被置 0 之后**再没有自增过**(整个类里只有一处 `putfield targetCount`)—— 这句是 Mixin 自己的计数 bug,没有信息量。
- 真正有用的是 `(0/1)`:`requiredCallbackCount=1` 而 `injectedCallbackCount=0`。对着 `CallbackInjector` 源码看,如果注入点找到了但描述符不匹配,它会打印 LVT 信息;日志里**没有**这类信息,所以是另一种情形:**一个注入节点都没匹配上**。

顺着 `ClientChunkManagerMixin` 里那个 9 参数处理器(捕获 3 个局部变量)读注解,拿到注入点:

```java
@Inject(method = "loadChunkFromPacket",
        at = @At(value = "NEW", target = "net/minecraft/world/chunk/WorldChunk", shift = At.Shift.BEFORE),
        locals = LocalCapture.CAPTURE_FAILHARD)
```

即"创建 `WorldChunk` 对象之前"。而 OptiFine 把这次对象创建换成了自己的子类:

```
原版:  81: new #120  // class net/minecraft/class_2818     (WorldChunk)
补丁:  92: new #234  // class net/optifine/ChunkOF          ← 注入点消失
```

Mixin 的 NEW 注入点按**创建的类型精确匹配**,`ChunkOF` 不是 `WorldChunk`,所以一个节点都匹配不到。

**为什么扫描器又漏了**:NEW 注入点的 `target` 是一个**裸类名**(既不是 `L…;` 形式,也没有成员部分),`RefmapScan.parseRef` 和 `parseBareRef` 都拒收它 —— 于是整条引用被静默跳过。这是继"具名字符串目标"之后的同类盲区,已在 `AtTargetScan` 里补上(裸类名按类引用处理,并允许 NEW 点只给类型不给成员)。补完后立刻现形,而且**全项目只有这一处**:

```
@At points: 77    call sites counted: 36    PROBLEMS: 2 → 1
```

**修法**不是恢复原版方法(那会让客户端创建普通 `WorldChunk` 而不是 `ChunkOF`,OptiFine 的区块渲染会跟着坏),而是在 OptiFine 创建自己子类的位置**前面插一条惰性标记**:

```
92: new  #122  // class net/minecraft/class_2818   ← 新增的标记(注入点回来了)
95: pop
96: new  #234  // class net/optifine/ChunkOF       ← OptiFine 的对象创建原样保留
```

这条标记**不构造任何对象**(否则会向世界注册一个重复区块),只是让 Mixin 找得到落点;注入的回调因此落在与原先相同的位置。`NEW` 后面跟 `POP` 是否合法我单独做了实验验证(新增 `test-downloads/NewPopTest.java`):ASM 验证器和真实 JVM 的定义/链接/执行**都通过**。该修复由新的 `ObjectCreationPointFix` 完成,并对 `class_631` 注册。

```
[OptiFabric] Marked a NEW net/minecraft/class_2818 in class_631.method_16020(...)
             so the injection point before it exists again (OptiFine instantiates net/optifine/ChunkOF)
VerifyPatched: 425 prepared / 0 failed, verified OK 425 / FAILED 0 / ASM 0
AtTargetScan: PROBLEMS 1(只剩被 contains_renderer 中和的 indigo)
```

`class_631` 的 `method_16020` 里局部变量表在标记处覆盖槽位 6/7/8(`i`/`levelchunk`/`chunkpos`),所以 `CAPTURE_FAILHARD` 也能正常捕获(逐条核对过)。

**第十二轮(最终验证:带着 Fabric API 进世界并正常运行)**:修完 `class_631` 之后重启真机验证,结果是:

```
窗口标题           : Minecraft* 1.20.6 - 单人游戏      ← 已在单人存档里
CPU                : 254.9s -> 264.9s(10 秒增 10 秒 = 满核渲染)
Starting integrated: 1        Client disconnected : 0(没有协议错误)
Unable to bake model: 0       Mixin transformation: 0      InjectionError: 0
[Shaders]          : 814 行,Loaded shaderpack: photon_v1.2a.zip,自定义 uniform/variable 已处理
```

也就是说:**同时加载 Fabric API + OptiFine,能进主界面、能开单人存档、模型与区块正常、光影生效** —— 本项目的目标达成。

## 一共处理掉的冲突(全部来自真机崩溃,逐个定位到字节码层面)

| 真机症状 | 根因 | 修复 |
|---|---|---|
| 启动崩:`@ModifyArg handler before this() invocation must be static` | OptiFine 的 `class_5944` 委托构造器在 `this()` 之前创建 `Identifier`,Fabric 的注入点落在 `super()` 前 | `DelegatingConstructorFix`:内联委托构造器,把标识符挪到 `super()` 之后(保留原参数布局) |
| `InvalidMixinException: @Shadow field field_3835 was not located` | OptiFine 把字段声明成混淆名 + 描述符不符 | `OptifineMappings`:按映射表对齐名字/类型/存入值(6 个字段) |
| 校验错 `Bad type on operand stack` / `Bad local variable type` | 构造器重写的槽位搬移破坏了描述符与调用点一致性 | 改为"末尾空闲槽位 + 保留参数布局",并用反汇编逐条核对 |
| 缺注入目标 `class_5619.method_32174/32175`、`class_3898.method_17227/18843` | OptiFine 重编译时把私有助手内联掉了 | `RestoreVanillaMethodsFix`(补回缺失方法) |
| 缺 `@Shadow` 字段 `field_27735`/`field_40571`/`field_20839` | 合成外部实例引用被 javac 命名为 `this$0`/`this$1`,映射表里没有这种名字 | `SyntheticFieldFix`:按描述符匹配并重命名(9/4/27 处引用) |
| 进世界崩(区块) | OptiFine 把 `class_6850.build` 改成转发给自己重载的瘦包装,局部变量搬走 | `RestoreVanillaMethodsFix(replace=true)` |
| **56,042 次模型烘焙失败**(方块全没了) | 同上手法:`ModelLoader$BakerImpl.bake` 变成转发,三条注入点都搬进了重载 | 同上,恢复原版方法体(已核对转发传参与原版一致) |
| **开存档报"网络协议错误"** | OptiFine 用 `net.optifine.ChunkOF` 取代 `WorldChunk`,Fabric 的 `@At(value="NEW", target="WorldChunk")` 精确匹配不到 → `class_631` 整个类变换失败 | `ObjectCreationPointFix`:在 OptiFine 创建子类处**前面**插入惰性 `NEW class_2818; POP` 标记(`NEW;POP` 的合法性由 `NewPopTest` 用 ASM 与真实 JVM 双向验证) |
| 区块构建时崩(隐患,提前拦住) | indigo 注入 `BlockPos.iterate`,而 OptiFine 重写了那段循环 | 声明 `fabric-renderer-api-v1:contains_renderer`,让 indigo 按 Fabric 的机制让位(OptiFine 本身就是渲染器) |

## 离线校验工具链(全部只读,可重复运行)

| 工具 | 查什么 | 当前结果 |
|---|---|---|
| `VerifyPatched` | 跑真实补丁管线,用**单一加载器**(与游戏一致)做 JVM 校验 + ASM 数据流校验 | 425/425 通过,0 失败,ASM 0 |
| `RefmapScan` | 每条 mixin 注解引用经 refmap / yarn 解析后,成员是否还存在(含继承) | MISSING 0 |
| `AtTargetScan` | `@At` 注入点(INVOKE/INVOKE_ASSIGN/FIELD/NEW/序号)在最终字节码里是否还在 | 只剩被有意中和的 indigo 那条 |
| `LocalsScan` | `LocalCapture` 需要的局部变量布局是否与原版一致 | 只剩 indigo 那条 |
| `ShadowScan` | `@Shadow`/`@Accessor`/`@Invoker` 成员 | 真缺失 0 |
| `NamedResolver` | 具名→intermediary 解析(供上面几个工具用) | yarn 映射驱动 |

扫描器自身踩过的坑也记录在案:`@Mixin(targets = "...")` 与 `@At(target = "...")` 里的目标是**具名字符串**、NEW 点的目标是**裸类名**、以及 `INVOKE_ASSIGN`/`CONSTANT` 等注入类型不建模 —— 这些都会导致**静默跳过**,让真实冲突藏起来。

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

**这段只适用于 1.21.x 线。** 26.1.2 上的 indigo 不再是地形渲染器(Fabric API 把地形与提交节点的整合搬进了 `fabric-renderer-api-v1` 自己,indigo 只剩 1 条物品 mixin + 2 个 accessor),那个键在那条线上只会关掉 Fabric API 唯一能问到的渲染器,所以 26.x 的 jar **不声明它** —— 见 `docs/PORT_26.x.md` 第 4 节。

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
| `RendererStubTest` | 用一个假接口离线跑"运行时生成的占位渲染器"的字节码(抽象/默认/静态方法、各种返回类型) | 全部通过 |
| `FapiRendererFallbackTest` | 拿**真实** `fabric-renderer-api-v1` + intermediary 游戏 jar + **构建产物 jar** 跑一遍:F3 那行 `Renderer.get().getClass().getSimpleName()` 读到什么、被真调用时抛什么 | 全部通过 |
| `LambdaScan` | 补丁类里每个 **invokedynamic** 的 bootstrap 方法/字段句柄,在"游戏真正会加载的那份类"(补丁类或原版)里是否还存在 —— `LambdaRebuilder` 要修的正是这个,而 JVM 与 ASM 验证器都**不解析** invokedynamic 目标(它们只在首次执行时才链接,也就是在游戏里) | 各版本 **0 dangling** |

扫描器自身踩过的坑也记录在案:`@Mixin(targets = "...")` 与 `@At(target = "...")` 里的目标是**具名字符串**、NEW 点的目标是**裸类名**、以及 `INVOKE_ASSIGN`/`CONSTANT` 等注入类型不建模 —— 这些都会导致**静默跳过**,让真实冲突藏起来。

## 1.21.11 移植(Minecraft 1.21.11 + Fabric Loader 0.19.5 + OptiFine HD_U J9)

1.21.11 的 OptiFine 换成 **xdelta 差分包**(`patch/**/*.class.xdelta` + `.md5`,2748 对),但 `optifine.Patcher.process(File, File, File)` 的签名和用法与 1.20.6 完全一样,所以补丁管线不用改。真正需要改的是**重映射**,而且问题一开始被"类名都映射对了"这个假象掩盖了。

### 关键发现:重映射必须能看见游戏类层次

OptiFine 的补丁类是**用它自己的源码重编译**出来的,再经过它自己的混淆器。混淆器对有映射表的成员会改回官方名,没有映射表的成员就保留 OptiFine 源码里的名字(`codec`、`val$prepBlocksIn`)。TinyRemapper 的成员映射是**按声明类记录**的:一个成员只在声明它的那个类下面有词条,子类里覆写它的方法要靠**类的继承关系**去继承这条映射。

原管线只把"启动类路径"喂给 remapper。真机上启动类路径**包含游戏本体**,所以看不出来;但一旦类路径里没有游戏(TinyRemapper 只看到被补丁的那几个类),它就看不见 `chn extends chl`,于是把子类里覆写的方法留成了 OptiFine 的名字。实测代价:

| 指标 | 修复前 | 修复后 |
|---|---|---|
| `class_1308` 里没映射上的方法 | 35 | 0 |
| 破坏的接口/抽象契约 | 281 | 0 |
| 丢失的虚方法覆写 | 254 | 0 |
| 无法解析的成员引用 | 0 | 0 |

修复:`remapOptifine` 在喂完启动类路径后,**显式把游戏本体也加进 classpath**(重复也无害),并把 `CACHE_FORMAT` 提到 3,让旧缓存自动失效重生成。

### 关键发现:同名同类型的合成字段只能按位置配对

`SyntheticFieldFix` 原来要求"同类型字段唯一"才能重命名。`ModelManager$1` 有两个同类型(`SpriteLoader$Preparations`)的捕获字段,于是两个都被判为"歧义"而跳过 —— 而 Fabric API 的 `ModelManager1Mixin` 正是 shadow `field_61871`/`field_64469` 这两个字段,shadow 找不到就会让整个 mixin 失败。OptiFine 重编译时**保留字段声明顺序**,所以改为优先按**同位置**配对,唯一性匹配作为兜底。

### 新增:`RuntimeContractScan`(唯一能抓到上面这类问题的手段)

`@Shadow` 扫描只看 mod 侧,refmap 扫描只看注入点,而"类不再实现它声明的接口"是**类型层面的契约破坏**:类加载不会报错,第一次走接口调用才抛 `AbstractMethodError`。这个扫描器把三件事一次算清:

1. **抽象契约**:补丁类是具体类时,它实现的所有接口/抽象父类的抽象方法是否都还在(找不到实现才报,抽象类/接口本身跳过 —— 否则全是假阳性);
2. **虚方法覆写**:原版声明过、补丁类丢掉的方法(调用会静默走父类实现);
3. **成员引用**:全游戏扫描(含未打补丁的类)对被补丁类的引用能否解析,并区分"调用方也被补丁"和"调用方是原版类"。

结果:`broken abstract contracts: 0`,`lost virtual overrides: 0`,`unresolvable member references: 0`。

### 新增:`MissingOverrideFix`(按规则补桥接方法)

对"原版有、补丁类没有、且被补丁类里有唯一同描述符实现"的可见方法,补一个**转发方法**(用游戏的名字调用 OptiFine 自己那个名字的实现)。它是**纯增量**的:OptiFine 自己的调用点用旧名字,照旧工作。1.21.11 上补了 10 个(全是 `SimpleOption` 系列 record 的 `comp_675()`/`comp_674()` —— OptiFine 把它叫 `codec()`/`valueSetter`)。歧义(同描述符多个候选)时**不猜**,只报告。

### `KeyboardFix` 在 1.21.11 停用

上游这个修复要把 OptiFine 改坏的键盘分发方法换回原版。1.21.11 的原版把分发重写了:`method_1454`/`method_1458`/`method_1473` 已不存在,`method_1466` 变成 `(JIclass_11908)V` —— 没有可以换回去的东西了。停用后 OptiFine 的 Keyboard 直接应用,`RefmapScan`/`AtTargetScan` 确认 Fabric API 的键盘 mixin 目标仍然存在(无缺失)。

### 1.21.11 扫描结果

| 工具 | 结果 |
|---|---|
| `VerifyPatched` | 568/568 通过(0 跳过、0 失败),ASM 0;缓存复用第二次启动 1.8 秒 |
| `RuntimeContractScan` | 契约 0 / 覆写 0 / 引用 0(见下"扫描器自身的三处修正") |
| `RefmapScan` | 430 个 mixin 类,147 条引用,MISSING 0 |
| `AtTargetScan` | 显式 `@At` 目标 81 条,PROBLEMS 0 |
| `LocalsScan` | 需要局部捕获的处理 0 条 |
| `UnsetFieldScan` | 568 个类,3608 个字段,读而未初始化 0 |
| `ShadowScan` | 检查 64 条,缺失 0,描述符不符 0 |

`ShadowScan` 一开始还在打印 16 条"缺失",逐条核对后**全是假阳性**,根因是扫描器自己的建模缺口:`@Accessor`/`@Invoker` 的目标写在**注解值**里(`@Accessor("field_18242")`、`@Invoker("method_71138")`、记录组件的 `comp_4049`),而且该值在发布 jar 里**已经是 intermediary 名**(构建时被 remap 过);扫描器却只看 Java 方法名(`getEntityTrackers`、`fabric$pipeline`),去原版类里找一个从来不存在的成员。

已修:① 读注解值,没有值时按 Mixin 的推导规则(`getX`/`setX`/`isX`/`callX` → `x`);② Accessor 查**字段**(描述符取返回类型,setter 取参数类型),Invoker 查**方法**;③ 名字在但描述符不同时单独记成"描述符不符",不再算缺失。修完:**检查 64 条,缺失 0,描述符不符 0**。

### 扫描器自身的三处修正(改完才敢信它的结论)

`RuntimeContractScan` 最初报"引用 0",其实是被自己的逻辑**压掉了所有发现**,三处都改过:

1. **"层次里有未知类就当不可判定"太粗**:每个类层次最后都到 `java/lang/Object`,于是*任何*缺失都被吞掉。改为只把 `java/lang/Object`/`Enum`/`Record` 视为"成员集合已知"(它们的成员在 `platformMember` 里枚举),其它游戏之外的类(DataFixerUpper、joml、`java/util/*`、`Throwable`)只让结论变成"不可判定",不再误报 —— 这一条把 1269 条误报降到 0。
2. **接口的抽象声明**:对"调用点能否解析"它**算数**(`invokeinterface class_7833.rotation` 就靠接口自己的声明解析),对"这个类有没有实现契约"它**不算数**。之前两者混用,前者误报 13,300 条,后者漏报。
3. **必须把 OptiFine 自己的类也纳入扫描**(`Optifine-mapped.jar` 里 874 个类,包括 `net/optifine/**`):它们同样在跑、同样在调游戏。只扫被补丁的 567 个类时,下面两个真实缺陷都看不见。

### 修正后扫出的两个真实缺陷(都会在真机上变成 Error)

| 缺陷 | 症状 | 根因 | 修复 |
|---|---|---|---|
| `class_778$class_780`(AO 计算器)里 19 处 `getfield h:Lnet/optifine/render/LightCacheOF;` 无对应字段 | 第一次环境光遮蔽计算时 `NoSuchFieldError` | `OptifineMappings.applyFieldRenames` 只按**声明类**匹配引用(`class_778$class_10931.h`),而 javac 对继承字段写的是**子类**做 owner(`class_778$class_780.h`);声明被改名成 `field_58166`,引用留在 `h` | 匹配时沿"引用 owner 的父类/接口链"找声明类(只走被补丁的类,原版祖先的名字本来就是对的) |
| `class_757`(被补丁)调 `class_2586.hasCustomOutlineRendering()Z`、`net/optifine/RandomTileEntity` 读 `class_2586.nbtTag/nbtTagUpdateMs` | 渲染方块实体 / 随机实体时 `NoSuchMethodError`、`NoSuchFieldError` | 我们沿用上游**跳过** OptiFine 的 BlockEntity,但它加了这些方法/字段,而调用方(OptiFine 自己重编译过的类和它自己的类)并不会跟着跳过 | 不再跳过,直接应用 OptiFine 的 BlockEntity;扫描确认 Fabric API 落到它上面的 mixin 目标仍然齐全(Refmap/AtTarget/Shadow 的检查条数还因此从 122/79/60 升到 122/81/64,全部通过) |

顺带:重映射现在把**游戏本体也当作 input**(不只是 classpath)。

下一步待办:① 真机实测(1.21.11 实例:主界面 / 单人 / 多人 / 模型 / 区块 / 光影);② 若要更贴近真机,可再给扫描器加"模拟 Mixin 注入点解析"这一层(目前 `@At` 的指令级匹配只做了显式 target 的 81 条)。

### `AtTargetScan` 曾经"什么都没查"却报 0 问题

给扫描器加上"按种类统计"之后才暴露出来:81 个落在被补丁类上的处理器全部进了统计,但 `call sites counted: 0` —— 也就是说 `@At` 那一层**一个都没真正校验**,之前的 "PROBLEMS: 0" 是**空结论**。

根因:Mixin 的 `method=` 引用是**不带 owner** 的写法(`method="method_4046(Lnet/minecraft/class_3887;)Z"`),而 `RefmapScan.parseRef` 要求 `Lowner;member` 前缀、`parseBareRef` 要求 `owner.member` 形式,两者都返回 null,于是每个处理器都被静默跳过 —— **Fabric API 不发布 refmap,所有 `method=` 都是这种无 owner 写法**,所以是"全军覆没"而不是个别漏网。

已修:新增 `resolveAgainst(...)`,先按老规矩解析,失败则按 Mixin 的规则把无 owner 引用**挂到该 mixin 的 `@Mixin` 目标类**上(多目标则逐个),再做 yarn 翻译。修完 `call sites counted: 26`,并立刻报出 **4 处真实缺失**:

| 缺失的注入点 | 来自 |
|---|---|
| `class_776.method_23071` 无 `INVOKE_ASSIGN class_773.method_3335` | `indigo.BlockRenderDispatcherMixin.afterGetModel` |
| `class_776.method_3353` 无 `INVOKE class_778.method_3367` | `indigo.BlockRenderDispatcherMixin.renderProxy` |
| `class_9810.method_60904` 无 `INVOKE class_2338.method_10097` | `indigo.SectionCompilerMixin.hookBuild` |
| `class_9810.method_60904` 无 `INVOKE class_2680.method_26217` | `indigo.SectionCompilerMixin.hookBuildRenderBlock` |

**四处全部来自 `fabric-renderer-indigo`**,而 indigo 正是本项目用 `fabric-renderer-api-v1:contains_renderer` 让位的那个模块(OptiFine 自己就是渲染器),它的 mixin 根本不会被应用 —— 所以不是问题,但这下**是有证据的"不是问题"**,而不是假设。扫描器现在会在报告里直接标注 `[indigo: disabled by ...]`。

同时补上:`@ModifyConstant`(没有 `@At` 时 Mixin 自己在目标方法里找常量,找不到就整类失败)现在也会被检查 —— 实测 Fabric API 落在被补丁类上的 81 个处理器**全部带显式 `@At`**,`@ModifyConstant` 命中 0 个,所以这一层暂时没有缺口。

### 真机第一次启动:三个注入目标被 OptiFine 重编译"改名改造"(已修)

第一次真机启动崩在:

```
Mixin apply for mod fabric-rendering-v1 failed fabric-rendering-v1.mixins.json:LevelRendererMixin
  -> net.minecraft.class_761: Critical injection failure:
     @ModifyExpressionValue annotation on onRenderBlockLayers could not find any targets matching
     'method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;...;)V' in net.minecraft.class_761. No refMap loaded.
```

进而 `RuntimeException: Mixin transformation of net.minecraft.class_761 failed` → OptiFine 自己的 `Reflector.<clinit>`(它会 `getDeclaredFields(class_761)`)连带失败 → 游戏崩。

根因:OptiFine 重编译 `class_761` 时把那个 **lambda 体**编译成了 `lambda$addMainPass$1`,而且**多带一个参数**(`class_9779`):

| | 名字 | 描述符 |
|---|---|---|
| 原版 | `method_62214` | 9 个参数 |
| OptiFine | `lambda$addMainPass$1` | 10 个参数(原版 9 个 + `class_9779`) |

Mixin 按**名字 + 描述符**找注入目标,名字和形状都对不上 → `require = 1` 缺省下整类失败。这属于"私有 lambda 体,只有 mod 会注入"的类型,正是 `RestoreVanillaMethodsFix` 存在的理由。

**扫描器当时看不见它,有两个盲区(都已修)**:

1. `RefmapScan` 也没有处理 **无 owner 引用**(`method="method_62214(...)V"`),于是 430 个 mixin 里只解析出 147 条引用,`MISSING 0` 是假的;
2. 更隐蔽的一条:对被补丁的类,它把**原版声明的成员也算作存在** —— 但游戏加载的是补丁后的那份,原版成员并不存在于其中。`method_62214` 正是因此被判为"存在"。

修完:引用解析数 147 → **560**,并如实报出 3 个"原版有、补丁后没有、而 Fabric API 要注入"的方法。三个都按既有机制补回原版方法体:

| 被补回的方法 | 谁注入 | 现状 |
|---|---|---|
| `class_761.method_62214(...)V` | fabric-rendering-v1 `LevelRendererMixin`(`@ModifyExpressionValue`) | 已补回,启动不再崩 |
| `class_3898.method_60440(class_3193;CompletableFuture;J)V` | fabric-lifecycle-events-v1 | 已补回 |
| `class_1092.method_65750(Map$Entry)Pair` | fabric-model-loading-api-v1 | 已补回 |

注意这三处是**补回原版方法体**(OptiFine 的活代码走它自己那份 lambda),因此这些注入点虽然存在、注入也会成功,但落在不再被调用的方法上 —— 相应 FAPI 功能(方块层渲染钩子等)可能不生效,**但不会再崩**。要做到"注入落在活代码上"需要把 OptiFine 的 lambda 改名并改造描述符(它的调用点写在 `invokedynamic` 的 bootstrap 参数里),风险高,留待需要时再评估。

另外一个真机日志里的**非致命**警告:`Failed to locate initialiser injection point in <init>(Lnet/minecraft/class_2591;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V, initialiser was not mixed in.` —— 这是第 5 轮把 OptiFine 的 BlockEntity 应用回来(修 5 处悬空引用)的代价:Fabric 的方块实体初始化钩子找不到注入点而失效(warn,不崩)。

### 第二次真机启动(ModelBakery)与第三次预防

第二次启动已经走得很远:补丁管线冷启动 7.3 秒全成功、OptiFine 初始化、着色器子系统起来、`[Indigo] Different rendering plugin detected; not applying Indigo`(让位按预期生效)—— `class_761` 崩溃消失。新崩溃在 `fabric-model-loading-api-v1`:

```
@WrapOperation wrapBlockModelBake 找不到 class_1088.method_68018(...)Lnet/minecraft/class_1087;
```

| 处理 | 对象 |
|---|---|
| 补回原版方法体 | `class_1088.method_68018`、`class_1088.method_68019`(ModelBakeryMixin) |
| **保留 OptiFine 方法体**、插回惰性调用点(新修复器 `InjectionCallPointFix`) | `class_775.method_3347` 里 `class_1163.method_4961` 那次调用(fabric-rendering-fluids-v1 要 wrap 它) |

`InjectionCallPointFix` 与 `ObjectCreationPointFix` 同思路:OptiFine 的方法体不动,在方法开头用**方法自己的参数**装载实参、调用目标方法并丢弃返回值。被调的是纯 getter,行为不变,mixin 又能找到注入点 —— 比"整段换回原版"更保守(那种做法会丢掉 OptiFine 的液体渲染)。

**扫描器第 4 个盲区**(这次崩溃被漏报的原因):`RefmapScan` 只从 `org.spongepowered` 注解里收集成员引用,`@WrapOperation`/`@ModifyExpressionValue` 这些 MixinExtras 注解整个被跳过,而 Fabric API 客户端大量使用(22 个模块用 `@ModifyExpressionValue`)。修完引用解析数 147 → **725**,并如实报出上述两处。

### 尚未标定的工具:`LocalSugarScan`

MixinExtras 的 `@Local` 是 FAPI 现在替代 `LocalCapture` 的写法(70 个类在用),解析失败是致命的 `LocalResolutionException`。新增了 `LocalSugarScan` 来离线检查:32 个处理器 / 44 个 `@Local` 参数;过滤掉注入管路参数(`CallbackInfo`/`Operation`/`LocalRef` 等,它们不是局部变量)后剩 7 条候选。

**但这 7 条目前不构成结论**:其中一条(`class_1088.method_68019` 缺 `class_10439$class_10441`)与事实矛盾 —— Fabric API 在原版上必须能正常工作,而 `javap -l` 显示该方法**根本没有 LocalVariableTable**,说明 MixinExtras 在这些方法上是**按字节码推断**而非查 LVT 来解析 `@Local` 的。所以工具目前的 LVT/ordinal 模型只在部分情况下成立,**未经真机错误信息标定前不应据此修代码**。下一次真机日志若出现 `LocalResolutionException`,就用它的原文标定这套规则。

### 真机实测进度(1.21.11 实例,逐次崩溃逐个修)

| 次 | 症状 | 根因 | 修复 |
|---|---|---|---|
| 1 | 启动崩:`Mixin transformation of class_761 failed` | OptiFine 把 lambda 体编成 `lambda$addMainPass$1` 且**多一个参数**,Mixin 按名字+描述符找不到 `method_62214` | 补回原版方法体(另两处同类:`class_3898.method_60440`、`class_1092.method_65750`) |
| 2 | 启动崩:`class_1088` | 同类,`method_68018/68019` 在补丁后消失 | 补回原版方法体;另新增 `InjectionCallPointFix` 修 `class_775.method_3347` 里被干掉的调用点 |
| 3 | 启动崩:`@Local class_2338$class_2339` 校验失败 | 原版在 `ARETURN` 处作用域内有 `MutableBlockPos`,补丁后没有(MixinExtras 在**注入点**上判别局部变量) | `RestoreVanillaMethodsFix(true, "method_24225")` |
| 4 | **进入世界成功**,约 4 秒后崩:`ChunkCacheOF.renderStart() ... regionIn is null` | OptiFine 的 `RenderChunkRegion` 只有**六参数**构造器会写 section 位置,而 `class_6850`(我们早就把 `build()` 换回原版方法体)调用的是五参数那个 → 字段恒为 null | 新增 `RegionSectionPosFix`:在原版方法体里改调六参数构造器,用该方法本就收到的**打包 long** 经 `ChunkSectionPos.from(long)` 生成第六个参数 |
| 5 | 进入世界约 4 秒后崩:`WorldRenderContextImpl.worldState()` 为 null | `beforeRender` 是 `@Inject(method_22710, at=HEAD)`,而 `beforeDrawBlockOutline` 读它准备的上下文;OptiFine 用 `RenderPass` + 自己的 `lambda$addMainPass$1` 替换了传入渲染状态的 `method_74923` 流程 → 上下文未填充。**这是 FAPI 与 OptiFine 渲染流程的语义不兼容,不是字节码形状问题** | 新增 `StubInjectionTargetFix`:把 `class_761` 的私有 `method_62210` 改名为 `optifabric$blockOutline`(4 处调用跟进)并留同名同描述符的空桩 → Mixin 注入进**没人调用**的桩,钩子失效但不再崩,描边仍由改名后的方法绘制 |
| 6 | 物品贴图全部丢失 | `class_10430.method_65584` 上的 `@Inject(at=RETURN)` 需要 OptiFine 重编译后不再存在的局部变量 → 每个物品模型都烘焙失败(见下一节) | `RestoreVanillaMethodsFix(true, "method_65584")` |
| 7 | 进多人服务器约 30 秒崩:`Attempted to retrieve active rendering plug-in before one was registered` | 第 5 项的同类钩子,但**调用者不在同一个类里**:改名后的副本仍被 `class_11684.method_73002` 按名字调到(见第 7 类) | 新增 `CallSiteRedirectFix`,把调用方也接管并改掉调用点 |

**代价与遗留**:`BEFORE_BLOCK_OUTLINE` 事件不再触发(非原版功能);`class_10444.update` 上 `fabric-renderer-api-v1` 的 `BlockModelWrapperMixin.onReturnUpdate` 仍是 `require = 0` 的**非致命**警告(1427 条),同样可用 `InjectionCallPointFix` 处理。

**已实测达成的目标项**:启动 → 主界面 → 创建存档 → 进入单人世界(`Preparing spawn area` → `Time elapsed` → `logged in with entity id` → 音效引擎与渲染运行)。
**未测**:多人、光影;以及第 5 项修复后的稳定性。

### 第 6 类:物品贴图全部丢失(class_10430.method_65584)

进入世界并加载光影后,用户报告"所有物品的贴图丢失"。日志里不是贴图加载失败,而是**每一个物品模型都烘焙失败**:

```
[Worker-Main-7/WARN]: Unable to bake item model: 'minecraft:nether_wart_block' / 'structure_block' / …(每个物品)
Caused by: InjectionError: Critical injection failure: Callback method onReturnUpdate(...)
```

**更正一个此前的错误判断**:`fabric-renderer-api-v1` 的 `BlockModelWrapperMixin.onReturnUpdate` 曾被当作"require = 0 的非致命警告"。`require = 0` 只表示注入点找不到时不报错,但它失败的**后果**是每条物品渲染链失效——物品模型全部烘不出来,所以贴图全丢。

定位过程(记录一下,因为第一次修错了):

1. 取出 mixin 注解:`@Inject(method="method_65584(class_10444, class_1799, class_10442, class_811, class_638, class_11566, I)V", at=@At("RETURN"))` + `@Local` 参数 → 与 `class_4603` **完全同形**(OptiFine 改写后 `RETURN` 处不再有 mixin 需要的局部变量);
2. 第一次按"方法首参是 `class_10444`"判断宿主,注册到 `class_10444` → **修复器没触发**:原版与补丁后的 `class_10444` 都没有 `method_65584`;
3. 查 mixin 的 `@Mixin(value = ...)` → 真正的目标类是 **`class_10430`**;
4. `registerFix("class_10430", new RestoreVanillaMethodsFix(true, "method_65584"))` → 日志确认 `Restored vanilla net/minecraft/class_10430.method_65584(...) over OptiFine's version`,物品模型恢复烘焙。

**教训**:Mixin 的 `method=` 目标要按 `@Mixin` 的**目标类**解析,不能按"方法描述符里的第一个参数类型"猜宿主。

### 第 7 类:多人游戏崩溃(钩子注入进了"会被调用"的那份副本)

单人一直正常,进多人服务器约 30 秒后崩(玩家处于旁观模式,服务器上有移动方块):

```
java.lang.UnsupportedOperationException: Attempted to retrieve active rendering plug-in before one was registered.
	at net.fabricmc.fabric.impl.renderer.RendererManager.getRenderer(RendererManager.java:29)
	at net.fabricmc.fabric.api.renderer.v1.Renderer.get(Renderer.java:72)
	at net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer.render(FabricBlockModelRenderer.java:68)
	at net.minecraft.class_11681.handler$zmb000$fabric-renderer-api-v1$beforeRenderMovingBlocks(class_11681.java:565)
	at net.minecraft.class_11681.method_72998(class_11681.java:24)
	at net.minecraft.class_11684.method_73002(class_11684.java:52)
```

`fabric-renderer-api-v1` 的 `BlockFeatureRendererMixin`(`@Mixin(class_11681)`)在这个类上挂了**两个**处理器,目标都是 `method_72998`:一个 `@Inject(at = INVOKE Iterator.hasNext(), ordinal = 0)` 用 `@Local` 抓到那个迭代器后**先把移动方块队列遍历并消费掉**(于是原版方法自己的循环变成空转),再走 Fabric 的渲染路径;另一个在 `RETURN` 收尾。两者都要 `Renderer.get()`,而 `contains_renderer` 让 Indigo 退场后 `RendererManager` 是空的 → 抛异常。

**第一次修复为什么会失效**:第 5 项的 `StubInjectionTargetFix` 只在**同一个类内部**把跟着改名的调用改掉了(日志里那句 "4 call(s) followed")。移动方块这条的调用来自**邻居类** `class_11684.method_73002`,它按名字 `method_72998` 调到的正是 Mixin 刚注入进去的那份副本,于是崩溃原样复现(上一次实测日志与本轮实测日志的栈完全相同)。这也说明 `class_761.method_62210` 之所以成立,只是碰巧它的 4 处调用都在 `class_761` 内部。

**修复**:新增 `CallSiteRedirectFix` —— 把指定调用者的调用点改到改名后的真实方法上,并把 `class_11684` 也用 `registerExtraClass` 登记(OptiFine 不补丁它,所以由我们接管,机制与 `class_11681` 完全一样;差别只是这次改动落在调用方)。离线日志:

```
[OptiFabric] Took over net/minecraft/class_11684 on our own (OptiFine does not patch it)
[OptiFabric] Renamed net/minecraft/class_11681.method_72998(...) to optifabric$movingBlocks (0 call(s) followed) and left an uncalled copy behind
[OptiFabric] Moved 1 call(s) from net/minecraft/class_11684 onto net/minecraft/class_11681.optifabric$movingBlocks(...) instead of method_72998
[OptiFabric] Prepared 570 patched classes (0 skipped, 0 failed) / verified OK: 570 / ASM verifier problems: 0
```

改完后 `method_72998` 这个名字只出现在 `class_11681`(声明 + 副本),而 `class_11684` 调的是 `optifabric$movingBlocks`(即原版方法体,里面本来就会遍历移动方块队列并逐个 `class_778.method_3374(...)` 绘制)——即"OptiFine 但没有 Fabric API"的行为。**教训**:让 Mixin 的注入点变成死代码,要求**所有**调用者(含跨类的)一起改名;只改本类内部的调用不够。

### 第 8 类(还没崩,但按 F3 必崩):没有任何渲染器被注册

顺着第 7 类那条异常查 `Renderer.get()` 的调用者,发现还有一条**活的**:`fabric-renderer-api-v1` 自己注册了一条调试屏条目(`DebugHudClient$ActiveRendererDebugHudEntry`),F3 打开时执行 `Renderer.get().getClass().getSimpleName()` 来显示 `Renderer: xxx`。我们这里 `RendererManager.activeRenderer` 永远是 null,所以**一按 F3 就会崩**,异常与多人那次一模一样。

根因是 `contains_renderer = true` 这个声明本来含义是"渲染器由别的 mod 提供"(Sodium 声明它,并确实注册了一个),而 OptiFine 不实现 Fabric 的渲染器 API —— 这个声明在我们这里只完成了"让 Indigo 退场"这一半。

**修复**:`RendererApiFallback` 注册一个惰性占位渲染器 —— 但**它第一版把这个项目自己踩进了第 9 类**(见下),现在的要求是:

- 类由 `RendererApiStubGenerator` 在**运行时用 ASM 生成**。本 mod 刻意不依赖 Fabric API(连 compileOnly 都没有),所以没法在编译期实现那个接口;生成时**只读那个接口自己的 class 文件**(`ClassReader` 解析,按名字+描述符**原样抄**抽象方法),**绝不用反射去看方法类型** —— 原因见第 9 类。注意同名重载(`Renderer.render` 有两个)必须按 `名字 → 描述符列表` 收集,否则会漏掉一个,生成的类仍是 abstract。类名 `OptifineRendererPlaceholder`,F3 里显示成 `Renderer: OptifineRendererPlaceholder`;
- 真被调用到的每个方法都抛 `UnsupportedOperationException` 并附一句说明(而不是让 NPE 出现在更深的地方);
- 注册调用用 `MethodHandles.publicLookup().findStatic(...)`,不用 `Class.getMethod(...)`(后者会解析该接口**所有**方法的签名,等于把游戏类全拉进来);
- 只在那个渲染器接口存在时注册(直接 `Class.forName`,不查 `isModLoaded`):没有 Fabric API 时连问都不会问,更不会报错;若已有别的渲染器注册过,则原样保留并只打一行日志(`RendererManager` 拒绝第二个);
- 注册时机放在**补丁类注入之后**(`ensureSetup` 的 finally 里):万一还有别的东西解析了游戏类,也要经过我们的补丁集,而不是绕过它。

生成的字节码由 `test-downloads/RendererStubTest.java` 离线验证:用一个假接口覆盖 对象返回 / void / 多宽度参数(long、double)/ 布尔返回 / 默认方法 / 静态方法 各种形状,全部通过。再用 `test-downloads/FapiRendererFallbackTest.java` 对**真实** `fabric-renderer-api-v1` + 构建产物 jar 跑一遍端到端,**而且故意不把 Minecraft jar 放进 classpath** —— 只要有任何一步去解析 `net.minecraft.*` 就会在这里以 NoClassDefFoundError 失败(这正是第 9 类的复现条件):

```
[OptiFabric] Generated kynarain/cn/optifabric/mod/OptifineRendererPlaceholder, implementing 6 method(s) of net.fabricmc.fabric.api.renderer.v1.Renderer without resolving any of their argument types
[OptiFabric] Registered OptifineRendererPlaceholder as Fabric's rendering plug-in: …
Renderer.get()          -> kynarain.cn.optifabric.mod.OptifineRendererPlaceholder
F3 shows                -> Renderer: OptifineRendererPlaceholder
  ok   the F3 line shows the placeholder: OptifineRendererPlaceholder
  ok   no game class was loaded: false          ← net.minecraft.class_2680 连加载都没有发生
second registration     -> refused as expected (Attempted to register a second rendering plug-in. Multiple rendering plug-ins are not supported.)
```

(其中"第二次注册被拒绝"正好证明第一次注册真的生效了。)

### 第 9 类:补丁类被"提前加载"成原版(`class_2680.getBlockStateBaseCacheClass` 找不到)

第 8 类的第一版注册占位渲染器时,直接在 preLaunch 里调了 `Class.getMethods()` 去读 `Renderer` 接口的抽象方法。于是下一次启动变成:

```
[OptiFabric] Ready: 569 patched classes taken over by Fabric Loader in 0.6 seconds
java.lang.NoSuchMethodError: 'java.lang.Class net.minecraft.class_2680.getBlockStateBaseCacheClass()'
	at net.optifine.reflect.Reflector.<clinit>(Reflector.java:401)
	at net.minecraft.class_128.method_557(class_128.java:138)
	...
[23:35:39] [main/ERROR]: Minecraft has crashed! (NoClassDefFoundError: Could not initialize class net.optifine.reflect.Reflector)
```

**根因**:`Renderer` 的方法签名里全是游戏类型(`BlockState`、`BakedModel`、`BlockRenderManager`、`MatrixStack`……),而 `Class.getMethods()` 会**解析每一个方法的参数与返回类型** —— 也就是**加载**这些游戏类。这件事发生在 preLaunch,发生在 `GameTransformerHook` 把补丁类交给 Fabric Loader **之前**,于是这些类是按**原版**classpath 加载的;类一旦加载就不会再查转换器,**整局游戏都停在那份原版类上**。OptiFine 的 `Reflector.<clinit>` 第一个用到 OptiFine 给 `class_2680`(BlockState)加的方法 `getBlockStateBaseCacheClass()`,自然报 NoSuchMethodError,而 `Reflector` 初始化失败又把崩溃报告本身也带崩了(所以这次连 crash-report 都没生成)。

定位它的证据链(全部离线可复现):`out/final` 里 `class_2680` **有** `public static java.lang.Class getBlockStateBaseCacheClass()`;`Optifine-mapped.jar` 里 `Reflector` 的 bootstrap method 是 `REF_invokeStatic net/minecraft/class_2680.getBlockStateBaseCacheClass:()Ljava/lang/Class;` —— 两边完全一致,所以缺的只能是"运行时那个 class_2680 不是补丁版"。而把 `FapiRendererFallbackTest` 的 Minecraft jar 拿掉后,旧写法立刻复现同一机制:

```
java.lang.NoClassDefFoundError: net/minecraft/class_778
	at java.base/java.lang.Class.getMethodsRecursive(Class.java:3146)
	at java.base/java.lang.Class.getMethod(Class.java:2164)
```

**修复**:`RendererApiStubGenerator` 改为用 ASM **读接口自己的 class 文件**(不解析任何类型),注册改用 `MethodHandles.findStatic`(只解析 `register` 一个签名),并把注册时机移到补丁类注入之后。修好后同一个测试在**没有 Minecraft jar** 的 classpath 上也全绿 —— 证明了这条路径确实一个游戏类都不碰。

**教训(这一条最值钱)**:**在把补丁类交给 Fabric Loader 之前,任何一次对游戏类的反射解析都会把它永久钉成原版。** 本项目其余代码都是拿字节(`getClassByteArray` / ASM)而不是拿 `Class` 对象,只有这一处越界了。以后凡是"在 preLaunch 阶段碰 Fabric API / 其他 mod 的类型"的代码,都要按这条检查:接口方法签名里有没有 `net.minecraft.*`。

### 最终真机状态(1.21.11,2026-09-11)

| 目标项 | 结果 | 证据 |
|---|---|---|
| 启动 → 主界面 | ✅ | 多次实测 |
| 进入单人世界 | ✅ | `logged in with entity id`、区块生成、音效引擎 |
| 方块与区块渲染 | ✅ | 世界正常、可走动;`Batching sections` 不再抛错 |
| 物品渲染 | ✅ | `Unable to bake item model` 归零;用户确认"贴图已全部恢复" |
| 光影 | ✅ | `[Shaders] Loaded shaderpack: ComplementaryReimagined_r5.9.1.zip` |
| 运行时错误 | ✅ | 单人会话 `[ERROR]` 0 条、注入失败 0 条、无崩溃报告 |
| 多人 | ✅ | 23:41:54 `Connecting to 8.148.31.159, 25565`,随后同会话进单人世界 `logged in with entity id 520`,23:44:45 正常 `Stopping!`;`[ERROR]` 0 条、无新崩溃报告 |
| F3 调试屏 | ✅ | 用户实测不再崩溃(`Renderer:` 一行显示 `OptifineRendererPlaceholder`,见第 8 类) |
| 早期类加载(第 9 类) | ✅ 已修 | 离线证据:`FapiRendererFallbackTest` 在**不带 Minecraft jar** 的 classpath 上全绿(旧写法在该条件下直接 `NoClassDefFoundError: net/minecraft/class_778`) |

最终一轮复测(2026-09-11 23:41:35 → 23:44:45,jar 843,017 字节 / SHA256 `DEA3CF19…02BC3BB`):启动、主界面、连接多人服务器、单人世界、区块与物品渲染、光影全部正常,`[ERROR]` 0 条,无崩溃报告;此前的第 7、8、9 类各修一次后均未复现。

修复器清单(本次移植新增,均可复用):`InjectionCallPointFix`(保留 OptiFine 方法体、插回惰性调用点)、`RegionSectionPosFix`(给 OptiFine 的区域构造器补 section 位置)、`StubInjectionTargetFix`(改名 + 留完整副本,让语义不兼容的钩子失效而非崩溃)、`CallSiteRedirectFix`(把**跨类**的调用点也改到改名后的方法上,否则副本照样被调用)、以及扩展的 `SyntheticFieldFix`(按声明位置配对同类型合成字段);另加运行时组件 `RendererApiFallback` / `RendererApiStubGenerator`(注册惰性占位渲染器,挡住 Fabric API 对 `Renderer.get()` 的查找)。

### 补上"OptiFine 自己的 874 个类"的校验

之前只校验被补丁的 568 个类,OptiFine 自己的类只是挂在 classpath 上"供解析" —— 但它们同样会被加载、同样在调游戏,出问题一样是崩。新增 `VerifyPatched --verify-jar`:

```
java -cp ... VerifyPatched --verify-jar <Optifine-mapped.jar> <vanilla intermediary jar> <final 目录> <libraries>
```

它把补丁后的类先定义进加载器(这样 OptiFine 的类看到的就是被补丁的游戏,和真机一致),再逐个定义+链接它们自己的类,然后跑 ASM 数据流校验。只对 Forge / launchwrapper 专用的类放行(那些 API 在 Fabric 上根本不存在)。

结果:**874/874 通过,0 失败,ASM 0**。加上被补丁的类(OptiFine 自己补丁 568 个游戏类,第 7 类又让我们接管了 `class_11681`、`class_11684` 两个 → **570 个**),这次移植共有 **1444 个类**通过 JVM 与 ASM 双向校验。

### 全新安装模拟(与用户操作一致)

在一个空目录里只放两个 jar(`OptiFabric-1.1.0+mc1.21.11.jar` + 官方命名的 `OptiFine_1.21.11_HD_U_J9.jar`),跑完整补丁管线:

```
首次补丁耗时 6.7 秒
[OptiFabric] Found 568 patched classes / Prepared 570 patched classes (0 skipped, 0 failed)
verified OK: 570 / FAILED: 0 / ASM verifier problems: 0
生成 .optifine/OptiFine_1.21.11_HD_U_J9/{cache-format.txt, Optifine-mapped.jar, Optifine.classes.gz}
```

第二次启动走缓存:1.8 秒(`Found existing patched OptiFine jar`)。也就是说首次启动不会长时间卡住。

安装位置注意:这台机器的启动器(PCL)对版本目录做了**版本隔离**,该实例的 mods 目录是
`%APPDATA%\.minecraft\versions\1.21.11-Fabric 0.19.5\mods\`,而不是 `.minecraft\mods\`;要启动的是 **Fabric 0.19.5** 那个版本(不是 `1.21.11-OptiFine_J9`)。

---

## 多版本:1.21.x 全系列

OptiFine 在 1.21.x 上出过构建的版本一共 **10 个**:1.21、1.21.1、1.21.3、1.21.4、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11(1.21.2、1.21.5 没有)。分支 `mc1.21.x` 用**一套源码**覆盖全部,每个版本产出一个 jar。

### 为什么能一套源码覆盖

两条实测依据:

1. **intermediary id 在 1.21.x 各版本之间是稳定的**。对比 1.21.10 与 1.21.11 的 intermediary 映射,本移植用到的类 id(`class_761`、`class_11681`、`class_11684`、`class_1088`、`class_10430`、`class_4603`、`class_6850`、`class_775`、`class_631`、`class_1092`、`class_3898`、`class_5619`、`class_5944`、`class_702`、`class_1059` 等)两边**都在**,方法 id 也都在(描述符里的类型名在 official 命名空间当然不同,但经映射后一致)。fixer 全部以 intermediary 名义注册,所以能跨版本复用。
2. **fixer 的行为是"找到就修、找不到就跳过"**,而且它读的是**当前版本游戏 jar 里的原版字节码**(`RestoreVanillaMethodsFix` 这类),不存在"把 1.21.11 的方法体塞进 1.21.10"的问题。于是版本差异自然退化为"某些 fixer 在某版本不触发",真正要处理的只有**该版本新出现的冲突** —— 由扫描器逐版暴露。

**但一个 jar 只能对应一个版本**:jar 里打包的是该版本的 `official→intermediary` 映射表(官方混淆名每版不同),用错版本会把 OptiFine 重映射成乱码。所以构建带版本参数,`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本。

### 构建

```powershell
.\gradlew -p v1.21.x build "-Pmc=1.21.8"   # PowerShell 里必须给参数加引号,否则 1.21.8 会被拆成 1
.\gradlew -p v1.21.x build                 # 不带参数 = v1.21.x/gradle.properties 里的 minecraft_version
```

- 项目布局:`common/` 放与版本无关的源码(patcher、fixer、mod、util),`v1.21.x/` 与 `v26.x/` 是**两个独立的 Gradle 项目**,各自有 `settings.gradle`、`gradle.properties`、`build.gradle`,并把 `../common/src/main` 一起编译。26.x 是未混淆的,走另一套插件与命名,见 [`PORT_26.x.md`](PORT_26.x.md)。
- 版本 → yarn 构建号的对应表在 `v1.21.x/build.gradle` 的 `yarnBuilds`(yarn 的版本串里含版本号,必须逐个列出);加一个版本就是加一行。
- 产物名固定为 `OptiFabric-<mod_version_base>+mc<版本>.jar`(例如 `OptiFabric-1.1.0+mc1.21.8.jar`),`mod_version_base` 在各项目的 `gradle.properties` 里;产物在 `v1.21.x/build/libs/`。

### 每版离线验证(一条命令)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-version.ps1 -Version 1.21.8
```

依次做六件事,全部离线可重复:

1. `gradlew -p v1.21.x build "-Pmc=<版本>"` —— 用该版本的 MC / yarn / intermediary 编译;
2. `test-downloads/version-setup.ps1` —— 从 loom 缓存取**混淆客户端 jar** 与 **intermediary 客户端 jar**,从 gradle 缓存取该版本的 **yarn 映射**,把该版本 **OptiFine 安装器**放进 harness 的游戏目录;
3. 下载并解包该版本 **Fabric API** 的 43 个模块 jar(扫描器要读每个模块的 mixin 注解);
4. `VerifyPatched --setup` —— 跑真实补丁管线,再对补丁类做 **JVM + ASM 双向校验**;
5. `VerifyPatched --verify-jar` —— 同样两种校验,对象是 **OptiFine 自己的类**;
6. `AtTargetScan` / `RefmapScan` / `RuntimeContractScan` / `LambdaScan` —— mixin 注入点、成员引用、抽象契约/覆写/引用,以及 **invokedynamic 句柄**。

产物:`test-downloads/verify-<版本>.log`(完整输出)、`test-downloads/out/final-<版本>/`(补丁类转储)、`test-downloads/scan-<版本>/*.log`(三份扫描报告)。

### 各版本状态

全部 10 个版本都跑过完整的离线链路(构建 → 补丁流水线 → JVM/ASM 双向校验 → 5 个扫描器)。「@At」一列剩下的全是**已被停用**的 indigo 注入点(它们不会应用,因为 `contains_renderer` 让 indigo 让位),其它三列是 0 才算通过。

| MC | OptiFine 目标构建 | 补丁类(JVM+ASM) | OptiFine 类(JVM+ASM) | @At | Refmap 缺失 | 契约扫描 | Lambda 句柄 | 真机 |
|---|---|---|---|---|---|---|---|---|
| 1.21 | `preview_..._J1_pre9` | 440/440 ✅ | 773/773 ✅ | 3(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:崩在 `SectionBuilder`(region 为 null)→ 已修,待复测 |
| 1.21.1 | `OptiFine_1.21.1_HD_U_J1` | 425/425 ✅ | 783/783 ✅ | 2(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:ShaderProgram 注入点(工厂委托)→ 已修,待复测 |
| 1.21.3 | `OptiFine_1.21.3_HD_U_J2` | 440/440 ✅ | 816/816 ✅ | 2(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:崩在 `SectionBuilder`(region 为 null)→ 已修,待复测 |
| 1.21.4 | `OptiFine_1.21.4_HD_U_J3` | 474/474 ✅ | 812/812 ✅ | 2(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:崩在 `SectionBuilder`(region 为 null)→ 已修,待复测 |
| 1.21.6 | `preview_..._J6_pre3` | 487/487 ✅ | 820/820 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:进世界正常,光影不加载(OptiFine 构建写死 cancelled)→ 已在管线修补,待复测 |
| 1.21.7 | `preview_..._J6_pre7` | 500/500 ✅ | 823/823 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:进世界正常,光影不加载(OptiFine 构建写死 cancelled)→ 已在管线修补,待复测 |
| 1.21.8 | `preview_..._J6_pre16` | 516/516 ✅ | 831/831 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:进世界正常,光影的 4 个 `*_translucent` 程序名 OptiFine 不认(包侧) |
| 1.21.9 | `preview_..._J7_pre2` | 519/519 ✅ | 832/832 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:FXAA 后处理的顶点着色器缺源 → 已在管线修补,待复测 |
| 1.21.10 | `preview_..._J7_pre11` | 553/553 ✅ | 836/836 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | 第二轮:光影加载正常,4 个 `*_translucent` 程序名 OptiFine 不认(包侧) |
| 1.21.11 | `OptiFine_1.21.11_HD_U_J9` | 570/570 ✅ | 874/874 ✅ | 4(indigo) | 0 ✅ | 0/0/0 ✅ | 0 ✅ | ✅ 已实测 |

「补丁类」= OptiFine 自己补丁的游戏类 + 本移植额外接管的类(1.21.6 及以上还多两个:`class_11681`、`class_11684`)。「OptiFine 类」= `Optifine-mapped.jar` 里 OptiFine 自身的类。两者相加就是每个版本通过 JVM 与 ASM 双向校验的类数(例:1.21.11 的 1444 个)。

「真机」一列是 2026-09-12 装机实测的结果,不是推断:其中两处 `VerifyError` 已定位到根因并修复(见文末[多版本真机反馈](#多版本真机反馈2026-09-12未被-fixer-改动的类必须保留-optifine-自己的栈帧)),其余 ⚠️ 与两条"原因待取"所需的证据也列在那里。

### 逐版记录

**1.21.10**(第一个非 1.21.11 的目标):同一份源码**直接编译通过**(mixin 用的 yarn 名字在该版本也存在),补丁流水线一次命中 —— 所有 fixer 都用与 1.21.11 相同的 intermediary id 触发,只有两类差异由既有机制自动吸收:

- `class_1092$1` 的捕获字段名字不同(`val$mapStitchIn` vs 1.21.11 的 `val$prepBlocksIn`)→ `SyntheticFieldFix` 按声明位置配对;
- 该版本多了一个需要桥接的方法(`class_156$4.onStart()V`)→ `MissingOverrideFix` 自动补。

唯一的新发现是 `RefmapScan` 报的 2 个"缺失成员"(`class_11659.method_73480` / `method_73484`),查证后是**扫描器自身的盲区**,不是补丁问题:`method_73484`/`method_73480` **声明在父接口 `class_11785`**,而 Fabric API 的 mixin 用子接口 `class_11659` 当 `@At(target=...)` 的 owner(`invokeinterface` 允许这么写,运行时会沿继承链解析)。`RefmapScan.hasMethod/hasField` 原来只沿 `superName` 走、**不跟 `interfaces`**,于是把继承来的成员判成缺失。已修(两处都补上接口遍历),1.21.10 复跑得到 **0 缺失**。

**1.21.9**(519 个补丁类 / 832 个 OptiFine 类):同样一次通过,fixer 触发集合与 1.21.10 完全一致(含 `class_156$4.onStart()` 的桥接)。

**1.21.8**(516 / 831)—— 第一个需要为版本差异改代码的目标,而且暴露了**两个成体系的教训**:

1. **写死描述符的 fixer 会在别的版本上静默失效。** `class_761.method_62210` 在 1.21.8 的描述符是 `(class_4184, class_4597$class_4598, class_4587, Z)`,在 1.21.11 是 `(class_4597$class_4598, class_4587, Z, class_11658)`。`StubInjectionTargetFix`/`CallSiteRedirectFix` 原本按"名字+描述符"匹配,于是 1.21.8 上**没命中**:方块描边钩子留在了活代码上,而它需要的 `INVOKE class_315.method_64858()` 在 OptiFine 重编译后的方法体里并不存在 → 真机必崩(fabric-rendering-v1 的 `WorldRendererMixin.onDrawBlockOutline`)。两个 fixer 现在都**按方法名匹配、描述符可选**,并且用"实际找到的那个方法"的描述符去改调用点。
2. **"副本"必须是原版身体,不能是 OptiFine 身体。** 改完上一条后那条 `@At` 依然报缺 —— 因为副本是 OptiFine 重编译后的字节码,而 Mixin 要注入的是方法体内的一个 INVOKE,OptiFine 早就把那句调用重写掉了;Mixin 找得到方法却找不到注入点,照样整类失败。所以副本现在优先取**游戏 jar 里原版方法的字节码**(副本是死代码,身体只需要"长得像原版";Mixin 本来就是照着原版写的),取不到才退回 OptiFine 的身体。

另外两点:

- `class_11681` / `class_11684` 在 1.21.8 上**不存在**(它们从 1.21.6 才有),接管被跳过 —— 这是正常的,日志会说明;
- 新增的 `LambdaScan` 抓到了 `LambdaRebuilder` 的一个隐患:上游在 `pairUp` 里写了一句 `assert`,而**断言只在 `-ea` 时生效**(harness 带 `-ea`,游戏不带),所以游戏里遇到"两侧名字不同、且都已不在待配对表里"的情况会**静默跳过**这对 lambda。现在改成计数 + 明确警告,并用 `LambdaScan` 验证后果:**0 dangling**(那三处跳过是良性的 —— 对应的调用点确实能在类里解析到)。`LambdaScan` 自己也踩了一个坑:record 的 `equals/hashCode/toString` 用的是**字段句柄**(`REF_getField`),当成方法查会误报 604 条,已按 handle tag 区分。

**1.21.7 / 1.21.6**:一次通过(500 / 487 个补丁类,823 / 820 个 OptiFine 类),fixer 触发集合与 1.21.8 一致。

**1.21.4 / 1.21.3 / 1.21.1 / 1.21**(这一批是 1.21.6 渲染重写**之前**的版本,Fabric API 的 mixin 集合与 OptiFine 的补丁都不一样)需要补两类修复,都属于"同一套 fixer、只是多几个该版本才存在的 id":

1. **被 OptiFine 重编译丢掉的方法**(Fabric API 仍往它们里注入,`require = 1` → 找不到就整类失败):

   | 版本 | 缺失的方法 | 谁要它 |
   |---|---|---|
   | 1.21.4 | `class_1088.method_65737` | fabric-model-loading-api-v1 的 ModelBakerMixin |
   | 1.21.1 | `class_1088.method_61072` | 同上(该版本的 ModelLoaderMixin) |
   | 1.21 起 ≤1.21.4 | `class_329.method_55806 / 55807 / 55808` | fabric-rendering-v1 的 InGameHudMixin(三个 HUD 层) |
   | 1.21 / 1.21.1 | `class_309.method_1454` | fabric-screen-api-v1 的 KeyboardMixin |

   全部由 `RestoreVanillaMethodsFix` 解决(把原版方法加回补丁类旁边)。这些 id 在**没有**它们的新版本上自动不触发,所以可以直接堆在同一个注册表里。

   其中 `KeyboardFix` 值得一提:它在 1.20.6 线上用过,在本分支被停用(1.21.11 根本没有 `method_1454/1458/1473/1466`)。现在改成**容错** —— 只 revert "游戏里确实存在"的那几个方法,其余跳过并打一行说明。上游当年是**直接抛异常**(`Failed to find Keyboard methods: ...`),所以它只能在方法全都在的版本上用;改成跳过之后,同一个 fixer 能同时服务 1.21 到 1.21.11。

2. **`RefmapScan` 漏了一种注解目标语法**:Mixin 同时接受 `Lowner;name(desc)ret` 和**点号形式** `owner.name(desc)ret`,而 Fabric API 在 **1.21.5 及更早**写的是点号形式(例如 `java/util/concurrent/CompletableFuture.thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)...`、`com/mojang/datafixers/util/Pair.of(...)`)。扫描器只认前者,于是把 owner 切错位置、描述符还丢了左括号,报出三条"补丁类里没有这个调用"——而调用**就在那里**(javap 一查即知)。已修 `parseRef`:支持点号形式。修的过程里还踩了一次自己的坑:第一版守卫用"成员部分不能含斜杠"来判断,结果**描述符里全是斜杠**,点号形式仍然落到 `parseBareRef` 上,于是仍然误报;现在只检查**成员名**里有没有分隔符。

另外 `LambdaScan` 也修了一次误报:句柄指向继承自 `java/lang/Object` 的方法时(`class_3999.toString()`),继承链走到 `Object` 就断了(表里没有它),于是把合法的句柄判成 dangling。现在 `java/lang/Object` 的标准方法单独识别,遇到**其他**不认识的祖先类则记为"无法判定"(而不是有问题)。

---

## 多版本真机反馈(2026-09-12):未被 fixer 改动的类必须保留 OptiFine 自己的栈帧

10 个版本的 jar 第一次全部装机实测,只有 1.21.11 正常。逐份 `latest.log` 看下来,能读出明确根因的那几个是**同一个**根因:

```
1.21.8   class_983.method_62593 @133  →  VerifyError: Bad type on operand stack in putfield
                                          (java/lang/Object 不是 class_1297)
1.21.3   class_898.method_3956 @206   →  VerifyError: Bad type on operand stack in putfield
```

`class_983` 是 `ShoulderParrotFeatureRenderer`、`class_898` 是 `EntityRenderDispatcher` —— 两个**跟 Fabric API / OptiFine 冲突毫无关系**的普通渲染类,没有任何 fixer 会碰它们。问题出在管线自己:

- `patch()` 原来在跑完 fixer 之后统一重算栈帧:只要有**任意一个** fixer 报了改动,就把整个类用 `COMPUTE_FRAMES` 重新序列化;
- 而 `MissingOverrideFix` 是**全局**的 —— 它在几乎每个类上都会报"有东西要补",于是全部 ~500 个补丁类**都**走了重算路径。本文档前面写下的设计意图("只有被 fixer 改过描述符的那几个类才重算栈帧")实际上从未生效;
- ASM 重算栈帧要把两条分支上的类型合并,合并靠 `getCommonSuperClass`;它退化时返回 `java/lang/Object`,于是某个局部变量的类型从 `class_1297`(Entity)变成 `Object`,而下一句正是往 Entity 字段里 `putfield` → 游戏拒绝加载这个类。

**为什么离线校验和 `LinkOne -Xverify:all` 都没抓到**:JVM 校验器的类型检查是**延迟**的 —— 字节码引用的类型在校验时**尚未加载**就只登记、不比较。1.21.8 那份里 `class_1297` 在链接该类时还没被加载,"Object 能否赋给 Entity"这一问被推到真正加载 Entity 时;游戏里 Entity 早就加载了,当场 VerifyError,harness 里加载顺序不同,于是全绿。这正是"离线全绿、真机必崩"的机制。

修法两条:

1. `OptifineInjector.patch()` 先用 `ClassWriter(0)` 序列化一遍**原始**字节再跑 fixer;**没有任何 fixer 改动**的类直接返回那份原始字节,栈帧就是 OptiFine(经 tiny-remapper 保留)的原帧,一个字节不动;只有真被改动的类才 `COMPUTE_FRAMES`;
2. `FrameComputingWriter.getCommonSuperClass` 退化时**打日志**(`[OptiFabric] No common supertype for X and Y … java/lang/Object`),不再静默降级;`VerifyLoader.prepare()` 在链接前**先把字节码引用到的类型全部加载**,消掉 harness 对加载顺序的依赖 —— 否则它永远抓不到这类问题。

复跑全系列:10 个版本都是 `Prepared N patched classes (0 skipped, 0 failed)` / `verified OK: N` / `FAILED: 0` / `ASM verifier problems: 0`,OptiFine 类 773–874 同样全通过,五个扫描器仍只剩 indigo 那几条,并且**没有出现任何一条 `No common supertype` 警告**(剩下几十个真被改动的类,类型合并都能解析出确切结果)。1.21.8 的 `class_983.method_62593` 再用 `javap` 复核:帧里已无 `java/lang/Object`。

### 另外两个根因:被我们改掉的"注入点本身"

`latest.log` 里 Mixin 只写 `Mixin transformation of net.minecraft.class_X failed`,不写原因,所以这两处是从**注入点**倒推出来的 —— 两个失败类的共同点是"**Fabric API 要注入的东西被我们(或 OptiFine)改掉了**",而且改掉的不是成员本身,而是 Mixin 用来**定位**注入点的那条指令。

**1.21 / `class_5944`(ShaderProgram):`DelegatingConstructorFix` 把注入点换成了别的调用。**
Fabric API 的 `ShaderProgramMixin` 用
`@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;ofVanilla(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"))`
包住构造函数里那次"String → Identifier"的转换。原版 1.21 的构造函数调的正是 `Identifier.ofVanilla`(`class_2960.method_60656`);而 `DelegatingConstructorFix` 是把 OptiFine 的委托构造函数内联回来,内联时用的创建方式抄的是 **OptiFine 自己的写法**(`new Identifier(name)`)。成员都在、描述符都对,`RefmapScan` 自然报 0 缺失,但那个 INVOKE **不在了** → 注入点找不到 → 整类失败,表现为 OptiFine 的 `Reflector` 初始化时崩溃(`ReflectorForge.<clinit>` → `FieldLocatorTypes` → `Class.getDeclaredFields(class_5944)`)。

修法:内联时**先问原版类**是怎么把 String 变成那个类型的(`findFactory`:同描述符构造函数里的 `(Ljava/lang/String;)L<类型>;` 静态工厂),有就照抄,没有才退回 OptiFine 的写法。1.21 复核:`class_5944.<init>(class_5912,String,class_293)` 现在第一条就是 `invokestatic class_2960.method_60656` ✓。

**1.21.4 / `class_329`(InGameHud):OptiFine 把 layer 的方法引用变成了 lambda。**
`fabric-rendering-v1` 从 1.21.2 起的 `InGameHudMixin` 不用普通注入点,而是用一个**自定义注入点** `net.fabricmc.fabric.impl.client.rendering.LayerInjectionPoint`:`find()` 遍历构造函数里的 `invokedynamic`,把**引导方法句柄**(`bsmArgs[1]`)的 owner/name/desc 和注解里的 `target` 逐一比对。也就是说它要的不是"类里有这个方法",而是"构造函数里有一条方法引用**指向**这个方法"。

OptiFine 的重编译把构造函数里三条 `this::method_55806/55807/55808` 改写成了 `this::lambda$new$0/1/2`,同时删掉了那三个方法;`RestoreVanillaMethodsFix` 把**方法**补了回去(所以 `RefmapScan` 报 0 缺失),但**句柄**还指着 lambda → 三个注入点一个都找不到 → 整类失败。对照原版字节码:BootstrapMethods #5/#7/#13 在原版分别指向 `method_55808`/`method_55807`/`method_55806`,我们这边指向 `lambda$new$0/1/2`,位置一一对应。

修法(新 fixer `LambdaMethodRefFix`,注册在 `RestoreVanillaMethodsFix` **之前**):逐个方法把两侧的 lambda 工厂按顺序对齐,当"我们的句柄指向本类的 `lambda$…`、原版同一位置的句柄指向本类的真实方法、描述符相同、且该名字当前空闲"时,**把 lambda 改名成那个方法名**(并跟改类内所有引用与句柄)。改名而不是"把句柄指回去",是因为 OptiFine 的 lambda 不一定等同于原版方法:1.21.4 的准星那一层,vanilla 只画准星,OptiFine 的 lambda 是"画准星 **+** `QuickInfo.render`",指回原版方法会把 QuickInfo 悄悄弄丢。fixer 每次改名都打日志说明身体是否与原版一致 ✓。

顺带查明:**这套自定义注入点只存在于 1.21.4 那一版 Fabric API**(1.21 / 1.21.8 / 1.21.11 的模块里根本没有 `LayerInjectionPoint`),所以只有 1.21.4 会因为它崩;相应地 fixer 也只注册给 `class_329`。1.21.4 复核:日志显示 11 处改名(`lambda$new$0/1/2` → `method_55808/55807/55806`,另外 8 处是别的 layer/记分板方法引用),10 处身体与原版逐指令一致、1 处是 OptiFine 的超集。

### 仍未确定的(下次真机复测需要的证据)

| 现象 | 版本 | 状态与需要的证据 |
|---|---|---|
| `VerifyError: Bad type on operand stack in putfield` | 1.21.3、1.21.8 | 已定位(栈帧重算)并修复,待复测 |
| 启动崩:`Mixin transformation of … failed` | 1.21、1.21.4 | 已定位到注入点被改写并修复,待复测 |
| Mojang 徽标之后黑屏、进程未无响应 | 1.21.6、1.21.7 | **先排除窗口状态**:本项目此前踩过一次一模一样的现象(`DEVELOPMENT.md` 前面的记录),根因是**窗口最小化时开着垂直同步,`SwapBuffers` 一直阻塞**,Render 线程出不来、加载界面永远不被移除,日志里自然一条错误都没有。请确认窗口在最前且未最小化,必要时关掉垂直同步;若仍黑屏,黑屏期间取一份 `jstack <pid>` |
| `[Shaders] No shaderpack loaded`,前面紧跟 `Couldn't find source for VERTEX shader (minecraft:post/blit)`、`Couldn't compile pipeline minecraft:fxaa_of_2x/1` 和 `Failed to parse post chain at minecraft:post_effect/fxaa_of_2x.json (No key fragment_shader)` | 1.21.9 | **已从日志读出报错链**:OptiFine 自己的 FXAA 后处理(`fxaa_of_2x` / `fxaa_of_4x`)用的还是旧格式 JSON,而 1.21.9 的解析器要新格式 → 后处理管线编译失败 → 光影子系统没起来,于是"没有光影"。**先在 OptiFine 视频设置里关掉 FXAA/抗锯齿再试**;若仍然不加载,再用启动器单独跑 OptiFine 版对比 |
| `[Shaders] Invalid program name: dh_water` / `gbuffers_entities_translucent` / `gbuffers_particles_translucent` / `gbuffers_block_translucent` / `gbuffers_particles` | 1.21.10 | 光影包请求了 **OptiFine 不认识的程序名**(`*_translucent`、`dh_*` 是 Iris / 其他加载器的命名),所以"光影加载成功但渲染不对"属于**光影包与 OptiFine 不匹配**,与补丁无关;换 OptiFine 专用包即可验证 |

另外更正一条早先的记录:**1.21.1 其实从未启动过** —— 该实例目录里连 `logs/` 和 `options.txt` 都没有(只有 `mods/`)。它此前被列进"崩溃"只是因为用户说"其他没提到的版本都崩了",而事实是那一版没被运行过。

---

## 第二轮真机反馈(2026-09-12 12:03–12:11):崩溃换了地方,光影"没用"有三个不同的原因

十版重新装机后:1.21 / 1.21.1 / 1.21.3 / 1.21.4 仍然崩(但崩点全变了,说明上一轮的注入点问题确实过了),1.21.6 / 1.21.7 / 1.21.9 是"光影完全没反应",1.21.8 / 1.21.10 是"光影加载了但渲染不对"。

### 两个崩溃

**`RegionSectionPosFix` 在 1.21–1.21.4 上没生效(1.21 / 1.21.3 / 1.21.4)**
进世界十几秒后崩在 `SectionBuilder.compile`(`class_9810`):

```
NullPointerException: Cannot invoke "net.optifine.override.ChunkCacheOF.renderStart()" because "regionIn" is null
  at net.minecraft.class_9810.compile(class_9810.java:85)
  at net.minecraft.class_846$class_851$class_4578.method_22783(...)
```

原因不是 fixer 写错了,而是**它认不出那些版本的形状**:1.21 到 1.21.4 的 `ChunkRendererRegionBuilder.method_39969` 直接收 `ChunkSectionPos` 对象,1.21.6 起才收打包 long,而 fixer 只处理 long(`longSlot < 0` 就整段跳过,只在日志里留一句"does not take the packed section position")。于是 region 仍然用原版 4 参构造器建出来,它内部的 `ChunkCacheOF` 是 null,OptiFine 的 `compile()` 第一句 `renderStart()` 就炸。

修法:两种形状都支持 —— 有 `ChunkSectionPos` 参数就直接 `ALOAD` 它,只有 long 时才 `LLOAD` + `ChunkSectionPos.from(J)`;另外**新描述符由"原描述符 + 追加参数"推出**,因为各版本构造器的参数个数本来就不同(1.21.4 是 `(World,int,int,ChunkSection[])`,1.21.8 是三个 int)。第二点上踩过一次:第一版实现用 `substring(0, length-1)` 砍尾巴,把 `...)V` 砍成了 `...)`,生成 `(Lclass_1937;III[Lclass_6849;)Lclass_4076;)V` 这种非法描述符 —— 离线验证器当场报 `define failed: ClassFormatError: Method "<init>" ... has illegal signature`,这类错误**只出现在 `ASM verifier problems` 里,不在 `FAILED:` 里**,第一次复跑时漏看了这一列,后来把它加进了复跑断言。

**1.21.1 的 `Mixin transformation of net.minecraft.class_5944 failed`**
和第一轮 1.21 是同一处注入点(Fabric API 的 `ShaderProgramMixin` 用 `@WrapOperation` 包 `Identifier.ofVanilla`),但形状不同:1.21 的 OptiFine 用 `new Identifier(name)`,1.21.1 用的是**静态工厂**委托:

```
1: aload_1                       // provider
2: aload_2                       // name
3: invokestatic class_2960.method_60654(String)Identifier   // Identifier.ofVanilla
7: invokespecial <init>(class_5912, Identifier, class_293)  // this(...)
```

`DelegatingConstructorFix.findDelegation` 只认 `NEW; DUP; …; INVOKESPECIAL <init>` 这一种"创建",于是根本没内联,注入点留在 `this()` 之前 —— Mixin 拒绝实例 handler 落在 `super()` 之前,整类失败。现在两种形状都识别(工厂的返回类型就是被委托过去的类型),内联后同样优先用**原版**的转换调用。

### 光影没能生效的三个原因(互不相同,只有一个在光影包那边)

**1.21.6 / 1.21.7:OptiFine 那个构建自己把光影关死了。**
`Shaders.loadShaderPack()` 里,在读配置、做检查之后、真正加载之前,字节码是:

```
169: astore_3                                  // String packName = shadersConfig.getProperty(...)
170: iconst_1                                  // cancelled = true;   ← 写死
171: istore_2
172: iload_2
173: ifne  197                                  // if (cancelled) 跳过 getShaderPack()
```

也就是说 `getShaderPack()` 永远不被调用,用户选什么包都是 `[Shaders] No shaderpack loaded.`。对照 1.21.8(pre16)、1.21.9、1.21.10 的构建,那里是 `iload_2; ifne`(读上面两个检查 —— 抗锯齿/华丽画质 —— 设置的标志),没有任何写死。**这是 OptiFine 预览构建的缺陷,配置里改不掉**(实例的 `ofAaLevel:0`、`graphicsMode:1` 与能正常加载的 1.21.8 完全一致)。

处理:新增 `mod/OptifineJarFixer`,在映射后的 jar 上作业 —— 找到 `loadShaderPack` 里 `ICONST_1; ISTORE n; ILOAD n; IFNE` 这个四连(中间可能夹着 Label/LineNumber/Frame 节点,所以要按**真实指令**比较),把那两条赋值删掉,恢复成 1.21.8 的形状。只在匹配到时才动,其它构建原样通过。

**1.21.9:OptiFine 自带的 FXAA 后处理引用了这一版没有的顶点着色器。**
OptiFine 的 FXAA 走游戏的 post effect 系统(1.21.6 起的新格式),它自己的 `assets/minecraft/post_effect/fxaa_of_{2x,4x}.json` 里第二个 pass(把 `swap` 拷回 `minecraft:main`)写的是:

```json
"vertex_shader": "minecraft:post/blit",
"fragment_shader": "minecraft:post/blit"
```

而 1.21.9 起游戏只提供 `assets/minecraft/shaders/post/blit.fsh`,顶点阶段改用 `core/screenquad`(原版自己的 `post_effect/transparency.json` 就是这么写的)。于是:

```
Couldn't find source for VERTEX shader (minecraft:post/blit)
Couldn't compile pipeline minecraft:fxaa_of_4x/1: vertex shader minecraft:post/blit was invalid
```

后处理管线编译失败,接着光影初始化失败 —— 用户看到的"1.21.9 不加载光影"就是这么来的(日志里那 5 次 `No shaderpack loaded`,紧跟在这些报错之后)。

同一个 fixer 处理:按"游戏 jar / OptiFine jar 里到底有没有这个 `program` 的 `.vsh`"判断,缺源就换成 `minecraft:core/screenquad`;顺带把 1.21.6 / 1.21.7 那份用了 `"program"` 键(那两个版本的解析器只认 `vertex_shader`/`fragment_shader`,报 `No key fragment_shader`)的 JSON 一并改写。1.21.10 / 1.21.11 的那两份本来就是对的,只在真的缺源时才动。

**1.21.8 / 1.21.10:光影包用了 OptiFine 不认识的程序名 —— 这条不归补丁管。**
两份日志里各有 30 条 `[Shaders] Invalid program name:`:`dh_water`(8)、`gbuffers_entities_translucent`(6)、`gbuffers_particles_translucent`(6)、`gbuffers_block_translucent`(6)、`gbuffers_particles`(4)。查光影包本体:`photon_v1.2a.zip` 里确实有 `gbuffers_all_translucent`、`gbuffers_block_translucent`、`gbuffers_entities_translucent`、`gbuffers_particles_translucent` 以及 `dh_terrain` / `dh_water` —— `*_translucent` 是 Iris 一侧的命名习惯,OptiFine 的固定程序表里没有,`dh_*` 是 Distant Horizons 用的(本实例没装)。OptiFine 的做法是报错并跳过,那些 pass 于是走内置程序,画面自然"不对"。
**验证办法:换一个 OptiFine 专用包**(本项目在 1.21.11 上验过 `ComplementaryReimagined_r5.9.1.zip`)。同一个包在 1.21.8 与 1.21.10 上报完全一样的程序名,说明它与"我们对字节码做了什么"无关。

### 这一轮的复跑口径

十版重跑(`verify-version.ps1`),断言收紧成四列一起看:`Prepared N patched classes (0 skipped, 0 failed)`、`verified OK: N`(补丁类与 OptiFine 类各一次)、`ASM verifier problems: 0`、以及扫描器的 `MISSING members / broken abstract contracts / DANGLING handles` 全为 0 —— 上一轮就是漏看 `ASM verifier problems` 才让一个非法描述符溜过去的。缓存格式提到 17(管线的产物变了两次:先是 OptiFine jar 修补,后是修补本身的修正),旧缓存会自动重建。

---

## 第三轮真机反馈(2026-09-12 12:28–12:37):1.21/1.21.3/1.21.4 通过,其余各有原因

用户复测:只有 **1.21、1.21.3、1.21.4 正常工作**;1.21.1、1.21.6、1.21.7 直接崩;1.21.8、1.21.9、1.21.10 光影渲染有问题。逐份 `latest.log` + 崩溃报告 + 字节码比对的结果:

### 1.21.1:同一处注入点还有一个漏网的调用(已修)

FAPI 1.21.1 的 `ShaderProgramMixin` 有**两个** `@WrapOperation` 包 `Identifier.ofVanilla`(构造函数里一个、`loadShader` 里一个)。第一轮我们只修了构造函数(静态工厂委托内联),`loadShader` 没动。查 yarn 映射:

```
m  (Ljava/lang/String;)Lnet/minecraft/class_2960;  method_60654  of
m  (Ljava/lang/String;)Lnet/minecraft/class_2960;  method_60656  ofVanilla
```

而 FAPI 的 refmap 里 `Identifier;ofVanilla(...)` 映射到 **`method_60656`**。原版 1.21.1 的 `loadShader` 调的正是 `ofVanilla`,**OptiFine 重编译后的身体改成了 `of`(method_60654)** —— 两个版本号只差 2,但语义与"被 mixin 包住的那个调用"都不同,于是 `@WrapOperation` 找不到目标、`require = 1` 让整类失败。

修法(新 fixer `VanillaFactoryCallFix`,注册给 `class_5944` 的 `<init>` 与 `method_34579`):把**游戏自己的同名方法**里"`(String) -> X` 的静态工厂"收集起来,凡是补丁类里产出同类型、但 owner/name 与游戏不同的调用,一律改写成游戏用法。只按"产出同类型"匹配,所以不相关的调用原样通过。1.21.1 复核:`loadShader` 与构造函数现在都调 `method_60656`,校验 425/425 + 783/783 全绿。

### 1.21.6 / 1.21.7:光影终于开始加载,于是撞上 OptiFine 自身的第二个缺陷

我上一轮把这两个构建里写死的 `cancelled = true` 去掉之后,光影包**确实开始加载**了 —— 然后启动崩,而崩点完全在 OptiFine 自己的代码里:

```
NullPointerException: Cannot invoke "com.mojang.blaze3d.textures.GpuTexture.getGlTextureId()" because "this.field_56974" is null
  at net.minecraft.class_1044.getGlTextureId(class_1044.java:111)
  at net.optifine.shaders.SimpleShaderTexture.loadTexture(SimpleShaderTexture.java:60)
  at net.optifine.shaders.Shaders.loadCustomTextureShaders(Shaders.java:1574)
  ... loadCustomTextures -> loadShaderPackDynamicProperties -> loadShaderPack -> Shaders.startup -> Config.initDisplay
```

1.21.6 起原版把纹理由 `AbstractTexture` 的 `GpuTexture` 字段承载,没创建就是 null;OptiFine 1.21.6/1.21.7 那两版预览的 `SimpleShaderTexture.loadTexture` **仍按旧 API** 直接取 `getGlTextureId()`,而 1.21.8 的构建已经改用 `RenderSystem.getDevice()` 走新 API(字节码逐条对比过)。这一版被 `cancelled = true` 挡在后面,所以从来没人踩到。

**没有更新的构建可以用**:查了 OptiFine 版本列表,1.21.6 只有 `J6_pre1/2/3`,1.21.7 只有 `J6_pre4/5/6/7`,用户手里已经是最新版。所以这不是"换个构建就好"的问题,而是那两版构建自身的两处缺陷。

### 1.21.8 / 1.21.9 / 1.21.10:光影加载成功但渲染不对

1.21.8 与 1.21.10 的日志里各有 30 条 `[Shaders] Invalid program name:`,查光影包本体确认:`photon_v1.2a.zip` 里有 `gbuffers_all_translucent`、`gbuffers_block_translucent`、`gbuffers_entities_translucent`、`gbuffers_particles_translucent` 以及 Distant Horizons 用的 `dh_terrain`/`dh_water`。`*_translucent` 是 Iris 一侧的命名,OptiFine 的固定程序表里没有,它只报错并跳过这些 pass。**注意 1.21 / 1.21.3 / 1.21.4 同样报这 30 条**(用户认为那三版"正常"),所以这条更像是"画面细节有出入",而不是致命问题 —— 需要用户描述具体现象或换包对比才能定性。

1.21.9 本轮日志干净(无 ERROR),需要同样对比确认。

### 第三轮补记:把 OptiFine 的纹理创建也补上(1.21.6 / 1.21.7)

上一节说到 1.21.6 / 1.21.7 的光影在打开后会崩在 OptiFine 自己的 `SimpleShaderTexture.loadTexture`。继续查下来**可以修**,依据是同一份类在两个构建里的差异:

| | 1.21.6 / 1.21.7(J6_pre3 / pre7) | 1.21.8 起 |
|---|---|---|
| 65 起 | `this.getGlTextureId()` → `TextureUtils.prepareImage(id, w, h)` | `RenderSystem.getDevice()` + `TextureFormat.RGBA8` + `createTexture(...)` + `createTextureView(...)`,把结果写回 `field_56974` / `field_60597` |
| 之后 | `image.uploadTextureSub(...)` | 同样 `image.uploadTextureSub(...)`(只用了图片,不用 id) |

两边前半段(取流、读 `NativeImage`、`loadTextureMetadataSection`)逐字节相同,说明只是尾巴没跟上 1.21.6 的贴图 API 改动。而且 `GpuDevice.createTexture(String,int,TextureFormat,int,int,int,int)`、`createTextureView(GpuTexture)`、`NativeImage.method_4307/method_4323`、`AbstractTexture` 的两个字段在 **1.21.6 里全都在**(逐个 `javap` 比对过),所以这段可以照抄 —— 常量也照抄:usage `5`、`RGBA8`、1 层、1 级 mip。

`OptifineJarFixer` 现在多一条:`SimpleShaderTexture.loadTexture` 里如果出现"`getGlTextureId()` + `TextureUtils.prepareImage`"这对旧 API 调用(**只在**这两个构建里有),就把从接收者 `aload_0` 到 `prepareImage` 这一整段(它本身就是栈平衡的)替换成上面那个创建块。**只在这两个构建上触发**:1.21.8 起的方法里根本没有 `prepareImage`,其它版本原样通过。

离线复核:1.21.6 / 1.21.7 的补丁日志出现 4 条修补(写死的 `cancelled`、FXAA JSON、纹理 API),补丁后的 OptiFine 类仍然 **820 / 823 个全部通过 JVM + ASM 双向校验,0 失败** —— 合成的 `loadTexture` 栈形状正确(这是我们离线能给出的最强证据;真正能不能跑,要看下一次真机)。

### 天空与水面闪烁(1.21.8 / 1.21.9 / 1.21.10):目前排除掉的可能

- **不是 OptiFine 设置差异**:四个实例的 `optionsof.txt` 关键项完全一致(`ofRenderRegions:false`、`ofSmartAnimations:false`、`ofAaLevel:0`、`ofChunkUpdates:1`…);
- **不是光影程序没加载**:1.21.4(可用)、1.21.8、1.21.10 都加载了 **43 个程序**,而且 `gbuffers_skybasic`、`gbuffers_skytextured`、`gbuffers_water`、`gbuffers_hand_water` 四个天空/水面程序三版都在;
- 三版共有的 30 条 `Invalid program name`(`gbuffers_*_translucent`、`dh_*`)在**可用的 1.21 / 1.21.3 / 1.21.4 上一条不少**,所以它解释不了"只有 1.21.8 起才闪"。

要区分是"光影包/驱动一侧"还是"补丁一侧",只需要两个对照:关掉光影(还闪不闪)、换成 `ComplementaryReimagined_r5.9.1.zip`(还闪不闪)。这条还没做,所以本节不给结论。

## 抗锯齿黑屏(1.21.9 / 1.21.10)—— 已修好

现象:1.21.9 / 1.21.10 关掉抗锯齿一切正常,一开抗锯齿就整屏黑。真机上已验证恢复正常。

根因是**两条同名的后处理路径在打架**,而 OptiFine 的着色器只认其中一条:

* OptiFine 的 FXAA 走着色器(`assets/minecraft/shaders/post/fxaa_of_2x.{vsh,fsh}`,`#version 150`)声明的是
  `layout(std140) uniform Projection` / `SamplerInfo` / `FxaaConfig`,外加顶点输入 `in vec4 Position`。
  这套是给 **OptiFine 自己的 post chain 运行器** 用的 —— 老式链描述文件里正好用
  `ProjMat` / `OutSize` / `SpanMax` / `SubPixelShift` / `ReduceMul` 填这些 uniform。
* 1.21.8 起的构建**不再带老式链文件**,只带游戏新形式的 `assets/minecraft/post_effect/fxaa_of_*.json`,
  而游戏那条管线不按上面的形式喂 uniform:顶点位置算不出来 -> 第一个 pass 往 `swap` 里画空 ->
  第二个 pass 把 `swap` 拷回主画面 -> **整屏黑**。

所以修好"解析失败"之后依旧黑屏 —— 因为病根不是解析,而是**这条管线根本不该由游戏来跑**。

`OptifinePostChainFixer`(`mod/OptifinePostChainFixer.java`)现在做两件事:

1. 按 OptiFine 自己的 schema **补写** `assets/minecraft/shaders/post/fxaa_of_{2,4}x.json`
   (结构照抄 1.21.6 那份 719/443 字节的原件:2x 带 SpanMax 8.0 / SubPixelShift 0.25 / ReduceMul 0.125,
   4x 只有 ProjMat + OutSize)。内容只引用用户自己那份 OptiFine 里**已经存在**的 `post/fxaa_of_*.vsh/.fsh`
   —— 不复制、也不分发 OptiFine 的任何文件。
2. **移除**游戏那条 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`,让抗锯齿只有一个机制负责。

只在"带了 FXAA 着色器"的构建上动手,其它 jar 一律不碰;已经自带 post chain 的构建只做第 2 步;
重复运行是幂等的。缓存格式 20 -> 21 -> 22(21 是补链、22 是移除游戏管线)。

> 统计口径:那两条 `Resource not found: minecraft:shaders/post/fxaa_of_*` 警告现在应当消失;
> 若哪天又出现"开了抗锯齿没效果",就是 OptiFine 没触发自己的链,下一步是把游戏管线的 uniform
> 按链的形式补齐(ProjMat/OutSize/... 塞进 pass 的 `uniforms`),而不是再删文件。

## 仍待办的两项(实现细节已备齐,可直接开工)

### A. 1.21.6 / 1.21.7 启动崩溃

```
NullPointerException: Cannot read field "norm" because "multiTex" is null
  at net.optifine.shaders.ShadersTex.initDynamicTextureNS
  at net.minecraft.class_1043.method_71142 / method_71141
```

1.21.8 的 OptiFine 在创建纹理之后先做 `this.field_56974.setParentTexture(this)`,而 1.21.6 / 1.21.7 的构建
**既没有这个方法、也没做这个关联**,于是它自己要用的 multi-tex 登记表是空的。可照抄的部分已从 1.21.8
反编译出来(全部只有 2~3 条指令):

```java
private net.minecraft.class_1044 parentTexture;                                   // 字段
public void setParentTexture(class_1044 p) { this.parentTexture = p; }            // aload_0; aload_1; putfield; return
public net.minecraft.class_1044 getParentTexture() { return this.parentTexture; } // aload_0; getfield; areturn
```

做法:新增一个 `ClassFixer`,注册给 `com/mojang/blaze3d/textures/GpuTexture` 与 `class_1043` 两个名字,
按 `optifine.name` 分流 —— 前者补字段与两个方法(已存在就跳过,1.21.8 起自带),
后者在调用 `ShadersTex.initDynamicTextureNS` 的方法里、其**之前**插入
`aload_0; getfield field_56974; aload_0; invokevirtual GpuTexture.setParentTexture(class_1044)V`
(栈平衡、不新增跳转目标,所以不涉及栈帧重算;已有关联调用则跳过)。`SimpleShaderTexture`
那边(我们先前的 GPU 纹理创建块)也顺手补同一个关联,与 1.21.8 一致。

需要的接口事实(已核对,不必再查):

* 包 `kynarain.cn.optifabric.patcher.fixes`,`import kynarain.cn.optifabric.util.RemappingUtils;`
* `public interface ClassFixer { void fix(ClassNode optifine, ClassNode minecraft); }`
* 注册:`OptifineFixer` 里 `registerFix("class_1043", new XxxFix());` —— 内部会过一遍
  `RemappingUtils.getClassName(className)` 再入表,所以 `com/mojang/...` 这类名字原样可用;
  另有 `extraClasses` 机制用于"OptiFine 不补丁、但需要我们的 fixer"的类。

### B. 1.21.8 多人游戏崩溃

```
UnsupportedOperationException: OptiFine is the active terrain renderer: Fabric's renderer API
  has no rendering plug-in behind it here (Indigo steps aside)
  at kynarain.cn.optifabric.mod.OptifineRendererPlaceholder.render
  at net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer.render
  at class_835.method_3575 -> class_824.method_23079 -> class_761.renderBlockEntities
```

占位渲染器是**故意抛异常**的,而多人服务器上某个方块实体的渲染正好走到这条路 -> 直接崩。真正可行的回落:
`FabricBlockModelRenderer` 是个**接口默认方法**,它收到的参数就是原版
`net.minecraft.class_778.render(...)` 那一套(`class_1920 / class_1087 / class_2680 / class_2338 / ...`),
所以占位器把**同一批参数转交原版 `class_778.render(...)`** 即可(占位器是 `Renderer` 实现、不是
`BlockModelRenderer`,不会递归)。注意 `Renderer.render(...)` 返回 `void`,Fabric 那边没有"返回 false
就让原版接管"的开关(`FabricBlockModelRenderer` 里只有 `Renderer.get()` 然后 `render(...)` 一路)。
实现点在运行时生成占位类的那段字节码(`RendererApiStubGenerator` / `RendererApiFallback`)。
## 收尾记录(1.21.x 全线)

### 已修好并真机确认

| 版本 | OptiFine 构建 | 结果 | 关键问题与修法 |
|---|---|---|---|
| 1.21 | `preview_..._J1_pre9` | ✅ | 工厂调用被 OptiFine 改体后错位 -> `VanillaFactoryCallFix` 对齐 |
| 1.21.1 | `HD_U_J1` | ✅ | `Identifier.of` 与 FAPI 包装的 `ofVanilla` 不一致 -> 同上 |
| 1.21.3 / 1.21.4 | `J2` / `J3` | ✅ | 区块 section 位置未传 -> `RegionSectionPosFix`;HUD 图层 lambda 名被换 -> `LambdaMethodRefFix` |
| 1.21.8 | `J6_pre16` | ✅ | 多人:占位渲染器抛异常终结会话 -> 该重载改为返回默认值(见下) |
| 1.21.9 / 1.21.10 | `J7_pre2` / `J7_pre11` | ✅ | FXAA 两条同名管线打架导致开抗锯齿黑屏 -> 补 OptiFine 自己的链 + 移除游戏的 post effect |

### 放弃

**1.21.6 / 1.21.7**(`J6_pre3` / `J6_pre7`,官方列表里已是最新)启动即崩:

```
NullPointerException: Cannot read field "norm" because "multiTex" is null
  at net.optifine.shaders.ShadersTex.initDynamicTextureNS
```

它们既不关联纹理也不带 1.21.8 的 `GpuTexture.setParentTexture`,而**跨类调用另一个被打补丁的类新长出来的方法会被注入器拒绝**
(`Failed to prepare the patched class net/minecraft/class_1043, it will not be replaced`),所以照搬 1.21.8 的关联补丁走不通;
退一步"删掉那次 `initDynamicTextureNS` 调用"虽然离线全绿(487 类 / 0 failed / ASM 0),但按用户决定不再投入,已取消注册。

### B 的现状与下一步

占位渲染器(`RendererApiStubGenerator` 运行时生成的那段字节码)现在对"接收原版参数"的那个重载**返回默认值而不抛异常**,
会话不再被打断。真机已确认 1.21.8 正常。

**仍未做的是真正的绘制回落**:把同一批参数转交原版 `class_778` 的渲染方法。参数天生为它准备(第一个参数就是模型渲染器本身),
缺的只是**该方法在当前映射里的名字** —— 而生成器**不得解析游戏类**(头注释记着当年因此提前加载游戏类、随后
`NoSuchMethodError: class_2680.getBlockStateBaseCacheClass()` 的教训)。正确路径:补丁器手里就有映射表,
用 `RemappingUtils` 按成员查出这个名字,再作为字符串传进生成器;不要用反射绕过。

### 纪律(仍然适用)

每版一条命令验证,断言必须包含 `Prepared … (0 skipped, 0 failed)`、`verified OK`、`ASM verifier problems: 0`、扫描器三列 0,
且日志里不得出现 `Failed to prepare` / `define failed`。产物变了就抬 `CACHE_FORMAT`(现为 **24**),否则 harness 会复用旧缓存而看不到改动。
### 1.21.11 真机确认(最后一个待复测版本)

`HD_U_J9` 上单机、光影(Complementary)、抗锯齿、多人全部正常 —— 该项目最初的移植目标至此闭环。

**最终成绩:十个版本里八个可用**(1.21、1.21.1、1.21.3、1.21.4、1.21.8、1.21.9、1.21.10、1.21.11),
两个(1.21.6、1.21.7)因 OptiFine 预览构建自身缺陷按用户决定放弃。全部十版由 **`v1.21.x` 项目的同一份源码**构建,
每版一个 jar(缓存格式 24),`.\gradlew -p v1.21.x build "-Pmc=<版本>"` 即可复现。
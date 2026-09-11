# 更新日志

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

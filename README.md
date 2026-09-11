# OptiFabric (Minecraft 1.20.6 / Fabric)

#!!!此模组由deepseek编写并验证请小心用于生产环境!!!#

让 **Fabric Loader** 与 **OptiFine** 在同一客户端共存。把 OptiFine 的 jar 丢进 `mods/`,OptiFabric 会在游戏启动时给原版客户端打补丁、重映射命名空间,并把结果接到 Fabric 的类加载流程里。

- 目标版本: Minecraft **1.20.6**, Fabric Loader **≥ 0.19.5**, Java 21
- 产物: `build/libs/OptiFabric-1.0-SNAPSHOT.jar`
- 许可: **MPL-2.0**(`LICENSE.txt`),核心机制移植自 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)

---

## 1. 工作原理

OptiFine 不是 Fabric 模组:它的 jar 里是**针对原版(混淆名)Minecraft 类的字节码补丁** + OptiFine 自己的类。要让它在 Fabric 下跑起来,必须完成四件事,这也是本模组在 `preLaunch` 阶段做的:

```
mods/OptiFine_1.20.6_HD_U_I9.jar
        │  ① 用 OptiFine 自带的 optifine.Patcher 给原版(混淆)客户端 jar 打补丁
        ▼
  打补丁后的 vanilla jar  (OptiFine 的补丁 + OptiFine 的类)
        │  ② LambdaRebuilder:补丁类里的 lambda(invokedynamic)指向已被搬走的原方法,需要重建
        │  ③ tiny-remapper:official(混淆) → intermediary 命名空间重映射
        ▼
  Optifine-remapped.jar
        │  ④ 拆成两部分
        ├── 非 Minecraft 类(OptiFine 自己的类/资源)──► 加进游戏 classpath
        └── net/minecraft/** 打过补丁的类 ──────────► ClassCache(替换用)
```

替换通过 **Fabric Loader 自己的 GameTransformer** 完成:Minecraft 类被加载时,Loader 的 `KnotClassDelegate.getPreMixinClassByteArray` 会先问游戏 provider 的 `GameTransformer.transform(类名)` 有没有现成的字节码 —— 这一步**在 Mixin 之前**。所以 OptiFabric 在 preLaunch 阶段把打过补丁的 MC 类(先应用 `patcher/fixes` 的版本修正、再对齐访问级别)直接放进那个 transformer 的 `patchedClasses` 里,类加载时就会被顶替;Loader 自己补过的类(客户端 brand、入口类)保持 Loader 的版本不动。

这样做的好处是:不需要为每个补丁类动态生成 stub mixin,也不依赖 Mixin 的扩展 API —— 而且因为交出去的是 Mixin 的**输入**而不是输出,其它模组针对这些类的 mixin 仍然照常生效。

所有中间产物缓存在 `<游戏目录>/.optifine/<OptiFine 版本>/`:

| 文件 | 内容 |
|---|---|
| `Optifine-mapped.jar` | 重映射后的 OptiFine(不含 MC 类),这就是加进 classpath 的 jar |
| `Optifine.classes.gz` | 打过补丁的 MC 类缓存(ClassCache),以及源 jar 的 MD5,用于判断是否需要重建 |

---

## 2. 使用

1. **准备 OptiFine**:下载与当前 MC 版本**严格一致**的 OptiFine(1.20.6)。OptiFabric 会读取 jar 内 `optifine/Config` 的 `MC_VERSION` 校验,不一致会直接在标题界面报错。安装器形态(`preview_OptiFine_...jar`,含 `patch/`)和已解包的模组形态(`OptiFine-...jar`,含 `notch/<混淆名>.class`)都支持。
2. **编译**(需要联网下载依赖,或本地已有 Gradle/Loom 缓存):
   ```
   gradlew build
   ```
3. **安装**:把 `build/libs/OptiFabric-1.0-SNAPSHOT.jar` 和 OptiFine 的 jar 一起放进 **该 Fabric 版本自己的 `mods` 目录**。**不要**同时放两份 OptiFine(会报 `DUPLICATED`)。
   - PCL2/HMCL 若开启了**版本隔离**,游戏目录是 `versions/<版本名>/`,mods 目录也在那里;`.optifine/` 缓存同样会建在版本目录下。没开隔离才是 `.minecraft/mods`。
   - 用 **Fabric 版本**启动,不要用启动器装的 `1.20.6-OptiFine_xxx` 版本(那个是启动器自己在启动时注入 OptiFine,会和本模组重复)。
4. **启动**:首次启动会多花几秒做补丁+重映射(日志里会看到 `[OptiFabric]` 前缀的输出),之后走缓存。成功的标志:标题界面左下角出现 OptiFine 版本号,视频设置里出现 OptiFine 选项。

### 支持的启动环境

目前**只支持生产环境**(runtime namespace = `intermediary`),也就是用正常启动器(官方启动器 + Fabric Loader,或 Prism/MultiMC 等)启动装好的 jar。

在开发环境里直接 `gradlew runClient` 会明确报错并被拒绝(dev 的命名空间是 `named`,需要额外的 contextual mapping 层,见"已知限制")。

---

## 3. 与上游 OptiFabric 的差异

| 方面 | 上游 (≤1.20.4, Loader 0.15) | 本移植 (1.20.6, Loader 0.19.3) |
|---|---|---|
| 类替换挂钩 | Fabric-ASM / Manningham Mills(`mm:early_risers` 入口 + `ClassTinkerers` + 运行时生成 stub mixin) | **自实现**:注入 Loader 的 `GameTransformer.patchedClasses`(`GameTransformerHook`),全程不碰 Mixin API |
| OptiFine jar 上 classpath | Fabric-ASM 反射式 `addURL` | Fabric Loader 自带 API `FabricLauncherBase.getLauncher().addToClassPath(...)` |
| 重映射器 | 自己依赖 `net.fabricmc:tiny-remapper:0.8.11` | 直接用 **Loader 内嵌的 tiny-remapper**(`net.fabricmc.loader.impl.lib.tinyremapper`,0.14 API),不额外打包依赖 |
| 映射表 | 构建期把 mappings 打进 jar | 同样:构建期把 `net.fabricmc:intermediary:1.20.6:v2` 的 `mappings/mappings.tiny` 打进去 |
| 每 mod 兼容 mixin | 数十个(`compat/**`,针对 fabric-api / architectury / apoli …) | **未包含**(它们依赖 MM 的 early riser 机制) |
| contextual mapping | 有:人工维护的硬编码表,按版本手写(`this$0`/`this$1`/`field_3835` 等) | **改为规则推导**:`OptifineMappings` 按字段名形状 + 描述符匹配,自动对齐名字、类型与构造器里存入的值 |
| 版本特定补丁修正 | 面向 1.20.4 等 | **11 个 fixer**(`patcher/fixes`,其中 3 个为本移植新增),已用离线验证器(JVM + ASM 双向)与真机逐项验证 |

新增或重写的文件(其余文件为逐行移植,仅改包名与必要的 API 适配;凡不在上游存在的文件,其文件头都会注明 "New in this 1.20.6 port"):

```
kynarain/cn/optifabric/Optifabric.java                入口(preLaunch)
kynarain/cn/optifabric/mod/OptifabricRuntime.java     总调度:找 jar → 打补丁 → 挂 classpath → 注册替换
kynarain/cn/optifabric/mod/GameTransformerHook.java   把补丁类注入 Loader 的游戏 transformer(按字段类型反射定位)
kynarain/cn/optifabric/mod/OptifineMappings.java      取代上游硬编码 contextual mapping 的规则推导
kynarain/cn/optifabric/mod/OptifineRuntime.java       准备结果(remapped jar + ClassCache)
kynarain/cn/optifabric/mod/OptifabricSetup.java       仅保留 optifineRuntimeJar(供崩溃报告用)
kynarain/cn/optifabric/patcher/fixes/DelegatingConstructorFix.java    重写 OptiFine 的委托构造器
kynarain/cn/optifabric/patcher/fixes/SyntheticFieldFix.java           this$0/this$1 → 真实字段名
kynarain/cn/optifabric/patcher/fixes/ObjectCreationPointFix.java      补回被换掉的 NEW 注入点
```

---

## 4. 已知限制与排查

### 验证情况

移植版是在真实游戏里逐轮排查出来的:每一处 Fabric API 与 OptiFine 的结构冲突,都先在**离线复现的补丁管线**上定位到具体字节码,再用 JVM 验证器与 ASM 数据流验证器双向确认,最后才交给真机验证。当前状态:

| 项目 | 结果 |
|---|---|
| 离线字节码校验(与游戏一致的单一加载器) | **425 / 425 通过**,0 失败 |
| ASM 数据流验证器 | **0 问题** |
| mixin 引用 / `@At` 注入点 / 局部变量捕获 / `@Shadow` 成员 / 未初始化字段扫描 | 全部通过(仅剩一处被有意中和的 indigo 注入点,见"与 OptiFine 的冲突") |
| 真机验证 | 带 Fabric API 进主界面、开单人存档、进多人服务器、模型与区块渲染、光影生效 |

逐轮排查过程、9 类冲突的根因与修法、以及可复现的离线校验工具(`VerifyPatched`、`RefmapScan`、`AtTargetScan`、`LocalsScan`、`ShadowScan`、`UnsetFieldScan`)记录在 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)。

### 已确认的第三方模组不兼容

**RyoamicLights(动态光源)—— 与 OptiFine 冲突,开视频设置界面即崩**:

```
Mixin transformation of net.minecraft.class_446 failed
Caused by: InjectionError: Delegate constructor lookup failed for @Inject target on
   ryoamiclights.fabric.mixins.json:VideoOptionsScreenMixin
```

原因是 **OptiFine 把原版 `VideoOptionsScreen`(`class_446`)整个换成了自己的实现,连父类都换掉了**:

```
原版  : public class class_446 extends net.minecraft.class_4667        (GameOptionsScreen)
OptiFine: public class class_446 extends net.optifine.gui.GuiScreenOF  (extends class_437/Screen)
```

这不是移植补丁造成的,原始 OptiFine jar 里就是这么写的 —— 从 `mods/OptiFine-*.jar` 里把对应类取出来看,它自己声明为 `Compiled from "VideoSettingsScreen.java"`,`extends net.optifine.gui.GuiScreenOF`。而 RyoamicLights 的 mixin 类继承的是**原版父类** `class_4667`,Mixin 因此找不到它期待的委托构造器,整个类的变换失败。

- **处理办法**:删掉 RyoamicLights。**不会损失任何功能** —— OptiFine 自带动态光源,实测配置里本来就是开着的(`optionsof.txt` 里 `ofDynamicLights:1`,位置:视频设置 → 品质 → 动态光源)。
- 已核对:`mods/` 里其余模组(litematica、malilib、ObsidianUI、trade-cycling、VisibleTraders 等)都**不引用** `VideoSettingsScreen`,不会撞同一个坑。
- 理论上唯一的规避方式是让 OptiFabric **不补丁 `class_446`** —— 那样用的就是原版视频设置界面(RyoamicLights 可用),但 **OptiFine 的游戏内设置界面会消失**(没有 Shaders/品质 按钮,只能手改 `optionsof.txt` / `optionsshaders.txt`),因此不推荐。

更一般地:凡是往 OptiFine **整类替换**的界面类里注入的模组,都可能以同样方式失败。日志出现 `Delegate constructor lookup failed` 或 `Mixin transformation of <类> failed` 时,先看 OptiFine 是否换掉了那个类的父类。

### 已知限制

- 依赖 FRAPI/indigo 的模组不再有 indigo 的自定义渲染(地形交给 OptiFine;见"与 OptiFine 的冲突")。
- OptiFine 不认识 Fabric 的资源包类型(日志里成片的 `[OptiFine] Unknown resource pack type: ...ModNioResourcePack`),所以**Fabric 模组内部的资源(贴图/CTM 配置等)OptiFine 看不到**。这是 OptiFine 侧的限制,不影响启动与运行。
- 光影包声明了 OptiFine 1.20.6 不使用/不支持的程序名时会有 `[Shaders] Invalid program name: ...` 报错(实测 Photon 的 `dh_water`、`gbuffers_particles*`),属光影包与 OptiFine 版本的匹配问题,与移植无关。

**第十三轮(多人服务器里两秒后掉线,以及"移植修复器把字段初始化改没了"这一整类问题)**:进多人服务器能看到世界,但约两秒后提示"网络协议错误"并断开。日志给出了完整链路:

```
NullPointerException: Cannot invoke "net.optifine.render.RenderEnv.reset(class_2680, class_2338)" because "renderEnv" is null
  at class_702.updateTerrainParticleColor(class_702.java:799)
  at class_702.lambda$addBlockDestroyEffects$13(class_702.java:659)
  at class_702.method_3046(...)          ← ParticleManager.addBlockBreakParticles
  at class_638.method_31595(...)         ← ClientWorld 处理"方块被破坏"世界事件
  at ... class_2600.lambda$checkThreadAndEnqueue$0
```

即**别的玩家打掉一个方块** → 服务器发世界事件 → 客户端粒子路径 → OptiFine 的 `renderEnv` 是 null → 异常抛在**网络包处理**里 → 客户端按协议错误断开。所以是"进去两秒后"(等别人挖方块),而不是连接本身有问题。

根因在移植的 `ParticleManagerFix`:它把 `class_702` 的 4 个方法(含**构造器**)换成原版实现,而 OptiFine 的构造器是 `renderEnv` 字段**唯一的初始化处**:

```
OptiFine 原始 <init>:  ... new RenderEnv; <init>(BlockState, BlockPos); putfield renderEnv
我们发出的  <init>:    ... 没有这条赋值   → putfield renderEnv 0 次,getfield renderEnv 2 次
```

当年上游要替换构造器,是因为 OptiFine 把工厂表字段声明成了普通 `Map` + `HashMap`;而这个移植版的 `OptifineMappings` 已经能**把字段类型和构造器里存入的值一起对齐**(`new Int2ObjectOpenHashMap` → `putfield field_3835:Int2ObjectMap`,实测生效),所以替换构造器成了**多余且有害**的一步 —— 已删除。改完:

```
putfield renderEnv : 0 -> 1        field_3835 仍然存入 Int2ObjectOpenHashMap
verified OK: 425 / FAILED: 0 / ASM verifier problems: 0
```

（若值改写没生效,把 `HashMap` 存进 `Int2ObjectMap` 字段会直接被验证器拒绝,所以这一步是自检的。）

**这一整类问题的通用检查**:新增 `test-downloads/UnsetFieldScan.java` —— 逐个类比较"OptiFine 原始字节码里给哪些字段赋值"与"我们发出的字节码里还给哪些字段赋值",报告**被读取但从未被赋值**的字段(也就是运行时必 NPE 的地雷)。当前结果:

```
classes compared: 425      fields OptiFine assigns: 3017
UNINITIALISED BUT READ: 0
```

也就是说 `renderEnv` 是唯一一处;被替换掉的其他方法(`createParticle`、两个 `registerFactory`,以及 `RestoreVanillaMethodsFix` 恢复的那几个方法)都没有丢掉 OptiFine 的字段初始化。

**仍未深入验证的**:OptiFine 各项功能的具体效果(连接纹理、缩放、动态光源、FPS 优化幅度等)。启动、进世界、模型与区块渲染、光影子系统均已确认工作。启动本身已确认,`[Shaders]` 子系统也已初始化,但具体功能还需要实际用一用。


### 排查手段

- 日志过滤 `[OptiFabric]`,可以看到准备过程、准备了多少个补丁类、Loader 接管了多少个。
- 标题界面会弹错误对话框:缺 OptiFine / jar 损坏 / 版本不匹配 / 多份 OptiFine / 内部错误,并给出 "打开 mods 文件夹 / 复制堆栈 / 打开 issues" 按钮。
- 崩溃报告里会多出一节 `OptiFabric`,包含 OptiFine 版本、jar 状态、重映射 jar 路径、错误信息。
- 强制重建缓存:删掉 `<游戏目录>/.optifine/`。
- 找不到原版 jar 时(Loader 没有暴露 `fabric-loader:inputGameJar`)可以显式指定:
  `-Doptifabric.mc-jar=<原版 1.20.6 client jar 路径>`
- **游戏能起来但方块/物品模型全都不见了**,日志里是成片的 `Unable to bake model: '...': java.lang.RuntimeException: Mixin transformation of <类> failed` —— 这是某个 Fabric mixin 变换那个类失败了(最外层的消息会把真实原因吃掉,日志里通常看不到堆栈)。用 `AtTargetScan` 查那个类的注入点是否还在:OptiFine 常把原版方法改成"转发到自己重载"的瘦包装,注入点会随之搬走。实测那次是 56,042 次失败。
- 调试:`-Doptifabric.extract=true` 会把重映射后的 OptiFine 类解包到 `.optifine/<版本>/optifine-classes/`。
- 注意:`[OptiFabric]` 的输出走**启动器控制台**,不在 `logs/latest.log` 里(loader 只把 log4j 的输出写进 latest.log)。所以"日志里没有 [OptiFabric]"不等于模组没跑。

**卡住了怎么定位**(画面停在 Mojang 图标/进度条不动):

1. 先看 `logs/latest.log` 的**最后一行**在做什么,以及文件是否还在增长(不增长说明真的停了,不是在慢慢算)。
2. 取**两次**线程转储对比(JDK 自带,只读):`jstack <pid>`,间隔十几秒。
   - 两次栈完全相同、进程 CPU 几乎不涨 → 是卡死,不是慢。
   - 栈顶在**原生调用**里(例如 `GLFW.glfwSwapBuffers`)→ 呈现层问题(vsync/驱动/窗口状态),不是 Java 死锁。再查 GPU 占用:`Get-Counter '\GPU Engine(*engtype_3D)\Utilization Percentage'`;若 GPU 也空闲,就是纯粹在等呈现 —— 可先关垂直同步(`options.txt` 里 `enableVsync:false`)或试全屏/移动窗口。
   - 停在 Java 锁上、且有两个线程互相等待 → 那才是死锁,转储里会写明各自持有的 monitor。
3. 对照时间线:如果转储里**已经没有资源重载工作线程**(`Worker-Main-*`),说明加载其实完成了 —— 此时画面还停在加载界面,通常是"移除加载界面"这一步没能执行(它排在卡住的 Render 线程队列里)。
4. **先查窗口是不是最小化了**(实测踩过一次,浪费了一轮):`IsIconic(hwnd)` 为真 + `WindowRect` 在 `-32000,-32000` 就是最小化。开着垂直同步时最小化窗口的 `SwapBuffers` 会一直阻塞,Render 线程整个卡在原生 `glfwSwapBuffers` 里,表现和"游戏卡死"完全一样。还原窗口(点任务栏图标)后它会立刻继续跑帧 —— 实测还原后转储里 Render 线程就从 `glfwSwapBuffers` 变成了正常的 `RenderSystem.limitDisplayFPS` 帧循环。所以:加载期间**不要最小化游戏窗口**,或者关掉垂直同步(`options.txt` 里 `enableVsync:false`)。

### 与 OptiFine 的冲突

`sodium` 与 OptiFine 天然冲突,`fabric.mod.json` 里已声明 `conflicts`;`no_fog`、`thallium`、`xradiation` 声明为 `breaks`(与上游一致)。

`fabric-renderer-indigo`(Fabric API 自带的地形渲染器)与 OptiFine 只能有一个在场,移植版用 Fabric 自己的机制让 indigo 让位(见上文第七轮):`fabric.mod.json` 里声明 `"custom": {"fabric-renderer-api-v1:contains_renderer": true}`。这个键本来就**是给"另一个渲染器"用的**(Sodium 用同一个键),而 OptiFine 本身就是地形渲染器。

这不是绕过检查,而是 Fabric API 明确支持的运行状态:F3 调试界面里会显示 `[Fabric] Active renderer: none (vanilla)`(`DebugHudMixin` 先调 `hasRenderer()` 再决定输出,`RendererAccessImpl.getRenderer()` 也允许返回 null),indigo 则打印 `[Indigo] Different rendering plugin detected; not applying Indigo.`。

- **代价**:依赖 FRAPI/indigo 的模组不再有 indigo 提供的自定义渲染(地形由 OptiFine 渲染)。
- **想换回 indigo**:删掉 `fabric.mod.json` 里那个 `custom` 键并重新构建 —— 但那样 `ChunkBuilder$BuiltChunk$RebuildTask` 一加载就会因为缺失注入点而崩(见第七轮)。

---

## 5. 国内镜像(实测)

| 用途 | 地址 | 实测 |
|---|---|---|
| OptiFine 版本列表 | `https://bmclapi2.bangbang93.com/optifine/1.20.6` | ✅ 返回 1.20.6 的全部构建(I9_pre1 / J1_pre17 / J1_pre18) |
| OptiFine 下载 | `https://bmclapi2.bangbang93.com/optifine/<MC版本>/<type>/<patch>` | ✅ 302 跳到 `bmclapi1.sliveridc1.cn` 直链,实测下到 7,366,130 字节的安装器 |
| Fabric 安装信息(meta) | `https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader` | ✅ 200,BMCLAPI 代理了 fabric-meta |
| Fabric Maven 本体 | `https://maven.fabricmc.net/` | ✅ 200,国内可直连(慢,但可用);**Aliyun 公共仓库没有 Fabric 构件(404),SJTU/NJU 的 fabric-maven 路径也是 404,不要照抄网上的老地址** |
| Gradle 依赖 | 本项目已全部缓存在本机 `~/.gradle`,直接 `gradlew build --offline` 即可 | ✅ 8 秒构建成功 |

1.20.6 的 OptiFine **只有 preview 构建**——下载后文件名类似 `preview_OptiFine_1.20.6_HD_U_I9_pre1.jar`,直接丢进 `mods/` 即可(它是安装器形态,OptiFabric 会自己跑 `optifine.Patcher`)。

```powershell
# 一条命令拿到 1.20.6 OptiFine(国内直链,已验证)
curl.exe -L -o preview_OptiFine_1.20.6_HD_U_I9_pre1.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.6/HD_U_I9/pre1"
```

---

## 6. 想继续完善的方向

1. **开发环境支持**:dev 的命名空间是 `named`,需要两段式重映射(official→intermediary→named)并补回上游的 contextual mapping 冲突修正。
2. **把上游 `compat/**` 的每 mod 兼容搬回来**:需要重写 early riser 机制(现在最接近的替代是 `IMixinConfigPlugin#getMixins` 的动态 mixin 列表)。
3. **按 1.20.6 校验 `patcher/fixes`**:对着真实 OptiFine 1.20.6 + 1.20.6 反编译产物逐个核对硬编码的 intermediary id 与描述符。

---

## 7. 许可与致谢

- 本项目的核心逻辑是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**;移植文件保留了来源说明头。
- `reference/upstream/` 保存了移植所依据的上游源码快照,便于逐行比对。

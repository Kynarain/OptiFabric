# OptiFabric

#!!!此模组由deepseek编写并验证请小心用于生产环境!!!#

在 Fabric Loader 下加载 **OptiFine** 的客户端模组。把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时 OptiFabric 会用 OptiFine 自带的补丁器给原版客户端打补丁、重建被搬走的 lambda、把 OptiFine 从官方混淆名重映射到 intermediary,并把打过补丁的 Minecraft 类交给 Fabric Loader 的类转换器接管,从而让两者共存。

本分支是 **1.21.x 线**,覆盖 Minecraft 1.21 – 1.21.11(OptiFine 出过构建的全部十个版本)。26.x 线(Minecraft 26.1.2)在 `26.x` 分支上独立开发,两条线的 jar 不能互相替代。

## 支持的版本

| Minecraft | 产物 | OptiFine 构建 | 状态 |
|---|---|---|---|
| 1.21 | `OptiFabric-1.1.0+mc1.21.jar` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` | 已实测 |
| 1.21.1 | `OptiFabric-1.1.0+mc1.21.1.jar` | `OptiFine_1.21.1_HD_U_J1.jar` | 已实测 |
| 1.21.3 | `OptiFabric-1.1.2+mc1.21.3.jar` | `OptiFine_1.21.3_HD_U_J2.jar` | 已实测 |
| 1.21.4 | `OptiFabric-1.1.2+mc1.21.4.jar` | `OptiFine_1.21.4_HD_U_J3.jar` | 已实测 |
| 1.21.6 | `OptiFabric-1.1.2+mc1.21.6.jar` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` | 不开光影可用;**启用光影会崩**,见下 |
| 1.21.7 | `OptiFabric-1.1.2+mc1.21.7.jar` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` | 同上 |
| 1.21.8 | `OptiFabric-1.1.2+mc1.21.8.jar` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` | 已实测 |
| 1.21.9 | `OptiFabric-1.1.2+mc1.21.9.jar` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` | 已实测 |
| 1.21.10 | `OptiFabric-1.1.2+mc1.21.10.jar` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` | 已实测 |
| 1.21.11 | `OptiFabric-1.1.2+mc1.21.11.jar` | `OptiFine_1.21.11_HD_U_J9.jar` | 已实测 |

- mod id `optifabric`,仅客户端,要求 **Fabric Loader ≥ 0.19.5**,**Java 21+**。
- **一个 jar 只对应一个版本**:jar 里打包着该版本的 `official → intermediary` 映射表(混淆名每版不同,用错版本会把 OptiFine 重映射坏),`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本。
- OptiFine 没有发布过 1.21.2 / 1.21.5 的构建,因此没有对应产物。
- 版本号:**1.21.3 – 1.21.11 这八个是 `1.1.2`**(抗锯齿全线修复),**1.21 与 1.21.1 仍是 `1.1.0`**。1.1.2 不再改写 OptiFine 自带的 `post_effect/fxaa_of_*.json`(此前删掉它会让每次资源重载都报 `Resource not found: minecraft:post_effect/fxaa_of_2x.json`,一动抗锯齿或切光影包就失败),并修正了 1.21.9 / 1.21.10 的 FXAA 顶点着色器(游戏自 1.21.9 起用 `gl_VertexID` 绘制全屏三角形,不再提供 `Position` 顶点属性)。
- OptiFine 1.21.11 有正式发布版,一条命令即可取得:
  ```powershell
  curl.exe -L -o OptiFine_1.21.11_HD_U_J9.jar `
    "https://bmclapi2.bangbang93.com/optifine/1.21.11/HD_U/J9"
  ```
- 十个版本都跑过完整的离线校验(补丁类与 OptiFine 类的 JVM + ASM 校验、注入点与成员引用等扫描器);1.1.2 这一轮八个产物逐个重跑,均无警告。

### 1.21.6 / 1.21.7 的光影限制

这两版**可以正常启动**(不开光影时标题界面正常渲染、无崩溃报告),但**只要启用光影包**,游戏就会在启动阶段崩溃:

```
java.lang.NullPointerException: Cannot read field "norm" because "multiTex" is null
  at net.optifine.shaders.ShadersTex.initDynamicTextureNS(ShadersTex.java:322)
  at net.minecraft.class_1043.method_71142 -> class_1043.<init> -> class_310.<init>
```

- 与光影包无关:三个互不相同的包(Complementary Reimagined、Sildur's Vibrant Shaders、BSL)崩在同一个栈;把包里的自定义纹理声明与动画元数据全部删掉再重打包,同样崩。
- 崩溃点在创建第一批纹理时,早于任何与具体光影包相关的逻辑,所以触发条件就是"光影被启用"。
- 根因在 **OptiFine 这两版的预览构建自身**:它给 `class_1043.<init>` 插入的调用缺少前置的 `setParentTexture` 关联,而被调用的 `initDynamicTextureNS` 会直接解引用 `getMultiTexID()` 的结果。这两版可用的 OptiFine 构建共七个,全部崩在同一个栈,降级到更早的 preview 不能规避。
- 不启用光影时两版均可正常启动,1.21.7 已实测可进入世界(集成服务器、区块构建与保存均正常)。

## 安装

1. 准备与本版本**严格一致**的 OptiFine(对应构建见上表)。OptiFabric 会读取 jar 内 `optifine/Config` 的 `MC_VERSION` 做校验,不一致会直接在标题界面报错。安装器形态(含 `patch/` 差分包)与解包形态(含 `notch/<混淆名>.class`)都支持,直接丢进 `mods/` 即可,**不需要**先运行 OptiFine 安装器。
2. 把**对应版本**的 jar 与 OptiFine 的 jar 一起放进该 Fabric 版本自己的 `mods/` 目录。不要放两份 OptiFine(会报 `DUPLICATED`),不要放错版本的 OptiFabric,也不要拿本线的 jar 去跑 26.1.2(或反过来)。
3. 用 **Fabric 版本**启动,不要用启动器注入 OptiFine 的 `1.21.x-OptiFine_xxx` 版本(那个是启动器在启动时注入 OptiFine,会与本模组重复)。
4. 首次启动会明显变慢(要跑完整的补丁与重映射流程,实测 5–7 秒),之后走缓存(1–2 秒)。标题界面出现 OptiFine 版本号、视频设置里出现 OptiFine 选项即表示成功。

PCL2 / HMCL 开启版本隔离时,游戏目录与 `mods/` 都在 `versions/<版本名>/` 下,`.optifine/` 缓存也建在那里;没开隔离才是 `.minecraft/mods`。

## 构建

需要 **JDK 21+**。仓库根目录就是 Gradle 项目,目标版本由 `-Pmc` 指定(不带则用 `gradle.properties` 里的默认版本):

```powershell
.\gradlew build "-Pmc=1.21.11"                 # PowerShell 里要加引号,否则 1.21.11 会被拆开
.\gradlew build "-Pmc=1.21.8" "-Pmod_version_base=1.1.2"   # 1.1.2 那八个产物要连版本号一起给
```

产物为 `build/libs/OptiFabric-<版本>+mc<MC版本>.jar`,例如 `OptiFabric-1.1.2+mc1.21.8.jar`。版本号只通过 `.\release\version.ps1` 修改。

开发环境不受支持:`gradlew runClient` 会被明确拒绝,因为开发环境的命名空间是 `named`,需要额外的 contextual mapping 层。

## 工作原理

OptiFine 不是 Fabric 模组:它的 jar 里是针对原版(混淆)客户端类的字节码补丁,加上 OptiFine 自己的类。本模组在 `preLaunch` 阶段完成四件事:

```
mods/<OptiFine>.jar
        │  ① 用 OptiFine 自带的 optifine.Patcher 给原版(混淆)客户端 jar 打补丁
        │     (1.21.6 起的 OptiFine 用自己的 xdelta 差分包,但 Patcher.process 的用法没变)
        │  ② LambdaRebuilder:补丁类里的 lambda(invokedynamic)指向已被搬走的原方法,需要重建
        │  ③ tiny-remapper:official(混淆) → intermediary 重映射
        │     必须把游戏 jar 一起放进重映射器的 classpath,否则子类里覆写的方法继承不到映射
        ▼
  Optifine-mapped.jar
        │  ④ 拆成两部分
        ├── 非 Minecraft 类(OptiFine 自己的类与资源)──► 加进游戏 classpath
        └── net/minecraft/** 打过补丁的类 ──────────► ClassCache(替换用)
```

类替换走 **Fabric Loader 自己的 GameTransformer**:Minecraft 类被加载时,Loader 会先问游戏 provider 的 `GameTransformer.transform(类名)` 有没有现成的字节码,而这一步发生在 Mixin **之前**。OptiFabric 在 preLaunch 阶段把打过补丁的 MC 类(先经过 `patcher/fixes` 的版本修正)放进该 transformer 的 `patchedClasses`,类加载时即被顶替;Loader 自己补过的类保持 Loader 的版本。

因此不需要为每个补丁类生成 stub mixin,也不依赖 Mixin 的扩展 API;交出去的是 Mixin 的**输入**而非输出,其它模组针对这些类的 mixin 照常生效。

一条硬性约束:**在补丁类交给 Loader 之前,不能对游戏类做任何反射解析**(例如 `Class.getMethods()` 会把方法签名里的游戏类型全部加载掉),否则这些类会被永久钉成原版。这段代码只允许接触字节(`getClassByteArray` / ASM),不允许持有 `Class` 对象。

中间产物缓存在 `<游戏目录>/.optifine/<OptiFine 版本>/`:

| 文件 | 内容 |
|---|---|
| `cache-format.txt` | 缓存格式版本(当前 `26`),与代码不一致就整份重建 |
| `Optifine-mapped.jar` | 重映射后的 OptiFine(不含 MC 类),这就是加进 classpath 的 jar |
| `Optifine.classes.gz` | 打过补丁的 MC 类缓存(ClassCache),供下次启动复用 |

## 已知限制

- **与 Sodium 不兼容**:两者都是渲染器,`fabric.mod.json` 已声明 `conflicts`。`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 同样声明为不兼容。RyoamicLights 的具体原因是 OptiFine 把原版视频设置界面**整类替换成自己的实现,连父类都换掉**,而它的 mixin 注入在原版父类上;删掉它不会损失功能,OptiFine 自带动态光源。
- **两条 Fabric API 钩子被有意中和**:"方块描边"(`BEFORE_BLOCK_OUTLINE` 事件不再触发,方块描边仍照画)与"移动方块的 FRAPI 渲染钩子"(移动方块由原版路径正常渲染)。
- **依赖 FRAPI/indigo 的模组没有 indigo 提供的自定义渲染**:地形由 OptiFine 渲染,`Renderer.get()` 拿到的是惰性占位实现,真去用它建网格会得到一句明确说明的 `UnsupportedOperationException`。26.1.2 上情况不同(那一线的 indigo 已不是地形渲染器)。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现成片的 `[OptiFine] Unknown resource pack type: ...ModNioResourcePack`,属于 OptiFine 侧的限制,不影响启动与运行。
- **光影包与 OptiFine 版本不匹配时会报 `[Shaders]` 错误**(程序名无效、宏表达式不符、变量不存在等),属于光影包自身问题。

### 与 indigo 的关系

`fabric-renderer-indigo`(Fabric API 自带的地形渲染器)与 OptiFine 只能有一个在场,本模组用 Fabric 自己的机制让 indigo 让位:`fabric.mod.json` 里声明 `"custom": {"fabric-renderer-api-v1:contains_renderer": true}`。这个键本来就是给"另一个渲染器"用的(Sodium 用同一个键),而 OptiFine 本身就是地形渲染器。indigo 会打印 `[Indigo] Different rendering plugin detected; not applying Indigo.`。

- 只声明这个键还不够:Fabric 的渲染器模块查的是注册表,为空时会抛 `Attempted to retrieve active rendering plug-in before one was registered`,所以本模组另外注册了惰性占位渲染器(F3 调试界面显示 `Renderer: OptifineRendererPlaceholder`)。
- **想换回 indigo**:删掉 `fabric.mod.json` 里那个 `custom` 键并重新构建 —— 但那样 `ChunkBuilder$BuiltChunk$RebuildTask` 一加载就会因为缺失注入点而崩。

## 常见日志信息

下列输出不影响运行:

| 日志 | 说明 |
|---|---|
| `[OptiFine] (Reflector) Class not present: net.minecraftforge.*` / `sun.misc.SharedSecrets` | OptiFine 在探测 Forge 与旧 JDK 的类,Fabric 上本就没有 |
| `Failed to locate initialiser injection point in <init>(class_2591,...)` | 应用 OptiFine 的 `BlockEntity` 补丁的代价(跳过它会留下 5 处悬空引用) |
| `[Shaders] Unknown macro value: IRIS_VERSION` / `ANGELICA_VERSION` | 光影包在探测 Iris / Angelica,OptiFine 不认这两个宏 |
| `[Shaders] Invalid macro expression` 等 | 光影包宏表达式与 OptiFine 解析器不符 |
| `[Shaders] ParseException: Model variable not found: ...` | 光影包引用了 OptiFine 尚未提供的变量 |
| `[OptiFabric] Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` | OptiFine 仍会到 1.21.6 之前的老位置探测一次抗锯齿链,纯探测警告 |
| `Skipping bad option: lastServer` | 选项文件里的旧字段 |

## 排查

- **升级本模组后行为没有变化**:先删掉 `<游戏目录>/.optifine/`,缓存里存的是打过补丁的字节码。
- `[OptiFabric]` 的输出走**启动器控制台**,通常不在 `logs/latest.log` 里;"日志里没有 `[OptiFabric]`"不代表模组没运行。过滤 `[OptiFabric]` 可以看到准备了多少补丁类、Loader 接管了多少。
- 标题界面会弹错误对话框(缺 OptiFine / jar 损坏 / 版本不匹配 / 多份 OptiFine / 内部错误),并提供打开 mods 目录、复制堆栈等按钮;崩溃报告里会多出一节 `OptiFabric`。
- Loader 没有暴露 `fabric-loader:inputGameJar` 时,可显式指定原版 jar:`-Doptifabric.mc-jar=<原版 client jar 路径>`。
- 调试:`-Doptifabric.extract=true` 会把重映射后的 OptiFine 类解包到 `.optifine/<版本>/optifine-classes/`。
- **模型/物品/贴图成片消失**,或日志里成片的 `Unable to bake ... model` 时:方向是某个 Fabric mixin 变换那个类失败(最外层消息常把真实原因吃掉)。OptiFine 会把原版方法改成转发给自己重载的瘦包装,注入点会随之搬走。
- **卡在加载界面**:取两次线程转储(`jstack <pid>`,间隔十几秒)对比。两次栈相同、CPU 不涨即为卡死;栈顶停在原生调用(如 `glfwSwapBuffers`)属于呈现层问题,注意加载期间不要最小化窗口(开着垂直同步时最小化会让 Render 线程一直阻塞)。

## 后续计划

1. 支持开发环境(dev 命名空间是 `named`,需要两段式重映射并补回上游的 contextual mapping 修正)。
2. 把被中和的两个 Fabric API 钩子换成真正可用的实现(例如让 `Renderer.get()` 返回能落到原版渲染路径上的实现)。
3. 1.21.6 / 1.21.7 的光影缺陷:等 OptiFine 出新构建,或让已写好但尚未接线的 `GpuTextureLinkFix` 上线。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE.txt`),核心逻辑移植自 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead),移植文件保留来源说明。
- **不包含、也不分发 OptiFine 本体**,OptiFine 版权归 sp614x 所有,请自行获取。
- 各版本差异、逐轮排查过程与离线校验工具见 `docs/DEVELOPMENT.md`,版本号规则见 `docs/VERSIONING.md`。

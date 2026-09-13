# OptiFabric Reforged

#!!!此模组由deepseek编写并验证请小心用于生产环境!!!#

在 Fabric Loader 下加载 **OptiFine** 的客户端模组。把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时 OptiFabric 会用 OptiFine 自带的补丁器给原版客户端打补丁、重建被搬走的 lambda,并把打过补丁的 Minecraft 类直接交给 Fabric Loader 的类转换器接管,从而让两者共存。

本仓库是 **26.x 线**,对应 **Minecraft 26.1.2**。1.21.x 线(Minecraft 1.21 – 1.21.11)在 `1.21.x` 分支上独立开发,两条线的 jar 不能互相替代。

## 支持的版本

| Minecraft | 产物 | OptiFine 构建 | Java |
|---|---|---|---|
| 26.1.2 | `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` | 25 |

- mod id `optifabric_reforged`,仅客户端,要求 **Fabric Loader ≥ 0.19.5**;**Java 25** 是 26.1.2 自身的硬性要求。
- 26.1 起游戏**未混淆**:官方名即运行名,既没有 yarn,也没有真正的 intermediary 可重映射。本线因此不重映射、jar 里也不打包映射表,运行期命名空间是 `official`。
- 26.1.2 之外的版本(26.1 的其他小版本、26.2+)需要各自重新移植。OptiFine 官方目前只发布过 26.1.2 的 preview 构建,可用下面的命令自查(判断依据是返回的数组是否为空):
  ```powershell
  curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.1.2"   # 列出 pre1 / pre2
  curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.2"     # -> []
  ```
- 实测搭配:Fabric API `0.155.3+26.1.2` + OptiFine `preview_OptiFine_26.1.2_HD_U_K1_pre2`。
- 26.1.2 上的启动、进世界、区块重建、方块/物品/生物渲染、抗锯齿、光影与多人均已实机验证。

## 安装

1. 准备与本版本**严格一致**的 OptiFine(见上表)。OptiFabric 会读取 jar 内 `optifine/Config` 的 `MC_VERSION` 做校验,不一致会直接在标题界面报错。安装器形态(含 `patch/` 差分包)与解包形态(含 `notch/`)都支持,直接丢进 `mods/` 即可,**不需要**先运行 OptiFine 安装器。
2. 把本模组的 jar 与 OptiFine 的 jar 一起放进该 Fabric 版本自己的 `mods/` 目录。不要放两份 OptiFine(会报 `DUPLICATED`),也不要放另一条线的 OptiFabric。
3. 用 **Fabric 版本**启动,不要用启动器注入 OptiFine 的那个版本。
4. 首次启动会明显变慢(要跑完整的补丁流程),之后走缓存。标题界面出现 OptiFine 版本号、视频设置里出现 OptiFine 选项即表示成功。

PCL2 / HMCL 开启版本隔离时,游戏目录与 `mods/` 都在 `versions/<版本名>/` 下,`.optifine/` 缓存也建在那里。

## 构建

需要 **JDK 25**。仓库根目录就是 Gradle 项目:

```powershell
.\gradlew build
```

产物为 `build/libs/OptiFabric-Reforged-2.0.0+mc26.1.2.jar`。版本号与 Minecraft 版本成对出现,只通过 `.\release\version.ps1` 修改。

开发环境不受支持:`gradlew runClient` 会被明确拒绝,因为开发环境的命名空间是 `named`,需要额外的映射层。

## 工作原理

OptiFine 不是 Fabric 模组:它的 jar 里是针对原版客户端类的字节码补丁,加上 OptiFine 自己的类。本模组在 `preLaunch` 阶段完成以下步骤(26.x 无第 ③ 步):

```
mods/<OptiFine>.jar
        │  ① 用 OptiFine 自带的 optifine.Patcher 给原版客户端 jar 打补丁
        │  ② LambdaRebuilder:补丁类里的 lambda(invokedynamic)指向已被搬走的原方法,需要重建
        │  ③ tiny-remapper:official → intermediary 重映射(仅 1.21.x 线需要)
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
| `Optifine-mapped.jar` | 处理后的 OptiFine(不含 MC 类),这就是加进 classpath 的 jar |
| `Optifine.classes.gz` | 打过补丁的 MC 类缓存(ClassCache),供下次启动复用 |

## 已知限制

- **与 Sodium 不兼容**:两者都是渲染器,`fabric.mod.json` 已声明 `conflicts`。`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 同样声明为不兼容。
- **两条 Fabric 渲染钩子被有意停用**:"移动方块提交"与"方块模型提交"(由 `StubInjectionTargetFix` 注入到不被调用的副本里)。依赖 `fabric-renderer-api-v1` 的模组所生成的几何由 `OptifineFrapiBridge` 转交 OptiFine 写入,其余路径由原版/OptiFine 正常渲染。
- **不声明 indigo 让位键**:26.1.2 上 indigo 已不是地形渲染器(地形与提交节点的整合搬进了 `fabric-renderer-api-v1` 自身),因此本线不声明 `fabric-renderer-api-v1:contains_renderer`,由 Indigo 注册真正的渲染器;`RendererApiFallback` 只在键确实被声明时才补占位渲染器。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现成片的 `[OptiFine] Unknown resource pack type: ...ModNioResourcePack`,属于 OptiFine 侧的限制,不影响启动与运行。
- **光影包与 OptiFine 版本不匹配时会报 `[Shaders]` 错误**,属于光影包自身问题。

## 常见日志信息

下列输出不影响运行:

| 日志 | 说明 |
|---|---|
| `[OptiFine] (Reflector) Class not present: net.minecraftforge.*` / `sun.misc.SharedSecrets` | OptiFine 在探测 Forge 与旧 JDK 的类,Fabric 上本就没有 |
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
- 调试:`-Doptifabric.extract=true` 会把处理后的 OptiFine 类解包到 `.optifine/<版本>/optifine-classes/`。
- **卡在加载界面**:取两次线程转储(`jstack <pid>`,间隔十几秒)对比。两次栈相同、CPU 不涨即为卡死;栈顶停在原生调用(如 `glfwSwapBuffers`)属于呈现层问题,注意加载期间不要最小化窗口(开着垂直同步时最小化会让 Render 线程一直阻塞)。
- **模型/物品/贴图成片消失**:方向是某个 Fabric mixin 变换类失败(最外层消息常把真实原因吃掉)。OptiFine 会把原版方法改成转发给自己重载的瘦包装,注入点会随之搬走。

## 后续计划

1. 支持开发环境(dev 命名空间是 `named`,需要两段式重映射)。
2. 把被停用的两个 Fabric API 钩子换成真正可用的实现。
3. 移植到 26.1.2 之外的版本(需要针对新的官方名逐个重新定位冲突点)。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE.txt`),核心逻辑移植自 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead),移植文件保留来源说明。
- **不包含、也不分发 OptiFine 本体**,OptiFine 版权归 sp614x 所有,请自行获取。
- 移植记录与逐轮排查过程见 `docs/PORT_26.x.md` 与 `docs/DEVELOPMENT.md`,版本号规则见 `docs/VERSIONING.md`。

# OptiFabric

#!!!此模组由deepseek编写并验证请小心用于生产环境!!!#

在 Fabric Loader 下加载 **OptiFine** 的客户端模组。把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时 OptiFabric 会用 OptiFine 自带的补丁器给原版客户端打补丁、重建被搬走的 lambda、把 OptiFine 从官方混淆名重映射到 intermediary,并把打过补丁的 Minecraft 类交给 Fabric Loader 的类转换器接管,从而让两者共存。

本分支是 **1.20.6 线**。更新的版本(1.21.x、26.x)在各自的分支上独立开发,各线的 jar 不能互相替代。

## 支持的版本

| Minecraft | 产物 | OptiFine 构建 | Java |
|---|---|---|---|
| 1.20.6 | `OptiFabric-1.0.0+mc1.20.6.jar` | `preview_OptiFine_1.20.6_HD_U_I9_pre1.jar` | 21 |

- mod id `optifabric`,仅客户端,要求 **Fabric Loader ≥ 0.19.3**。
- 一个 jar 只对应一个版本:jar 里打包着该版本的 `official → intermediary` 映射表(混淆名每版不同,用错版本会把 OptiFine 重映射坏),`fabric.mod.json` 里的 `minecraft` 依赖也精确到该版本。
- 1.20.6 的 OptiFine **只有 preview 构建**,下载后直接丢进 `mods/` 即可(它是安装器形态,OptiFabric 会自己运行 `optifine.Patcher`):
  ```powershell
  curl.exe -L -o preview_OptiFine_1.20.6_HD_U_I9_pre1.jar `
    "https://bmclapi2.bangbang93.com/optifine/1.20.6/HD_U_I9/pre1"
  ```
- 离线字节码校验 **425 / 425 通过**、ASM 数据流验证器 0 问题;真机验证:进入主界面、单人存档、多人服务器、模型与区块渲染、光影生效。

## 安装

1. 准备与本版本**严格一致**的 OptiFine(1.20.6)。OptiFabric 会读取 jar 内 `optifine/Config` 的 `MC_VERSION` 做校验,不一致会直接在标题界面报错。安装器形态(含 `patch/` 差分包)与解包形态(含 `notch/<混淆名>.class`)都支持,直接丢进 `mods/` 即可,**不需要**先运行 OptiFine 安装器。
2. 把本模组的 jar 与 OptiFine 的 jar 一起放进该 Fabric 版本自己的 `mods/` 目录。不要放两份 OptiFine(会报 `DUPLICATED`)。
3. 用 **Fabric 版本**启动,不要用启动器注入 OptiFine 的 `1.20.6-OptiFine_xxx` 版本(那个是启动器在启动时注入 OptiFine,会与本模组重复)。
4. 首次启动会明显变慢(要跑完整的补丁与重映射流程),之后走缓存。标题界面出现 OptiFine 版本号、视频设置里出现 OptiFine 选项即表示成功。

## 构建

需要 **JDK 21**。仓库根目录就是 Gradle 项目:

```powershell
.\gradlew build
```

产物为 `build/libs/OptiFabric-1.0.0+mc1.20.6.jar`。

开发环境不受支持:`gradlew runClient` 会被明确拒绝,因为开发环境的命名空间是 `named`,需要额外的 contextual mapping 层。

## 工作原理

OptiFine 不是 Fabric 模组:它的 jar 里是针对原版客户端类的字节码补丁,加上 OptiFine 自己的类。本模组在 `preLaunch` 阶段完成四件事:

```
mods/<OptiFine>.jar
        │  ① 用 OptiFine 自带的 optifine.Patcher 给原版(混淆)客户端 jar 打补丁
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
| `cache-format.txt` | 缓存格式版本,与代码不一致就整份重建 |
| `Optifine-mapped.jar` | 重映射后的 OptiFine(不含 MC 类),这就是加进 classpath 的 jar |
| `Optifine.classes.gz` | 打过补丁的 MC 类缓存(ClassCache),供下次启动复用 |

## 已知限制

- **与 Sodium 不兼容**:两者都是渲染器,`fabric.mod.json` 已声明 `conflicts`。`no_fog`、`thallium`、`xradiation`、`ryoamiclights` 同样声明为不兼容。
- **RyoamicLights 的具体冲突**:OptiFine 把原版视频设置界面(`class_446`)**整类替换成自己的实现,连父类都换掉**,而 RyoamicLights 的 mixin 注入在原版父类上,于是变换失败(`Delegate constructor lookup failed`)。这是 OptiFine 自身的行为,不是补丁造成的。删掉它不会损失功能 —— OptiFine 自带动态光源(视频设置 → 品质 → 动态光源)。更一般地,凡是往 OptiFine 整类替换的界面类里注入的模组都可能同样失败。
- **OptiFine 看不到 Fabric 模组内部的资源**:日志里会出现成片的 `[OptiFine] Unknown resource pack type: ...ModNioResourcePack`,属于 OptiFine 侧的限制,不影响启动与运行。
- **光影包与 OptiFine 版本不匹配时会报 `[Shaders] Invalid program name: ...`**(例如 Photon 的 `dh_water`、`gbuffers_particles*`),属于光影包自身问题。
- OptiFine 各项功能的具体效果(连接纹理、缩放、动态光源、FPS 优化幅度)尚未逐项验证;启动、进世界、模型与区块渲染、光影子系统已确认工作。

### 与 indigo 的关系

`fabric-renderer-indigo`(Fabric API 自带的地形渲染器)与 OptiFine 只能有一个在场,本模组用 Fabric 自己的机制让 indigo 让位:`fabric.mod.json` 里声明 `"custom": {"fabric-renderer-api-v1:contains_renderer": true}`。这个键本来就是给"另一个渲染器"用的(Sodium 用同一个键),而 OptiFine 本身就是地形渲染器。indigo 会打印 `[Indigo] Different rendering plugin detected; not applying Indigo.`,F3 调试界面显示 `[Fabric] Active renderer: none (vanilla)`。

- **代价**:依赖 FRAPI/indigo 的模组不再有 indigo 提供的自定义渲染(地形由 OptiFine 渲染)。
- **想换回 indigo**:删掉 `fabric.mod.json` 里那个 `custom` 键并重新构建 —— 但那样 `ChunkBuilder$BuiltChunk$RebuildTask` 一加载就会因为缺失注入点而崩。

## 常见日志信息

下列输出不影响运行:

| 日志 | 说明 |
|---|---|
| `[OptiFine] (Reflector) Class not present: net.minecraftforge.*` / `sun.misc.SharedSecrets` | OptiFine 在探测 Forge 与旧 JDK 的类,Fabric 上本就没有 |
| `[OptiFine] Unknown resource pack type: ...ModNioResourcePack` | OptiFine 不认识 Fabric 的资源包类型 |
| `[Indigo] Different rendering plugin detected; not applying Indigo.` | 让位键生效,属预期行为 |
| `[Shaders] Invalid program name: ...` | 光影包声明了该版本 OptiFine 不支持的程序名 |
| `Skipping bad option: lastServer` | 选项文件里的旧字段 |

## 排查

- **升级本模组后行为没有变化**:先删掉 `<游戏目录>/.optifine/`,缓存里存的是打过补丁的字节码。
- `[OptiFabric]` 的输出走**启动器控制台**,通常不在 `logs/latest.log` 里;"日志里没有 `[OptiFabric]`"不代表模组没运行。过滤 `[OptiFabric]` 可以看到准备了多少补丁类、Loader 接管了多少。
- 标题界面会弹错误对话框(缺 OptiFine / jar 损坏 / 版本不匹配 / 多份 OptiFine / 内部错误),并提供打开 mods 目录、复制堆栈等按钮;崩溃报告里会多出一节 `OptiFabric`。
- Loader 没有暴露 `fabric-loader:inputGameJar` 时,可显式指定原版 jar:`-Doptifabric.mc-jar=<原版 1.20.6 client jar 路径>`。
- 调试:`-Doptifabric.extract=true` 会把重映射后的 OptiFine 类解包到 `.optifine/<版本>/optifine-classes/`。
- **方块/物品模型成片消失**(日志里成片的 `Unable to bake model: ...: Mixin transformation of <类> failed`):方向是某个 Fabric mixin 变换那个类失败(最外层消息常把真实原因吃掉)。OptiFine 会把原版方法改成转发给自己重载的瘦包装,注入点会随之搬走。
- **卡在加载界面**:取两次线程转储(`jstack <pid>`,间隔十几秒)对比。两次栈相同、CPU 不涨即为卡死;栈顶停在原生调用(如 `glfwSwapBuffers`)属于呈现层问题,注意加载期间不要最小化窗口(开着垂直同步时最小化会让 Render 线程一直阻塞)。

## 后续计划

1. 支持开发环境(dev 命名空间是 `named`,需要两段式重映射并补回上游的 contextual mapping 修正)。
2. 把上游 `compat/**` 的按模组兼容搬回来(需要重写 early riser 机制)。
3. 对着真实的 OptiFine 1.20.6 反编译产物,逐个核对 `patcher/fixes` 里硬编码的 intermediary id 与描述符。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE.txt`),核心逻辑移植自 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead),移植文件保留来源说明。
- **不包含、也不分发 OptiFine 本体**,OptiFine 版权归 sp614x 所有,请自行获取。
- 逐轮排查过程与离线校验工具见 `docs/DEVELOPMENT.md`,`reference/upstream/` 保存了移植所依据的上游源码快照。

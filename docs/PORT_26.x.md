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

1. 26.x 分支改用**官方映射**(Loom 的 official mappings 路径),不走 yarn;
2. **跳过"把 intermediary mappings 打进 jar"**那一步(未混淆下不重映射,`Patcher.process(...)` 的 official->intermediary 阶段成为恒等);
3. `gradle.properties` 的 `minecraft_version` 指到 `26.1.2`;
4. 之后再谈 `patcher/fixes/*` 按官方名重写(本轮主要工作量,逐个 fixer 的注册名与判据字符串都要换)。
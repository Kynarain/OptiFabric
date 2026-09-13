# 发布指引(GitHub / CurseForge)

本文档记录"把本项目发出去"需要做的步骤。仓库里已经准备好的东西、以及**你还需要自己做的部分**都写在下面。

> 本仓库只有**一条发布线**:**26.x** = `2.0.0+mc26.1.2`。26.1 起 Minecraft **未混淆**,官方名即运行名,
> 没有 yarn、也没有真正的 intermediary 可重映射(26.1.2 只发布占位 `intermediary:0.0.0`),所以这一线是
> 另一套构建与运行期路径 —— 仓库根目录就是那个 Gradle 项目,下面各节都以它为例。
>
> **分支:**
>
> | 分支 | 用途 |
> |---|---|
> | **`26.x`** | **开发与发布分支**。26.x 的修复提交在这里,26.x 的 tag 也打在这里 |
> | `1.21.x` | 另一条线(混淆名 + yarn/intermediary,一份源码出十个版本)的开发与发布分支,与本线无关 |
> | `main` | 历史:`1.0.0+mc1.20.6`(第一个发布版) |
> | `mc1.21.x`、`mc1.21.11` | 历史:1.1.0 发布时的**旧布局**(仓库根目录单项目),只作保留、不再更新 |
>
> ⚠️ **`mc1.21.x` 不是 1.21.x 的开发分支** —— 名字像,内容是 1.1.0 那一刻的快照。
>
> **发布标签是版本号本身**(已发的:`v1.2.0`、`v2.0.0`),不带 `+mc` —— MC 版本留在产物名与标题里;
> 每个版本一个条目,`1.2.0` 那个旧条目原样保留。
> `release/publish.ps1` 取 tag 的目标分支用脚本顶部的 `$defaultTagTarget`(当前为 `26.x`)。

> **版本号规则**:本项目按 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/) 定版本,`+mc<版本>` 是编译信息。
> 什么算不兼容修改、什么算新功能、一次改动要同步哪些文件,全部写在 [`docs/VERSIONING.md`](VERSIONING.md);
> **不要手改版本号**(一次要动 9 个文件,漏一处就文档与产物对不上)。

- **没有 `-Pmc=`**:这一线的目标版本就是根目录 `gradle.properties` 里的 `minecraft_version` 那一个值,一个项目一个版本
  (那个开关属于 1.21.x 线 —— 它一份源码要出十个 MC 版本,构建时要按版本传参);
- **Java 25**(26.1.2 自身的硬要求),发布说明里要提醒用户;
- 离线校验用 `test-downloads\verify-26.ps1`(不是 `verify-version.ps1`,后者属于 yarn/intermediary 那条线);
- 正文、逐版数据、三个平台要填的字段都在 `release/MANUAL_RELEASE.md` 里;
- 别把 26.x 的 jar 传成别的版本的 —— 文件名里的 `mc` 版本是唯一的区分点。

## 一、已经准备好的东西

| 项目 | 位置 | 说明 |
|---|---|---|
| 源码仓库 | 仓库根目录 | 已配好 `.gitignore`(不含 OptiFine、测试工件、构建产物) |
| 构建配置 | `build.gradle` / `gradle.properties`(仓库根目录) | 版本号 `2.0.0+mc26.1.2`,产物名 `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` |
| 许可 | `LICENSE.txt` | MPL-2.0(上游 OptiFabric 的许可,移植必须保留) |
| 使用者文档 | `README.md` | 原理、安装、已知问题、排查(已按 26.1.2 更新) |
| 开发记录 | `docs/DEVELOPMENT.md` | 逐轮排查与可复现的离线校验工具 |
| 移植记录 | `docs/PORT_26.x.md` | 26.x 为什么单独一条线、官方名带来的每一类冲突 |
| 更新日志 | `CHANGELOG.md` | 26.x 的 2.0.0 与 1.2.0 两节,后面是 1.21.x / 1.20.6 的历史 |
| 页面文案 | `docs/DESCRIPTION.md` | 简要描述 + 详细描述,中英双语,可直接粘贴(已按 26.1.2 更新) |
| 模组图标 | `src/main/resources/assets/optifabric/icon.png` | 128×128,已接进 `fabric.mod.json`;想换风格直接替换这个文件 |
| 发布包副本 | `dist/` | 已构建好的 jar + `README.txt`(含 SHA-256),`.gitignore` 排除,本地上传用 |

## 二、构建发布包

26.x 只对应 26.1.2 一个版本,从**仓库根目录**构建:

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric-Reforged
git checkout 26.x          # 从发布分支构建(见文首的分支表)
.\gradlew build --offline
Copy-Item "build\libs\OptiFabric-Reforged-2.0.0+mc26.1.2.jar" dist -Force
```

> 这一线**没有 `-Pmc`,也没有逐 MC 版本的版本号**:`minecraft_version` 就是 `26.1.2`,
> `mod_version_base` 就是 `2.0.0`(都在根目录 `gradle.properties` 里)。要升版别手改,走
> `.\release\version.ps1 -Line 26.x -Kind <major|minor|patch>` —— 它一次把 `gradle.properties`、
> `release/publish.ps1` 与各文档里那 9 处一起改掉(规则见 [`VERSIONING.md`](VERSIONING.md) 第四节)。

产物在 `build\libs\`(顺手复制到 `dist\`,里面已有一份现成的):

- `OptiFabric-Reforged-2.0.0+mc26.1.2.jar` ← **上传这个**
- `OptiFabric-Reforged-2.0.0+mc26.1.2-sources.jar`(可选,一般不用发)

发布之前建议先跑一遍离线验证(一条命令,几分钟):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-26.ps1
```

## 三、发到 GitHub

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric-Reforged
git add -A
git commit -m "OptiFabric Reforged 2.0.0+mc26.1.2: OptiFine on Fabric for 26.1.2"
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin 26.x        # 推当前分支(26.x)
```

发 Release —— 标签就是版本号本身,`1.2.0` 那个旧条目原样保留:

```powershell
git tag v2.0.0
git push origin v2.0.0
```

然后在 GitHub 网页上基于该 tag 建 Release(`Target` 选 **`26.x`** 分支 —— 见文首分支表),
把 `OptiFabric-Reforged-2.0.0+mc26.1.2.jar`
(以及 `-sources.jar`,可选)作为附件上传。仓库根目录的发布脚本也能做同样的事:

```powershell
.\release\publish.ps1 -DryRun   # 先看它会创建什么(不联网、不改远端)
```

> Release 文案已经写好了:[`docs/RELEASE_NOTES.md`](RELEASE_NOTES.md) —— 详细版(逐类冲突的根因);
> 发布脚本正文用的是 [`release/notes/mc26.1.2.md`](../release/notes/mc26.1.2.md)。
> 里面的 SHA-256 与 `dist/` 里那个 jar 是核对过的。

**仓库里不该出现的东西**(`.gitignore` 已经排除,提交前可再确认一次):

- OptiFine 的 jar(许可不允许再分发)
- `test-downloads/`(里面有你下载的 OptiFine 安装器、日志与扫描输出)
- `build/`、`dist/`、`reference/`、`build-log.txt`

## 四、发到 CurseForge

1. 用 CurseForge 账号创建一个 **Minecraft → Mods** 项目。
   - **项目名**:上游 OptiFabric 已经在 CurseForge 上有项目了,重名会被审核挡下。用能区分开的名字,例如
     **`OptiFabric Reforged`**(与本模组自己的显示名一致);项目正文里说明它是 Chocohead 版 OptiFabric 的移植(署名必须保留)。
   - 游戏版本:**26.1.2**(项目级支持范围);上传文件时再按文件指定具体版本。
   - 模组加载器:**Fabric**
   - 许可:**MPL-2.0**(与上游一致,必须一致)
   - 分类建议:Optimization / Miscellaneous
2. **上传文件**:一个 jar **一个文件**,版本名用 `2.0.0+mc26.1.2`,并在文件设置里把 **26.1.2** 勾上。
   changelog 用 `release/notes/mc26.1.2.md`。
3. **项目描述**:`docs/DESCRIPTION.md` 里给了成套文案 —— "简介"栏粘贴**简要描述**(英文在前、中文在后,CF 要求英文排最前),
   项目正文粘贴**详细描述**(有中文和英文两版,CF 支持 Markdown;里面已经带了"支持的版本"表)。
   GitHub 仓库的 About 也可以直接用那句简要描述。
4. **项目图标**:CurseForge 的图标要在网页上单独上传(要求 ≥400×400,且**不能是纯色图**;
   `src/main/resources/assets/optifabric/icon.png` 是给游戏内模组列表用的 128×128,两者可以同图)。
5. **依赖关系设置**:把 **Fabric API** 标为可选依赖(Optional dependency);**不要**把 OptiFine 列为依赖项 ——
   CurseForge 不允许分发 OptiFine,依赖项里也不要指向它的下载。
6. 提交后等审核。

## 五、发布前请再确认这几点

- [ ] jar 里**没有**包含 OptiFine 的任何类或资源(本项目的构建脚本不会打包它,但换过构建配置的话要复查)。
- [ ] jar 里**没有** `mappings/mappings.tiny`:这一线未混淆,本来就没有映射表可打包(有的话说明构建配置被改错了)。
- [ ] `LICENSE.txt` 还在(打包成 `LICENSE.txt_OptiFabric-Reforged`),`fabric.mod.json` 里的 `license` 仍是 `MPL-2.0`,
      README 里保留了对上游项目的署名。
- [ ] 项目描述里写明"需要自行获取 OptiFine 26.1.2"(目前只有 preview 构建 `HD_U_K1_pre2`)。
- [ ] 用**干净的实例**实测一次:只放 Fabric API + OptiFabric + OptiFine,能进主界面、能进存档;
      Java 必须是 25(26.1.2 的硬要求,Java 21 会在游戏窗口出现前就失败)。
      - 提示:`<游戏目录>/.optifine/` 是缓存目录,删掉它可强制重新生成,适合用来测"首次安装"的路径。
- [ ] 不要把开发时产生的日志、映射文件、OptiFine 安装器提交进仓库。

## 六、后续版本怎么发

1. 改根目录 `gradle.properties` 里的 `mod_version_base`(例如 `2.0.1`)—— 别手改,走
   `.\release\version.ps1 -Line 26.x -Kind patch`(见 [`VERSIONING.md`](VERSIONING.md) 第四节);
2. 在 `CHANGELOG.md` 顶部加一节(写清新的版本号,例如 `2.1.0+mc26.1.2`;`release/notes/mc26.1.2.md` 同步);
3. 构建(这一线没有 `-Pmc`,目标版本来自 `gradle.properties`):
   ```powershell
   .\gradlew build --offline
   ```
4. 跑离线校验(`test-downloads\verify-26.ps1`);
5. 复制到 `dist\`,用 `.\release\version.ps1 -Line 26.x -RecordDigest` 把字节数与 SHA-256 同步进
   `release/notes/mc26.1.2.md` 与 `release/MANUAL_RELEASE.md`;
6. `.\release\publish.ps1 -DryRun` 先看一眼要发什么,再打 tag、发 Release、上传三个平台。

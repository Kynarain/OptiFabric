# 发布指引(GitHub / CurseForge)

本文档记录"把本项目发出去"需要做的步骤。仓库里已经准备好的东西、以及**你还需要自己做的部分**都写在下面。

> 本仓库有**两条独立的发布线**,jar 不能互相替代,发哪条就 checkout 哪个分支构建;
> tag 名里带 MC 版本,不会互相冲突:
>
> - **`mc1.21.x` 分支** = `1.1.0+mc1.21` … `1.1.0+mc1.21.11`(10 个版本,一份源码一个项目 `v1.21.x/`。
>   本文下面第三节起**以它为例**);
> - **`26.x` 分支** = `1.2.1+mc26.1.2`(26.1 起游戏**未混淆**,是另一套构建与运行期路径,项目 `v26.x/`)。
>   它有自己的清单 [`release/MANUAL_RELEASE_26.x.md`](../release/MANUAL_RELEASE_26.x.md) 与移植记录
>   [`PORT_26.x.md`](PORT_26.x.md),差异见下面第二节末尾。
>
> 历史:`main` 分支 = `1.1.0+mc1.20.6`(第一个发布版)。

## 〇、如果你发的是 26.x 线(与 1.21.x 的差别)

26.1 起 Minecraft **未混淆**,官方名即运行名,没有 yarn、也没有真正的 intermediary 可重映射
(26.1.2 只发布占位 `intermediary:0.0.0`)。所以这一线:

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git checkout 26.x
.\release\version.ps1 -Line 26.x            # 版本号只走这个脚本(见 docs\VERSIONING.md)
.\gradlew -p v26.x build --offline
Copy-Item "v26.x\build\libs\OptiFabric-Reforged-1.2.1+mc26.1.2.jar" dist -Force
.\release\version.ps1 -Line 26.x -RecordDigest   # 把尺寸与 SHA-256 写回文档
```

> **版本号规则**:本项目按 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/) 定版本,`+mc<版本>` 是编译信息。
> 什么算不兼容修改、什么算新功能、一次改动要同步哪些文件,全部写在 [`docs/VERSIONING.md`](VERSIONING.md);
> **不要手改版本号**(一次要动 9 个文件,漏一处就文档与产物对不上)。

- **没有 `-Pmc=`**:26.x 项目的目标版本就是 `v26.x/gradle.properties` 里那一个值,一个项目一个版本;
- **Java 25**(1.21.x 是 21),发布说明里要提醒用户;
- 离线校验用 `test-downloads\verify-26.ps1`(不是 `verify-version.ps1`,后者是 yarn/intermediary 那一线的);
- 正文、逐版数据、三个平台要填的字段都在 `release/MANUAL_RELEASE_26.x.md` 里;
- 别把 26.x 的 jar 传成 1.21.x 的版本、也别反过来 —— 文件名里的 `mc` 版本是唯一的区分点。

## 一、已经准备好的东西

| 项目 | 位置 | 说明 |
|---|---|---|
| 源码仓库 | 仓库根目录 | 已配好 `.gitignore`(不含 OptiFine、测试工件、构建产物) |
| 构建配置 | `v1.21.x/build.gradle` / `gradle.properties` | 版本号 `1.1.0+mc1.21.11`,产物名 `OptiFabric-1.1.0+mc1.21.11.jar` |
| 许可 | `LICENSE.txt` | MPL-2.0(上游 OptiFabric 的许可,移植必须保留) |
| 使用者文档 | `README.md` | 原理、安装、已知问题、排查(已按 1.21.11 更新) |
| 开发记录 | `docs/DEVELOPMENT.md` | 逐轮排查与可复现的离线校验工具(1.21.11 的 9 类崩溃都在里面) |
| 更新日志 | `CHANGELOG.md` | 1.21.11 与 1.20.6 两节 |
| 页面文案 | `docs/DESCRIPTION.md` | 简要描述 + 详细描述,中英双语,可直接粘贴(已按 1.21.11 更新) |
| 模组图标 | `src/main/resources/assets/optifabric/icon.png` | 128×128,已接进 `fabric.mod.json`;想换风格直接替换这个文件 |
| 发布包副本 | `dist/` | 已构建好的 jar + `README.txt`(含 SHA-256),`.gitignore` 排除,本地上传用 |

## 二、构建发布包

1.21.x 全系列都从**同一个项目**(`v1.21.x/`)构建,每个版本一个 jar:

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git checkout mc1.21.x
foreach ($v in @("1.21","1.21.1","1.21.3","1.21.4","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11")) {
	.\gradlew -p v1.21.x build "-Pmc=$v" --offline
	Copy-Item "v1.21.x\build\libs\OptiFabric-1.1.0+mc$v.jar" dist -Force
}
```

（`-Pmc=` 的参数在 PowerShell 里必须加引号,否则 `1.21.8` 会被拆成 `1`。首次构建某个版本需要联网下载它的 MC/yarn/intermediary;之后可以 `--offline`。）

产物在 `v1.21.x\build\libs\`(顺手复制到 `dist\`,里面已有一份现成的):

- `OptiFabric-1.1.0+mc<版本>.jar` ← **上传对应版本这个**
- `OptiFabric-1.1.0+mc<版本>-sources.jar`(可选,一般不用发)

每个版本发之前建议先跑一遍离线验证(大约 4 分钟一个版本):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-version.ps1 -Version 1.21.8
```

## 三、发到 GitHub

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git add -A
git commit -m "OptiFabric 1.1.0+mc1.21.x: OptiFine on Fabric for 1.21 through 1.21.11"
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin mc1.21.x     # 推当前分支;想一起带上 1.20.6 那版再 git push origin main
```

发 Release —— **每个版本一个 tag / 一个 Release**,这样别人能按自己的游戏版本下载:

```powershell
foreach ($v in @("1.21","1.21.1","1.21.3","1.21.4","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11")) {
	git tag "v1.1.0+mc$v"
	git push origin "v1.1.0+mc$v"
}
```

然后在 GitHub 网页上基于每个 tag 建 Release(`Target` 选 `mc1.21.x` 分支),把 `OptiFabric-1.1.0+mc<版本>.jar` 作为附件上传。

> Release 文案已经写好了:
> - 1.21 ~ 1.21.10:[`docs/RELEASE_NOTES_1.21.x.md`](RELEASE_NOTES_1.21.x.md) —— 每个版本一节,连同"全系列共用段落"一起粘;
> - 1.21.11:[`docs/RELEASE_NOTES.md`](RELEASE_NOTES.md) —— 详细版(逐类崩溃的根因);
>
> 里面的 SHA-256 与 `dist/` 里那些 jar 是核对过的。

**仓库里不该出现的东西**(`.gitignore` 已经排除,提交前可再确认一次):

- OptiFine 的 jar(许可不允许再分发)
- `test-downloads/`(里面有你下载的 OptiFine 安装器、yarn 映射、日志与扫描输出)
- `build/`、`dist/`、`reference/`、`build-log.txt`

## 四、发到 CurseForge

1. 用 CurseForge 账号创建一个 **Minecraft → Mods** 项目。
   - **项目名**:上游 OptiFabric 已经在 CurseForge 上有项目了,重名会被审核挡下。用一个能区分开的名字,例如 **`OptiFabric (1.21.11 port)`** 或 `OptiFabric Reloaded`;项目正文里说明它是 Chocohead 版 OptiFabric 的移植(署名必须保留)。
   - 游戏版本:**把 1.21 / 1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 全勾上**(项目级支持范围),上传文件时再按文件指定具体版本。
   - 模组加载器:**Fabric**
   - 许可:**MPL-2.0**(与上游一致,必须一致)
   - 分类建议:Optimization / Miscellaneous
2. **上传文件**:10 个 jar **各传一个文件**,版本名用同一个格式 `1.1.0+mc<版本>`,并在文件设置里把**对应的那一个游戏版本**勾上(例如 `OptiFabric-1.1.0+mc1.21.8.jar` 只勾 1.21.8)。changelog 用 `docs/RELEASE_NOTES_1.21.x.md` 里对应那一节。
   - 也可以先只发几个版本(1.21.1 / 1.21.4 / 1.21.8 这类用的人多),其余随时补传。
3. **项目描述**:`docs/DESCRIPTION.md` 里给了成套文案 —— "简介"栏粘贴**简要描述**(英文在前、中文在后,CF 要求英文排最前),项目正文粘贴**详细描述**(有中文和英文两版,CF 支持 Markdown;里面已经带了"支持的版本"表)。
   GitHub 仓库的 About 也可以直接用那句简要描述。
4. **项目图标**:CurseForge 的图标要在网页上单独上传(要求 ≥400×400,且**不能是纯色图**;`src/main/resources/assets/optifabric/icon.png` 是给游戏内模组列表用的 128×128,两者可以同图)。`dist/icons/` 里有几张之前准备的 512×512 候选。
5. **依赖关系设置**:把 **Fabric API** 标为可选依赖(Optional dependency);**不要**把 OptiFine 列为依赖项 —— CurseForge 不允许分发 OptiFine,依赖项里也不要指向它的下载。
6. 提交后等审核。

## 五、发布前请再确认这几点

- [ ] jar 里**没有**包含 OptiFine 的任何类或资源(本项目的构建脚本不会打包它,但换过构建配置的话要复查)。
- [ ] `LICENSE.txt` 还在,`fabric.mod.json` 里的 `license` 仍是 `MPL-2.0`,README 里保留了对上游项目的署名。
- [ ] 项目描述里写明"需要自行获取 OptiFine 1.21.11"。
- [ ] 用**干净的实例**实测一次:只放 Fabric API + OptiFabric + OptiFine,能进主界面、能进存档。
      - 提示:`<游戏目录>/.optifine/` 是缓存目录,删掉它可强制重新生成,适合用来测"首次安装"的路径。
      - 想连"首次安装"也测一遍:把 `mods/` 与 `.optifine/` 清空,只放 OptiFabric + OptiFine(先不带 Fabric API),确认能起;再放 Fabric API 起一次。
- [ ] 不要把开发时产生的日志、映射文件、OptiFine 安装器提交进仓库。

## 六、后续版本怎么发

1. 改**该项目**的 `gradle.properties` 里的 `mod_version_base`(例如 `1.0.1`);
2. 在 `CHANGELOG.md` 顶部加一节(写清新的版本号,例如 `1.0.1+mc1.21.11` 或 `1.2.1+mc26.1.2`);
3. 构建对应项目(1.21.x 还要带 `-Pmc=`):
   ```powershell
   .\gradlew -p v1.21.x build "-Pmc=1.21.11" --offline
   .\gradlew -p v26.x   build --offline
   ```
4. 跑对应的离线校验(`verify-version.ps1 -Version <版本>` / `verify-26.ps1`);
5. 复制到 `dist\`,同步 `release/notes/mc<版本>.md` 与对应清单里的字节数 / SHA-256
   (两个清单:`release/MANUAL_RELEASE.md`、`release/MANUAL_RELEASE_26.x.md`);
6. `.\release\publish.ps1 -DryRun` 先看一眼要发什么,再打 tag、发 Release、上传三个平台。

# 发布指引(GitHub / CurseForge)

本文档记录"把本项目发出去"需要做的步骤。仓库里已经准备好的东西、以及**你还需要自己做的部分**都写在下面。

> 本仓库现在有两条发布线:
> - **`main` 分支** = `1.0.0+mc1.20.6`(第一个发布版)
> - **`mc1.21.11` 分支** = `1.0.0+mc1.21.11`(当前这一版,**下面以它为例**)
>
> 两个分支都是独立可用的,发哪一版就 checkout 哪个分支构建;tag 名里带 MC 版本,不会互相冲突。

## 一、已经准备好的东西

| 项目 | 位置 | 说明 |
|---|---|---|
| 源码仓库 | 仓库根目录 | 已配好 `.gitignore`(不含 OptiFine、测试工件、构建产物) |
| 构建配置 | `build.gradle` / `gradle.properties` | 版本号 `1.0.0+mc1.21.11`,产物名 `OptiFabric-1.0.0+mc1.21.11.jar` |
| 许可 | `LICENSE.txt` | MPL-2.0(上游 OptiFabric 的许可,移植必须保留) |
| 使用者文档 | `README.md` | 原理、安装、已知问题、排查(已按 1.21.11 更新) |
| 开发记录 | `docs/DEVELOPMENT.md` | 逐轮排查与可复现的离线校验工具(1.21.11 的 9 类崩溃都在里面) |
| 更新日志 | `CHANGELOG.md` | 1.21.11 与 1.20.6 两节 |
| 页面文案 | `docs/DESCRIPTION.md` | 简要描述 + 详细描述,中英双语,可直接粘贴(已按 1.21.11 更新) |
| 模组图标 | `src/main/resources/assets/optifabric/icon.png` | 128×128,已接进 `fabric.mod.json`;想换风格直接替换这个文件 |
| 发布包副本 | `dist/` | 已构建好的 jar + `README.txt`(含 SHA-256),`.gitignore` 排除,本地上传用 |

## 二、构建发布包

1.21.x 全系列都从**同一条分支**(`mc1.21.x`)构建,每个版本一个 jar:

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git checkout mc1.21.x
foreach ($v in @("1.21","1.21.1","1.21.3","1.21.4","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11")) {
	.\gradlew build "-Pmc=$v" --offline
	Copy-Item "build\libs\OptiFabric-1.0.0+mc$v.jar" dist -Force
}
```

（`-Pmc=` 的参数在 PowerShell 里必须加引号,否则 `1.21.8` 会被拆成 `1`。首次构建某个版本需要联网下载它的 MC/yarn/intermediary;之后可以 `--offline`。）

产物在 `build\libs\`(顺手复制到 `dist\`,里面已有一份现成的):

- `OptiFabric-1.0.0+mc<版本>.jar` ← **上传对应版本这个**
- `OptiFabric-1.0.0+mc<版本>-sources.jar`(可选,一般不用发)

每个版本发之前建议先跑一遍离线验证(大约 4 分钟一个版本):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-version.ps1 -Version 1.21.8
```

## 三、发到 GitHub

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git add -A
git commit -m "OptiFabric 1.0.0+mc1.21.x: OptiFine on Fabric for 1.21 through 1.21.11"
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin mc1.21.x     # 推当前分支;想一起带上 1.20.6 那版再 git push origin main
```

发 Release —— **每个版本一个 tag / 一个 Release**,这样别人能按自己的游戏版本下载:

```powershell
foreach ($v in @("1.21","1.21.1","1.21.3","1.21.4","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11")) {
	git tag "v1.0.0+mc$v"
	git push origin "v1.0.0+mc$v"
}
```

然后在 GitHub 网页上基于每个 tag 建 Release(`Target` 选 `mc1.21.x` 分支),把 `OptiFabric-1.0.0+mc<版本>.jar` 作为附件上传。

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
2. **上传文件**:10 个 jar **各传一个文件**,版本名用同一个格式 `1.0.0+mc<版本>`,并在文件设置里把**对应的那一个游戏版本**勾上(例如 `OptiFabric-1.0.0+mc1.21.8.jar` 只勾 1.21.8)。changelog 用 `docs/RELEASE_NOTES_1.21.x.md` 里对应那一节。
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

1. 改 `gradle.properties` 里的 `mod_version`(例如 `1.0.1+mc1.21.11`);
2. 在 `CHANGELOG.md` 顶部加一节;
3. `.\gradlew build --offline`;
4. 打 tag、发 Release、在 CurseForge 上传新文件。

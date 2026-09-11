# 发布指引(GitHub / CurseForge)

本文档记录"把本项目发出去"需要做的步骤。仓库里已经准备好的东西、以及**你还需要自己做的部分**都写在下面。

## 一、已经准备好的东西

| 项目 | 位置 | 说明 |
|---|---|---|
| 源码仓库 | 仓库根目录 | 已配好 `.gitignore`(不含 OptiFine、测试工件、构建产物) |
| 构建配置 | `build.gradle` / `gradle.properties` | 版本号 `1.0.0+mc1.20.6`,产物名 `OptiFabric-1.0.0+mc1.20.6.jar` |
| 许可 | `LICENSE.txt` | MPL-2.0(上游 OptiFabric 的许可,移植必须保留) |
| 使用者文档 | `README.md` | 原理、安装、已知问题、排查 |
| 开发记录 | `docs/DEVELOPMENT.md` | 逐轮排查与可复现的离线校验工具 |
| 更新日志 | `CHANGELOG.md` | 首个发布版 |
| 页面文案 | `docs/DESCRIPTION.md` | 简要描述 + 详细描述,中英双语,可直接粘贴 |
| 模组图标 | `src/main/resources/assets/optifabric/icon.png` | 128×128,已接进 `fabric.mod.json`;想换风格直接替换这个文件 |

## 二、构建发布包

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
.\gradlew build --offline
```

产物在 `build\libs\`:

- `OptiFabric-1.0.0+mc1.20.6.jar` ← **上传这个**
- `OptiFabric-1.0.0+mc1.20.6-sources.jar`(可选,一般不用发)

> 首次构建若离线失败,去掉 `--offline` 让它联网补齐依赖即可。

## 三、发到 GitHub

```powershell
cd C:\Users\kynar\IdeaProjects\OptiFabric
git add -A
git commit -m "OptiFabric 1.0.0+mc1.20.6: OptiFine on Fabric for 1.20.6"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

发 Release(可选,但推荐,方便别人直接下载):

```powershell
git tag v1.0.0+mc1.20.6
git push origin v1.0.0+mc1.20.6
```

然后在 GitHub 网页上基于该 tag 建 Release,把 `OptiFabric-1.0.0+mc1.20.6.jar` 作为附件上传。

**仓库里不该出现的东西**(`.gitignore` 已经排除,提交前可再确认一次):

- OptiFine 的 jar(许可不允许再分发)
- `test-downloads/`(里面有你下载的 OptiFine 安装器、yarn 映射、日志与扫描输出)
- `build/`、`dist/`、`reference/`、`build-log.txt`

## 四、发到 CurseForge

1. 用 CurseForge 账号创建一个 **Minecraft → Mods** 项目。
   - 游戏版本:选择 **1.20.6**
   - 模组加载器:**Fabric**
   - 许可:**MPL-2.0**(与上游一致,必须一致)
   - 分类建议:Optimization / Miscellaneous
2. **上传文件**:把 `OptiFabric-1.0.0+mc1.20.6.jar` 作为 release 上传,版本名填 `1.0.0+mc1.20.6`。
3. **项目描述**:`docs/DESCRIPTION.md` 里给了成套文案 —— "简介"栏粘贴**简要描述**(中文或英文),项目正文粘贴**详细描述**(有中文和英文两版,CF 支持 Markdown)。
   GitHub 仓库的 About 也可以直接用那句简要描述;如果以后加了英文 README,再补一版英文详细描述即可。
4. **项目图标**:CurseForge 的图标要在网页上单独上传(`src/main/resources/assets/optifabric/icon.png` 是给游戏内模组列表用的,两者可以同图)。图标建议 400×400 或以上。
5. **依赖关系设置**:把 **Fabric API** 标为可选依赖(Optional dependency);**不要**把 OptiFine 列为依赖项 —— CurseForge 不允许分发 OptiFine,依赖项里也不要指向它的下载。
6. 提交后等审核。

## 五、发布前请再确认这几点

- [ ] jar 里**没有**包含 OptiFine 的任何类或资源(本项目的构建脚本不会打包它,但换过构建配置的话要复查)。
- [ ] `LICENSE.txt` 还在,`fabric.mod.json` 里的 `license` 仍是 `MPL-2.0`,README 里保留了对上游项目的署名。
- [ ] 项目描述里写明"需要自行获取 OptiFine 1.20.6"。
- [ ] 用**干净的实例**实测一次:只放 Fabric API + OptiFabric + OptiFine,能进主界面、能进存档。
   - 提示:`<游戏目录>/.optifine/` 是缓存目录,删掉它可强制重新生成,适合用来测"首次安装"的路径。
- [ ] 不要把开发时产生的日志、映射文件、OptiFine 安装器提交进仓库。

## 六、后续版本怎么发

1. 改 `gradle.properties` 里的 `mod_version`(例如 `1.0.1+mc1.20.6`);
2. 在 `CHANGELOG.md` 顶部加一节;
3. `.\gradlew build --offline`;
4. 打 tag、发 Release、在 CurseForge 上传新文件。

# 版本号规则(语义化版本 2.0.0)

本项目按 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/) 定版本号。**以后每次改版本号都走
`release\version.ps1`**,不要手改 —— 一次手改要同时动 9 个文件,漏一处就会出现"文档写 1.2.1、jar 是 1.2.2"这种
对不上的情况(这套脚本就是为那次手工改名的收尾写的)。

## 一、规范里与我们相关的三条

> 版本格式:**主版本号.次版本号.修订号**,版本号递增规则如下:
> 1. **主版本号**:当你做了不兼容的 API 修改,
> 2. **次版本号**:当你做了向下兼容的功能性新增,
> 3. **修订号**:当你做了向下兼容的问题修正。
>
> —— [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)(摘要)

另外两条对本项目直接适用:

- **§3**:「标记版本号的软件发行后,禁止改变该版本软件的内容。任何修改都必须以新版本发行。」→ `dist/` 里
  已发布的 jar **永不覆盖**,1.1.0 就此冻结;
- **§10**:「版本编译信息可以标注在……之后,先加上一个加号……判断版本的优先层级时,版本编译信息可被忽略。」
  → 我们的 `+mc26.1.2` 是**编译信息**(build metadata),所以**版本号本体相同、只差 MC 版本的两个 jar 属于同一优先层级**
  (1.2.1+mc1.21.11 与 1.2.1+mc26.1.2)。这正合本项目的做法:一个 MC 版本一个 jar,它俩不是"同一个版本的两个变体",
  而是两个产物,各自按 §3 冻结。因此**发布记录里必须成对写版本号与 MC 版本**,只写 `1.2.1` 不足以定位产物。

## 二、在本项目里,什么算"公共 API"

SemVer §1 要求先定义公共 API。对这个模组来说,它是:

| 属于公共 API | 说明 |
|---|---|
| mod id 与显示名 | 别的模组会 `depends`/`breaks`/`conflicts` 它;改 id 就是不兼容修改 |
| 支持的 Minecraft / Fabric Loader / Java / OptiFine 构建 | 少支持一个,对用着那个组合的人就是不兼容 |
| 用户可见行为 | 哪些模组组合能启动、什么能被渲染、`BEFORE_BLOCK_OUTLINE` 之类钩子是否生效 |
| 配置与调试开关 | `-Doptifabric.*` 参数、`.optifine/` 缓存的对外含义 |
| 加载方式 | `mods/` 放 jar 即用、不依赖 Fabric API 等承诺 |

**不属于**公共 API:内部 fixer 表、`.optifine/` 缓存的字节布局(`CACHE_FORMAT` 变了会自动重建)、日志措辞、
`docs/` 里的实现细节。

## 三、映射表(改了东西就照这张表选)

| 改动 | 递增 | 例子 |
|---|---|---|
| 修 fixer、修兼容性、修正元数据/文档、缓存格式调整 | **修订号** `1.2.1 → 1.2.2` | 某个注入点又消失了、修好一个 `VerifyError` |
| 向下兼容地新增功能或支持范围 | **次版本号** `1.2.1 → 1.3.0` | 移植到一个新的 MC 版本(新 jar)、支持新 OptiFine 构建、**让以前画不出来的几何能画出来** |
| 不兼容修改 | **主版本号** `1.x → 2.0.0` | 换 mod id、丢开某个 MC 版本、抬高 Java/Loader 下限、让原本能用的模组组合不再启动 |

> 曾经踩过的一个现实例子:26.x 线的 `1.2.0` 只在开发期构建、**从未发布**,而它 `1.2.1` 里装着"实时几何能渲染了"
> 这类**新功能** —— 按 §7 严格说该是 `1.3.0`。只因 `1.2.0` 从未发行,`1.2.1` 作为首发并不违反规范(没有"上一个版本"
> 可言)。**已发行的版本不能这样处理**:那种情况一律按新版本发行(§3)。

**先行版本号**(§9)在本项目里用于试发布,例如 `1.3.0-beta.1+mc26.1.2`:它的优先级低于 `1.3.0`,适合"先给几个人试"。
tag 名可以带 `v` 前缀(§FAQ:「`v1.2.3` 并不是语义化版本号……但增加前缀 v 是常用做法」),我们的 tag 就是
`v1.3.0+mc26.1.2`。

## 四、怎么改(一条命令)

```powershell
# 只是看看:当前版本 + 按三类各会变成什么
.\release\version.ps1

# 改(默认会真正写入;先加 -DryRun 只看结果)
.\release\version.ps1 -Line 26.x -Kind minor          # 1.2.1 -> 1.3.0
.\release\version.ps1 -Line 26.x -Kind patch -DryRun  # 1.2.1 -> 1.2.2,不写入
.\release\version.ps1 -Line 26.x -Set 1.3.0-beta.1    # 直接指定(校验格式与优先级)
.\release\version.ps1 -Line 26.x -Part                # 只打印当前版本号,给脚本用
```

脚本会:

1. 校验**格式**(SemVer §2:不许前导零)与**优先级**(§11:新版本必须更高,否则拒绝,除非 `-Force`);
2. 改 `v26.x/gradle.properties` 或 `v1.21.x/gradle.properties` 的 `mod_version_base`;
3. 改 `release/publish.ps1` 里该线的版本映射;
4. 把仓库里所有 `<旧版本>+mc` 的写法换成新版本(产物名 `OptiFabric-Reforged-<版本>+mc26.1.2.jar`、
   `README.md`、`CHANGELOG.md`、`docs/`、`release/notes/`、`release/MANUAL_RELEASE*.md`、`dist/README.txt`),
   并**打印逐文件改动数**;
5. 提醒你接下来要做的三件事:构建、`-RecordDigest` 把尺寸/SHA-256 写进文档、按 `release/MANUAL_RELEASE*.md` 发布。

构建完把尺寸与摘要同步进文档:

```powershell
.\gradlew -p v26.x build --offline
.\release\version.ps1 -Line 26.x -RecordDigest        # 读 build/libs 里的 jar,写回尺寸与 SHA-256
```

## 五、发布前的检查

- [ ] `.\release\version.ps1 -Line <线>` 显示的版本是这次要发的那个;
- [ ] `git status` 干净、该提交的都提交了(git tag 直接指向当前提交);
- [ ] `.\gradlew -p <项目> build --offline` 通过;
- [ ] 离线校验数字写进 CHANGELOG / release notes(见对应 `MANUAL_RELEASE*.md`);
- [ ] `-RecordDigest` 跑过,文档里的尺寸与 SHA-256 与 `dist/` 里的 jar 一致;
- [ ] 已发布过的版本号**没有**被复用(§3);要改内容就发新版本。

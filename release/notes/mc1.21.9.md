# OptiFabric 1.1.2+mc1.21.9

**Minecraft 1.21.9** / Fabric Loader 0.19.5 / Java 21+ / 需求 OptiFine `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar`

状态:**已实测正常(含抗锯齿)**

## 1.1.2 修了什么

**抗锯齿之前是坏的(或干脆没生效),这一版把它修好了。**

- **病根**:管线会删掉 OptiFine 自带的 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`(以为这条链该走
  1.21.6 之前的老位置),而事实上链是由**游戏自己的后处理链注册表**按 `minecraft:fxaa_of_2x` 从 `post_effect/`
  解析的(OptiFine 把 `ShaderManager` 改成在那儿注册)。文件被删了,于是每次资源重载都刷一条
  `Resource not found: minecraft:post_effect/fxaa_of_2x.json`,一旦动抗锯齿(或切光影包 —— 两者都会重载光影)
  就 `Failed to load post chain: minecraft:fxaa_of_2x`,界面上就是"重载资源失败"。
- **现在**:不再碰 OptiFine 的后处理文件,链由游戏按原样解析。
- **顶点着色器(本版本特有)**:游戏从 1.21.9 起改用 `gl_VertexID` 生成全屏三角形、**不再提供 `Position`
  顶点属性**,而 OptiFine 那两个预览构建的 `fxaa_of_*.vsh` 还在读 `Position` → 顶点塌成一点 →
  当初"一开抗锯齿就整屏黑"就是这么来的。管线会把这两个 `.vsh` 改写成同一套全屏三角形写法
  (`SamplerInfo`/`FxaaConfig` 两个 uniform 块与 FXAA 的 `posPos` 计算**原样保留**,片段阶段还在读它们)。
- 判据都是文件内容(游戏那份 `core/screenquad.vsh` 是否用 `gl_VertexID`、OptiFine 那份 `.vsh` 是否还在读
  `Position`),所以不需要按版本列表维护:**本 jar** 属于哪一档已由上面的命令行验证过。

## 这个版本是什么

把 OptiFine 完整接入 Fabric:OptiFine 的补丁在构建期离线应用到 Minecraft jar(官方名 -> intermediary 重映射、
补丁类修复、逐类双向校验),运行时把补丁类交给 Fabric Loader,光影、连接纹理、缩放等 OptiFine 功能照常工作。
本 jar 只适配 Minecraft 1.21.9,不要跨版本使用。

## 本版修复(1.0.0)

- 补丁管线:保留 OptiFine 自带栈帧、必要时只对改动过的类重算;注入点(调用/句柄)被 OptiFine 改写时逐一对齐;
- 工厂调用错位、HUD 图层 lambda 名被改写、区块 section 位置缺失、HUD 与区块路径的类型不匹配等逐一修复;
- 抗锯齿(1.21.9 / 1.21.10):OptiFine 的 FXAA 走它自己的 post chain,而 1.21.8 起的构建不再带那个老位置的文件,
  且游戏那条同名 post effect 会画出黑屏 -> 管线补写该文件并让 OptiFine 自己的链独占;
- 多人(1.21.8):Fabric 渲染器 API 的占位实现不再抛异常终结会话。

## 安装

1. 安装 Fabric Loader 0.19.5(Java 21 或更高);
2. 把本 jar 和对应版本的 OptiFine 一起放进 `mods\`;
3. 启动一次即可(首次会重建 `.optifine` 缓存)。

## 已知限制

- **光影包请用 Complementary 等主流包**:`photon_v1.2a.zip` 在 1.21.6 起的 OptiFine 上不工作(包与 OptiFine 的版本差异);
- 1.21.8 起 Indigo 让位于 OptiFine,走 Fabric 渲染器 API 的方块实体由原版路径绘制,个别情况下可能画不出来;
- 1.21.6 / 1.21.7 的 OptiFine 预览构建自身有缺陷,本移植不提供支持(见上)。

## 校验

`OptiFabric-1.1.2+mc1.21.9.jar` — 856054 字节

`SHA-256: 66711E7D8D0EAC83F73EE96012A4C3ACAFF94960B49E9BECBF74FEED19022276`
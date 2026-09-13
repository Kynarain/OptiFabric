# OptiFabric 1.1.2+mc1.21.11

**Minecraft 1.21.11** / Fabric Loader 0.19.5 / Java 21+ / 需求 OptiFine `OptiFine_1.21.11_HD_U_J9.jar`

状态:**已实测正常(含抗锯齿)**

## 1.1.2 修了什么

**抗锯齿之前是坏的,这一版把它修好了。** 1.1.2 覆盖 **1.21.3 – 1.21.11**;1.21.11 在 1.1.1 里已经先修过一次,
1.1.2 把那次的开关并入了现在这套做法 —— 本 jar 在 1.21.11 上的**行为与 1.1.1 相同**,但字节不同(管线不再做
那次后处理链修复,缓存格式号也从 25 升到 26),所以它同样是一个新版本号。

- **病根**:管线会删掉 OptiFine 自带的 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`(以为这条链该走
  1.21.6 之前的老位置),而事实上链是由**游戏自己的后处理链注册表**按 `minecraft:fxaa_of_2x` 从 `post_effect/`
  解析的(OptiFine 把 `ShaderManager` 改成在那儿注册)。文件被删了,于是每次资源重载都刷一条
  `Resource not found: minecraft:post_effect/fxaa_of_2x.json`,一旦动抗锯齿(或切光影包 —— 两者都会重载光影)
  就 `Failed to load post chain: minecraft:fxaa_of_2x`,界面上就是"重载资源失败"。
- **现在**:不再碰 OptiFine 的后处理文件,链由游戏按原样解析。
- **顶点着色器**:1.21.11 这份 OptiFine 自己已经把 `fxaa_of_*.vsh` 换成了屏幕四边形写法
  (文件里写着 `// Copy of core/screenquad.vsh`),所以管线**跳过不改** —— 它只改写那些还在读 `Position`
  顶点属性的版本(1.21.9 / 1.21.10)。判据是文件内容,不是版本号列表。

## 这个版本是什么

把 OptiFine 完整接入 Fabric:OptiFine 的补丁在构建期离线应用到 Minecraft jar(官方名 -> intermediary 重映射、
补丁类修复、逐类双向校验),运行时把补丁类交给 Fabric Loader,光影、连接纹理、缩放等 OptiFine 功能照常工作。
本 jar 只适配 Minecraft 1.21.11,不要跨版本使用。

## 安装

1. 安装 Fabric Loader 0.19.5(Java 21 或更高);
2. 把本 jar 和对应版本的 OptiFine 一起放进 `mods\`;
3. **从旧版升级**:删掉旧的那份 1.21.11 jar(1.1.0 或 1.1.1),换上这一个即可;`.optifine/` 缓存会自动重建
   (缓存格式号已升到 `26`),第一次启动因此多花几秒。

## 已知限制

- **光影包请用 Complementary 等主流包**:`photon_v1.2a.zip` 在 1.21.6 起的 OptiFine 上不工作(包与 OptiFine 的版本差异);
- 1.21.8 起 Indigo 让位于 OptiFine,走 Fabric 渲染器 API 的方块实体由原版路径绘制,个别情况下可能画不出来;
- 1.21.6 / 1.21.7 的 OptiFine 预览构建自身有缺陷,本移植不提供支持(见 README);
- 手上拿着的方块在开光影时偶尔黑一下再恢复(1.21.11 实测):**在只装 OptiFine、不装本模组的 Forge 客户端上同样复现**,
  属 OptiFine 侧的问题,与本 jar 无关(可先试关闭动态光源 / 换光影包)。

## 校验

- 离线:570 / 570 个补丁类与 874 / 874 个 OptiFine 类通过 JVM 校验,ASM 数据流验证器 0 问题;
  扫描器:注入点 4 条(全部属于**已停用**的 indigo)、mixin 成员引用缺失 0、契约/覆写/引用 0/0/0、句柄 0 悬空。
- 真机:1.1.1 已由用户在自己的 1.21.11 实例确认(启动、资源重载、切光影包、开关抗锯齿都正常,`[ERROR]` 0 条);
  1.1.2 用同一个实例再跑一遍资源重载,`Resource not found: minecraft:post_effect/*` **0 条**。

`OptiFabric-1.1.2+mc1.21.11.jar` — 873615 字节

`SHA-256: B62AB6AEBD441E67C75F1FD239DFFF3286B437EA8B6B95AC5597AE7AC4FEFEF0`

# OptiFabric 1.1.1+mc1.21.11

**Minecraft 1.21.11** / Fabric Loader 0.19.5 / Java 21+ / 需求 OptiFine `OptiFine_1.21.11_HD_U_J9.jar`

状态:**已实测正常**

## 这个版本是什么

把 OptiFine 完整接入 Fabric:OptiFine 的补丁在构建期离线应用到 Minecraft jar(官方名 -> intermediary 重映射、
补丁类修复、逐类双向校验),运行时把补丁类交给 Fabric Loader,光影、连接纹理、缩放等 OptiFine 功能照常工作。
本 jar 只适配 Minecraft 1.21.11,不要跨版本使用。

## 1.1.1 修了什么

**切换光影包时不再弹"重载资源失败"**(1.1.0 上会看到 `Resource not found: minecraft:post_effect/fxaa_of_2x.json`)。

原因是 1.0.0/1.1.0 那条抗锯齿修复:1.21.8 起的 OptiFine 不再带老位置的
`assets/minecraft/shaders/post/fxaa_of_2x.json`,管线于是补写它、并把游戏新位置的
`assets/minecraft/post_effect/fxaa_of_2x.json` 删掉(1.21.6–1.21.10 上 OptiFine 确实读老位置,实测可用)。
但 1.21.11 的后处理链由**游戏自己的加载器**解析,它按 `minecraft:fxaa_of_2x` 去 `post_effect/` 取文件 ——
文件被删了:每次资源重载日志里都多一条 `Resource not found`,需要这条链时(开抗锯齿 / 选光影包)就直接失败
并弹出"重载资源失败"(`Failed to load post chain: minecraft:fxaa_of_2x`)。

1.1.1 在 1.21.11 上**原样保留** OptiFine 自带的那份文件,不补写、也不删。缓存格式号升到 `25`,
升级后第一次启动会重建 `.optifine/`(多花几秒)。

## 与 1.1.0 的关系

1.21.x 是"一份源码、每个 MC 版本一个 jar",而每个 jar 的版本号描述的是**它自己那份产物的内容**:
这次的行为改动只发生在 1.21.11 上,其余九个版本(1.21 … 1.21.10)的 1.1.0 jar 内容没变、不用换。
所以 1.21.11 单独升到 1.1.1,`v1.1.0` 那个发布条目原样保留。

## 安装

1. 安装 Fabric Loader 0.19.5(Java 21 或更高);
2. 把本 jar 和对应版本的 OptiFine 一起放进 `mods\`;
3. 从 1.1.0 升级的话:删掉旧的 `OptiFabric-1.1.0+mc1.21.11.jar`,换上这一个即可(缓存会自动重建)。

## 已知限制

- **光影包请用 Complementary 等主流包**:`photon_v1.2a.zip` 在 1.21.6 起的 OptiFine 上不工作(包与 OptiFine 的版本差异);
- 1.21.8 起 Indigo 让位于 OptiFine,走 Fabric 渲染器 API 的方块实体由原版路径绘制,个别情况下可能画不出来;
- 1.21.6 / 1.21.7 的 OptiFine 预览构建自身有缺陷,本移植不提供支持(见 README);
- 手上拿着的方块在开光影时偶尔黑一下再恢复(1.21.11 实测):**在只装 OptiFine、不装本模组的 Forge 客户端上同样复现**,
  属 OptiFine 侧的问题,与本 jar 无关(可先试关闭动态光源 / 换光影包)。

## 校验

`OptiFabric-1.1.1+mc1.21.11.jar` — 876446 字节

`SHA-256: 9E78C98FC0FC568C453ACA880FE167545192021D16C4A2DA0E4127AC5F3A9143`

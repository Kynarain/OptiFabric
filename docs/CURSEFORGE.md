# OptiFabric for Minecraft 1.20.6 (Fabric)

**让 OptiFine 与 Fabric 共存。** 把 OptiFine 放进 `mods/`,由本模组在启动时对它做解包、重映射与兼容性修补,使其能在 Fabric 环境下正常运行。

> 本模组**不包含、也不分发 OptiFine 本体**。请自行从 OptiFine 官网获取 1.20.6 版本。

---

## 它做了什么

OptiFine 是给原版(以及 Forge)做的,它的补丁是针对**官方混淆名**编译的,Fabric 用的是 **intermediary** 命名空间;两者直接放在一起会以各种诡异方式崩溃。OptiFabric 在游戏启动的最早阶段(preLaunch)完成:

1. 运行 OptiFine 安装器,取出它对原版类的补丁;
2. 把补丁从官方混淆名重映射到 intermediary;
3. 修正 OptiFine 与 Fabric API 之间已知的结构冲突(构造器形状、合成字段名、被内联掉的方法、被换掉的父类与对象创建等);
4. 把结果交给 Fabric Loader 的类变换器,并在 `<游戏目录>/.optifine/` 缓存,二次启动免重算。

---

## 安装

1. 用 **Fabric Loader 0.19.3 或更高**安装一个 1.20.6 客户端。
2. 把 **OptiFabric** 与**你自备的 OptiFine 1.20.6 jar** 一起放进 `mods/` 文件夹。
   OptiFine 1.20.6 目前只有 preview 构建,文件名类似 `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar`,**直接放进去即可**(不需要先运行它的安装器)。
3. 启动游戏。标题界面左上角显示 OptiFine 版本号即为生效。

Fabric API 可以同时加载(已针对它专门适配:进主界面、进单人存档、进多人服务器均正常)。

---

## 依赖

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.6 |
| Fabric Loader | ≥ 0.19.3 |
| Java | 21 及以上(实测运行于 Java 25) |
| 环境 | 客户端 |

---

## 已知问题

- **与 Sodium 冲突**:二者都是渲染器,已声明为不兼容,请勿同时安装。
- **与 RyoamicLights 不兼容**:OptiFine 把原版视频设置界面整个换成了自己的实现(连父类都换掉),该模组往这个界面注入时会失败并导致游戏崩溃。已声明为不兼容;OptiFine **自带动态光源**(视频设置 → 品质 → 动态光源),无需额外模组。
- **依赖 FRAPI/indigo 的模组**不再获得 indigo 提供的自定义渲染(地形由 OptiFine 渲染)。
- **OptiFine 看不到 Fabric 模组内部的资源**(日志里会出现 `Unknown resource pack type: ...ModNioResourcePack`),这是 OptiFine 侧的限制。
- 光影包与 OptiFine 版本不完全匹配时会出现 `[Shaders] Invalid program name: ...` 之类报错,属光影包自身问题。

---

## 反馈问题时请附上

- `logs/latest.log`(若崩溃,再附 `crash-reports/` 里对应的报告)
- `mods/` 文件夹的截图或文件列表
- 你的 OptiFine 版本(例如 `OptiFine_1.20.6_HD_U_J1_pre18`)

崩溃报告的末尾会有一段 `-- OptiFabric --`,包含 OptiFine 版本、jar 状态与重映射 jar 路径,请一并附上。

---

## 许可与致谢

本项目是 [Chocohead/OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)的移植,遵循 **MPL-2.0**。

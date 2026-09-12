# 手动发布清单(26.x,逐版一个发布条目)

版本号与标签统一为 `1.1.0+mc<版本>` / `v1.1.0+mc<版本>`。正文直接用 `release/notes/mc<版本>.md`
(已是 Markdown,含安装步骤、已知限制、该 jar 的尺寸与 SHA-256)。

> **26.x 与 1.21.x 是两条独立的发布线。** 26.1 起 Minecraft **未混淆**,构建与运行期路径都不同
> (见 `docs/PORT_26.x.md`),所以 26.x 的 jar 与 1.21.x 的 jar **不能互相替代**,各版本条目也各发各的。
> 1.21.x 那一线的清单见 [`MANUAL_RELEASE.md`](MANUAL_RELEASE.md)。

## 逐版数据

| 版本 | 版本号 / 标签 | jar | 字节 | SHA-256 | 正文 |
|---|---|---|---|---|---|
| 26.1.2 | 1.2.1+mc26.1.2 / v1.2.1+mc26.1.2 | dist\OptiFabric-Reforged-1.2.1+mc26.1.2.jar | 177165 | D7CD02A83E84D67C3D147A1DEE97D5ACAD2C6461D62D07D7EF53873D6D7E9352 | release/notes/mc26.1.2.md |

## 三处平台各自要填什么

### GitHub Release

- **Tag**:`v1.2.1+mc26.1.2`,Create new tag on publish,target 选 **`26.x` 分支的当前提交**;
- **Release title**:OptiFabric 1.2.1+mc26.1.2;
- **Describe this release**:粘贴 `release/notes/mc26.1.2.md`(Markdown);
- **Attach binaries**:上传 `dist\OptiFabric-Reforged-1.2.1+mc26.1.2.jar`(正文里已写好尺寸与 SHA-256,方便用户校验)。

### Modrinth

| 字段 | 填什么 |
|---|---|
| Name | OptiFabric 1.2.1+mc26.1.2 |
| Version number | 1.2.1+mc26.1.2 |
| Release channel | Release |
| Game versions | 只勾 **26.1.2** |
| Loaders | Fabric |
| Environment | 客户端 Required、服务端 Unsupported(OptiFine 只在客户端存在) |
| Dependencies | Fabric API 可标 required;OptiFine 无法上架,正文已写明需用户自备 |
| Changelog | 粘贴 `release/notes/mc26.1.2.md` |
| Files | 上传该版 jar |

### CurseForge

| 字段 | 填什么 |
|---|---|
| Display name | OptiFabric 1.2.1+mc26.1.2 |
| Release type | Release |
| Game version | Minecraft **26.1.2** 加 Fabric(只勾该版) |
| Changelog | 粘贴 `release/notes/mc26.1.2.md`,格式选 Markdown |
| File | 上传该版 jar |

> 若项目页面的**支持版本范围**里还没有 26.1.2,先在项目设置里加上,再传文件。

## 发布前自查

- [ ] `.\gradlew -p v26.x build --offline` 通过,且产物名是 `OptiFabric-Reforged-1.2.1+mc26.1.2.jar`;
- [ ] jar 里**没有** OptiFine 的类或资源、没有 `mappings/mappings.tiny`(26.x 本就不该有映射表);
- [ ] `LICENSE.txt_OptiFabric` 在,`fabric.mod.json` 的 `license` 仍是 `MPL-2.0`;
- [ ] `fabric.mod.json` 的 `minecraft` 是 `26.1.2`、`fabricloader` 是 `>=0.19.5`;
- [ ] 离线校验跑过:`powershell -NoProfile -ExecutionPolicy Bypass -File test-downloads\verify-26.ps1`,
      口径为 `Prepared … (0 skipped, 0 failed)`、`verified OK`、`ASM verifier problems: 0`、扫描器全 0;
- [ ] 真机在**干净实例**上实测一次(只要 Fabric API + OptiFabric + OptiFine):能进主界面、能进存档;
      提示:`<游戏目录>/.optifine/` 是缓存,删掉可强制重建,适合测"首次安装";
- [ ] 正文里写的 OptiFine 构建名与用户实际要装的 jar 名一致;
- [ ] 三处都挂上了 jar 本体,不要只贴正文;
- [ ] 发布说明里提醒用户需要 **Java 25**(26.1.2 的硬要求,与 1.21.x 的 Java 21 不同);
- [ ] 先在 GitHub 发一版看排版,再补 Modrinth / CurseForge。

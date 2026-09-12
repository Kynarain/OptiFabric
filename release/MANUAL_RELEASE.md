# 手动发布清单(1.21.x,逐版一个发布条目)

版本号与标签统一为 1.0.0+mc<版本> / v1.0.0+mc<版本>。正文直接用 release/notes/mc<版本>.md(已是 Markdown,含安装步骤、已知限制、该 jar 的尺寸与 SHA-256)。

> 建议不发 1.21.6 / 1.21.7:这两版的 OptiFine 预览构建自身有缺陷(启动即崩于 ShadersTex.initDynamicTextureNS),官方列表里已是最新构建。

## 逐版数据

| 版本 | 版本号 / 标签 | jar | 字节 | SHA-256 | 正文 |
|---|---|---|---|---|---|
| 1.21 | 1.0.0+mc1.21 / v1.0.0+mc1.21 | dist\OptiFabric-1.0.0+mc1.21.jar | 740125 | 6AFBA7A9433D31AB5DE602F2ECB0DBC1B2439C39CB6793F1C9DBAC9857509CFC | release/notes/mc1.21.md |
| 1.21.1 | 1.0.0+mc1.21.1 / v1.0.0+mc1.21.1 | dist\OptiFabric-1.0.0+mc1.21.1.jar | 740201 | 3D03A31AB98E15EA5ED37941E2F8D568FB8D6993E454643C6FEDA616BD9F0F05 | release/notes/mc1.21.1.md |
| 1.21.3 | 1.0.0+mc1.21.3 / v1.0.0+mc1.21.3 | dist\OptiFabric-1.0.0+mc1.21.3.jar | 771984 | AE2267948B72BDB0D46EC72A788A5F0543C53503FDDF22717CFED15FC082928F | release/notes/mc1.21.3.md |
| 1.21.4 | 1.0.0+mc1.21.4 / v1.0.0+mc1.21.4 | dist\OptiFabric-1.0.0+mc1.21.4.jar | 779768 | 279E448E441B5B7A7245DAAC3B34C1DAE71BC0764CD5D5B71A8CBDBD8A0918A4 | release/notes/mc1.21.4.md |
| 1.21.6 | 1.0.0+mc1.21.6 / v1.0.0+mc1.21.6 | dist\OptiFabric-1.0.0+mc1.21.6.jar | 819537 | C6E9594487DC9E07AE658FE4A3FB6D78F8028E9379F87A79C3FD345127E091C4 | release/notes/mc1.21.6.md |
| 1.21.7 | 1.0.0+mc1.21.7 / v1.0.0+mc1.21.7 | dist\OptiFabric-1.0.0+mc1.21.7.jar | 819587 | A318F06C65E4B8306843C2FA07C2236D72DC3C7989806832E0AF47274DBB265C | release/notes/mc1.21.7.md |
| 1.21.8 | 1.0.0+mc1.21.8 / v1.0.0+mc1.21.8 | dist\OptiFabric-1.0.0+mc1.21.8.jar | 819659 | 9FF796A99793897FE3B0BDA282CB8A2BEA8CA73684A25FFB189CCF178E4B1F2C | release/notes/mc1.21.8.md |
| 1.21.9 | 1.0.0+mc1.21.9 / v1.0.0+mc1.21.9 | dist\OptiFabric-1.0.0+mc1.21.9.jar | 847691 | A56A1A8005E777D4678856C86DF31D88180D05C06636186AF5B49A61B7100DB0 | release/notes/mc1.21.9.md |
| 1.21.10 | 1.0.0+mc1.21.10 / v1.0.0+mc1.21.10 | dist\OptiFabric-1.0.0+mc1.21.10.jar | 847710 | 60C4AFE91E1D7B8BDEB77E24B2C00B4D5D69AB535A869E51C7A2137FFBB1019E | release/notes/mc1.21.10.md |
| 1.21.11 | 1.0.0+mc1.21.11 / v1.0.0+mc1.21.11 | dist\OptiFabric-1.0.0+mc1.21.11.jar | 865254 | 9419F203E51C0C567B66C359F30DC486CCF76C34625177E3765F0B72D249F3DE | release/notes/mc1.21.11.md |

## 三个平台各自要填什么

### GitHub Release(每版一个)

- Tag:v1.0.0+mc<版本>,选 Create new tag on publish,target 选 mc1.21.x 分支的当前提交;
- Release title:OptiFabric 1.0.0+mc<版本>;
- Describe this release:粘贴 release/notes/mc<版本>.md 的内容(Markdown);
- Attach binaries:上传该版的 dist\OptiFabric-1.0.0+mc<版本>.jar(正文里已写好它的尺寸与 SHA-256,方便用户校验)。

### Modrinth(每版一个 version)

| 字段 | 填什么 |
|---|---|
| Name | OptiFabric 1.0.0+mc<版本> |
| Version number | 1.0.0+mc<版本> |
| Release channel | Release |
| Game versions | 只勾该版对应的那一个(如 1.21.8) |
| Loaders | Fabric |
| Environment | 客户端 Required、服务端 Unsupported(OptiFabric 加载的 OptiFine 只在客户端存在) |
| Dependencies | 建议把 Fabric API 标为 required;OptiFine 无法在 Modrinth 上架,正文里已写明需用户自备对应版本的 OptiFine |
| Changelog | 粘贴 release/notes/mc<版本>.md 的内容 |
| Files | 上传该版 jar |

### CurseForge(每版一个 file)

| 字段 | 填什么 |
|---|---|
| Display name | OptiFabric 1.0.0+mc<版本> |
| Release type | Release |
| Game version | Minecraft <版本> 加 Fabric(只勾该版) |
| Changelog | 粘贴 release/notes/mc<版本>.md 的内容,格式选 Markdown |
| File | 上传该版 jar |

## 发布前自查

- 每版正文里写的 OptiFine 构建名,必须与用户实际要装的 jar 名一致(正文已写明);
- 三处都要挂上 jar 本体,不要只贴正文;
- 1.21.9 / 1.21.10 的正文里那条「光影包请用 Complementary 等主流包」别删(Photon 在 1.21.6 起的 OptiFine 上不工作);
- 先在 GitHub 发一版看排版,再批量做其余版本。

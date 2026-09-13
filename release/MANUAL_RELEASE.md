# 手动发布清单(1.21.x,逐版一个发布条目)

> 本文件只覆盖 **1.21.x 线**。26.1 起 Minecraft 未混淆,构建与运行期路径都不同,那一线有自己的清单:
> [`MANUAL_RELEASE_26.x.md`](MANUAL_RELEASE_26.x.md)。两条线的 jar 不能互相替代。

版本号统一写成 `<版本>+mc<MC版本>`(jar 名与发布标题都用这一串);**GitHub 的标签是版本号本身,不带 `+mc`**
(与已发的 `v1.1.0` / `v1.2.0` / `v2.0.0` 一致)。正文直接用 release/notes/mc<版本>.md(已是 Markdown,含安装步骤、已知限制、该 jar 的尺寸与 SHA-256)。

> **一个版本号可以只覆盖一个 MC 版本**:1.1.0 那次把 10 个 jar 放在同一个 `v1.1.0` 条目里;
> 只改了某一个版本的行为时就单独给那个产物升版、单独发一个条目(1.1.1 就是只修 1.21.11 的抗锯齿后处理链),
> 其余版本继续停在原来的版本号。规则与命令见 [`docs/VERSIONING.md`](../docs/VERSIONING.md)。

> 建议不发 1.21.6 / 1.21.7:这两版的 OptiFine 预览构建自身有缺陷(启动即崩于 ShadersTex.initDynamicTextureNS),官方列表里已是最新构建。

## 逐版数据

| 版本 | 版本号 / 标签 | jar | 字节 | SHA-256 | 正文 |
|---|---|---|---|---|---|
| 1.21 | 1.1.0+mc1.21 / v1.1.0 | dist\OptiFabric-1.1.0+mc1.21.jar | 740125 | E6DE36890931E4F14D90FCC1798FE76D04849BE648CADB94B3B99D4837FB3DC6 | release/notes/mc1.21.md |
| 1.21.1 | 1.1.0+mc1.21.1 / v1.1.0 | dist\OptiFabric-1.1.0+mc1.21.1.jar | 740201 | E57C101210F04B2D07DE50705F5E48541533DF9086CA6218256C4C5799B3CD07 | release/notes/mc1.21.1.md |
| 1.21.3 | 1.1.2+mc1.21.3 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.3.jar | 771984 | 64A40A9FF482C1E23B3E7A6AA8B3651E5368130AA52B2ED9EA0AAB2781C46006 | release/notes/mc1.21.3.md |
| 1.21.4 | 1.1.2+mc1.21.4 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.4.jar | 779768 | B9B34D9E47E2FD978CA66251B8BEFC369FA19CDA3B95CBFA18617801069848D0 | release/notes/mc1.21.4.md |
| 1.21.6 | 1.1.2+mc1.21.6 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.6.jar | 819537 | 3293A37651447B55DFF0CC1CFCC7BC18706517FC270EDB53F3436AA56277E103 | release/notes/mc1.21.6.md |
| 1.21.7 | 1.1.2+mc1.21.7 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.7.jar | 819587 | 239B8D8C564EF1927367AC85E46337430EBC146EF6552AA6384D218AF4A84B64 | release/notes/mc1.21.7.md |
| 1.21.8 | 1.1.2+mc1.21.8 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.8.jar | 819659 | A253CF86C1ECD6BC399EDDB0AB0DD9FFE59E59FDA51CD593C0773B6BB3C3E949 | release/notes/mc1.21.8.md |
| 1.21.9 | 1.1.2+mc1.21.9 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.9.jar | 847691 | 0CE2ADC1F73A0B1F0D02D8C88A53D0B24023EA0301F93348908C75C3579C7BAD | release/notes/mc1.21.9.md |
| 1.21.10 | 1.1.2+mc1.21.10 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.10.jar | 847709 | 5D9DA5856E85D13A8EF924B86AC3BFE6B3512A1CF7AA381FE9081BCA8008ADDD | release/notes/mc1.21.10.md |
| 1.21.11 | 1.1.2+mc1.21.11 / v1.1.2 | dist\OptiFabric-1.1.2+mc1.21.11.jar | 865253 | B314887657CB78B1B4BD9A76CA1E0B57A0890C931BD9C05A3F8982D01CBE71D7 | release/notes/mc1.21.11.md |

## 三个平台各自要填什么

### GitHub Release(每版一个)

- Tag:v1.1.0+mc<版本>,选 Create new tag on publish,target 选 mc1.21.x 分支的当前提交;
- Release title:OptiFabric 1.1.0+mc<版本>;
- Describe this release:粘贴 release/notes/mc<版本>.md 的内容(Markdown);
- Attach binaries:上传该版的 dist\OptiFabric-1.1.0+mc<版本>.jar(正文里已写好它的尺寸与 SHA-256,方便用户校验)。

### Modrinth(每版一个 version)

| 字段 | 填什么 |
|---|---|
| Name | OptiFabric 1.1.0+mc<版本> |
| Version number | 1.1.0+mc<版本> |
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
| Display name | OptiFabric 1.1.0+mc<版本> |
| Release type | Release |
| Game version | Minecraft <版本> 加 Fabric(只勾该版) |
| Changelog | 粘贴 release/notes/mc<版本>.md 的内容,格式选 Markdown |
| File | 上传该版 jar |

## 发布前自查

- 每版正文里写的 OptiFine 构建名,必须与用户实际要装的 jar 名一致(正文已写明);
- 三处都要挂上 jar 本体,不要只贴正文;
- 1.21.9 / 1.21.10 的正文里那条「光影包请用 Complementary 等主流包」别删(Photon 在 1.21.6 起的 OptiFine 上不工作);
- 先在 GitHub 发一版看排版,再批量做其余版本。

# NOTE: keep this file UTF-8 WITH BOM. Windows PowerShell reads .ps1 as ANSI when there is no BOM, and the
# Chinese text below then mis-parses (a trailing quote gets eaten and the whole file fails to load).
<#
    把 dist/ 里十版 jar 逐个发到三个平台。逐版一个发布条目,版本号就是 1.0.0+mc<版本>。

    用法:
      # 先看要执行什么(不联网、不改远端)
      .\release\publish.ps1 -DryRun
      # 只发某一个版本
      .\release\publish.ps1 -Version 1.21.8
      # 真发(需要下面的凭据)
      .\release\publish.ps1

    凭据(用环境变量,不要写进文件):
      GitHub      : gh auth login 之后即可(或设 GITHUB_TOKEN)
      Modrinth    : $env:MODRINTH_TOKEN   (modrinth.com/settings/account 里创建 PAT,需 create versions 权限)
                    $env:MODRINTH_PROJECT_ID
      CurseForge  : $env:CURSEFORGE_TOKEN (curseforge.com/account/api-tokens)
                    $env:CURSEFORGE_PROJECT_ID

    说明:Modrinth 与 CurseForge 的提交都用 curl.exe 发 multipart(Windows 自带 PowerShell 5.1 没有 -Form 参数),
    元数据 JSON 会先写到 release\tmp\ 里,方便你看清楚究竟提交了什么。
#>
[CmdletBinding()]
param(
	[string]$Version = "all",
	[switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
# 1.21.x 与 26.x 是两条独立发布线,jar 与标签各发各的(见 release\MANUAL_RELEASE*.md)。
# 版本基数也按线分:1.21.x 的 1.1.0 已经发布出去、就此冻结,26.x 从 1.2.1 起。
$versions = @("1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1.2")
$defaultModVersion = "1.1.0"
$modVersions = @{ "26.1.2" = "1.2.1" }
# 26.x 那条线的产物名也是它自己的:它的 mod id 是 optifabric_reforged(见 v26.x/build.gradle),
# 所以 jar 名与 1.21.x 不同,发布脚本必须按线取文件名。
$defaultArtifact = "OptiFabric"
$modArtifacts = @{ "26.1.2" = "OptiFabric-Reforged" }

if ($Version -ne "all") {
	if ($versions -notcontains $Version) { throw "未知版本: $Version(可选:" + ($versions -join ", ") + ")" }
	$versions = @($Version)
}

$tmp = Join-Path $root "release\tmp"
New-Item -ItemType Directory -Force $tmp | Out-Null

#1.21.6 / 1.21.7 的 OptiFine 构建自身有缺陷,默认不推荐发布(要发就加 -Version 单独发)
$unsupported = @("1.21.6", "1.21.7")

foreach ($mc in $versions) {
	$modVersion = if ($modVersions.ContainsKey($mc)) { $modVersions[$mc] } else { $defaultModVersion }

	$artifact = if ($modArtifacts.ContainsKey($mc)) { $modArtifacts[$mc] } else { $defaultArtifact }
$jar = Join-Path $root "dist\$artifact-$modVersion+mc$mc.jar"
	$notes = Join-Path $root "release\notes\mc$mc.md"
	$tag = "v$modVersion+mc$mc"
	$title = "OptiFabric $modVersion+mc$mc"

	if (-not (Test-Path $jar)) { Write-Warning "跳过 $mc :没有 $jar"; continue }
	if (-not (Test-Path $notes)) { Write-Warning "跳过 $mc :没有 $notes"; continue }
	if ($unsupported -contains $mc) { Write-Warning "$mc 属于不推荐的版本(OptiFine 构建自身缺陷),仍会按你指定的版本号发布" }

	Write-Host "=== $title ==="

	#GitHub
	$gh = "gh release create `"$tag`" `"$jar`" --title `"$title`" --notes-file `"$notes`""
	if ($DryRun) { Write-Host "  [github]     $gh" }
	else {
		git push origin $tag
		Invoke-Expression $gh
	}

	#Modrinth
	$mrMeta = Join-Path $tmp "modrinth-$mc.json"
	$payload = [ordered]@{
		name           = $title
		version_number = "$modVersion+mc$mc"
		changelog      = [System.IO.File]::ReadAllText($notes, [System.Text.Encoding]::UTF8)
		dependencies   = @()
		game_versions  = @($mc)
		version_type   = "release"
		loaders        = @("fabric")
		client_side    = "required"       # OptiFabric 是客户端模组:OptiFine 本身只在客户端
		server_side    = "unsupported"
		featured       = $false
		project_id     = $env:MODRINTH_PROJECT_ID
		file_parts     = @("file")
		primary_file   = "file"
	}
	[System.IO.File]::WriteAllText($mrMeta, ($payload | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
	$mr = "curl.exe -sS -X POST https://api.modrinth.com/v2/version -H `"Authorization: $env:MODRINTH_TOKEN`" -F `"data=@$mrMeta;type=application/json`" -F `"file=@$jar`""
	if ($DryRun) { Write-Host "  [modrinth]   metadata -> $mrMeta"; Write-Host "               $mr" }
	else {
		if (-not $env:MODRINTH_TOKEN -or -not $env:MODRINTH_PROJECT_ID) { Write-Warning "  缺 MODRINTH_TOKEN / MODRINTH_PROJECT_ID,跳过 Modrinth" }
		else { Invoke-Expression $mr }
	}

	#CurseForge
	$cfMeta = Join-Path $tmp "curseforge-$mc.json"
	$meta = [ordered]@{
		changelog     = [System.IO.File]::ReadAllText($notes, [System.Text.Encoding]::UTF8)
		changelogType = "markdown"
		displayName   = $title
		releaseType   = "release"
		gameVersions  = @($mc, "Fabric")
	}
	[System.IO.File]::WriteAllText($cfMeta, ($meta | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
	$cf = "curl.exe -sS -X POST `"https://minecraft.curseforge.com/api/projects/$env:CURSEFORGE_PROJECT_ID/upload`" -H `"X-Api-Token: $env:CURSEFORGE_TOKEN`" -F `"metadata=@$cfMeta;type=application/json`" -F `"file=@$jar`""
	if ($DryRun) { Write-Host "  [curseforge] metadata -> $cfMeta"; Write-Host "               $cf" }
	else {
		if (-not $env:CURSEFORGE_TOKEN -or -not $env:CURSEFORGE_PROJECT_ID) { Write-Warning "  缺 CURSEFORGE_TOKEN / CURSEFORGE_PROJECT_ID,跳过 CurseForge" }
		else { Invoke-Expression $cf }
	}
}

Write-Host ""
Write-Host "完成。逐版备注在 release\notes\,元数据留档在 release\tmp\。"
if ($DryRun) { Write-Host "这是 -DryRun:刚才什么都没发。" }

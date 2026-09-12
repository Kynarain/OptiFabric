# 版本号自动化(SemVer 2.0.0,见 docs/VERSIONING.md)
#
# 一次版本号改动要同时落在 9 个地方:两个项目的 gradle.properties、release\publish.ps1 的映射,以及
# README / CHANGELOG / docs\ / release\notes\ / release\MANUAL_RELEASE*.md / dist\README.txt 里所有
# "<版本>+mc" 的写法。手改漏一处就会出现文档与产物对不上,所以只走这个脚本。
#
#   .\release\version.ps1                                  # 看:两条线当前版本,以及三类递增各会变成什么
#   .\release\version.ps1 -Line 26.x -Kind minor           # 1.2.1 -> 1.3.0(真正写入)
#   .\release\version.ps1 -Line 26.x -Kind patch -DryRun   # 只看结果,不写文件
#   .\release\version.ps1 -Line 1.21.x -Kind patch         # 1.1.0 -> 1.1.1(那条线的 jar 名不含 -Reforged)
#   .\release\version.ps1 -Line 26.x -Set 1.3.0-beta.1     # 直接指定(校验格式与优先级)
#   .\release\version.ps1 -Line 26.x -Part                 # 只打印当前版本号(给别的脚本用)
#   .\release\version.ps1 -Line 26.x -RecordDigest         # 构建之后:把产物的字节数与 SHA-256 写回文档
#
# 递增依据 SemVer:patch = 向下兼容的修正(minor 与 patch 归零规则见规范 §7/§8),
# minor = 向下兼容的新功能,patch 号归零;major = 不兼容修改,次版本号与修订号都归零。
# 新版本必须比当前版本**优先级更高**(§11),否则拒绝写入(要硬来加 -Force)。
[CmdletBinding()]
param(
	# Which release line to version: 26.x (project v26.x) or 1.21.x (project v1.21.x).
	[ValidateSet("26.x", "1.21.x")]
	[string]$Line,
	# Which part of the version to increment, per SemVer: major | minor | patch.
	[ValidateSet("major", "minor", "patch")]
	[string]$Kind,
	# Set an explicit version instead of incrementing one (validated like any other).
	[string]$Set,
	# Print the line's current version and nothing else.
	[switch]$Part,
	# Show what would change without writing anything.
	[switch]$DryRun,
	# Write the built jar's size and SHA-256 into the documents that record them.
	[switch]$RecordDigest,
	# Allow a new version that is not greater than the current one.
	[switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# SemVer 2.0.0, second official regex (the one without named groups): major, minor, patch, prerelease, build.
$semverRegex = '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$'

# Per line: the project directory, the artifact prefix (the 26.x line renamed itself, see v26.x/build.gradle)
# and the document files that carry "<version>+mc" strings.
$lines = @{
	"26.x" = @{
		project  = "v26.x"
		artifact = "OptiFabric-Reforged"
		mc       = "26.1.2"
	}
	"1.21.x" = @{
		project  = "v1.21.x"
		artifact = "OptiFabric"
		mc       = "1.21.11"
	}
}

$documentFiles = @(
	"README.md",
	"CHANGELOG.md",
	"docs/DESCRIPTION.md",
	"docs/PORT_26.x.md",
	"docs/PUBLISHING.md",
	"release/MANUAL_RELEASE.md",
	"release/MANUAL_RELEASE_26.x.md",
	"release/notes/mc26.1.2.md",
	"release/notes/mc1.21.x.md",
	"release/notes/mc1.21.11.md",
	"dist/README.txt"
)
# docs/VERSIONING.md is deliberately absent: its version numbers are examples of the rules, not statements about
# the current release, so a bump must not rewrite them.

function Get-CurrentVersion([string]$line) {
	$properties = Join-Path $root (Join-Path $lines[$line].project "gradle.properties")
	$match = Select-String -Path $properties -Pattern '^mod_version_base=(.+)$' | Select-Object -First 1
	if (-not $match) { throw "找不到 $properties 里的 mod_version_base" }

	return $match.Matches[0].Groups[1].Value.Trim()
}

function Parse-Version([string]$text) {
	if ($text -notmatch $semverRegex) { throw "不是合法的语义化版本号: '$text'(见 https://semver.org/lang/zh-CN/)" }

	return @{
		text   = $text
		major  = [int]$Matches[1]
		minor  = [int]$Matches[2]
		patch  = [int]$Matches[3]
		pre    = $Matches[4]
		build  = $Matches[5]
	}
}

# SemVer §11: compare major, minor and patch numerically, then prerelease presence (a prerelease is lower),
# then the dot-separated prerelease identifiers. Build metadata is ignored, as the spec requires.
function Compare-Version($a, $b) {
	foreach ($part in @("major", "minor", "patch")) {
		if ($a[$part] -ne $b[$part]) { return [Math]::Sign($a[$part] - $b[$part]) }
	}

	$preA = [string]$a.pre
	$preB = [string]$b.pre
	if ($preA -eq $preB) { return 0 }
	if ($preA -eq "") { return 1 }    # a release outranks a prerelease
	if ($preB -eq "") { return -1 }

	$idsA = $preA.Split(".")
	$idsB = $preB.Split(".")
	for ($i = 0; $i -lt [Math]::Min($idsA.Count, $idsB.Count); $i++) {
		$numA = $idsA[$i] -match '^\d+$'
		$numB = $idsB[$i] -match '^\d+$'
		if ($numA -and $numB) {
			if ([int]$idsA[$i] -ne [int]$idsB[$i]) { return [Math]::Sign([int]$idsA[$i] - [int]$idsB[$i]) }
		} elseif ($numA -ne $numB) {
			return $(if ($numA) { -1 } else { 1 })   # numeric identifiers rank lower
		} elseif ($idsA[$i] -cne $idsB[$i]) {
			return [Math]::Sign([string]::CompareOrdinal($idsA[$i], $idsB[$i]))
		}
	}

	return [Math]::Sign($idsA.Count - $idsB.Count)
}

function Next-Version([string]$current, [string]$kind) {
	$v = Parse-Version $current

	switch ($kind) {
		"major" { return "$($v.major + 1).0.0" }
		"minor" { return "$($v.major).$($v.minor + 1).0" }
		"patch" { return "$($v.major).$($v.minor).$($v.patch + 1)" }
	}

	throw "未知的递增类型: $kind"
}

# Rewrites one file in place, keeping its encoding (and its BOM, where it has one - a BOM-less .ps1 with Chinese
# text fails to parse under Windows PowerShell).
function Update-File([string]$path, [hashtable]$pairs) {
	$full = Join-Path $root $path
	if (-not (Test-Path $full)) { return 0 }

	$bytes = [System.IO.File]::ReadAllBytes($full)
	$bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
	$text = [System.Text.Encoding]::UTF8.GetString($bytes)
	if ($bom) { $text = $text.Substring(1) }

	$count = 0
	foreach ($key in $pairs.Keys) {
		$hits = ([regex]::Matches($text, [regex]::Escape($key))).Count
		if ($hits -gt 0) {
			$text = $text.Replace($key, $pairs[$key])
			$count += $hits
		}
	}

	if ($count -gt 0 -and -not $DryRun) {
		[System.IO.File]::WriteAllText($full, $text, (New-Object System.Text.UTF8Encoding($bom)))
	}

	return $count
}

function Get-BuiltJar([string]$line, [string]$version) {
	$name = "$($lines[$line].artifact)-$version+mc$($lines[$line].mc).jar"

	return @{ name = $name; path = (Join-Path $root (Join-Path $lines[$line].project "build/libs/$name")) }
}

# ---------------------------------------------------------------- listing mode

if (-not $Line -and -not $RecordDigest) {
	foreach ($name in @("26.x", "1.21.x")) {
		$current = Get-CurrentVersion $name
		$parsed = Parse-Version $current
		Write-Host ("{0,-8} 当前 {1,-10} 产物 {2}-{1}+mc{3}.jar" -f $name, $current, $lines[$name].artifact, $lines[$name].mc)
		Write-Host ("          按 SemVer 递增:patch -> {0}   minor -> {1}   major -> {2}" -f `
			(Next-Version $current "patch"), (Next-Version $current "minor"), (Next-Version $current "major"))
	}
	Write-Host ""
	Write-Host "用法见 docs\VERSIONING.md;要真改就加 -Line 与 -Kind(或 -Set)。"
	return
}

if (-not $Line) { throw "-RecordDigest 需要同时给 -Line" }

# ---------------------------------------------------------------- digest mode

if ($RecordDigest) {
	$current = Get-CurrentVersion $Line
	$jar = Get-BuiltJar $Line $current

	if (-not (Test-Path $jar.path)) {
		throw "还没有构建产物:$($jar.path)`n先跑 .\gradlew -p $($lines[$Line].project) build"
	}

	$size = (Get-Item $jar.path).Length
	$hash = (Get-FileHash $jar.path -Algorithm SHA256).Hash
	$sizeText = "$size 字节"
	Write-Host "产物 $($jar.name)"
	Write-Host "  $sizeText"
	Write-Host "  SHA-256 $hash"

	# Only the paragraphs that mention *this* line's artifact or version are rewritten. A blanket search for
	# "<n> 字节" would also hit the ten 1.1.0-era figures in dist\README.txt and in the changelog, which belong
	# to the other release line - one jar per Minecraft version means those numbers are all different.
	$artifactPattern = [regex]::Escape($lines[$Line].artifact)
	$versionPattern = [regex]::Escape("$current+mc$($lines[$Line].mc)")
	$sizePattern = '([\d,]{4,})(\s*字节)'
	$hashPattern = '([0-9A-Fa-f]{64})'

	$touched = 0
	$files = 0

	foreach ($file in $documentFiles) {
		$full = Join-Path $root $file
		if (-not (Test-Path $full)) { continue }

		$bytes = [System.IO.File]::ReadAllBytes($full)
		$bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
		$text = [System.Text.Encoding]::UTF8.GetString($bytes)
		if ($bom) { $text = $text.Substring(1) }

		$paragraphs = [regex]::Split($text, '(\r?\n[ \t]*\r?\n)')
		$changes = 0
		$previousMatched = $false

		for ($i = 0; $i -lt $paragraphs.Count; $i++) {
			$paragraph = $paragraphs[$i]

			# The split keeps the blank-line separators as elements of their own; they must not clear the
			# "previous paragraph named the artifact" flag, or a digest written under a blank line gets missed.
			if ($paragraph -match '^(\r?\n[ \t]*\r?\n)$') { continue }

			# A paragraph about a *different* version of this artifact is history (an earlier release's figures),
			# not the current state: leave it alone. The changelog keeps exactly such a section. The lookahead
			# matters: without it this also matches this release's own name, and then nothing is ever updated.
			$otherVersion = [regex]::IsMatch($paragraph,
				[regex]::Escape($lines[$Line].artifact) + '-(?!' + [regex]::Escape($current) + ')\d+\.\d+\.\d+[^\s`]*\+mc')
			$mentionsCurrent = [regex]::IsMatch($paragraph, [regex]::Escape($lines[$Line].artifact) + '-' + [regex]::Escape("$current+mc")) -or
				$paragraph -match $versionPattern
			$matched = $mentionsCurrent -and -not $otherVersion

			# The digest is often written on its own line under the file name, separated by a blank line; treat such
			# a paragraph as the continuation of the one that named the artifact.
			if (-not $matched -and $paragraph -match '^[`\s]*SHA-256[:`\s]*[0-9A-Fa-f]{64}') { $matched = $previousMatched }

			if (-not $matched) { $previousMatched = $false; continue }
			$previousMatched = $true

			$before = $paragraph
			$after = [regex]::Replace($before, $sizePattern, { param($m) "$size 字节" })
			# In a markdown table the size is a bare cell next to the digest, with no "字节" after it.
			$after = [regex]::Replace($after, '\|\s*[\d,]{4,}\s*\|', { param($m) "| $size |" })
			$after = [regex]::Replace($after, $hashPattern, $hash)
			if ($after -ne $before) { $paragraphs[$i] = $after; $changes++ }
		}

		if ($changes -gt 0) {
			$files++
			$touched += $changes
			if (-not $DryRun) {
				[System.IO.File]::WriteAllText($full, ($paragraphs -join ""), (New-Object System.Text.UTF8Encoding($bom)))
			}
			Write-Host "  $file  $changes 段"
		}
	}

	Write-Host ""
	if ($DryRun) { Write-Host "[DryRun] 会更新 $files 个文件、$touched 段" }
	else { Write-Host "已更新 $files 个文件、$touched 段;请 git diff 复核(尤其 dist\README.txt 与 CHANGELOG)" }
	return
}

# ---------------------------------------------------------------- version mode

$current = Get-CurrentVersion $Line

if ($Part) { Write-Output $current; return }

if ($Set -and $Kind) { throw "-Set 与 -Kind 只能给一个" }
if (-not $Set -and -not $Kind) { throw "给 -Kind major|minor|patch,或用 -Set 指定版本号" }

$target = if ($Set) { (Parse-Version $Set).text } else { Next-Version $current $Kind }
$old = Parse-Version $current
$new = Parse-Version $target

$order = Compare-Version $new $old
if ($order -eq 0) { throw "新版本号与当前相同($current);SemVer §3:已发行版本的内容不能改,只能发新版本" }
if ($order -lt 0 -and -not $Force) { throw "新版本号 $target 低于当前版本 $current(SemVer §11);确实要降就加 -Force" }
if ($new.build) { Write-Host "提示:版本号里带了编译信息(+$($new.build));按 §10 它不参与优先级比较。" }

Write-Host "版本号:$current -> $target  ($Line 线,$($lines[$Line].project) 项目)"
if ($DryRun) { Write-Host "[DryRun] 不写任何文件" }

# 1. the project's own version
$properties = Join-Path $root (Join-Path $lines[$Line].project "gradle.properties")
$count = Update-File (Join-Path $lines[$Line].project "gradle.properties") @{ "mod_version_base=$current" = "mod_version_base=$target" }
Write-Host ("  {0,-45} {1} 处" -f "$($lines[$Line].project)/gradle.properties", $count)

# 2. the release script's per-line version map (and its default for the 1.21.x line)
$publishPairs = @{}
if ($Line -eq "26.x") {
	$publishPairs['@{ "26.1.2" = "' + $current + '" }'] = '@{ "26.1.2" = "' + $target + '" }'
} else {
	$publishPairs['$defaultModVersion = "' + $current + '"'] = '$defaultModVersion = "' + $target + '"'
}
$count = Update-File "release/publish.ps1" $publishPairs
Write-Host ("  {0,-45} {1} 处" -f "release/publish.ps1", $count)
if ($count -eq 0) { Write-Host "    (没找到该线在 publish.ps1 里的映射,请手动确认)" }

# 3. every "<version>+mc" reference in the documents. Matching on the bare version covers both the artifact
#    names (OptiFabric-Reforged-<version>+mc<mc>.jar) and the version fields of the release checklists.
$total = 0
foreach ($file in $documentFiles) {
	$count = Update-File $file @{ "$current+mc" = "$target+mc" }
	if ($count -gt 0) { Write-Host ("  {0,-45} {1} 处" -f $file, $count) }
	$total += $count
}
Write-Host "  文档合计 $total 处"

$jar = Get-BuiltJar $Line $target
Write-Host ""
Write-Host "接下来:"
Write-Host "  1. .\gradlew -p $($lines[$Line].project) build --offline"
Write-Host "     -> 产物名应是 $($jar.name)"
Write-Host "  2. .\release\version.ps1 -Line $Line -RecordDigest"
Write-Host "     -> 把尺寸与 SHA-256 写回文档(dist/README.txt、CHANGELOG、release/notes/)"
Write-Host "  3. 按 release\MANUAL_RELEASE*.md 发布;tag 名与产物名成对写,别只写版本号"
Write-Host "  4. 已发布过的版本号不得复用(§3);内容要改就发新版本"

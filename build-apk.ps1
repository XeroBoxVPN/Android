# Builds the XeroBox Android APKs into sing-box-for-android\dist\:
#   XeroBox-<version>-arm64-v8a-<debug|release>.apk   (phones)
#   XeroBox-<version>-x86_64-<debug|release>.apk      (emulators)
#   SHA256SUMS.txt
# Every APK is checked with apksigner, and the signing certificate's SHA-256 is printed.
# Release builds refuse to start without our own key (see SIGNING.md).
#
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1                 debug build
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release        release build
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Libbox         rebuild the Go core first
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Clean          clean Gradle outputs first
param(
  [switch]$Release,
  [switch]$Libbox,
  [switch]$Clean
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$dist = Join-Path $root "dist"
$type = if ($Release) { "release" } else { "debug" }

function Step($msg) { Write-Host "`n== $msg" -ForegroundColor Cyan }

# Reads a key from local.properties, or from the LOCAL_PROPERTIES secret (base64) on a
# build server: the same two places app\build.gradle.kts reads.
function Get-Prop($name) {
  $text = ""
  if ($env:LOCAL_PROPERTIES) { $text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($env:LOCAL_PROPERTIES)) + "`n" }
  $lp = Join-Path $root "local.properties"
  if (Test-Path $lp) { $text += Get-Content $lp -Raw }
  foreach ($line in $text -split "`r?`n") {
    if ($line -match "^\s*$([regex]::Escape($name))\s*=\s*(.*)$") { return $Matches[1].Trim() }
  }
  return ""
}

# ---- toolchain -----------------------------------------------------------------------
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:LOCALAPPDATA "AndroidBuildEnv\jdk-17" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { throw "JDK 17 not found at $env:JAVA_HOME (set JAVA_HOME)" }
if (-not (Test-Path $env:ANDROID_HOME)) { throw "Android SDK not found at $env:ANDROID_HOME (set ANDROID_HOME)" }
$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Directory | Sort-Object { [version]($_.Name -replace '[^\d.].*$', '') } | Select-Object -Last 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

$props = Get-Content (Join-Path $root "version.properties") | ConvertFrom-StringData
$version = $props.VERSION_NAME
Write-Host "XeroBox Android $version ($type), versionCode $($props.VERSION_CODE)"

# ---- signing pre-check ----------------------------------------------------------------
if ($Release) {
  Step "Server address"
  # The app has no server field: a release without XEROBOX_SERVER_URL could never sign in.
  $server = Get-Prop "XEROBOX_SERVER_URL"
  if (-not ($server -match '^https://')) { throw "Release needs XEROBOX_SERVER_URL=https://... in local.properties (the built-in XeroBox server)." }
  Write-Host "  server $server"
  if (-not (Get-Prop "XEROBOX_GITHUB_REPO")) { Write-Host "  XEROBOX_GITHUB_REPO not set: no GitHub fallback for updates" -ForegroundColor Yellow }

  Step "Signing key"
  $missing = @("KEYSTORE_PASS", "ALIAS_NAME", "ALIAS_PASS") | Where-Object { -not (Get-Prop $_) }
  if ($missing) { throw "Release needs $($missing -join ', ') in local.properties. See SIGNING.md." }
  $ks = Get-Prop "KEYSTORE_FILE"
  if (-not $ks) { $ks = "release.keystore" }
  $ksPath = if ([IO.Path]::IsPathRooted($ks)) { $ks } else { Join-Path $root "app\$ks" }
  if (-not (Test-Path $ksPath)) { throw "Keystore not found: $ksPath. See SIGNING.md." }
  Write-Host "  keystore $ksPath (passwords not shown)"
}

# ---- optional: Go core (libbox.aar) ------------------------------------------------------
if ($Libbox) {
  Step "Go core (libbox.aar)"
  # gomobile must run from Git Bash: it panics on PowerShell's "=C:" environment variable.
  $bash = Join-Path $env:ProgramFiles "Git\bin\bash.exe"
  if (-not (Test-Path $bash)) { throw "Git Bash not found at $bash" }
  $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME "ndk") -Directory | Sort-Object Name | Select-Object -Last 1
  $env:ANDROID_NDK_HOME = $ndk.FullName
  $script = @'
set -e
cd ../sing-box
rm -rf build
export PATH="$(cygpath "$JAVA_HOME")/bin:$HOME/go/bin:$PATH"
go run ./cmd/internal/build_libbox -target android
'@
  Push-Location $root
  & $bash -lc ("cd '" + ($root -replace '\\', '/') + "' && " + ($script -replace "`r", ""))
  $code = $LASTEXITCODE
  Pop-Location
  if ($code) { throw "libbox build failed" }
  Copy-Item (Join-Path $root "..\sing-box\libbox.aar") (Join-Path $root "app\libs\libbox.aar") -Force
  Write-Host "  libbox.aar updated"
}

# ---- Gradle ---------------------------------------------------------------------------
Push-Location $root
try {
  $task = if ($Release) { "assembleOtherRelease" } else { "assembleOtherDebug" }
  $tasks = @()
  if ($Clean) { $tasks += "clean" }
  $tasks += $task
  Step "Gradle $($tasks -join ' ')"
  & .\gradlew.bat --console=plain @tasks
  if ($LASTEXITCODE) { throw "Gradle build failed" }
}
finally {
  Pop-Location
}

# ---- collect, verify, checksum --------------------------------------------------------
Step "Verify and package"
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force $dist | Out-Null
$outDir = Join-Path $root "app\build\outputs\apk\other\$type"
$apks = Get-ChildItem $outDir -Filter "*.apk"
if (-not $apks) { throw "No APKs in $outDir" }

$sums = @()
foreach ($apk in $apks) {
  $abi = if ($apk.Name -match "(arm64-v8a|armeabi-v7a|x86_64|x86|universal)") { $Matches[1] } else { "all" }
  $name = "XeroBox-$version-$abi-$type.apk"
  $target = Join-Path $dist $name
  Copy-Item $apk.FullName $target

  $info = & $apksigner verify --print-certs $target 2>&1 | Out-String
  if ($LASTEXITCODE) { throw "apksigner rejected ${name}:`n$info" }
  $dn = ([regex]::Match($info, "certificate DN: (.+)")).Groups[1].Value.Trim()
  $digest = ([regex]::Match($info, "certificate SHA-256 digest: ([0-9a-f]+)")).Groups[1].Value
  Write-Host ("  {0}  {1:N1} MB" -f $name, ($apk.Length / 1MB))
  Write-Host "    signer  $dn"
  Write-Host "    SHA-256 $digest"
  if ($Release -and $dn -match "Android Debug") { throw "$name is signed with the debug key. Do not ship." }

  $sums += "$((Get-FileHash $target -Algorithm SHA256).Hash.ToLower())  $name"
}
$sums | Set-Content -Encoding ascii (Join-Path $dist "SHA256SUMS.txt")
if (-not $Release) { Write-Host "`n  Debug build: for testing only, not for customers." -ForegroundColor Yellow }
Write-Host "`nDone: $dist" -ForegroundColor Green

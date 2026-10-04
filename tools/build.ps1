# 周期日程 APK 构建脚本（手动管线：aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner）
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk  = Join-Path $root "_sdk"
$bt   = Join-Path $sdk "build-tools\34.0.0"
$platform = Join-Path $sdk "platforms\android-34\android.jar"
$build = Join-Path $root "build"

# ---- JDK ----
$jdkDir = Get-ChildItem $sdk -Directory -Filter "jdk-17*" | Select-Object -First 1
if (-not $jdkDir) { $jdkDir = Get-ChildItem (Join-Path $root "_dl") -Directory -Filter "jdk-17*" | Select-Object -First 1 }
if (-not $jdkDir) { throw "JDK 17 not found" }
$env:JAVA_HOME = $jdkDir.FullName
$env:PATH = "$($jdkDir.FullName)\bin;$env:PATH"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

# ---- 清理 ----
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$build\gen", "$build\classes", "$build\dex" | Out-Null

# ---- 1. aapt2 compile ----
Write-Host "== aapt2 compile =="
& "$bt\aapt2.exe" compile --dir (Join-Path $root "app\res") -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# ---- 2. aapt2 link ----
Write-Host "== aapt2 link =="
& "$bt\aapt2.exe" link `
    -o "$build\unsigned.apk" `
    -I $platform `
    --manifest (Join-Path $root "app\AndroidManifest.xml") `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name "1.0" `
    --java "$build\gen" `
    --auto-add-overlay `
    "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ---- 3. javac ----
Write-Host "== javac =="
$sources = @()
$sources += Get-ChildItem "$build\gen" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$sources += Get-ChildItem (Join-Path $root "app\src") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 8 -Xlint:-options -classpath $platform -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# ---- 4. d8 (dex) ----
Write-Host "== d8 =="
$classFiles = Get-ChildItem "$build\classes" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --release --lib $platform --min-api 24 --output "$build\dex" $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# ---- 5. 打包 classes.dex ----
Write-Host "== aapt add =="
Push-Location $build
try {
    Copy-Item "$build\dex\classes.dex" "$build\classes.dex" -Force
    & "$bt\aapt.exe" add "unsigned.apk" "classes.dex" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "aapt add failed" }
} finally { Pop-Location }

# ---- 6. zipalign ----
Write-Host "== zipalign =="
& "$bt\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---- 7. keystore ----
$ks = Join-Path $build "release.keystore"
if (-not (Test-Path $ks)) {
    Write-Host "== keytool 生成签名密钥 =="
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & keytool -genkeypair -keystore $ks -alias cyclecal -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass cyclecal123 -keypass cyclecal123 `
        -dname "CN=CycleCal, O=CycleCal" 2>&1 | Out-Null
    $ErrorActionPreference = $oldEap
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

# ---- 8. apksigner ----
Write-Host "== apksigner =="
$outApk = Join-Path $root "CycleCal.apk"
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:cyclecal123 --key-pass pass:cyclecal123 `
    --out $outApk "$build\aligned.apk" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { $ErrorActionPreference = $oldEap; throw "apksigner failed" }

& "$bt\apksigner.bat" verify --print-certs $outApk 2>&1 | Out-String
$ErrorActionPreference = $oldEap

$size = [math]::Round((Get-Item $outApk).Length / 1MB, 2)
Write-Host ""
Write-Host "BUILD OK: $outApk ($size MB)"

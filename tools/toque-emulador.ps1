# Toca no elemento do EMULADOR (nunca do A54) cujo texto contém -Texto, ou no N-ésimo campo de texto (-Campo N). Opcional -Escrever.
param([string]$Texto, [int]$Campo = 0, [string]$Escrever)
$e = 'emulator-5554'
$tmp = Join-Path $env:TEMP 'kupila-ui.xml'
adb -s $e shell uiautomator dump /sdcard/kupila-ui.xml | Out-Null
adb -s $e pull /sdcard/kupila-ui.xml $tmp | Out-Null
$nos = ([xml](Get-Content $tmp -Raw -Encoding UTF8)).SelectNodes('//node')
$alvo = if ($Campo -gt 0) { @($nos | Where-Object { $_.class -like '*EditText*' })[$Campo - 1] } else { $nos | Where-Object { $_.text -like "*$Texto*" -or $_.'content-desc' -like "*$Texto*" } | Select-Object -First 1 }
if (-not $alvo) { Write-Output "não encontrado: $Texto$Campo"; exit 1 }
if ($alvo.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
    $x = [int](([int]$matches[1] + [int]$matches[3]) / 2); $y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
    adb -s $e shell input tap $x $y
    if ($Escrever) { Start-Sleep -Milliseconds 400; adb -s $e shell input text ($Escrever -replace '#', '\#' -replace ' ', '%s') }
    Write-Output "tocado ($x,$y)"
}

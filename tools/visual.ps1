# Iteração visual: compila o APK de depuração apontado aos emuladores Firebase, REPÕE a flag local e instala só no emulador Android.
# ATENÇÃO: depois de correr, o app-debug.apk em build/ aponta para o emulador — recompilar antes de publicar (tools\gradle.ps1).
$p = 'android\kupila-firebase.properties'
$orig = [IO.File]::ReadAllText($p)
try {
    [IO.File]::WriteAllText($p, ($orig -replace 'usarEmulador=false', 'usarEmulador=true'), (New-Object Text.UTF8Encoding $false))
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\gradle.ps1 visual.log assembleDebug | Select-Object -Last 1
} finally { [IO.File]::WriteAllText($p, $orig, (New-Object Text.UTF8Encoding $false)) }
Select-String -Path docs\build-logs\visual.log -Pattern '^e: ' | Select-Object -First 30 | ForEach-Object { $_.Line -replace '.*gestaonegocios/', '' }
$e = 'emulator-5554'
adb -s $e install -r android\app\build\outputs\apk\debug\app-debug.apk | Select-Object -Last 1
foreach ($porta in 9099, 8080) { adb -s $e reverse tcp:$porta tcp:$porta | Out-Null }
adb -s $e shell am start -n ao.kupila.gestaonegocios.dev/ao.kupila.gestaonegocios.MainActivity | Out-Null

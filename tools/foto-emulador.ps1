# Captura o ecrã do EMULADOR Android (nunca do A54) para comparação visual com docs/build-logs/referencia.
# Uso: powershell -File tools\foto-emulador.ps1 <saida.png>
param([Parameter(Mandatory)][string]$Saida)
$e = 'emulator-5554'
adb -s $e shell screencap -p /sdcard/kupila-foto.png | Out-Null
adb -s $e pull /sdcard/kupila-foto.png $Saida | Out-Null
adb -s $e shell rm /sdcard/kupila-foto.png | Out-Null

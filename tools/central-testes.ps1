# Testes do Central: unitários (Node) e de integração no Firebase Emulator (Auth + Firestore).
# No Windows o processo Java do emulador do Firestore fica por vezes órfão após `emulators:exec`; este script
# encerra apenas processos java que estejam a ESCUTAR nas portas do emulador definidas em backend/firebase.json.
param([string]$Log = 'central-testes.log')
$raiz = Split-Path -Parent $PSScriptRoot
$portas = 8080, 9099, 9150, 4400, 4500
function Libertar {
    Get-NetTCPConnection -LocalPort $portas -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq 'java') { Stop-Process -Id $p.Id -Force -Confirm:$false -ErrorAction SilentlyContinue }
    }
    Start-Sleep -Seconds 1
}
Push-Location "$raiz\backend\functions"
try {
    "== unitários"; node --test "test/*.test.js" 2>&1 | Select-String -Pattern '(tests|pass|fail) \d' | ForEach-Object Line
    Libertar
    Set-Location "$raiz\backend"
    cmd /c "firebase emulators:exec --only auth,firestore --project demo-kupila ""node --test --test-concurrency=1 functions/test/integracao/*.test.js"" > ""$raiz\docs\build-logs\$Log"" 2>&1"
    $codigo = $LASTEXITCODE
    "== integração (emulador) exit=$codigo"
    Get-Content "$raiz\docs\build-logs\$Log" | Select-String -Pattern '^\s*(✔|✖)|(tests|pass|fail) \d|AssertionError|Error:' | ForEach-Object Line
} finally { Libertar; Pop-Location }

# Executa o Gradle do projecto Android através de uma unidade virtual (o caminho real contém "ã",
# o que impede a execução dos testes unitários — mesma solução usada pelo PDV Digital com P:).
# Uso: powershell -File tools\gradle.ps1 <log> <tarefas gradle...>
param([Parameter(Mandatory)][string]$Log, [Parameter(ValueFromRemainingArguments)][string[]]$Tarefas)
$raiz = Split-Path -Parent $PSScriptRoot
$letra = 'K:'
$criada = $false
if (-not (Test-Path "$letra\android\gradlew.bat")) {
    if (Test-Path "$letra\") { throw "A unidade $letra já está em uso por outro destino." }
    subst $letra $raiz; $criada = $true
}
try {
    New-Item -ItemType Directory -Force "$letra\docs\build-logs" | Out-Null
    $args = ($Tarefas -join ' ')
    cmd /c "cd /d $letra\android & $letra\android\gradlew.bat --no-daemon $args > $letra\docs\build-logs\$Log 2>&1"
    $codigo = $LASTEXITCODE
    Get-Content "$letra\docs\build-logs\$Log" -Tail 30
    "exit=$codigo"
} finally {
    if ($criada) { subst $letra /d }
}

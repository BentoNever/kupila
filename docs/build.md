# Build, testes e instalação

## Requisitos
JDK 27 (`C:\Program Files\Java\jdk-27`), Android SDK (compile/target 37, min 26), Node 22+, firebase-tools, Java 21+ para o emulador.

## Android
O caminho do projecto contém "ã"; os testes JVM correm através de uma unidade virtual:
```
powershell -File tools\gradle.ps1 <log> assembleDebug testDebugUnitTest
```
APK de desenvolvimento: `android/app/build/outputs/apk/debug/app-debug.apk` (`ao.kupila.gestaonegocios.dev`).

### Configuração Firebase (nunca no repositório)
`android/kupila-firebase.properties` (ignorado pelo git): `projectId`, `applicationId`, `apiKey`, `storageBucket`, `gcmSenderId`,
`modo` (`spark` por omissão, `api` = Blaze), `functionsRegion`, `emulatorHost`.
Sem o ficheiro, o build de depuração usa o **Firebase Emulator** (projecto `demo-kupila`) em `127.0.0.1`; o release fica em
"serviço não configurado".

### Testar num telemóvel por USB
```
cd backend && firebase emulators:start --only auth,firestore,storage --project demo-kupila
adb reverse tcp:9099 tcp:9099 ; adb reverse tcp:8080 tcp:8080 ; adb reverse tcp:9199 tcp:9199 ; adb reverse tcp:5001 tcp:5001
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Central
```
powershell -File tools\central-testes.ps1
```
Unitários (motor financeiro) + integração no emulador (regras do modo Spark e Functions do modo Blaze).

## Assinatura de produção
Propriedades Gradle fora do repositório: `KUPILA_KEYSTORE`, `KUPILA_KEYSTORE_SENHA`, `KUPILA_CHAVE`, `KUPILA_CHAVE_SENHA`.

## Publicação
```
node tools/publicar-github.cjs --apk <apk> --tag <tag> --titulo <título> --notas <notas.md> --prerelease
```

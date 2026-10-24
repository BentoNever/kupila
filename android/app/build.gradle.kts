import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Configuração Firebase fora do código (decisão D-03): android/kupila-firebase.properties, ignorado pelo git.
// Sem o ficheiro, o build de depuração liga-se ao Firebase Emulator (projecto "demo-kupila") e o de
// produção arranca em modo "serviço não configurado" — nunca com valores inventados.
val firebaseLocal = Properties().apply {
    val f = rootProject.file("kupila-firebase.properties")
    if (f.exists()) f.inputStream().use(::load)
}
fun fb(chave: String, padrao: String = "") = "\"" + (firebaseLocal.getProperty(chave) ?: padrao) + "\""

android {
    namespace = "ao.kupila.gestaonegocios"
    compileSdk = 37

    defaultConfig {
        applicationId = "ao.kupila.gestaonegocios"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_PROJECTO", fb("projectId"))
        buildConfigField("String", "FIREBASE_APP_ID", fb("applicationId"))
        buildConfigField("String", "FIREBASE_API_KEY", fb("apiKey"))
        buildConfigField("String", "FIREBASE_STORAGE", fb("storageBucket"))
        buildConfigField("String", "FIREBASE_SENDER", fb("gcmSenderId"))
        buildConfigField("String", "FUNCTIONS_REGIAO", fb("functionsRegion", "europe-west1"))
        // Plano do backend (D-27): "spark" (vigente, regras como autoridade) ou "api" (Blaze, Cloud Functions). Os ecrãs não mudam.
        buildConfigField("String", "MODO", fb("modo", "spark"))
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Emulador do Firebase no PC, alcançado por `adb reverse` (telemóvel por USB ou emulador Android) — docs/build.md.
            // ID da app Android registada para a variante .dev no projecto real.
            buildConfigField("String", "FIREBASE_APP_ID", fb("applicationIdDev", firebaseLocal.getProperty("applicationId") ?: ""))
            // Sem configuração ou com usarEmulador=true: Firebase Emulator (testes); caso contrário, o projecto real.
            buildConfigField("Boolean", "USAR_EMULADOR", (firebaseLocal.getProperty("projectId") == null || firebaseLocal.getProperty("usarEmulador") == "true").toString())
            buildConfigField("String", "EMULADOR_HOST", fb("emulatorHost", "127.0.0.1"))
        }
        release {
            buildConfigField("Boolean", "USAR_EMULADOR", "false")
            buildConfigField("String", "EMULADOR_HOST", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinatura de produção: configurada fora do repositório (ver docs/build.md). Sem ela, o release não é assinado.
            val ks = findProperty("KUPILA_KEYSTORE") as String?
            if (ks != null) signingConfig = signingConfigs.create("producao") {
                storeFile = file(ks)
                storePassword = findProperty("KUPILA_KEYSTORE_SENHA") as String?
                keyAlias = findProperty("KUPILA_CHAVE") as String?
                keyPassword = findProperty("KUPILA_CHAVE_SENHA") as String?
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// ---------------------------------------------------------------------- voz IA
// Desde la 2.2 la voz IA viaja DENTRO del APK release (unos 2.9 GB de MP3), para
// que cualquier teléfono la tenga al instalar la app, sin copiar nada por cable.
// Sale de la carpeta del generador (E:\PG\BibliaVoz-IA\audio). Los MP3 van sin
// comprimir: así MediaPlayer los lee directamente del APK (AssetManager.openFd).
// Para compilar rápido sin el audio:  compilar.ps1 assembleRelease -PsinVozIa
val vozIaOrigen: File = providers.gradleProperty("vozIaDir").map { file(it) }
    .getOrElse(rootProject.file("../BibliaVoz-IA/audio"))
// -PsinVozIa (o =true) la deja fuera; -PsinVozIa=false la mete igual.
val incluirVozIa = providers.gradleProperty("sinVozIa")
    .map { it.equals("false", ignoreCase = true) }
    .getOrElse(true)

/**
 * Deja en `salida/voz-ia` los MP3 y el manifest.json del generador. Usa enlaces
 * duros (mismo disco): no ocupa otros 2.9 GB y tarda segundos. Si el disco no
 * los admite, copia.
 */
abstract class PrepararVozIa : DefaultTask() {
    @get:Internal
    abstract val origen: DirectoryProperty

    @get:OutputDirectory
    abstract val salida: DirectoryProperty

    init {
        // El origen cambia cada vez que el generador graba algo: se revisa siempre.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun preparar() {
        val desde = origen.get().asFile
        if (!File(desde, "manifest.json").isFile) {
            throw GradleException(
                "No hay voz IA en $desde (falta manifest.json). Genera el audio con " +
                    "generar-audio.bat o compila sin él: compilar.ps1 assembleRelease -PsinVozIa"
            )
        }
        val destino = File(salida.get().asFile, "voz-ia").apply { mkdirs() }
        val quiero = desde.listFiles { f -> f.isFile && (f.name.endsWith(".mp3") || f.name == "manifest.json") }
            .orEmpty().associateBy { it.name }
        // Lo que ya no está en el origen (audio de un reparto anterior) sobra.
        destino.listFiles().orEmpty().filter { it.name !in quiero }.forEach { it.delete() }
        var nuevos = 0
        for ((nombre, f) in quiero) {
            val d = File(destino, nombre)
            if (d.isFile && d.length() == f.length() && d.lastModified() == f.lastModified()) continue
            d.delete()
            runCatching { Files.createLink(d.toPath(), f.toPath()) }.getOrElse {
                Files.copy(f.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            }
            nuevos++
        }
        logger.lifecycle("Voz IA: ${quiero.size} archivos en el APK ($nuevos nuevos o cambiados).")
    }
}

val prepararVozIa = tasks.register<PrepararVozIa>("prepararVozIa") {
    origen.set(vozIaOrigen)
    salida.set(layout.buildDirectory.dir("vozIa"))
}

android {
    namespace = "com.bibliavoz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bibliavoz.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "2.2"
        resourceConfigurations += setOf("es", "en")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time solo existe desde Android 8; el desugaring lo hace
        // funcionar también en Android 7, que es el mínimo que soporta la app.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    androidResources {
        // La voz IA se reproduce desde dentro del APK: tiene que ir sin comprimir.
        noCompress += listOf("mp3")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (incluirVozIa) {
            variant.sources.assets?.addGeneratedSourceDirectory(prepararVozIa, PrepararVozIa::salida)
        }
    }
}

// La salida de compressReleaseAssets es otra copia de los 2.9 GB de MP3: guardarla
// en la caché de Gradle (org.gradle.caching) llenaría el disco en cada compilación.
tasks.matching { it.name == "compressReleaseAssets" }.configureEach {
    outputs.doNotCacheIf("voz IA: 2.9 GB de MP3 sin comprimir") { true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
    // org.json real: el de android.jar es un stub que lanza excepciones.
    testImplementation("org.json:json:20240303")
}

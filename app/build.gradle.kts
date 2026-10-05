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
// Sale de la carpeta del generador (E:\PG\BibliaVoz-IA\audio-mx desde la 3.0). Los MP3 van sin
// comprimir: así MediaPlayer los lee directamente del APK (AssetManager.openFd).
// Para compilar rápido sin el audio:  compilar.ps1 assembleRelease -PsinVozIa
val vozIaOrigen: File = providers.gradleProperty("vozIaDir").map { file(it) }
    .getOrElse(rootProject.file("../BibliaVoz-IA/audio-mx"))
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

    /** Sello de la versión del texto que debe traer el manifiesto (TextosIa.ID). */
    @get:Input
    abstract val textosId: Property<String>

    init {
        // El origen cambia cada vez que el generador graba algo: se revisa siempre.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun preparar() {
        val desde = origen.get().asFile
        val manifiesto = File(desde, "manifest.json")
        if (!manifiesto.isFile) {
            throw GradleException(
                "No hay voz IA en $desde (falta manifest.json). Genera el audio con " +
                    "generar-audio.bat o compila sin él: compilar.ps1 assembleRelease -PsinVozIa"
            )
        }
        @Suppress("UNCHECKED_CAST")
        val raiz = groovy.json.JsonSlurper().parse(manifiesto) as Map<String, Any?>

        // El sello del texto tiene que coincidir con TextosIa.ID: así nunca entra
        // en el APK audio grabado con otra Biblia o con otro leccionario (diría algo
        // distinto de lo que muestra la pantalla).
        val idManifiesto = raiz["textos"] as? String
        if (idManifiesto != textosId.get()) {
            throw GradleException(
                "El audio de $desde es de otra versión del texto (textos=\"$idManifiesto\", se esperaba " +
                    "\"${textosId.get()}\"). Regenera el audio con el texto actual (otra carpeta con -PvozIaDir)."
            )
        }

        // Solo los archivos que el manifiesto referencia, NO todo .mp3 de la carpeta:
        // así nunca se cuela audio huérfano de un reparto anterior (que además haría
        // pasar el APK del límite de 4 GiB).
        val referenciados = LinkedHashSet<String>()
        for (grupo in listOf("capitulos", "lecturas")) {
            val g = raiz[grupo] as? Map<String, Any?> ?: continue
            for (unidad in g.values) {
                for (tramo in (unidad as List<Any?>)) {
                    referenciados.add((tramo as List<Any?>)[2] as String)
                }
            }
        }

        var bytes = 0L
        for (nombre in referenciados) {
            val f = File(desde, nombre)
            if (!f.isFile) throw GradleException("Falta el audio «$nombre» que el manifiesto referencia. Regenera el audio.")
            bytes += f.length()
        }
        // Una APK no admite ZIP64: pasar de 4 GiB rompe la firma o la instalación.
        if (bytes > 3_900_000_000L) {
            throw GradleException("La voz IA no cabe en una APK (límite 4 GiB): el audio referenciado suma ${bytes / (1024 * 1024)} MB.")
        }

        val destino = File(salida.get().asFile, "voz-ia").apply { mkdirs() }
        val quiero = referenciados + "manifest.json"
        // Lo que ya no está referenciado (audio de un reparto anterior) sobra.
        destino.listFiles().orEmpty().filter { it.name !in quiero }.forEach { it.delete() }
        var nuevos = 0
        for (nombre in quiero) {
            val f = File(desde, nombre)
            val d = File(destino, nombre)
            if (d.isFile && d.length() == f.length() && d.lastModified() == f.lastModified()) continue
            d.delete()
            runCatching { Files.createLink(d.toPath(), f.toPath()) }.getOrElse {
                Files.copy(f.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            }
            nuevos++
        }
        logger.lifecycle("Voz IA: ${quiero.size} archivos en el APK ($nuevos nuevos o cambiados, ${bytes / (1024 * 1024)} MB).")
    }
}

// El ID esperado se saca de la propia TextosIa.kt (una sola fuente de verdad):
// así, si se sube el sello al cambiar la Biblia o el leccionario, el empaquetado
// exige audio regrabado con ese texto.
val textosIaId: String = run {
    val f = file("src/main/java/com/bibliavoz/app/voz/TextosIa.kt")
    Regex("""const\s+val\s+ID\s*=\s*"([^"]+)"""").find(f.readText())?.groupValues?.get(1)
        ?: throw GradleException("No encontré TextosIa.ID en ${f.path}")
}

val prepararVozIa = tasks.register<PrepararVozIa>("prepararVozIa") {
    origen.set(vozIaOrigen)
    salida.set(layout.buildDirectory.dir("vozIa"))
    textosId.set(textosIaId)
}

android {
    namespace = "com.bibliavoz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bibliavoz.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "3.0"
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
        // La voz IA se reproduce desde dentro del APK: tiene que ir sin comprimir
        // (MediaPlayer la abre con AssetManager.openFd). Vale mp3 y opus.
        noCompress += listOf("mp3", "opus")
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

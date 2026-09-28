import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Programa de PC que genera, una sola vez, el audio de la voz IA con Fish Audio.
// La app no lo usa: solo reproduce los archivos que este programa deja listos.

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

// El director, los anuncios, el reparto en tramos y el calendario litúrgico son
// los MISMOS archivos que compila la app: así el audio que se genera aquí casa
// exactamente con los versículos que resalta el teléfono.
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java/com/bibliavoz/app/voz")
            srcDir("../app/src/main/java/com/bibliavoz/app/liturgia")
            // Estos dos dependen de Android.
            exclude("**/Leccionario.kt", "**/ColaLecturas.kt")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.bibliavoz.generador.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

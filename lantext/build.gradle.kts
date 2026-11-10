import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Compiles the LanText sources in-place so this app and the LanText project stay one codebase.
// LanTextApp is final in that project. A build-time copy marks it open so MessagesApp can
// subclass it and keep LanTextApp.instance, the gateway, and the computer UI working.
// The pinned submodule is the release source. A sibling checkout is used only
// when Gradle is started with -Plantext.local=true.
val pinnedMain = rootProject.projectDir.resolve("external/lantext/app/src/main")
val siblingMain = rootProject.projectDir.resolve("../lantext/app/src/main")
val useLocalLantext = providers.gradleProperty("lantext.local").orNull == "true"
val lantextMain = when {
    useLocalLantext && siblingMain.resolve("java").isDirectory -> siblingMain
    pinnedMain.resolve("java").isDirectory -> pinnedMain
    else -> error("LanText sources are missing. Run: git submodule update --init")
}
val lantextJava = layout.buildDirectory.dir("generated/lantext-src")
val syncLantextSources = tasks.register<Sync>("syncLantextSources") {
    from(lantextMain.resolve("java"))
    into(lantextJava)
    filter { line: String ->
        line.replace("class LanTextApp : Application()", "open class LanTextApp : Application()")
    }
}

android {
    namespace = "app.lantext"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"0.2.5\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(lantextJava.get().asFile))
        res.setSrcDirs(listOf(lantextMain.resolve("res")))
        assets.setSrcDirs(listOf(lantextMain.resolve("assets")))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach { dependsOn(syncLantextSources) }
tasks.withType<JavaCompile>().configureEach { dependsOn(syncLantextSources) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.ktor.network.tls.certificates)
    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libphonenumber)
    implementation(libs.zxing.core)
}

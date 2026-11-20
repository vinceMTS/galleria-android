import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing (D-034): the key and its passwords live outside the project.
// Default location: <user home>/.keys/galleria-keystore.properties, with
// storeFile, storePassword, keyAlias, keyPassword. Without it, release builds are unsigned.
val keystorePropertiesFile = providers.gradleProperty("galleria.keystoreProperties")
    .map { file(it) }
    .getOrElse(file("${System.getProperty("user.home")}/.keys/galleria-keystore.properties"))
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "app.galleria"
    // D-038: latest stable Android (17); recent Compose needs the 37.1+ minor SDK.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.galleria"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Test build installed next to the real one (D-040): own id, name and icon.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// D-044: the build fails if a network permission shows up in the final merged manifest,
// e.g. because a library adds it and the `tools:node="remove"` line was dropped.
abstract class CheckNoNetworkPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val forbidden = setOf("android.permission.INTERNET")
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val declared = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { i ->
                (nodes.item(i) as org.w3c.dom.Element).getAttributeNS(androidNs, "name")
            }
        }
        val found = declared.filter { it in forbidden }
        if (found.isNotEmpty()) {
            throw GradleException(
                "Forbidden permission(s) in the merged manifest: ${found.joinToString()}. " +
                    "The app must never have network access (D-002, D-044)."
            )
        }
        report.get().asFile.writeText("OK: ${declared.sorted().joinToString("\n")}\n")
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val checkTask = tasks.register<CheckNoNetworkPermission>("check${name}NoNetworkPermission") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-network/${variant.name}.txt"))
        }
        tasks.matching { it.name == "package$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(checkTask) }
    }
}

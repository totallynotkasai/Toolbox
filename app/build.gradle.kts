import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing reads the gitignored keystore.properties in the project root (see the README). Without it a
// release build still works but comes out unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.toolbox.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.toolbox.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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

// ToolIconsTest checks the website's icons in docs/icons against every tool's icon; `-PupdateIcons` rewrites them
// instead. WebsiteTest checks the tool list in docs/index.html against Tools.kt.
tasks.withType<Test>().configureEach {
    val icons = rootProject.file("docs/icons")
    val website = rootProject.file("docs/index.html")
    systemProperty("iconsDir", icons.path)
    systemProperty("websiteFile", website.path)
    systemProperty("updateIcons", providers.gradleProperty("updateIcons").isPresent)
    if (icons.isDirectory) inputs.dir(icons).withPropertyName("websiteIcons")
    if (website.isFile) inputs.file(website).withPropertyName("website")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
}

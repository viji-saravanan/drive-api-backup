import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.util.Locale
import java.util.Properties

abstract class ValidatePublicReleasePrivacy : DefaultTask() {
    @get:Input
    abstract val containsPrivateConfiguration: Property<Boolean>

    @TaskAction
    fun validate() {
        require(!containsPrivateConfiguration.get()) {
            "Public release cannot embed private account or Drive configuration"
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val privateProperties = providers
    .fileContents(rootProject.layout.projectDirectory.file("private.properties"))
    .asText
    .orElse("")
    .map { content ->
        Properties().apply {
            if (content.isNotBlank()) {
                content.reader().use(::load)
            }
        }
    }

fun privateConfigurationValue(gradleKey: String, environmentKey: String): String =
    providers
        .gradleProperty(gradleKey)
        .orElse(providers.environmentVariable(environmentKey))
        .orElse(privateProperties.map { it.getProperty(gradleKey).orEmpty() })
        .getOrElse("")
        .trim()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val driveUploadFolderId = privateConfigurationValue(
    gradleKey = "vijiBackup.driveUploadFolderId",
    environmentKey = "VIJI_BACKUP_DRIVE_UPLOAD_FOLDER_ID",
)
val allowedGoogleAccounts = privateConfigurationValue(
    gradleKey = "vijiBackup.allowedGoogleAccounts",
    environmentKey = "VIJI_BACKUP_ALLOWED_GOOGLE_ACCOUNTS",
)
val internalAndroidOAuthClientId = privateConfigurationValue(
    gradleKey = "vijiBackup.internalAndroidOAuthClientId",
    environmentKey = "VIJI_BACKUP_INTERNAL_ANDROID_OAUTH_CLIENT_ID",
)
val publicAndroidOAuthClientId = privateConfigurationValue(
    gradleKey = "vijiBackup.publicAndroidOAuthClientId",
    environmentKey = "VIJI_BACKUP_PUBLIC_ANDROID_OAUTH_CLIENT_ID",
)
val googleWebClientId = privateConfigurationValue(
    gradleKey = "vijiBackup.googleWebClientId",
    environmentKey = "VIJI_BACKUP_GOOGLE_WEB_CLIENT_ID",
)

val oauthClientIdPattern = Regex("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")
mapOf(
    "vijiBackup.internalAndroidOAuthClientId" to internalAndroidOAuthClientId,
    "vijiBackup.publicAndroidOAuthClientId" to publicAndroidOAuthClientId,
    "vijiBackup.googleWebClientId" to googleWebClientId,
).forEach { (key, value) ->
    require(value.isEmpty() || value.matches(oauthClientIdPattern)) {
        "$key must be a Google OAuth client ID"
    }
}

require(
    googleWebClientId.isEmpty() ||
        googleWebClientId != internalAndroidOAuthClientId &&
        googleWebClientId != publicAndroidOAuthClientId,
) {
    "vijiBackup.googleWebClientId must be separate from Android OAuth clients"
}

val normalizedAllowedGoogleAccounts = allowedGoogleAccounts
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map { account -> account.lowercase(Locale.ROOT) }

val googleAccountEmailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
require(normalizedAllowedGoogleAccounts.all { it.matches(googleAccountEmailPattern) }) {
    "vijiBackup.allowedGoogleAccounts contains an invalid address"
}

require(normalizedAllowedGoogleAccounts.size == normalizedAllowedGoogleAccounts.toSet().size) {
    "vijiBackup.allowedGoogleAccounts must not contain duplicate addresses"
}

val validatePublicReleasePrivacy by tasks.registering(ValidatePublicReleasePrivacy::class) {
    group = "verification"
    description = "Prevents private identifiers from entering a public release artifact."
    containsPrivateConfiguration.set(
        normalizedAllowedGoogleAccounts.isNotEmpty() || driveUploadFolderId.isNotEmpty(),
    )
}

tasks.matching { task -> task.name == "prePublicReleaseBuild" }.configureEach {
    dependsOn(validatePublicReleasePrivacy)
}

android {
    namespace = "com.aryasubramani.vijibackup"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aryasubramani.vijibackup"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "DRIVE_UPLOAD_FOLDER_ID",
            driveUploadFolderId.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ALLOWED_GOOGLE_ACCOUNTS",
            normalizedAllowedGoogleAccounts.joinToString(",").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            googleWebClientId.asBuildConfigString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            buildConfigField(
                "String",
                "ANDROID_OAUTH_CLIENT_ID",
                internalAndroidOAuthClientId.asBuildConfigString(),
            )
            resValue("string", "app_channel", "internal")
        }
        create("public") {
            dimension = "distribution"
            buildConfigField(
                "String",
                "ANDROID_OAUTH_CLIENT_ID",
                publicAndroidOAuthClientId.asBuildConfigString(),
            )
            resValue("string", "app_channel", "public")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.google.identity.googleid)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

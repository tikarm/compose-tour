plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.tikarm.tour"
        compileSdk = 36
        minSdk = 24
    }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(artifactId = "compose-tour")

    pom {
        name.set("Compose Tour")
        description.set("A Compose Multiplatform overlay that walks users through a screen, one element at a time.")
        url.set("https://github.com/tikarm/compose-tour")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("tikarm")
                name.set("Tigran Parsadanyan")
                url.set("https://github.com/tikarm")
            }
        }
        scm {
            url.set("https://github.com/tikarm/compose-tour")
            connection.set("scm:git:git://github.com/tikarm/compose-tour.git")
            developerConnection.set("scm:git:ssh://git@github.com/tikarm/compose-tour.git")
        }
    }
}

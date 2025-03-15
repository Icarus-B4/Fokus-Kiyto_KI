// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.android.library") version "8.2.2" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    }
}

// Task zum Erstellen und Hochladen eines Releases
tasks.register("releaseToGitHub") {
    group = "publishing"
    description = "Erstellt eine signierte APK und lädt sie zu GitHub hoch"
    
    doLast {
        val version = project.findProperty("version") ?: "1.9.0"
        val token = project.findProperty("githubToken") ?: System.getenv("GITHUB_TOKEN") ?: ""
        
        exec {
            workingDir = rootDir
            commandLine("cmd", "/c", "scripts\\release.bat", version, token)
        }
    }
}
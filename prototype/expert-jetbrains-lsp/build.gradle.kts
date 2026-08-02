plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.cimere.spellixir.prototype"
version = "0.0.0-prototype"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.4")
    }
}

kotlin {
    jvmToolchain(25)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.cimere.spellixir.prototype.expert"
        name = "Spellixir Expert Prototype"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "261"
        }
    }
}

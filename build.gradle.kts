plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
}

group = "gg.lode"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper API — consumers (Catalyst, PhytorPractice) already bundle this; compileOnly here.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Mongo driver — supplied by the host plugin.
    compileOnly("org.mongodb:mongodb-driver-sync:5.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "kit-api"
            version = project.version.toString()
            from(components["java"])
        }
    }

    repositories {
        // Internal artifacts publish to Reposilite on the game node, so other
        // repos resolve them without a developer having run publishToMavenLocal
        // first. Reposilite refuses to overwrite a released version, so a
        // coordinate identifies exactly one build. URL is loopback — publish
        // over the SSH forward.
        maven {
            name = "phytor"
            url = uri((project.findProperty("phytorMavenUrl") as String?) ?: "http://127.0.0.1:8081/releases")
            isAllowInsecureProtocol = true
            credentials {
                username = project.findProperty("phytorMavenUser") as String?
                password = project.findProperty("phytorMavenPassword") as String?
            }
        }
    }
}

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
}

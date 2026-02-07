plugins {
    kotlin("jvm") version "2.3.20-Beta1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.katacr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public/"){
        name = "Aliyun"
    }
    maven("https://maven.aliyun.com/repository/central"){
        name = "central"
    }
    maven("https://repo.alessiodp.com/releases/"){
        name = "libby"
    }
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
    }
    maven("https://mvnrepository.com/artifact/com.mojang/authlib/") {
        name = "authlib"
    }
    maven("https://repo.papermc.io/repository/maven-offline/")

    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "vaultapi"
    }
}

dependencies {
    implementation("net.byteflux:libby-bukkit:1.3.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("com.destroystokyo.paper:paper-api:1.16.1-R0.1-SNAPSHOT")
    compileOnly("com.mojang:authlib:1.5.25")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("com.zaxxer:HikariCP:5.0.1")
    compileOnly("org.xerial:sqlite-jdbc:3.42.0.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.16")
    }
}

val targetJavaVersion = 12
kotlin {
    jvmToolchain(targetJavaVersion)
}
tasks.withType<JavaCompile> {
    options.release.set(targetJavaVersion)
}
tasks {
    // 让默认的 build 任务执行 shadowJar
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        relocate("org.bstats", project.group.toString())
        archiveClassifier.set("")

    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

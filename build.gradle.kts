plugins {
    kotlin("jvm") version "2.3.20-Beta1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.katacr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // SkinsRestorer 仓库
    maven("https://mvnrepository.com/artifact/com.mojang/authlib/") {
        name = "authlib"
    }
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
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
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("com.destroystokyo.paper:paper-api:1.16.1-R0.1-SNAPSHOT")
    // compileOnly("net.skinsrestorer:skinsrestorer-api:15.9.3")
    compileOnly("com.mojang:authlib:1.5.25")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
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
        // 移除文件名末尾的 "-all"
        archiveClassifier.set("")

        // 可选：重定向包路径（Relocation）
        // 这是为了防止其他插件也带了不同版本的相同库导致冲突
        relocate("kotlin", "org.katacr.kaguilds.libs.kotlin")
        relocate("com.zaxxer.hikari", "org.katacr.kaguilds.libs.hikari")
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

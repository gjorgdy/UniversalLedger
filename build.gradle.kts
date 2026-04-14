import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.github.cdimascio:dotenv-kotlin:6.4.1")
    }
}

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.modrinth.minotaur") version "2.+"
    id("net.fabricmc.fabric-loom") version "${project.property("loom_version")}"
    id("maven-publish")
}

val dotenv = dotenv { directory = project.rootDir.path }
val minecraftVersions = (project.property("minecraft_versions") as String).split(',')
val minMinecraftVersion = minecraftVersions.first()
version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion)
        vendor = JvmVendorSpec.ORACLE
    }
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("universalledger") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    mavenLocal() {
        metadataSources {
            artifact()
            ignoreGradleMetadataRedirection()
        }
    }
    maven { url = uri("https://api.modrinth.com/maven") }
    repositories {
        maven {
            url = uri("https://repo.opencollab.dev/main/")
        }
    }
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        // Disable module metadata validation
        capabilitiesResolution {
            // This allows inconsistent module names
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    // Ledger and its dependencies
    implementation("maven.modrinth:ledger:${project.property("ledger_version")}")
    compileOnly("com.uchuhimo:konf-core:1.1.2")
    compileOnly("com.uchuhimo:konf-toml:1.1.2")
    // Floodgate
    compileOnly("org.geysermc.geyser:api:${project.property("geyser_version")}")
    compileOnly("org.geysermc.floodgate:api:${project.property("floodgate_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version,
            "minecraft_version" to minMinecraftVersion,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
    repositories {
    }
}

fun readChangelog(): String =
    file("changelog.md").readText(Charsets.UTF_8)

tasks.named("modrinth") {
    dependsOn(tasks.named("modrinthSyncBody"))
}
modrinth {
    token.set(dotenv["MODRINTH_TOKEN"])
    projectId.set("universal-ledger")
    versionNumber.set("${project.property("mod_version")}+${project.property("minecraft_version")}")
    versionName.set("Universal Ledger ${project.property("mod_version")}+${project.property("minecraft_version")}")
    versionType.set(project.property("mod_release_type") as String)
    changelog.set(readChangelog())
    uploadFile.set(tasks.jar)
    gameVersions.addAll(minecraftVersions)
    loaders.add("fabric")
    syncBodyFrom.set(rootProject.file("README.md").readText(Charsets.UTF_8))
    dependencies {
        required.project("fabric-language-kotlin")
        required.project("ledger")
    }
}
kotlin {
    jvmToolchain(25)
}
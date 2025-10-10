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
    kotlin("jvm") version "2.2.10"
    id("com.modrinth.minotaur") version "2.+"
    id("fabric-loom") version "1.11-SNAPSHOT"
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

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
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
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    // Ledger and its dependencies
    modCompileOnly("com.github.quiltservertools:ledger:${project.property("ledger_version")}+local")
    compileOnly("com.uchuhimo:konf-core:1.1.2")
    compileOnly("com.uchuhimo:konf-toml:1.1.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version,
            "minecraft_version" to minMinecraftVersion,
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version"))
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

modrinth {
    token.set(dotenv["MODRINTH_TOKEN"])
    projectId.set("universal-ledger")
    versionNumber.set(project.property("mod_version") as String)
    versionName.set("Universal Ledger ${project.property("mod_version") as String}")
    versionType.set(project.property("mod_release_type") as String)
    changelog.set(readChangelog())
    uploadFile.set(tasks.jar)
    gameVersions.addAll(minecraftVersions)
    loaders.add("fabric")
    dependencies {
        required.project("fabric-language-kotlin")
        required.project("ledger")
    }
}

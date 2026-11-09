import java.util.jar.JarFile

plugins {
    java
    application
}
group = "dev.itsdropper.vantaloader"
version = providers.gradleProperty("vantaVersion").orElse("0.1.0-SNAPSHOT").get()
repositories { mavenCentral() }
dependencies {
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
    // Shared bytecode toolkit exposed to native mods through the agent classpath.
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}
application { mainClass.set("dev.itsdropper.vantaloader.bootstrap.VantaLoaderMain") }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to "VantaLoader",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "ItsDropper"
        )
    }
}


// A self-contained javaagent artifact for launcher-managed Minecraft instances.
val agentJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveFileName.set("VantaLoader-agent.jar")
    destinationDirectory.set(layout.buildDirectory.dir("agent"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
    manifest {
        attributes(
            "Premain-Class" to "dev.itsdropper.vantaloader.bootstrap.VantaLoaderAgent",
            "Can-Redefine-Classes" to "false",
            "Can-Retransform-Classes" to "false",
            "Implementation-Title" to "VantaLoader Java Agent",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "ItsDropper"
        )
        // Mixin detects ASM's minor version from package manifest metadata.
        // A shaded agent JAR must retain the ASM package version explicitly.
        attributes(mapOf("Implementation-Version" to "9.8"), "org/objectweb/asm/")
    }
}

val apiJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveFileName.set("VantaLoader-api.jar")
    destinationDirectory.set(layout.buildDirectory.dir("agent"))
    from(sourceSets.main.get().output) {
        include("dev/itsdropper/vantaloader/api/**")
    }
    manifest {
        attributes(
            "Implementation-Title" to "VantaLoader Mod API",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "ItsDropper"
        )
    }
}

val verifyAgentJar by tasks.registering {
    dependsOn(agentJar, apiJar)
    doLast {
        val archive = agentJar.get().archiveFile.get().asFile
        val apiArchive = apiJar.get().archiveFile.get().asFile
        JarFile(apiArchive).use { jar ->
            check(jar.getEntry("dev/itsdropper/vantaloader/api/VantaMod.class") != null) {
                "Standalone mod API JAR is missing VantaMod"
            }
            check(jar.getEntry("dev/itsdropper/vantaloader/api/ModContext.class") != null) {
                "Standalone mod API JAR is missing ModContext"
            }
            check(jar.getEntry("dev/itsdropper/vantaloader/api/ModMenuApi.class") != null) {
                "Standalone mod API JAR is missing ModMenuApi"
            }
            check(jar.getEntry("dev/itsdropper/vantaloader/api/ModMenuEntry.class") != null) {
                "Standalone mod API JAR is missing ModMenuEntry"
            }
            check(jar.manifest.mainAttributes.getValue("Implementation-Version") == project.version.toString()) {
                "Standalone mod API JAR version does not match project version"
            }
        }
        JarFile(archive).use { jar ->
            val attributes = jar.manifest.mainAttributes
            check(attributes.getValue("Implementation-Version") == project.version.toString()) {
                "VantaLoader agent JAR version does not match project version"
            }
            check(attributes.getValue("Premain-Class")
                == "dev.itsdropper.vantaloader.bootstrap.VantaLoaderAgent") {
                "VantaLoader agent JAR is missing its Premain-Class manifest entry"
            }
            check(jar.manifest.entries["org/objectweb/asm/"]?.getValue("Implementation-Version") == "9.8") {
                "VantaLoader agent JAR must preserve ASM's package implementation version for Mixin compatibility checks"
            }
            check(jar.getEntry("dev/itsdropper/vantaloader/bootstrap/VantaLoaderAgent.class") != null) {
                "VantaLoader agent class is missing from the packaged artifact"
            }
            check(jar.getEntry("com/google/gson/JsonParser.class") != null) {
                "Gson runtime dependency is missing from the self-contained agent JAR"
            }
        }
    }
}

tasks.named<Test>("test") {
    dependsOn(agentJar)
}

tasks.named("build") {
    dependsOn(verifyAgentJar, apiJar)
}

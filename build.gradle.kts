import java.util.jar.JarFile

plugins {
    java
    application
}
group = "dev.itsdropper.vantaloader"
version = "0.1.0-SNAPSHOT"
repositories { mavenCentral() }
dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    // Shared bytecode toolkit exposed to native mods through the agent classpath.
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("org.ow2.asm:asm-tree:9.11.1")
    implementation("org.ow2.asm:asm-commons:9.11.1")
    implementation("org.ow2.asm:asm-util:9.11.1")
    implementation("org.ow2.asm:asm-analysis:9.11.1")
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
    }
}

val verifyAgentJar by tasks.registering {
    dependsOn(agentJar)
    doLast {
        val archive = agentJar.get().archiveFile.get().asFile
        JarFile(archive).use { jar ->
            val attributes = jar.manifest.mainAttributes
            check(attributes.getValue("Premain-Class")
                == "dev.itsdropper.vantaloader.bootstrap.VantaLoaderAgent") {
                "VantaLoader agent JAR is missing its Premain-Class manifest entry"
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
    dependsOn(verifyAgentJar)
}

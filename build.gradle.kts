plugins {
    java
    application
}
group = "dev.itsdropper.vantaloader"
version = "0.1.0-SNAPSHOT"
repositories { mavenCentral() }
dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
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

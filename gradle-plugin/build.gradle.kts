plugins {
    `java-gradle-plugin`
}
group = "dev.itsdropper.vantaloader"
version = "1.0.0"
repositories { mavenCentral() }
gradlePlugin {
    plugins {
        create("vantaMod") {
            id = "dev.itsdropper.vantaloader"
            implementationClass = "dev.itsdropper.vantaloader.gradle.VantaModPlugin"
            displayName = "Vanta Mod Plugin"
            description = "Configures native VantaLoader mod projects and validates their metadata."
        }
    }
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }

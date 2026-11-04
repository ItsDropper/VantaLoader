plugins {
    java
}

group = "dev.itsdropper.vantaloader.examples"
version = "1.0.0"

repositories { }

dependencies {
    // Build VantaLoader first: gradle jar
    compileOnly(files("../../build/libs/VantaLoader-0.1.0-SNAPSHOT.jar"))
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

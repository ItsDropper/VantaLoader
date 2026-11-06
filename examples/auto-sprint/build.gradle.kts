plugins {
    java
}

group = "dev.itsdropper.vantaloader.examples"
version = "1.0.0"

repositories { }

dependencies {
    compileOnly(files("../../build/agent/VantaLoader-api.jar"))
    compileOnly("org.ow2.asm:asm:9.8")
    testImplementation("org.ow2.asm:asm:9.8")
    testRuntimeOnly(files("../../build/agent/VantaLoader-api.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

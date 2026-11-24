plugins {
    id("dev.itsdropper.vantaloader")
}
group = "dev.itsdropper.vantaloader.examples"
version = "1.0.0"
base { archivesName.set("vantaloader-vma-showcase") }
vantaMod {
    apiJar.set(layout.projectDirectory.file("../../build/agent/VantaLoader-api.jar"))
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

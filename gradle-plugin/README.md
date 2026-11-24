# Vanta Mod Gradle Plugin

The plugin applies the Java plugin, adds the VantaLoader API JAR as a compile-only dependency, and validates the API artifact plus `vanta.mod.json` before Java compilation.

The plugin is currently consumed through Gradle composite builds while VantaLoader is pre-alpha:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../VantaLoader/gradle-plugin")
}
```

```kotlin
// build.gradle.kts
plugins {
    id("dev.itsdropper.vantaloader")
}

vantaMod {
    apiJar.set(layout.projectDirectory.file("libs/VantaLoader-api.jar"))
}
```

Place the API JAR at `libs/VantaLoader-api.jar` (or set another path). The plugin does not bundle the API into the mod JAR.

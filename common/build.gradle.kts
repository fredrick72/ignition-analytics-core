plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")

    // Analytics libraries — scope GCD means they are bundled and available in
    // Gateway, Designer, and Client JVMs.
    modlImplementation("tech.tablesaw:tablesaw-core:0.43.1")
    modlImplementation("org.apache.commons:commons-math3:3.6.1")
}

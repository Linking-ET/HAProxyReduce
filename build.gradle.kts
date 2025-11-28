plugins {
    kotlin("jvm")
}
allprojects {
    group = "top.zient"
    version = "3.2.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.velocitypowered.com/snapshots/")
        maven("https://plugins.gradle.org/m2/")
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}

tasks.register<Sync>("collectJars") {
    group = "build"
    description = "Copy every subproject's build/libs/*.jar into build/{project}/build/libs/"
    dependsOn(tasks.named("build"))
    subprojects.forEach { sub ->
        from(sub.layout.buildDirectory.dir("libs")) {
            include("*.jar")
            // 搬过去时自动套两层目录：{子项目名}/build/libs/
            into("${sub.name}/build/libs")
        }
    }
    into(layout.buildDirectory)
}

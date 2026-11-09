plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.asciidoctor) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    group = "io.github.jkanie.schedkeel"
    version = "0.1.0-SNAPSHOT"

    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat()
            importOrder()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    plugins.withType<JavaPlugin> {
        // Compiles for Java 21 on any newer JDK, so CI can test on 21 and 25.
        tasks.withType<JavaCompile>().configureEach {
            options.release = 21
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

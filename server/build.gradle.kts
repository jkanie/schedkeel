plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.asciidoctor)
}

val asciidoctorExt: Configuration by configurations.creating
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.modulith.bom))
    asciidoctorExt(platform(libs.spring.boot.bom))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.data:spring-data-envers")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restdocs")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("com.redis:testcontainers-redis")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    asciidoctorExt("org.springframework.restdocs:spring-restdocs-asciidoctor")
}

tasks.test {
    outputs.dir(snippetsDir)
    systemProperty("org.springframework.restdocs.outputDir", snippetsDir.get().asFile.path)
}

// Spring REST Docs 4 needs AsciidoctorJ 3. The plugin's default is older.
asciidoctorj {
    setVersion("3.0.0")
}

tasks.asciidoctor {
    inputs.dir(snippetsDir)
    // Quietens a JRuby start-up warning about native file access.
    jvm { jvmArgs("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED") }
    configurations("asciidoctorExt")
    dependsOn(tasks.test)
    baseDirFollowsSourceFile()
}

// Builds the Angular dashboard and bundles it into the server jar.
// Run with -PwithDashboard; the default build skips npm.
if (providers.gradleProperty("withDashboard").isPresent) {
    val dashboardDir = rootProject.layout.projectDirectory.dir("dashboard")
    val npmCi by tasks.registering(Exec::class) {
        workingDir(dashboardDir)
        commandLine("npm", "ci")
    }
    val buildDashboard by tasks.registering(Exec::class) {
        dependsOn(npmCi)
        workingDir(dashboardDir)
        commandLine("npm", "run", "build")
    }
    tasks.processResources {
        dependsOn(buildDashboard)
        from(dashboardDir.dir("dist/schedkeel-dashboard/browser")) { into("static") }
    }
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.ple"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.4.4"
val junitJupiterVersion = "5.9.1"

val mainName = "com.ple.visur.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
//  annotationProcessor("io.vertx:vertx-codegen:$vertxVersion:processor")
//  annotationProcessor("io.vertx:vertx-service-proxy:$vertxVersion")
//  annotationProcessor("io.vertx:vertx-sockjs-service-proxy:$vertxVersion")
  implementation("io.vertx:vertx-web-client:$vertxVersion")
//  implementation("io.vertx:vertx-service-proxy:$vertxVersion")
  implementation("io.vertx:vertx-health-check:$vertxVersion")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-opentelemetry:$vertxVersion")
  implementation("io.vertx:vertx-web-sstore-cookie:$vertxVersion")
  implementation("io.vertx:vertx-micrometer-metrics:$vertxVersion")
  implementation("io.vertx:vertx-shell:$vertxVersion")
  implementation("io.vertx:vertx-rx-java3:$vertxVersion")
  implementation("io.vertx:vertx-tcp-eventbus-bridge:$vertxVersion")
  testImplementation("io.vertx:vertx-junit5:$vertxVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

tasks.register<JavaCompile>("annotationProcessing") {
  group = "other"
  source = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java
  destinationDir = project.file("${project.buildDir}/generated/main/java")
  classpath = configurations.compileClasspath.get()
  options.annotationProcessorPath = configurations.compileClasspath.get()
  options.compilerArgs = listOf(
    "-proc:only",
    "-processor", "io.vertx.codegen.CodeGenProcessor",
    "-Acodegen.output=${project.projectDir}/src/main"
  )
}

tasks.compileJava {
  dependsOn(tasks.named("annotationProcessing"))
}

sourceSets {
  main {
    java {
      srcDirs(project.file("${project.buildDir}/generated/main/java"))
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_16
  targetCompatibility = JavaVersion.VERSION_16
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    // "Main-Verticle" is the attribute io.vertx.core.Launcher reads, which is what makes `java -jar` alone start the
    // server. It was written as "MainVerticle-Verticle", so the jar printed its usage instead of starting.
    attributes(mapOf("Main-Verticle" to mainName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("run", mainName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}

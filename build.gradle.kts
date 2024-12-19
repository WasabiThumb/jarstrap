
plugins {
	id("java-library")
}

group = "io.github.wasabithumb"
version = "0.1.0"
description = "A tool to create native executables from a runnable JAR"

repositories {
	mavenCentral()
}

dependencies {
	implementation("io.github.wasabithumb:josdirs-core:0.1.0")
	implementation("io.github.wasabithumb:josdirs-platform-windows:0.1.0")
	implementation("io.github.wasabithumb:josdirs-platform-unix:0.1.0")
	compileOnly("org.jetbrains:annotations:26.0.1")

	testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
	val javaVersion = JavaVersion.toVersion(17)
	sourceCompatibility = javaVersion
	targetCompatibility = javaVersion

	withSourcesJar()
	withJavadocJar()
}

tasks.compileJava {
	options.encoding = "UTF-8"
}

tasks.javadoc {
	(options as CoreJavadocOptions)
		.addBooleanOption("Xdoclint:none", true)
}

tasks.processResources {
	// Add the "tool" filesystem to resources
	from("$rootDir")
		.include("tool", "tool/*", "tool/**/*")
}

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

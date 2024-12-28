
plugins {
	id("java-library")
	id("maven-publish")
	id("signing")
	id("net.thebugmc.gradle.sonatype-central-portal-publisher") version "1.2.4"
}

group = "io.github.wasabithumb"
version = "0.2.0"
description = "Tool to create native executables from a runnable JAR"

repositories {
	mavenCentral()
}

dependencies {
	// Runtime Dependencies
	implementation("io.github.wasabithumb:josdirs-core:0.1.0")
	implementation("io.github.wasabithumb:josdirs-platform-windows:0.1.0")
	implementation("io.github.wasabithumb:josdirs-platform-unix:0.1.0")

	// Source Dependencies
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

centralPortal {
	name = rootProject.name
	jarTask = tasks.jar
	sourcesJarTask = tasks.sourcesJar
	javadocJarTask = tasks.javadocJar
	pom {
		name = "JARStrap"
		description = project.description
		url = "https://github.com/WasabiThumb/jarstrap"
		licenses {
			license {
				name = "The Apache License, Version 2.0"
				url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
			}
		}
		developers {
			developer {
				id = "wasabithumb"
				email = "wasabithumbs@gmail.com"
				organization = "Wasabi Codes"
				organizationUrl = "https://wasabithumb.github.io/"
				timezone = "-5"
			}
		}
		scm {
			connection = "scm:git:git://github.com/WasabiThumb/jarstrap.git"
			url = "https://github.com/WasabiThumb/jarstrap"
		}
	}
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.9.21"
	id("org.jetbrains.dokka") version "1.9.10"
	id("maven-publish")
	idea
}

group = "io.taff"
version = "0.13.0${ if (isReleaseBuild()) "" else "-SNAPSHOT" }"
java.sourceCompatibility = JavaVersion.VERSION_20

repositories {
	maven {
		name = "exposed"
		url = uri("https://maven.pkg.github.com/tpasipanodya/exposed")
		credentials {
			username = System.getenv("PACKAGE_STORE_USERNAME")
			password = System.getenv("PACKAGE_STORE_TOKEN")
		}
	}
	maven {
		name = "spek-expect"
		url = uri("https://maven.pkg.github.com/tpasipanodya/spek-expekt")
		credentials {
			username = System.getenv("PACKAGE_STORE_USERNAME")
			password = System.getenv("PACKAGE_STORE_TOKEN")
		}
	}
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
	runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	api("io.github.microutils:kotlin-logging-jvm:3.0.5")
	api("com.github.kittinunf.fuel:fuel:2.3.1")
	api("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
  api("org.slf4j:slf4j-simple:2.0.9")
	api("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
	api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
	api("io.taff.exposed:exposed-core:0.10.0")
	api("io.taff.exposed:exposed-jdbc:0.10.0")
	api("io.taff.exposed:exposed-java-time:0.10.0")
	implementation("org.postgresql:postgresql:42.7.0")
	testImplementation("io.taff:spek-expekt:0.10.3")
	testImplementation(enforcedPlatform("org.junit:junit-bom:5.10.1"))
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "20"
	}
}

tasks {
	register<Jar>("dokkaJar") {
		from(dokkaHtml)
		dependsOn(dokkaHtml)
		archiveClassifier.set("javadoc")
	}
	register<Jar>("sourcesJar") {
		from(sourceSets.main.get().allSource)
		archiveClassifier.set("sources")
	}
}

tasks.withType<Test> { useJUnitPlatform() }

publishing {
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/tpasipanodya/exposed-extensions")
			credentials {
				username = System.getenv("GITHUB_ACTOR")
				password = System.getenv("GITHUB_TOKEN")
			}
		}
	}
	publications {
		create<MavenPublication>("mavenJava") {
			this.groupId = project.group.toString()
			this.artifactId = project.name
			this.version = project.version.toString()
			from(components["java"])

			versionMapping {
				usage("java-api") {
					fromResolutionOf("runtimeClasspath")
				}
			}

			artifact(tasks["dokkaJar"])
			artifact(tasks["sourcesJar"])

			pom {
				name.set(project.name)
				description.set("${project.name} $version - Lightweight utilities for simplifying backend application configuration")
				url.set("https://github.com/tpasipanodya/exposed-extensions")

				licenses {
					license {
						name.set("The Apache Software License, Version 2.0")
						url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
					}
				}

				developers {
					developer {
						name.set("Tafadzwa Pasipanodya")
						email.set("tmpasipanodya@gmail.com")
					}
				}

				scm {
					connection.set("scm:git:git://github.com/tpasipanodya/exposed-extensions.git")
					developerConnection.set("scm:git:ssh://github.com/tpasipanodya/exposed-extensions.git")
					url.set("http://github.com/tpasipanodya/exposed-extensions/tree/main")
				}
			}
		}
	}
}

fun isReleaseBuild() = !project.properties["IS_SNAPSHOT_BUILD"].let { isReleaseBuild ->
	println("isReleaseBuild: $isReleaseBuild")
	isReleaseBuild.toString().toBoolean()
}

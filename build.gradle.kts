import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig
import groovy.lang.GroovyObject

plugins {
	kotlin("jvm") version "1.4.32"
	id("com.jfrog.artifactory") version "4.21.0"
	id("org.jetbrains.dokka") version "1.4.30"
	kotlin("plugin.serialization") version "1.4.32"
	`maven-publish`
	idea
}

group = "io.taff"
version = "0.1.0${ if (isReleaseBuild()) "" else "-SNAPSHOT" }"
java.sourceCompatibility = JavaVersion.VERSION_14

repositories {
	jcenter()
	maven("https://jitpack.io")
	maven {
		name = "JFrog"
		url = uri("https://pasitaf.jfrog.io/artifactory/releases")
		credentials {
			username = System.getenv("ARTIFACTORY_USER")
			password = System.getenv("ARTIFACTORY_PASSWORD")
		}
	}
}

dependencies {
	runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	api("io.github.microutils:kotlin-logging-jvm:2.0.6")
	api("com.github.kittinunf.fuel:fuel:2.3.1")
	api("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
	api("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "14"
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
				name.set("project.name")
				description.set("${project.name} $version - Lightweight utilities for simplifying backend application configuration")
				url.set("https://github.com/tpasipanodya/hephaestus")

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
					connection.set("scm:git:git://github.com/tpasipanodya/hephaestus.git")
					developerConnection.set("scm:git:ssh://github.com/tpasipanodya/hephaestus.git")
					url.set("http://github.com/tpasipanodya/hephaestus/tree/main")
				}
			}

		}
	}
}


artifactory {
	setContextUrl("https://pasitaf.jfrog.io/artifactory/")

	publish(delegateClosureOf<PublisherConfig> {

		repository(delegateClosureOf<GroovyObject> {
			setProperty("repoKey", if (isReleaseBuild()) "releases" else "snapshots")
			setProperty("username", System.getenv("ARTIFACTORY_USER"))
			setProperty("password", System.getenv("ARTIFACTORY_PASSWORD"))
			setProperty("maven", true)
		})

		defaults(delegateClosureOf<GroovyObject> {
			invokeMethod("publications", "mavenJava")
		})
	})

	resolve(delegateClosureOf<ResolverConfig> {
		setProperty("repoKey", if (isReleaseBuild()) "releases" else "snapshots")
	})
}

fun isReleaseBuild() = System.getenv("IS_SNAPSHOT_BUILD")?.toBoolean() == true
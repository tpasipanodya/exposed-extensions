import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig
import groovy.lang.GroovyObject

plugins {
	kotlin("jvm") version "1.7.10"
	id("com.jfrog.artifactory") version "4.28.4"
	id("org.jetbrains.dokka") version "1.7.10"
	id("maven-publish")
	idea
}

group = "io.taff"
version = "0.11.0${ if (isReleaseBuild()) "" else "-SNAPSHOT" }"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	mavenCentral()
	maven("https://jitpack.io")
	maven {
		name = "JFrog"
		url = uri("https://tmpasipanodya.jfrog.io/artifactory/releases")
		credentials {
			username = System.getenv("ARTIFACTORY_USER")
			password = System.getenv("ARTIFACTORY_PASSWORD")
		}
	}
}

dependencies {
	runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
	runtimeOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	api("io.github.microutils:kotlin-logging-jvm:2.1.23")
	api("com.github.kittinunf.fuel:fuel:2.3.1")
	api("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
	api("org.slf4j:slf4j-simple:2.0.4")
	api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
	api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.3")
	api("io.taff.exposed:exposed-core:0.7.0")
	api("io.taff.exposed:exposed-jdbc:0.7.0")
	api("io.taff.exposed:exposed-java-time:0.7.0")
	implementation("org.postgresql:postgresql:42.4.0")
	testImplementation("io.taff:spek-expekt:0.7.5")
	testImplementation(enforcedPlatform("org.junit:junit-bom:5.8.2"))
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
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


artifactory {
	setContextUrl("https://tmpasipanodya.jfrog.io/artifactory/")
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

fun isReleaseBuild() = !project.properties["IS_SNAPSHOT_BUILD"].let { isReleaseBuild ->
	println("isReleaseBuild: $isReleaseBuild")
	isReleaseBuild.toString().toBoolean()
}

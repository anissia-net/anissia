import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	val kotlinVersion = "2.4.20-Beta2"
	id("org.jetbrains.kotlin.jvm") version kotlinVersion
	id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
	id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
	id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.ec4j.editorconfig") version "0.1.0"
	id("idea")
	java
}

group = "anissia"
version = "1.0"

repositories {
	mavenCentral()
}

idea {
	module {
		excludeDirs = listOf("build", "logs", "out", "tmp").map { file(it) }.toSet()
	}
}

extra["jooq.version"] = "3.21.6"

dependencies {
	// persistence
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jooq")

	// DB connector
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

	// elasticsearch
	implementation("org.elasticsearch.client:elasticsearch-rest-client:9.4.3")

	// lib
	implementation("me.saro:kit:0.2.3")
    implementation("me.saro:dat:4.6.1")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// logger
	implementation("org.slf4j:slf4j-api")
	implementation("ch.qos.logback:logback-classic")

	// spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	// 기존 비밀번호 해시(jbcrypt)가 계속 검증되는지 확인하는 용도. 런타임에는 포함하지 않는다.
	testImplementation("org.mindrot:jbcrypt:0.4")
}

tasks.test {
	useJUnitPlatform()
	failOnNoDiscoveredTests = false
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_25)
	}
}

configure<JavaPluginExtension> {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

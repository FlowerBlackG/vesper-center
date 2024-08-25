import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.3.3"
	id("io.spring.dependency-management") version "1.1.6"
	kotlin("jvm") version "2.0.20"
	kotlin("plugin.spring") version "2.0.20"
}

group = "com.gardilily"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
	implementation("org.springframework.session:spring-session-core")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.3")
	testImplementation("org.springframework.amqp:spring-rabbit-test")

	// https://mvnrepository.com/artifact/org.json/json
	implementation("org.json:json:20240303")

	// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")

	implementation("com.baomidou:mybatis-plus-boot-starter:3.5.7")

	implementation("org.springframework.boot:spring-boot-starter-data-redis")


	implementation("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.4.0")

	// https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-datetime
	runtimeOnly("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "21"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

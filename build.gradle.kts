plugins {
    java
}

group = "fr.zeffut"
version = "1.1.0"

val paperTarget = providers.gradleProperty("paperTarget").orElse("1.21.11").get()
val targetConfig = mapOf(
    "1.21.11" to ("1.21.11-R0.1-SNAPSHOT" to 21),
    "26.1.2" to ("26.1.2.build.74-stable" to 25),
    "26.2" to ("26.2.build.129-stable" to 25),
)
val (paperApiVersion, defaultJavaVersion) = targetConfig[paperTarget]
    ?: throw GradleException("Unsupported paperTarget '$paperTarget'; choose ${targetConfig.keys.joinToString()}")
val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).orElse(defaultJavaVersion).get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    // La version est capturée à la configuration, hors du bloc filesMatching : y lire
    // `project` se ferait à l'exécution de la tâche, ce que Gradle déprécie et refusera en 10.
    // Déclarée en inputs.property pour qu'un changement de version invalide bien le cache.
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveFileName.set("AnonymousPotion.jar")
}

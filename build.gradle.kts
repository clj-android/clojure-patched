plugins {
    `java-library`
    `maven-publish`
}

group = "com.goodanser.clj-android"
version = "1.12.0-1"
description = "Clojure 1.12.0 with Android-aware classloader in RT.makeClassLoader()"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Stock Clojure is compile-only: we compile our patched RT.java against it,
// then merge our RT.class into the stock JAR to produce a single artifact.
val stockClojure: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    compileOnly("org.clojure:clojure:1.12.0")
    stockClojure("org.clojure:clojure:1.12.0")

    // Declare the same transitive dependencies as stock Clojure 1.12.0.
    // These are needed both for Maven publishing (pom.withXml below) and
    // for Gradle composite builds (includeBuild) where the POM isn't used.
    api("org.clojure:spec.alpha:0.5.238")
    api("org.clojure:core.specs.alpha:0.4.74")
}

// The default `jar` task produces only our compiled RT.class.
// This task merges it into the stock Clojure JAR to produce a complete artifact.
val repackageJar by tasks.registering(Jar::class) {
    archiveBaseName.set("clojure")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Our compiled classes go FIRST so patched RT.class wins over stock
    from(tasks.named("compileJava").map { (it as JavaCompile).destinationDirectory })

    // Then include everything from the stock Clojure JAR (duplicates excluded)
    from(zipTree(stockClojure.singleFile))

    // Exclude signatures from the stock JAR (they'd be invalid after modification)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Replace the default jar with our repackaged version
tasks.named<Jar>("jar") {
    enabled = false
}

configurations {
    named("runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(repackageJar)
        // AGP prefers the secondary "classes" variant (java-classes-directory)
        // over the primary JAR.  Replace its artifact with the repackaged JAR
        // so AGP gets the complete Clojure runtime regardless of which variant
        // it selects.
        outgoing.variants.named("classes") {
            artifacts.clear()
            artifact(repackageJar)
        }
    }
    named("apiElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(repackageJar)
        outgoing.variants.named("classes") {
            artifacts.clear()
            artifact(repackageJar)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.goodanser.clj-android"
            artifactId = "clojure"
            version = project.version.toString()
            artifact(repackageJar)

            // Declare the same runtime dependencies as stock Clojure 1.12.0
            pom.withXml {
                val deps = asNode().appendNode("dependencies")

                val specAlpha = deps.appendNode("dependency")
                specAlpha.appendNode("groupId", "org.clojure")
                specAlpha.appendNode("artifactId", "spec.alpha")
                specAlpha.appendNode("version", "0.5.238")

                val coreSpecs = deps.appendNode("dependency")
                coreSpecs.appendNode("groupId", "org.clojure")
                coreSpecs.appendNode("artifactId", "core.specs.alpha")
                coreSpecs.appendNode("version", "0.4.74")
            }
        }
    }
}

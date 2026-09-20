plugins {
    `java-library`
}

// Optional server module: Apache Parquet writer for historian export (ADR-0059 § heavy dependencies).
// parquet-mr + Avro + hadoop-common stay here so ispf-server can be built without them
// (-Pispf.exportParquet=false), see packages/ispf-server/build.gradle.kts.
dependencies {
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.parquet:parquet-avro:1.18.1") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
    }
    implementation("org.apache.parquet:parquet-hadoop:1.18.1") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
    }
    implementation("org.apache.avro:avro:1.12.2")
    implementation("org.apache.hadoop:hadoop-common:3.5.0") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }

    constraints {
        // hadoop-common transitive; keep on the patched line (moved here from ispf-server).
        implementation("commons-beanutils:commons-beanutils:1.11.0")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "iot-solutions-platform-framework"

include(
    "packages:ispf-core",
    "packages:ispf-expression",
    "packages:ispf-analytics-engine",
    "packages:ispf-analytics-api",
    "packages:ispf-analytics-core-ext",
    "packages:ispf-analytics-marketplace-demo",
    "packages:ispf-server",
    "packages:ispf-plugin-blueprint",
    "packages:ispf-plugin-workflow",
    "packages:ispf-ai-api",
    "packages:ispf-ai-openai-compatible",
    "packages:ispf-ai-ollama",
    "packages:ispf-ai-agent",
)

// Device driver packs (+ api / ddk / stub-kit): discover packages/ispf-driver-*
file("packages")
    .listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("ispf-driver-") }
    ?.sortedBy { it.name }
    ?.forEach { include("packages:${it.name}") }

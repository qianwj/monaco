plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "monaco"

include(
    // 旧模块（迁移期保留，不被新模块依赖）
    "common",
    "gateway",

    // 独立日志库（长期保留，可选依赖）
    "logging",

    // 新架构模块
    "protocol",
    "core",
    "runtime-reactor",
    "transport-reactor",
    "store",
    "store:memory",
    "store:rocksdb",
    "security-default",
    "plugin",
    "plugin:api",
    "plugin:runtime",
    "plugin:remote",
    "observability-micrometer",
    "broker",
    "testkit"
)

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("26.5.0")
    download.set(false)
}

tasks.named("npmInstall") {
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("vite.config.ts")
    outputs.dir("dist")
}

tasks.named("assemble") {
    dependsOn(tasks.named("npmBuild"))
}

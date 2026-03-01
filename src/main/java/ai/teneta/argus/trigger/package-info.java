@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "shared", "queue", "tool :: sanitizer", "hitl" }
)
package ai.teneta.argus.trigger;

@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "shared", "queue", "tool :: sanitizer" }
)
package ai.teneta.argus.trigger;

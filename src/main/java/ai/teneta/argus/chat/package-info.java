@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "agent", "agent::impl", "audit", "security", "shared", "tool::sanitizer" }
)
package ai.teneta.argus.chat;

## migration-fabric-26.2 Fabric sibling
- Forge code is deeply platform-coupled; a separate Fabric Loom project avoids broken mixed source sets.
- Verified coordinates: Minecraft 26.2, Java 25, Forge 65.1.3, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.18.1.
- Fabric adapter is intentionally lifecycle/config-only because Fabric 26.2 behavior hooks were not verified.
- Local Gradle/buildClient execution was forbidden; validation used editor diagnostics and static checks.
- Learnings consumed: (none)

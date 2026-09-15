## migration-fabric Fabric sibling
- Forge code is deeply platform-coupled; a separate Fabric Loom project avoids broken mixed source sets.
- Verified coordinates: Fabric Minecraft 26.2 unobfuscated toolchain, Java 25, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.17-SNAPSHOT; NeoForge Minecraft 26.2 uses 26.2.0.87.
- Fabric adapter remains limited where 26.2 behavior hooks were not verified.
- Local Gradle/buildClient execution was forbidden; validation used editor diagnostics and static checks.
- Learnings consumed: (none)

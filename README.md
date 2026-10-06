# Constancy

Constancy is a Forge compatibility shim for the legacy Continuity mod ID.

Connected textures and related model rendering are delegated to
[ConnectedTexturesMod (CTM)](https://www.curseforge.com/minecraft/mc-mods/ctm).
Constancy does not embed Fabric API, Fabric Renderer, Sinytra Connector, or the
old Continuity rendering implementation.

## Current backend

- Minecraft: 1.20.1
- Loader: Forge 47.4.16
- Rendering backend: CTM 1.20.1-1.1.10
- Compatibility mod ID: `continuity`

The `continuity` entry is retained only so that other mods which check for the
legacy Continuity mod ID remain compatible. CTM is the required client-side
backend and owns model wrapping and connected-texture rendering.

## Build

Use the project Gradle cache location:

```powershell
$env:JAVA_HOME = 'D:\java\jdk21'
$env:GRADLE_USER_HOME = 'D:\document\dev\gradle\.gradle'
.\gradlew.bat --no-daemon jar
```

The old Continuity/Fabric renderer sources and Forgified Fabric API build plugin
have been removed. Resource-pack format adaptation remains a separate follow-up
area where legacy Continuity/OptiFine properties need to be translated to CTM's
model and texture metadata format.

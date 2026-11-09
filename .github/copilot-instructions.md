# Local Minecraft paths

The user's downloads are in `D:\downloads`, not the Windows default Downloads
folder. Check there first for downloaded Minecraft files.
NeoForge texture/resource packs go in
`D:\MinecraftInstances\NeoForge-26.2\resourcepacks`; keep resource-pack ZIPs zipped.

# Build deployment preference

After every successful NeoForge release JAR build, copy
`neoforge-26.2\build\libs\farmupcraft-neoforge-26.2-2.0.0.jar` to both:

- `D:\MinecraftShare\farmupcraft-neoforge-26.2-2.0.0.jar`
- `D:\MinecraftInstances\NeoForge-26.2\mods\farmupcraft-neoforge-26.2-2.0.0.jar`

Before overwriting, back up each existing destination JAR with a timestamp and
destination label under
`D:\MinecraftInstances\NeoForge-26.2\farmupcraft-backups`.
Verify that both destination SHA-256 hashes match the source. Report failures
explicitly. Do not restart Minecraft automatically.

Never overwrite a mod JAR used by a running Minecraft client or server.
Check the destination's owning process before copying. If it is running or its
status is uncertain, defer that destination until Minecraft is fully closed and
tell the user; do not stop their game automatically. Apply the same check to the
shared JAR if a server uses it directly. Replacing an open JAR can cause delayed
class-loading crashes even when the replacement file passes hash verification.

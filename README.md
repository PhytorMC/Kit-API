# Kit-API

Shared kit data model + Mongo persistence + chest-GUI editor for the Phytor stack.
Designed so Catalyst (bot kits) and the upcoming PhytorPractice (duel kits) can both
depend on this single artifact and share the same kit storage.

- **Language**: Kotlin (JVM 21), Paper API
- **Persistence**: MongoDB (driver is `compileOnly`; host plugin supplies it)
- **Group / artifact**: `gg.lode:kit-api:<git-tag>` via JitPack

## What you get

- `Kit` — 4 armor + 1 offhand + 36 main inventory slots, plus name / owner / category / timestamps
- `KitRepository` — interface; bundled `MongoKitRepository` implementation
- `KitApplicator<T>` — generic applier; bundled `PlayerKitApplicator` for vanilla Players
- `KitEditor` — 54-slot chest GUI with SAVE / CLEAR / COPY-FROM-ME buttons

## Consuming it

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.PhytorMC:Kit-API:0.1.0")
}
```

In your plugin:

```kotlin
val repo = MongoKitRepository(mongoClient.getDatabase("network"))
val editor = KitEditor(repo, plugin)
server.pluginManager.registerEvents(KitEditor.Listener(editor), plugin)

editor.open(player, "sword-default", category = "sword")
PlayerKitApplicator.apply(otherPlayer, repo.findByName("sword-default")!!)
```

For non-Player targets (bots, NPCs), implement `KitApplicator<YourTarget>` and copy
items from the Kit's `armor` / `offhand` / `contents` arrays into the target's
inventory.

## License

Copyright © 2026 Lodestone Services LLC. All rights reserved.

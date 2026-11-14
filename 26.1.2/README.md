# RS Storage Locator for NeoForge 26.1.2

Hover an item or fluid in a Refined Storage grid (Grid, Crafting Grid, Pattern Grid, or a Wireless Grid) and the
tooltip lists where the network keeps it: which disk in which Disk Drive slot, which block each External Storage
faces (chests, drawers, tanks...), and any other storage on the network (Storage Blocks...), with how much each.
The six biggest holders are listed and the rest summed. The Portable Grid has no network, so it shows nothing.

Press **Insert** (rebindable: Options > Controls > RS Storage Locator) on an entry in a grid: the grid closes and the
blocks holding it are outlined in the world, through walls too. `config/rsstoragelocator-client.toml` `highlightSeconds` (default 30) sets how
long.

- Server: answers from the network of the grid the player has open (max 20 lookups a second per player).
- Client: asks once per entry and caches the answer for 5 seconds; shows "Locating..." until it arrives.
- Install on both sides for the tooltip. Either side alone loads fine and does nothing.

Built against Refined Storage 3.2.1 for NeoForge (works with any 3.x). Run `./gradlew build` in this folder
(Java 25); the jar lands in `build/libs` as `rsstoragelocator-<mod version>-26.1.2-neoforge.jar`.

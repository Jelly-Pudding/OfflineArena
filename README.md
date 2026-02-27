# OfflineArena

A Paper plugin for [minecraftoffline.net](https://www.minecraftoffline.net).

Spawns a shrinking cylindrical "Dead Zone" at a random location near world origin. Players inside earn tokens through SimpleVote with rewards getting higher the smaller the zone gets. Hostile mob spawns scale inversely with occupancy so the fewer players there are inside the worse it gets. Zone parameters (radius, shrink rate, spawn interval) are randomised each cycle so there is some variation.

## Dependencies

- **Required:** [SimpleVote](https://github.com/Jelly-Pudding/SimpleVote) - token economy
- **Optional:** [DiscordRelay](https://github.com/Jelly-Pudding/minecraft-discord-relay) - zone open/close announcements to Discord

## Commands

`/offlinearena <reload | status | open | close>` - requires `offlinearena.admin` (default: op)

## Configuration

See `config.yml` for all options. Key settings:

| Key | Description |
|-----|-------------|
| `zone.spawn-radius` | Max distance from origin the zone can appear |
| `zone.initial-radius-min/max` | Randomised starting radius range |
| `zone.shrink-interval-min/max` | Randomised shrink tick rate |
| `zone.shrink-amount-min/max` | Randomised blocks removed per tick |
| `zone.respawn-delay-min/max` | Time before zone reopens after collapse |
| `tokens.base-reward` / `max-reward` | Tokens per minute at full/minimum size |
| `mobs.base-spawn-count` | Mobs per cycle when zone is empty |
| `mobs.player-capacity` | Player count at which mob spawns are minimised |
| `mobs.max-withers` | Max simultaneous Withers during Collapse phase |

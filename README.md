# OfflineArena

A Paper plugin designed for [minecraftoffline.net](https://www.minecraftoffline.net).

Spawns a shrinking cylindrical Dead Zone at a random location within a configurable radius of world origin (0, 0). Players inside earn tokens through SimpleVote with rewards scaling up as the zone shrinks. Hostile mob spawns scale inversely with player occupancy so the fewer players inside the more dangerous it gets. Custom named mobs grow progressively harder as the zone shrinks through its phases and the environment fills with fire and explosions. Zone parameters including radius, shrink rate and mob cap are randomised each cycle to give some variety.

## Dependencies

- **Required** [SimpleVote](https://github.com/Jelly-Pudding/SimpleVote) for the token economy
- **Required** [VelocityGuard](https://github.com/Jelly-Pudding/VelocityGuard) to disable flight inside the zone
- **Optional** [DiscordRelay](https://github.com/Jelly-Pudding/minecraft-discord-relay) for zone open/close announcements
- **Optional** [SimpleLifesteal](https://github.com/Jelly-Pudding/SimpleLifesteal) for ghost mob heart drops to work

## How it works

1. A Dead Zone opens at a random location with water biomes avoided. Radius, shrink rate, spawn interval and mob cap are all randomised per cycle.
2. The zone passes through four phases as it shrinks: **Awakening ---> Intensifying ---> Critical ---> Collapse**. Each phase brings harder mobs, more fire and explosions and faster shrink cycles.
3. Players inside earn tokens on a dynamic interval that gets shorter as the zone shrinks. A flat bonus is awarded to anyone still inside when the zone collapses.
4. After collapsing the zone stays dormant for a random delay between 10 minutes and 4 hours before reopening elsewhere.

## Phases

| Phase | Shrink interval | Notable mobs |
|---|---|---|
| Awakening | 20 to 30 s | Vagabond, Lurker, Vagrant, Tracker, Loot Goblin |
| Intensifying | 17 to 23 s | Marauder, Marksman, Hunter, Raider, Incendiary |
| Critical | 13 to 18 s | Defiler, Alchemist, Reaper, Wraith, Hollow |
| Collapse | 8 to 15 s | Warlord, Psychopath, Void, Conjurer |

Any phase can also spawn a **ghost mob** named after a banned player (e.g. "Shadow of PlayerX"). Ghost mobs are tougher than normal and drop a lifesteal heart on death.

## Zone boundaries

The zone is a vertical cylinder with configurable Y bounds. Players outside the height range are treated as outside the zone. Particle walls plus ceiling and floor rings visually mark all boundaries.

## Visuals and audio

- Coloured particle walls rendered around the border follow your eye height. Anchor rings always show at the floor and ceiling.
- Concentric cap rings appear directly above and below you as you approach the height limits.
- Boss bar shows the current phase and time until next shrink.
- Proximity sound intensifies as you approach the border and changes with each phase.
- Fire, explosions and lightning escalate through the phases. The collapse finale triggers a configurable burst of large explosions.

## Commands

`/offlinearena reload | status | open | close` requires the `offlinearena.admin` permission (default op).

## Key config settings

| Setting | Description |
|---|---|
| `zone.spawn-radius` | Max distance from origin the zone can appear |
| `zone.initial-radius-min/max` | Randomised starting radius |
| `zone.height-min/max` | Vertical bounds of the zone |
| `zone.shrink-interval-<phase>-min/max` | Per-phase shrink cadence |
| `zone.shrink-amount-min/max` | Blocks removed per shrink |
| `zone.respawn-delay-min/max` | Dormancy window after collapse in seconds |
| `zone.collapse-delay-min/max` | Pause at minimum radius before collapsing |
| `tokens.base-reward` and `max-reward` | Tokens per tick at full and minimum zone size |
| `tokens.reward-interval-min/max` | Reward cadence range in seconds |
| `tokens.zone-close-reward` | Flat bonus for surviving the collapse |
| `mobs.base-spawn-count-min/max` | Mobs per cycle when nobody is inside |
| `mobs.phase-multiplier-<phase>` | Per-phase spawn count multiplier |
| `mobs.max-total-min/max` | Hard mob cap per zone |
| `mobs.ghost-spawn-chance` | Chance any spawn is replaced by a ghost mob |
| `environment.fire-*` and `explosions-*` | Per-phase fire and explosion counts |
| `environment.explosion-power-min/max` | Power of regular tick explosions |
| `environment.finale-explosions-*` | Collapse finale explosion count and power |
| `environment.lightning-chance-critical/collapse` | Lightning strike chance per explosion |

# ValorSky SkySignals

**Version 2.1.3** — Dynamic celestial events for the ValorSky Minecraft network, targeting **Folia 1.21.11**.

## Version 2.1.3

All six event types expose their actual destination or affected worlds in chat and `/skysignals active`. Meteor and sky chest messages show block coordinates and world; invasion and mineral rain messages include the zone radius. Storm and growth boost list affected worlds without inventing a landing point. Locations appear once when ready and again on impact/landing. Existing `messages.yml` files use built-in defaults for the new `location.*` keys.

Chat durations now render as `2 min 49 s`; duplicate meteor warnings are removed. The boss bar keeps its `MM:SS` display.

## 3D meteor model

Version 2.1.3 includes the supplied meteor model as a separate Minecraft 1.21.11 resource pack. Build everything with `./gradlew build dist`; the client pack is written to `build/distributions/ValorSky-SkySignals-ResourcePack-2.1.3.zip`.

Host that ZIP and set its URL and SHA-1 in the server's `server.properties`. The plugin renders `skysignals:meteor` with an animated `ItemDisplay`. Its material, scale, rotation, 20-second diagonal descent, starting height and horizontal approach distance are configurable under `events-config.meteor.model`. Players without the resource pack see a visible 3D magma block fallback.

Fixes Folia scheduling, event phase progression, standalone scheduling without Redis, reward claims, configuration loading, and distribution packaging. Build and distribution checks pass with 54 unit tests.

Install the complete `ValorSky-SkySignals-2.0.5.jar`; the `-plain.jar` does not include runtime dependencies. Stop the server before replacing older versions.

Live Folia validation is still required. Island/protection adapters and PlaceholderAPI integration remain incomplete. See [the audit and validation notes](AUDIT.md) for known limitations and deployment checks.

## Features

- **6 dynamic celestial events**: Meteor, Storm, Sky Chest, Mob Invasion, Mineral Rain, Growth Boost
- **Folia-native architecture** — uses `GlobalRegionScheduler`, `RegionScheduler`, `EntityScheduler`, and `AsyncScheduler`
- **Graceful degradation** — plugin starts even if MySQL/Redis/RabbitMQ are unavailable
- **Distributed state** — Redis pub/sub + RabbitMQ event propagation for multi-server networks
- **Configurable scheduling** — weighted random event selection with cooldowns and conflicts
- **Rich notifications** — boss bars, chat messages, titles, action bar, and particles
- **Reward system** — money, commands, and items via configurable rewards
- **3D animations** — meteor trajectory and visual impact effects

## Supported Events

| Event | Description | Default Duration |
|-------|-------------|-----------------|
| **Meteor** | Meteorite descends with visual impact effects | 180s |
| **Storm** | Lightning strikes, darkened sky, ambient effects | 120s |
| **Sky Chest** | Floating treasure chest descends with loot | 300s |
| **Mob Invasion** | Waves of hostile mobs spawn around players | 300s |
| **Mineral Rain** | Ore blocks rain from the sky for players to collect | 120s |
| **Growth Boost** | Accelerated crop growth in a radius | 300s |

## Requirements

- **Java 25** (JDK 25+)
- **Folia 1.21.11** (paperweight mappings, `folia-supported: true`)
- **MySQL 8+** or **MariaDB 10.6+** (optional — degrades gracefully)
- **Redis 6+** (optional — distributed locking disabled without it)
- **RabbitMQ 3.9+** (optional — messaging disabled without it)

## Installation

1. Download `ValorSky-SkySignals-2.0.5.jar` from `build/libs/`
2. Place it in your Folia server's `plugins/` directory
3. Start the server once to generate default configuration files
4. Edit `plugins/ValorSky-SkySignals/config.yml` with your service credentials
5. Restart the server

## Configuration

### config.yml

The default configuration includes all settings with sensible defaults. Key sections:

```yaml
server:
  id: "SkyBlock-01"

scheduler:
  enabled: true
  min-interval: 300      # Minimum seconds between scheduled events
  max-interval: 900      # Maximum seconds between scheduled events
  allowed-events: []      # Empty = all events allowed

event-weights:
  meteor: 30
  storm: 10
  sky_chest: 25
  mob_invasion: 15
  mineral_rain: 15
  growth_boost: 5

events:
  allow-concurrent: false
  max-active: 1

redis:
  enabled: true
  uri: "redis://localhost:6379"

rabbitmq:
  enabled: true
  host: "localhost"
  port: 5672

database:
  host: "localhost"
  port: 3306
  database: "valorsky"
  pool-size: 10

cache:
  maximum-size: 100
  expire-after-minutes: 10
```

All external services (MySQL, Redis, RabbitMQ) are **optional**. If unavailable, the plugin starts in degraded mode with reduced functionality.

### Degraded Mode

| Service | Impact if Unavailable |
|---------|----------------------|
| MySQL | No event history persistence; rewards recorded in-memory only |
| Redis | No distributed locking; single-server operation only |
| RabbitMQ | No cross-server event synchronization |

The plugin will retry connections in the background using Folia's `AsyncScheduler`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skysignals` | `valorsky.skysignals.use` | Base command |
| `/skysignals start <event>` | `valorsky.skysignals.start` | Start a specific event |
| `/skysignals stop` | `valorsky.skysignals.stop` | Stop active event |
| `/skysignals list` | `valorsky.skysignals.use` | List active/scheduled events |
| `/skysignals reload` | `valorsky.skysignals.reload` | Reload configuration |
| `/skysignals test <event>` | `valorsky.skysignals.test` | Test event animations |
| `/skysignals debug` | `valorsky.skysignals.debug` | Toggle debug mode |

## Building

### Prerequisites

- JDK 25
- Gradle 8.x (wrapper included)

### Build

```bash
# Full clean build with tests
./build.sh

# Or manually
./gradlew clean shadowJar

# Create distribution ZIP (includes JAR + config)
./gradlew dist
```

**Output:**
- `build/libs/ValorSky-SkySignals-2.0.5.jar` — Shaded plugin JAR (ready for deployment)
- `build/distributions/ValorSky-SkySignals-2.0.5.zip` — Distribution package

### Building on Windows

```cmd
build.bat
```

## Architecture

```
src/
├── main/java/com/valorsky/skysignals/
│   ├── SkySignalsPlugin.java              # Plugin entry point (Folia-aware)
│   ├── config/                             # Config management
│   ├── model/                              # Domain models
│   ├── event/                              # Event system
│   │   ├── impl/                           # Event implementations (Meteor, Storm, etc.)
│   │   └── EventContext.java               # Dependency injection context
│   ├── cache/                              # Caffeine local caching
│   ├── database/                           # HikariCP data source + migrations
│   ├── redis/                              # Redis pub/sub + distributed locks
│   ├── rabbitmq/                           # RabbitMQ pub/sub
│   ├── reward/                             # Loot reward system
│   ├── notification/                       # Boss bar, chat, title notifications
│   ├── listener/                           # Bukkit event listeners
│   ├── scheduler/                          # Event scheduling (Folia-safe)
│   │   └── SignalScheduler.java            # Weighted random event scheduler
│   ├── command/                            # SkySignals command
│   ├── particle/                           # Particle shape generators
│   ├── sound/                              # Sound management
│   ├── animation/                          # Animation sequence system
│   ├── location/                           # Safe location services
│   ├── island/                             # Island provider abstraction
│   ├── protection/                         # Block protection provider
│   ├── util/
│   │   └── FoliaScheduler.java             # Folia scheduler abstraction
│   └── api/                                # Public API
│       └── event/                          # Bukkit events for API integration
└── resources/
    ├── plugin.yml                          # Paper/Folia plugin metadata
    ├── paper-plugin.yml                    # Folia support flags
    ├── config.yml                          # Default configuration
    ├── messages.yml                        # Notification messages
    └── database/
        └── migration/                      # SQL schema migrations
```

### Scheduler Architecture

The plugin uses a centralized `FoliaScheduler` utility that abstracts all scheduling:

| Scope | Folia API | Use Case |
|-------|-----------|----------|
| **Global** | `Bukkit.getGlobalRegionScheduler()` | Event tick loop, scheduler timer |
| **Region** | `World.getChunkAtAsync().getScheduler()` | Particle effects, block modifications, meteor movement |
| **Entity** | `Entity.getScheduler()` | Player-specific notifications, entity effects |
| **Async** | `Bukkit.getAsyncScheduler()` | Database operations, network I/O, serialization |

All schedulers use reflection to access Folia APIs, with fallback behavior for non-Folia servers.

### Event Lifecycle

```
SCHEDULED → ANNOUNCING → WARNING → ACTIVE → COMPLETING → FINISHED
                                       ↓           or → CANCELLED
                                   EXPIRED (if not started)
```

Each event type implements its own phase behavior in `onPhaseChange()`.

### Data Flow

```
Scheduler (GlobalRegionScheduler)
  → SkyEventManager
    → Event tick (GlobalRegionScheduler)
      → Event state transitions (in-memory + Redis)
      → World operations (RegionScheduler)
        → Particles (RegionScheduler)
        → Sounds (RegionScheduler)
        → Blocks (RegionScheduler)
      → Notifications (AsyncScheduler → EntityScheduler for players)
    → Persistence (AsyncScheduler → HikariCP)
```

## Testing

```bash
./gradlew test
```

Tests cover:
- Event state transitions and lifecycle
- Cache service behavior
- Redis serialization format
- Event factory creation
- Scheduler cooldown logic

## Development

### Version

Current version: **2.0.5** (Folia 1.21.11 compatible)

### Code Style

- Java 25+ features used (switch expressions, records, pattern matching)
- No raw Bukkit scheduler calls outside `FoliaScheduler`
- All Minecraft world operations dispatch through region schedulers
- All I/O operations use `AsyncScheduler`

### Dependencies

| Library | Version | Scope | Shaded |
|---------|---------|-------|--------|
| Paper API | 1.21.11 | compileOnly | No |
| Adventure API | 4.20.0 | compileOnly | No |
| Caffeine | 3.2.0 | implementation | Yes |
| Lettuce Redis | 6.5.2.RELEASE | implementation | Yes |
| RabbitMQ Client | 5.21.0 | implementation | Yes |
| HikariCP | 5.1.0 | implementation | Yes |
| Gson | 2.10.1 | implementation | Yes |
| JUnit | 5.11.4 | testImplementation | No |
| Mockito | 5.14.2 | testImplementation | No |

## License

This project is proprietary software developed for the ValorSky network. All rights reserved.

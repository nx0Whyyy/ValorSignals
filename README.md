# ValorSky SkySignals

Dynamic celestial events for the ValorSky Minecraft network.

## Features

- Dynamic celestial events with configurable scheduling
- MySQL/MariaDB persistence for event state
- Caffeine local cache for fast lookups
- Redis distributed state synchronization across servers
- RabbitMQ event propagation for multi-server support
- Adventure and MiniMessage-based notifications
- Boss bar, chat, and title notifications

## Supported Events

- **Meteor** — Meteor showers light up the night sky
- **Storm** — Intense weather with lightning strikes
- **Sky Chest** — Rare floating treasure chests
- **Mob Invasion** — Hostile mobs spawn around players
- **Mineral Rain** — Ore blocks rain from the sky
- **Growth Boost** — Accelerated crop growth in a radius

## Requirements

- Java 21
- Paper 1.21.11
- MySQL or MariaDB
- Redis
- RabbitMQ

## Installation

1. Download the latest JAR from `build/libs/`
2. Place it in your Paper server's `plugins/` directory
3. Start the server once to generate the configuration files
4. Configure `config.yml` with your database, Redis, and RabbitMQ credentials
5. Create a `.env` file based on `.env.example` for environment-specific settings
6. Restart the server

## Configuration

### config.yml

```yaml
server-id: "skyblock-01"
scheduler:
  interval-seconds: 300
  min-events-per-hour: 1
  max-events-per-hour: 3
cache:
  maximum-size: 100
  expire-after-minutes: 10
notifications:
  bossbar: true
  chat: true
  title: true
database:
  pool-size: 10
redis:
  enabled: true
  channel: "skysignals"
rabbitmq:
  enabled: true
  exchange: "skysignals"
```

### messages.yml

Notification messages use MiniMessage formatting. See the bundled `messages.yml` for available keys and customization options.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skysignal` | `skysignals.command` | Base command |
| `/skysignal start <type>` | `skysignals.command.start` | Start a specific event |
| `/skysignal stop <id>` | `skysignals.command.stop` | Stop a running event |
| `/skysignal list` | `skysignals.command.list` | List active events |

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `skysignals.command` | `op` | Access to the sky signal command |
| `skysignals.command.start` | `op` | Start events |
| `skysignals.command.stop` | `op` | Stop events |
| `skysignals.command.list` | `true` | List active events |
| `skysignals.bypass` | `op` | Bypass event restrictions |

## Architecture

```
src/
├── main/java/com/valorsky/skysignals/
│   ├── SkySignalsPlugin.java              # Plugin entry point
│   ├── config/                             # Configuration management
│   ├── model/                              # Domain models (EventState, SkyEventType, SkyEvent)
│   ├── event/                              # Event system (AbstractSkyEvent, factory, manager)
│   │   ├── impl/                           # Concrete event implementations
│   ├── cache/                              # Caffeine-based local caching
│   ├── database/                           # HikariCP + Flyway migration support
│   │   ├── schema/                         # SQL migration scripts
│   ├── redis/                              # Redis pub/sub synchronization
│   ├── rabbitmq/                           # RabbitMQ event propagation
│   ├── reward/                             # Loot reward system
│   ├── notification/                       # Boss bar, chat, and title notifications
│   ├── listener/                           # Bukkit event listeners
│   ├── scheduler/                          # Event scheduling logic
│   ├── command/                            # SkySignals command
│   └── api/                                # Public API and expansions
└── resources/
    ├── plugin.yml                          # Paper plugin metadata
    ├── config.yml                          # Default configuration
    └── messages.yml                        # Notification messages

database/
└── migration/                              # SQL schema migrations
    ├── V1__create_events_table.sql
    └── V2__create_participants_table.sql
```

### Components

- **SkyEventManager** — Central coordinator for event lifecycle (create, start, stop, cancel)
- **SkyEventFactory** — Factory for creating event instances from types and states
- **CacheService** — Wraps Caffeine for local event state caching with TTL
- **DatabaseManager** — HikariCP connection pool + schema migration runner
- **RedisService** — Redis pub/sub for cross-server event state synchronization
- **RabbitManager** — RabbitMQ topic exchange for event broadcasting
- **NotificationService** — Multi-channel notifications (boss bar, chat, title)
- **RewardService** — Configurable loot rewards parsed from config

### Event Lifecycle

```
SCHEDULED → ACTIVE → FINISHED
SCHEDULED → CANCELLED
SCHEDULED → EXPIRED (if not started in time)
```

## Building

```bash
./gradlew clean build
```

The shaded JAR is output to `build/libs/`.

## Testing

```bash
./gradlew test
```

## Development

This project is developed for the ValorSky Minecraft network and is maintained by the ValorSky development team.

## License

This project is proprietary software developed for the ValorSky network. All rights reserved.

# 🔗 Synergy
*Velocity • BungeeCord • Paper • Fabric*

[![Version](https://img.shields.io/badge/version-1.5.4-blue.svg)](https://github.com/mrbear22/Synergy/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-Paper%201.21%2B%20%7C%20Fabric%201.21.1%20%2F%2026.2-orange.svg)](https://minecraft.net)
[![Java](https://img.shields.io/badge/java-21+-red.svg)](https://adoptium.net)

> **Create synergy between your Minecraft servers and unite them into a solid, seamless project**

One jar for the whole network. The proxy is the brain: it relays events between servers and runs Discord, Twitch and the other network-wide services, so backend servers stay light.

---

## ✨ Features

🌐 **Cross-Server Events** - Servers talk about anything under any string ID, with delivery guarantees, request/reply and addressing  
🗺️ **Localization** - `<locale:key>` translations, MiniMessage, color themes, gendered text  
💬 **Chat Manager** - Channels, filters, emojis, radius chat, Discord bridge, mutes  
💾 **Player Data** - `BreadMaker` with persistent data on SQLite or MySQL  
🤖 **Discord, Twitch, TikTok, Monobank, Votifier** - Linking, role sync, stream rewards, donations, votes  
🧩 **Self-sufficient** - Every optional plugin is optional: a module that needs a missing plugin turns itself off

---

## 📦 Installation

Put the same `Synergy.jar` everywhere:

| Platform | Where | Needs |
|---|---|---|
| Velocity / BungeeCord | `plugins/` | — |
| Paper / Purpur 1.21+ | `plugins/` | PacketEvents for packet localization (optional) |
| Fabric 1.21.1 (Java 21) / 26.2 (Java 25) | `mods/` | Fabric API |

Nothing goes on the client.

Optional integrations, picked up automatically when installed: LuckPerms, PlaceholderAPI, PacketEvents, AuthMe, SkinsRestorer, Plan, fabric-permissions-api. Each one is a module that turns itself off when its plugin is missing.

### Network setup

1. Start the proxy once: it generates `messaging.token` in `plugins/Synergy/config.yml`.
2. On every backend set `messaging.enabled: true` and the same `messaging.token`. `messaging.servername` is filled in by itself from the proxy's name for that server once the first player joins.
3. Point every server at the same MySQL database in `storage` so links and player data are shared.

In a network the proxy is the hub: Discord, the web server and Votifier run there and are switched off on backends. A standalone server (`messaging.enabled: false`) runs all of them itself.

Synergy warns once in the console when something is off: no answer from the proxy (no Synergy there, wrong `messaging.token`, or a standalone server with messaging on), SQLite in a network, a hub module enabled on a backend, or Discord without a bot token or guild id.

Modded clients with many mods are kicked by Velocity with *"A packet did not decode successfully"*. Synergy raises Velocity's known-packs limit by itself, no `-D` flag needed.

### Configuration files

```
plugins/Synergy/
├─ config.yml          messaging, storage, motd, commands, votifier, web-server, resource-pack, deepl…
├─ discord.yml
├─ chat-manager.yml
├─ localizations.yml
├─ twitch.yml
├─ tiktok.yml
├─ monobank.yml
└─ locales/
   └─ synergy.yml      English defaults of every module, missing keys are added on start
```

A file only gets the settings of modules that run on that server: in a network `discord.yml`, the web server and the Votifier listener live on the proxy, chat and commands on the backends.

---

## 🧑‍💻 API

### Maven

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/mrbear22/Synergy</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>archi.quest</groupId>
        <artifactId>synergy</artifactId>
        <version>1.5.4</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

GitHub Packages needs a [personal access token](https://github.com/settings/tokens) with `read:packages` in `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_PERSONAL_ACCESS_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Cross-server events

Any server can send any event; there is no list of allowed events.

```java
Synergy.event("player-achievement")
       .setPlayerUniqueId(uuid)
       .setOption("achievement", "dragon_slayer")
       .setOption("reward", Map.of("coins", 100, "items", List.of("diamond")))
       .send();
```

```java
public class Achievements implements SynergyListener {

    public void initialize() {
        Synergy.getEventManager().registerEvents(this);
    }

    @SynergyHandler("player-achievement")
    public void onAchievement(SynergyEvent event) {
        String achievement = event.getOption("achievement").getAsString();
        Reward reward = event.getOption("reward").getAs(Reward.class);
        BreadMaker bread = event.getBread();
    }
}
```

- `@SynergyHandler` without arguments receives every event; `@SynergyHandler({"vote", "tiktok-*"})` only those, `*` is a wildcard.
- Option values can be strings, numbers, booleans, lists, maps or objects (sent as JSON).
- Static handlers work too: `registerEvents(MyModule.class)`.
- On Paper every event is also fired as the Bukkit event `me.synergy.bukkit.events.SynergyEvent`.

**Addressing**

```java
Synergy.event("dispatch-command").to("survival").setOption("command", "say hi").send();
Synergy.event("open-menu").toPlayer(uuid).send();
```

`to(server)` delivers to one server, `toPlayer(uuid)` to whichever server the player is on, `toProxy()` to the hub, nothing means everyone. On a standalone server every event is handled locally whatever the address.

**Request / reply**

```java
Synergy.event("balance").to("survival").setPlayerUniqueId(uuid).request()
       .thenAccept(reply -> sender.sendMessage("Balance: " + reply.getOption("amount").getAsInteger()));

@SynergyHandler("balance")
public void onBalance(SynergyEvent event) {
    event.reply().setOption("amount", economy.balance(event.getPlayerUniqueId())).send();
}
```

`request()` times out after 10 seconds (`request(Duration)` to change it). Replies only go to the one waiting request, never to handlers.

**Delivery**

- Every message carries an id. Backends acknowledge it, and the proxy resends until they do.
- Messages for a server that is down or empty wait on the proxy and are delivered as soon as a player joins it (`messaging.queue-ttl-seconds`, 600 by default). Chat-like events listed in `messaging.not-queued` are not kept for empty servers.
- A backend with nobody online keeps its outgoing messages until the first player joins.
- Duplicates are dropped, and every message is signed with HMAC-SHA256 using `messaging.token`.
- `/synergy trace`, `/vsynergy trace` or `/bsynergy trace` logs every event passing through that server.

### Player data

```java
BreadMaker bread = Synergy.getBread(uuid);

int level = bread.getData("level").getAsInteger();
bread.setData("level", 25);
bread.setData("invite-code", code, Duration.ofMinutes(10));
if (bread.cooldown("gift", 30)) return;
```

Values are cached, synced to every server and can expire on their own.

### Messages

```java
LocaleBuilder.of("welcome").placeholder("player", name).send(bread);
String forDiscord = LocaleBuilder.of("welcome").placeholder("player", name).stripped();
```

`send` reaches the player on whatever server they are on; `stripped(language)` gives plain text for Discord, web pages and consoles.

### Modules

Every feature is a `Module`. Synergy checks its config toggle, side, required plugins and placeholder values before starting it, and stops it on shutdown:

```java
public class Shop implements Module {
    public String toggle() { return "shop.enabled"; }
    public String[] plugins() { return new String[] {"LuckPerms"}; }
    public String[] filled() { return new String[] {"shop.api-key"}; }
    public void defaults() {
        Config.addDefault("shop.enabled", false);
        Config.addDefault("shop.api-key", "key");
        Locales.addDefault("shop.bought", "<success>You bought %item%!");
    }
    public void start() { Tasks.every(Shop::refresh, Duration.ofMinutes(5)); }
}
```

Other plugins add theirs with `Synergy.register(new Shop())`. Background work goes through `Tasks.async / later / every` (virtual threads) and HTTP through `Http.get(url).async()`.

### Commands

Commands are written once and work on Paper and Fabric (and on proxies with a `v`/`b` prefix). A command declares its syntax lines and gets help, tab completion, permission and console checks for free:

```java
public class HomeCommand extends Command {

    public HomeCommand() {
        super("home|h [name]", "home set <name>", "home remove <name>");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        switch (path) {
            case "home" -> teleport(sender, args.length == 0 ? "home" : args[0]);
            case "home.set" -> save(sender, args[0]);
            case "home.remove" -> remove(sender, args[0]);
        }
    }
}
```

- `path` is the literal words of the matched line, `args` are its `<required>`, `[optional]` and `<rest...>` values
- permission is `synergy.<path>` (`synergy.home.set`), override `permission(path)` to change it or return `null` for everyone
- only players can run it unless `console(path)` returns `true`
- a wrong input shows every line the sender may use, each described by the `command.<path>` locale through the `help` locale
- `values(sender, name, input)` feeds tab completion for `<name>`; `<player>` completes online players by default

---

## 🌍 Localization & message processing

Every message goes through the same pipeline:

1. **Translation** - `<locale:key>` replaced in the player's language
2. **Gender** - `<male>/<female>/<nonbinary>` picked from player data
3. **Colors** - legacy `&` codes, theme tags (`<primary>`, `<danger>`…), custom color tags
4. **Placeholders** - PlaceholderAPI on Paper; LuckPerms placeholders (`%luckperms_prefix%`, `%luckperms_suffix%`, `%luckperms_primary_group_name%`, `%luckperms_meta_<key>%`) everywhere LuckPerms is installed
5. **Interactive tags** - sounds, titles, actionbars
6. **MiniMessage** - gradients, hover, click

```yaml
localizations:
  color-themes:
    default:
      primary: <#1abc9c>
      secondary: <#3498db>
      success: <#2ecc71>
      danger: <#e74c3c>
```

**Arguments and nesting**

Tags take arguments that fill `%name%` placeholders, and translations can contain other tags:

```yaml
account.link.done:
  en: '<success>%service% account linked!'
  uk: '<success>Акаунт %service% прив''язано!'
command.vote:
  en: vote for the server
  uk: проголосувати за сервер
command.usage:
  en: '<primary>/%command% <secondary>— %description%'
```

```java
sender.send("<locale:account.link.done service=TikTok>");
sender.send(LocaleBuilder.of("player.name.unknown").placeholder("name", name));
```

Translations live in `locales/*.yml`, split into as many files as you like. Modules register English defaults the same way as config defaults (`Locales.addDefault("chat.message.empty", "<danger>Message can't be empty!")`, several lines for a list), missing ones are added to `synergy.yml` on start, a language that misses keys of another is reported in the console, and `/synergy locale <lang>` fills a new language through DeepL.

**Interactive tags**

```yaml
level-up: '<sound:''entity.player.levelup''>You leveled up!'
welcome: '<title:''Welcome!'':''Enjoy'':20:100:20>'
boss: '<actionbar:''Boss nearby!''>'
delayed: '<delay:3>This appears after 3 seconds'
clear: '<clear_chat>Fresh start!'
```

**Gendered text**

```yaml
quest-complete:
  en: '%player% completed<male:' his'><female:' her'><nonbinary:' their'> first quest!'
  uk: '%player% виконал<male:''><female:а><nonbinary:о> перше завдання!'
```

**Packet localization (Paper + PacketEvents)**

Other plugins' messages are translated too: put `<locale:key>` into their configs (AuthMe and others) and Synergy translates chat, titles, scoreboards, entity names, items and more before they reach the player.

---

## 💬 Chat manager

Works the same on Paper and Fabric.

```yaml
chats:
  global:
    enabled: true
    color: <#dedee0>
    tag: '[G]'
    symbol: '!'
    discord:
      channel: '1234567890123456789'
  local:
    enabled: true
    color: <#deceb4>
    tag: '[L]'
    radius: 500
  admin:
    enabled: true
    tag: '[A]'
    symbol: \
    permission: synergy.chat.admin
format: '%COLOR%%CHAT% %luckperms_prefix%%DISPLAYNAME%%COLOR%: %MESSAGE%'
```

- Channels by symbol (`!global`, `\admin`), permission, radius or a shared placeholder value
- Blocked words with fuzzy matching, ignored words, emojis, custom color tags
- Discord bridge both ways, chat lock, slowmode, mutes (`/tempmute`, `/unmute`)
- Muted players can't use the commands in `muted-blocked-commands`; commands are logged to Discord

Discord embeds for chat messages are a template; every field is optional and a chat can override any of them under `chats.<name>.discord.embed`:

```yaml
discord-embed:
  author: '%PLAYER%'
  author-icon: ''          # empty = discord.avatar-link
  author-url: ''
  title: ''
  url: ''
  description: '%MESSAGE%'
  thumbnail: ''
  image: ''
  footer: '%CHAT% · %SERVER%'
  footer-icon: ''
  color: '%COLOR%'
  timestamp: true
```

Placeholders: `%PLAYER%`, `%MESSAGE%`, `%SERVER%` (the server's name in the network), `%CHAT%`, `%COLOR%`, `%UUID%`, plus PlaceholderAPI / LuckPerms ones.

Custom filters from code:

```java
Chat.register("custom", (sender, viewers) -> {
    viewers.removeIf(uuid -> !isFriend(sender, uuid));
    return true;
});
```

**Commands:** `/chat`, `/colors`, `/emojis`, `/chatfilter`, `/tempmute`, `/unmute`, `/language`, `/theme`, `/pronoun`, `/discord`, `/twitch`, `/tiktok`, `/monobank`, `/vote`, `/delayed`, `/synergy`, plus your own text commands from `config.yml → commands`.

---

## 🎮 Streams & donations

### Twitch channel points

```yaml
enabled: true
client-id: your-client-id
rewards:
  creeper_prank:
    cost: 25
    title: Creeper Sneak Attack
    description: Spawn a creeper behind the streamer
    input-required: false
    commands:
      - execute at %streamer_name% run summon minecraft:creeper ^ ^ ^-1
```

### TikTok Live

Gifts, likes, follows and comments run commands; `/tiktok link @user` and Synergy connects when the player joins.

### Monobank

```yaml
enabled: true
rewards:
  flash_speed:
    cost: 15
    title: Flash Speed
    commands:
      - effect give %streamer_name% minecraft:speed 20 5 true
```

Placeholders: `%streamer_name%`, `%viewer_name%` / `%counter_name%`.

---

## 🤖 Discord

Runs on the proxy.

- Account linking (`/discord link`), kick if not linked or banned on Discord
- Role sync through LuckPerms installed next to the bot (the proxy, or a standalone server): on join, on link/unlink, on Discord role changes and on LuckPerms changes

```yaml
roles:
  enabled: true
  from-discord: true    # Discord roles decide the LuckPerms groups
  to-discord: false     # LuckPerms groups decide the Discord roles (ignored while from-discord is on)
  groups:
    admin: '1234567890123456789'
```

- Welcome messages with buttons, highlights with reactions and threads, join/leave and chat logs, embeds from any server:

```java
Synergy.event("discord-embed")
       .setOption("channel", "1234567890123456789")
       .setOption("title", "New player joined!")
       .setOption("color", "#00FF00")
       .send();
```

---

## 🔧 More

- **Votifier** - built-in vote listener on the proxy; rewards run on the backend where the player is
- **Web server** - `/head/{uuid|name}`, `/skin/{uuid|name}` (SkinsRestorer-aware), Discord OAuth
- **DeepL** - `/synergy locale <lang>` translates every missing key from the default language
- **Placeholder output replacements** - turn empty placeholder results into nothing (`%none%`)

---

## 🛠️ Building

```
mvn package
```

The jar is `dist/target/Synergy.jar`. With JDK 25 it includes Fabric 26.2; with JDK 21 everything except Fabric 26.2. The first build downloads the Minecraft 1.21.1 server and its mappings to compile the Fabric 1.21.1 part (never shipped).

---

## 📄 License

MIT - see [LICENSE](LICENSE).

## 🐛 Issues & support

Found a bug or want a feature? [Create an issue](https://github.com/mrbear22/Synergy/issues).

<div align="center">

**Made with ❤️ for the Minecraft community**

</div>

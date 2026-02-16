# 🔗 Synergy
*Velocity • BungeeCord • Spigot*

[![Version](https://img.shields.io/badge/version-1.5.0-blue.svg)](https://github.com/mrbear22/Synergy/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-1.8+-orange.svg)](https://minecraft.net)
[![Java](https://img.shields.io/badge/java-8+-red.svg)](https://java.com)

> **Create synergy between your Minecraft servers and unite them into a solid, seamless project**

A comprehensive messaging and tooling plugin designed for modern Minecraft server networks. Synergy bridges the gap between proxy servers and individual game servers, providing seamless communication, data synchronization, and localization features.

---

## ✨ Features

🌐 **Cross-Server Messaging** - Synchronize events and data across your entire network  
🗺️ **Multi-Language Support** - Built-in localization system with MiniMessage formatting  
💬 **Advanced Chat Manager** - Custom chats, filters, emojis, and gender-specific messages  
💾 **Player Data Storage** - Convenient API for persistent player data management  
🔧 **Proxy & Standalone** - Works with Velocity, BungeeCord, and standalone Spigot servers  
⚡ **Fast** - Optimized performance with minimal resource usage

---

## 📦 Installation

### Maven
Add the repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub mrbear22 Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/mrbear22/Synergy</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>archi.quest</groupId>
        <artifactId>synergy</artifactId>
        <version>1.5.0</version>
    </dependency>
</dependencies>
```

### Gradle
Add to your `build.gradle`:

```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/mrbear22/Synergy")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation 'archi.quest:synergy:1.5.0'
}
```

### 🔐 Authentication Setup

You'll need a [Personal Access Token](https://github.com/settings/tokens) with `read:packages` permission.

**For Maven users** - Add to `~/.m2/settings.xml`:
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

**For Gradle users** - Set environment variables or add to `gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

---

## 🚀 Quick Start

### Cross-Server Event System

Send events across your network with ease:

```java
// Trigger an event from any server
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    Synergy.event("broadcast-message")
           .setOption("message", String.join(" ", args))
           .send();      
    return true;
}

// Handle the event on all servers
@EventHandler
public void onSynergyEvent(SynergyEvent e) {
    if (!e.getIdentifier().equals("broadcast-message")) {
        return;
    }
    Bukkit.broadcastMessage(e.getOption("message"));
}
```

### Player Data Management

Store and retrieve player data effortlessly:

```java
BreadMaker bread = Synergy.getBread(uuid);

// Get player data
int level = bread.getData("level").getAsInt();
String language = bread.getData("language").getAsString();

// Save data permanently
bread.setData("level", 25);
bread.setData("language", "uk");
```

---

## ⚡ Cross-Server Event System

Powerful event-driven architecture for synchronizing data and actions across your entire network.

### How It Works

Send events from any server (Spigot, Velocity, BungeeCord) and handle them on all servers simultaneously:

```java
// Send event from Server A
Synergy.event("player-achievement")
       .setPlayerUniqueId(player.getUniqueId())
       .setOption("achievement", "dragon_slayer")
       .setOption("server", "survival")
       .send();

// Handle event on Server B, C, D...
@EventHandler
public void onSynergyEvent(SynergyEvent e) {
    if (!e.getIdentifier().equals("player-achievement")) return;
    
    String achievement = e.getOption("achievement").getAsString();
    String server = e.getOption("server").getAsString();
    BreadMaker bread = e.getBread();
    
    Bukkit.broadcastMessage(
        bread.getDisplayName() + " unlocked " + achievement + " on " + server + "!"
    );
}
```

### Real-World Use Cases

**Global Announcements**
```java
// From lobby server
Synergy.event("network-broadcast")
       .setOption("message", "<rainbow>Server restart in 5 minutes!")
       .send();
```

**Cross-Server Player Data Sync**
```java
// Update player currency across all servers
Synergy.event("economy-update")
       .setPlayerUniqueId(uuid)
       .setOption("balance", "1000")
       .setOption("action", "set")
       .send();
```

**Discord Integration**
```java
// Send embed to Discord from any server
Synergy.event("discord-embed")
       .setOption("channel", "announcements")
       .setOption("title", "New Player Joined!")
       .setOption("description", player.getName() + " joined the network")
       .setOption("color", "#00FF00")
       .setOption("avatar", "%self%")
       .send();
```

**Punishment Synchronization**
```java
// Ban player across entire network
Synergy.event("global-ban")
       .setPlayerUniqueId(uuid)
       .setOption("reason", "Cheating")
       .setOption("duration", "7d")
       .send();
```

### Event Features

- **Network-wide propagation** - Events reach all connected servers instantly
- **Player context** - Attach UUID to access player data on any server
- **Flexible data** - Send any key-value pairs as options
- **BreadMaker integration** - Access player data through `e.getBread()`
- **Type-safe options** - Get values as String, Int, Boolean, etc.
- **Proxy support** - Works seamlessly with Velocity and BungeeCord

---

## 🖼️ Self-Hosted Skin Server

Built-in HTTP server for serving player skins and heads with automatic caching:

- **Endpoints**: `/head/{uuid|name}` and `/skin/{uuid|name}`
- **SkinsRestorer integration** - Automatically uses custom skins from SkinsRestorer
- **Mojang API fallback** - Falls back to official Mojang textures
- **Smart caching** - 10-minute cache with automatic cleanup
- **Performance** - Multi-threaded processing with connection pooling

Configure in your config:

```yaml
web-server:
  enabled: true
  port: 8080
```

Access skins at `http://your-server:8080/head/Notch` or `http://your-server:8080/skin/uuid-here`

---

## 🌍 Advanced Localization & Message Processing

Synergy's localization system is a complete message processing pipeline that transforms every text element on your server.

### Processing Pipeline

Every message passes through these stages in order:

1. **Translation Layer** - `<locale:key>` tags replaced with player's language
2. **Gender Adaptation** - `<male>/<female>/<nonbinary>` tags processed based on player data
3. **Color Processing**:
   - Legacy color codes (`&c`, `&l`) → MiniMessage tags
   - Theme tags (`<primary>`, `<accent>`) → Custom colors
   - Custom color codes (`&p`, `&s`) → Hex colors
   - Color replacement system for global color schemes
4. **PlaceholderAPI** - All PAPI placeholders expanded
5. **Interactive Tags** - Special action tags executed
6. **MiniMessage Rendering** - Full MiniMessage support (gradients, hover, click events)

### Color System

Define color themes that work across your entire server:

```yaml
localizations:
  color-themes:
    default:
      primary: <#deceb4>
      secondary: <#8b8680>
      accent: <#f66463>
      success: <green>
      error: <red>
    
    dark:
      primary: <#1a1a1a>
      secondary: <#404040>
      accent: <#ff6b6b>
  
  color-replace:
    highlight: <bold><accent>
    subtitle: <italic><secondary>
```

Use theme tags anywhere:

```yaml
welcome: '<primary>Welcome to <accent>%server%<primary>!'
error: '<error>Something went wrong!'
```

### Interactive Tags

Transform static messages into interactive experiences:

**Sound Effects**
```yaml
level-up: '<sound:''entity.player.levelup''>You leveled up!'
achievement: '<sound:''ui.toast.challenge_complete''>Achievement unlocked!'
```

**Titles & Subtitles**
```yaml
welcome: '<title:''Welcome!'':'':20:100:20>Join our community'
quest-complete: '<title:''Quest Complete'':''Reward: 100 coins'':10:60:10>'
```

**Actionbar Messages**
```yaml
boss-warning: '<actionbar:''Boss nearby!''>A powerful enemy approaches'
cooldown: '<actionbar:''Ability ready in 5s''>'
```

**Special Effects**
```yaml
# Delay message by 3 seconds
delayed: '<delay:3>This appears after 3 seconds'

# Cancel original message (useful for custom formatting)
custom: '<cancel_message>Your custom message here'

# Clear player's chat
clear: '<clear_chat>Fresh start!'
```

### Packet-Level Interception

Using ProtocolLib, Synergy intercepts and processes packets before they reach the player:

**What Gets Translated:**
- All chat messages (system, player, plugin)
- Titles, subtitles, actionbars
- Boss bar text
- Scoreboard objectives, teams, scores
- Player list headers/footers
- Inventory titles
- Item names and lore (including books)
- Disconnect/kick messages
- Entity names
- Map text

**Example - Third-Party Plugin Integration:**

AuthMe config (`messages_en.yml`):
```yaml
login:
  command_usage: '<locale:login-usage>'
  wrong_password: '<locale:login-wrong-password>'
  success: '<locale:login-success>'
```

Your `locales.yml`:
```yaml
login-usage:
  en: '<error>Usage: /login <password>'
  uk: '<error>Використання: /login <пароль>'
  
login-success:
  en: '<sound:''entity.player.levelup''><success>Successfully logged in!'
  uk: '<sound:''entity.player.levelup''><success>Ви успішно увійшли!'
```

**Result:** AuthMe messages are now translated, colored with your theme, and play sounds - without modifying AuthMe itself.

### Gender-Specific Messages

Synergy automatically adapts messages to each player's gender:

```yaml
quest-complete:
  en: '%player% completed<male:' his'><female:' her'><nonbinary:' their'> first quest!'
  uk: '%player% виконал<male:''><female:а><nonbinary:о> перше завдання!'

achievement:
  en: 'He<m:''><f:' She'><nb:' They'> earned the Dragon Slayer achievement!'
```

Set player gender:
```java
bread.setGender(Gender.FEMALE);
```

### Advanced Features

**Delayed Messages**
```java
// Send message after 5 seconds
player.sendMessage("<delay:5>This appears later!");
```

**Cancelled Messages**
```java
// Intercept and replace messages
if (message.contains("<cancel_message>")) {
    // Original message cancelled, send custom version
}
```

**Component Conversion**
```java
// Convert Adventure Component to MiniMessage
String miniMessage = Color.componentToMiniMessage(component);

// Process and send
Component processed = new Locale(miniMessage, "en")
    .setGendered(Gender.MALE)
    .getColoredComponent("dark");
```

---

## 💬 Chat Manager

Advanced chat system with multiple channels, filters, and customization options.

### Custom Chat Registration (API)

Register custom chats programmatically with filter logic:

```java
Chat.register("custom", (p, e) -> {
    // Filter logic
    return true;
});
```

### Configuration-Based Chats

Define chats in config using any shared placeholder:

```yaml
chat-manager:
  chats:
    global:
      enabled: true
      color: <#deceb4>
      tag: G
      symbol: '!'
      discord:
        channel: '1234567890'
        color: <#cbc7ff>
        tag: Discord
      
    local:
      enabled: true
      color: <#dedee0>
      tag: L
      radius: 500
      
    admin:
      enabled: true
      color: <#ffb4a1>
      tag: Admin
      symbol: \
      permission: synergy.chat.admin
      
    rp:
      symbol: '*'
      color: <#BF40BF>
      format: '<#BF40BF>*%DISPLAYNAME% %MESSAGE%*'
```

### Chat Features

**Multi-Channel System**
- Trigger chats with custom symbols (`!message`, `\admin chat`, `*roleplay`)
- Permission-based access control
- Radius-based local chat
- Per-channel custom formatting

**Content Filtering**
- Block inappropriate words with fuzzy matching
- Tolerance-based detection (handles intentional misspellings)
- Ignored words whitelist
- Configurable sensitivity

**Custom Emojis**
```yaml
custom-emojis:
  <3: ❤
  ':flip:': (╯°益°)╯( ┻━┻
  ':v:': ✔
  (c): ©
```

**Custom Color Tags**
```yaml
custom-color-tags:
  '&p': <#BDC581>
  '&s': <#F66463>
```

**Discord Integration**
- Bridge chats to Discord channels
- Separate formatting for Discord messages
- Merge similar embeds automatically
- Custom avatar links

**Interactive Messages**
- Play sounds on chat activity
- Interactive tag support (clickable elements)
- Warnings when nobody is in chat channel

**Format Customization**
```yaml
format: '<secondary>[%COLOR%%CHAT%<secondary>] %luckperms_prefix%%DISPLAYNAME%%COLOR%: %MESSAGE%'
```

Available placeholders: `%CHAT%`, `%COLOR%`, `%DISPLAYNAME%`, `%MESSAGE%`, plus any PlaceholderAPI placeholder

---

## 🎮 Stream Integrations

### Twitch Channel Points

Connect Twitch channel points to in-game commands:

```yaml
twitch:
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
        - execute at %streamer_name% run playsound minecraft:entity.creeper.primed
        - title @a[distance=..100] title {"text":"💥 OH NO!","color":"green"}
```

**Available placeholders:**
- `%streamer_name%` - Minecraft username of the streamer
- `%viewer_name%` - Twitch viewer who redeemed the reward

### Monobank Donations

Accept donations through Monobank (Ukrainian payment system) with in-game rewards:

```yaml
monobank:
  enabled: true
  rewards:
    flash_speed:
      cost: 15
      title: Flash Speed
      description: Super speed for 20 seconds
      commands:
        - effect give %target_name% minecraft:speed 20 5 true
        - title @a title {"text":"⚡ Lightning Speed!"}
```

**Available placeholders:**
- `%target_name%` - Player who receives the reward
- `%counter_name%` - Name of the person who donated

---

## 🤖 Discord Bot Features

### Advanced Discord Integration

**Role Synchronization**
```yaml
discord-roles-sync:
  enabled: true
  sync-roles-from-discord-to-mc: true
  sync-roles-form-mc-to-discord: false
  verified-role: '1234567890'
  roles:
    admin: '1234567890'
    helper: '1234567890'
  custom-command-add: lp user %PLAYER% parent add %GROUP%
  custom-command-remove: lp user %PLAYER% parent remove %GROUP%
```

Automatically sync Discord roles with in-game permissions using LuckPerms.

**Account Verification**
```yaml
discord:
  kick-player:
    if-has-no-link:
      enabled: true
      message: '<danger>You must link your Discord to join!'
    if-banned:
      enabled: true
      message: '<danger>Banned from Discord = banned from server'
```

**Welcome Messages**
```yaml
discord:
  welcome-message:
    enabled: true
    text: 'Welcome, %NAME%!'
    buttons:
      link:
        label: Link your account
        emoji: 🔗
        style: primary
        value: '<locale:you-have-to-link-account>'
```

**GPT-Powered Chat Bot**
```yaml
discord:
  gpt-bot:
    enabled: true
    name: Stepan
    personality: 'Act as a cat. Answer this question in a cat style: %MESSAGE%'
```

Add personality to your Discord bot using OpenAI's GPT.

**Highlights System**
```yaml
discord:
  hightlights:
    enabled: true
    channels:
      - '1234567890'
    reaction-emoji: ♥
    comments: true
```

Automatically react to messages with attachments and create comment threads.

---

## 🔧 Additional Features

### Votifier Integration

Reward players for voting on monitoring sites:

```yaml
votifier:
  enabled: true
  monitorings:
    - https://www.planetminecraft.com/server/your-server/vote/
    - https://minecraft-statistic.net/en/server/your-server.html
  rewards:
    - eco give %PLAYER% 100
    - give %PLAYER% diamond 5
```

### DeepL Translation

Automatic translation powered by DeepL API:

```yaml
deepl:
  api-key: your-key
  pro-account: false
  max-retries: 3
  request-delay-ms: 200
```

### Placeholder Output Replacements

Clean up placeholder outputs automatically:

```yaml
placeholder-output-replacements:
  '%guild_name%':
    '%guild_name%': '[%guild_name%] '
    '%none%': ''
  '%faction_name%':
    '%faction_name%': '[%faction_name%] '
    '%none%': ''
```

Remove unwanted placeholder values like `%none%` from your messages.

### Custom Resource Pack Hosting

Host your own resource pack through Synergy's web server:

```yaml
web-server:
  enabled: true
  port: 25593
  custom-texturepack: true
  custom-texturepack-url: http://your-server:25593/pack.zip
```

---

## 📚 Documentation

- 📋 **[Permissions](https://github.com/mrbear21/Synergy/wiki/Permissions)** - Complete permissions reference
- 📖 **[Wiki](https://github.com/mrbear21/Synergy/wiki)** - Detailed documentation and guides
- 🐛 **[Issues](https://github.com/mrbear21/Synergy/issues)** - Report bugs or request features

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🐛 Issues & Support

Found a bug or have a feature request? Please [create an issue](https://github.com/mrbear21/Synergy/issues) on GitHub.

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[⭐ Star this repository](https://github.com/mrbear21/Synergy) if you find it useful!

</div>
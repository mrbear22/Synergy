# 🔗 Synergy
*Velocity • BungeeCord • Spigot*

[![Version](https://img.shields.io/badge/version-1.4.4-blue.svg)](https://github.com/mrbear22/Synergy/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-1.8+-orange.svg)](https://minecraft.net)
[![Java](https://img.shields.io/badge/java-8+-red.svg)](https://java.com)

> **Create synergy between your Minecraft servers and unite them into a solid, seamless project**

A comprehensive messaging and tooling plugin designed for modern Minecraft server networks. Synergy bridges the gap between proxy servers and individual game servers, providing seamless communication, data synchronization, and localization features.

---

## ✨ Features

🌐 **Cross-Server Messaging** - Synchronize events and data across your entire network  
🗺️ **Multi-Language Support** - Built-in localization system with placeholder support  
💾 **Player Data Storage** - Convenient API for persistent player data management  
🔧 **Proxy & Standalone** - Works with Velocity, BungeeCord, and standalone Spigot servers  
⚡ **Lightweight & Fast** - Optimized performance with minimal resource usage

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
        <version>1.4.6</version>
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
    implementation 'archi.quest:synergy:1.4.4'
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
    Synergy.createSynergyEvent("broadcast-message")
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

## 🌍 Localization System

### Setting Up Translations

Create translations in Synergy's `locales.yml`:

```yaml
login-command-usage:
    en: "&cUsage: /login <password>"
    uk: "&cВикористання: /login <пароль>"
    
login-wrong-password:
    en: "&cWrong password!"
    uk: "&cНевірний пароль!"
    
localized-unknown-command-message:
    en: "Unknown command. Type '/help' for help."
    uk: "Невідома команда. Введіть '/help' для допомоги"
```

### Integrating with Third-Party Plugins

Replace static messages with Synergy translation keys:

**AuthMe (`messages_en.yml`)**:
```yaml
login:
  command_usage: '<lang>login-command-usage</lang>'
  wrong_password: '<lang>login-wrong-password</lang>'
```

**Spigot (`spigot.yml`)**:
```yaml
messages:
  unknown-command: '<lang>localized-unknown-command-message</lang>'
```

### Available Placeholders

```
%translation_<translation_key>%  - Insert translated text
%breadmaker_<option_key>%        - Insert player data
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

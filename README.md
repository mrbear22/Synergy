# Synergy [Velocity-Bungee-Spigot]
Basic tools and server messaging plugin for minecraft servers. The plugin can be used both in proxy and standalone servers.

> The purpose of the plugin is to create synergy between servers and unite them into a solid and seamless project

# Installation

## Maven

Add repository and dependency to your `pom.xml`:

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
        <version>1.4.4</version>
    </dependency>
</dependencies>
```

## Gradle

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

## Authentication

Create a [Personal Access Token](https://github.com/settings/tokens) with `read:packages` permission and configure:

**Maven**: Add to `~/.m2/settings.xml`:
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

**Gradle**: Set environment variables `USERNAME` and `TOKEN` or add to `gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

# Permissions
https://github.com/mrbear21/Synergy/wiki/Permissions

# Convenient data synchronization between servers
```
//The event will be sent to the proxy server
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
	Synergy.createSynergyEvent("broadcast-message").setOption("message", String.join(" ", args)).send();      
	return true;
}


//The proxy server will trigger the event on all servers in the network synchronously
@EventHandler
public void onSynergyEvent(SynergyEvent e) {
	if (!e.getIdentifier().equals("broadcast-message")) {
		return;
	}
	Bukkit.broadcastMessage(e.getOption("message")));
}
```

# Convenient localization system (including third-party plugins and system messages)

## Make your own translations in Synergy's locales.yml
```
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
## And replace texts in third-party plugins' messages files with Synergy translation keys
Authme's messages_en.yml
```
login:
  command_usage: '<lang>login-command-usage</lang>'
  wrong_password: '<lang>login-wrong-password</lang>'
...
```
Spigot.yml
```
messages:
  unknown-command: '<lang>localized-unknown-command-message</lang>'
```

## Placeholders
```
%translation_<translation_key>%
%breadmaker_<option_key>%
```

# Convenient storage of player data

```
BreadMaker bread = Synergy.getBread(uuid);
//Get player data
bread.getData("level").getAsInt()
bread.getData("language").getAsString()

//Save data permanently
bread.setData("key", "value");
```
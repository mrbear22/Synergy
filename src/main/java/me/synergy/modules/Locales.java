package me.synergy.modules;

public class Locales {

	public void initialize() {

		addDefault("no-permission", "en", "<danger>You don't have permission to use this.");

		addDefault("command-usage", "en", "<danger>Command usage:");

		addDefault("player-join-message", "en", "<secondary>[<success>+<secondary>] <primary>%player% <secondary>joined the server");

		addDefault("player-quit-message", "en", "<secondary>[<danger>-<secondary>] <primary>%player% <secondary>left the server");

		addDefault("player-first-time-join-message", "en", "<primary>%player% <secondary>joined for the first time!");

		addDefault("reloaded", "en", "<success>Configuration and translations reloaded!");

		addDefault("confirm-action", "en", "Confirm");

		addDefault("confirmation-nothing-to-confirm", "en", "<danger>Nothing to confirm");

		addDefault("confirmation-success", "en", "<success>Successfully confirmed!");

		addDefault("confirmation-canceled", "en", "<danger>Confirmation canceled.");

		addDefault("your-gender", "en", "<primary>Your gender: <secondary>%gender%");

		addDefault("selected-language", "en", "<success>Language selected: <primary>%language%<sound:'entity.ocelot.death'>");

		addDefault("selected-chat", "en", "<success>Chat selected: <primary>%ARGUMENT%<sound:'entity.ocelot.death'>");

		addDefault("selected-theme", "en", "<success>Theme selected: <primary>%theme%<sound:'entity.ocelot.death'>");

		addDefault("help-message", "en", new String[] {
				"Your text",
			});
		
		// Themes list
		addDefault("themes", "en", new String[] {
			"   <danger><bold>Choose a theme:</bold>",
			"",
			"<primary>▶<click:run_command:/theme default><hover:show_text:Click to select><#dd8ea3>Default</click>",
			"<secondary>"
		});

		// Languages
		addDefault("languages", "en", new String[] {
			"<danger>Choose your language",
			"",
			"<primary>▶ <click:run_command:/language auto><hover:show_text:Use your game language><danger>[AUTO]</click><primary>",
			"<primary>▶ <click:run_command:/language en><hover:show_text:Click to select><danger>[ENGLISH]</click><primary>",
			"<primary>▶ <click:run_command:/language uk><hover:show_text:Натисни, щоб вибрати><danger>[УКРАЇНСЬКА]</click><primary>",
			"",
			"<lang>language-auto</lang>",
			"<secondary>"
		});

		addDefault("language-auto", "en", "<secondary>Server automatically detects your game language");

		addDefault("player-doesnt-exist", "en", "<danger>Player doesn't exist!");

		addDefault("message-cant-be-empty", "en", "<danger>Message can't be empty!");

		addDefault("noone-hears-you", "en", "<danger>No one can hear you. Use ! for global chat.");

		addDefault("click-to-open", "en", "Click to open %ARGUMENT%");

		addDefault("cooldown", "en", "<danger>Please wait a few seconds!");

		addDefault("message-recipients", "en", "Players who saw your message: %RECIPIENTS%");

		// Voting
		addDefault("votifier-message", "en", "<success>Vote from %service% counted. Thank you!<sound:'entity.player.levelup'>");
		
		addDefault("votifier-announcement", "en", "<success>%player% successfully voted on %service%!");

		addDefault("vote-for-server", "en", "Vote for the server");

		addDefault("vote-monitorings", "en", "<secondary>Earn by voting for the server:");

		addDefault("player-voted", "en", "<primary>%ARGUMENT% <secondary>voted for the server!");

		// Twitch ConnectionManager translations
		addDefault("twitch-invalid-credentials", "en", "<danger>Invalid Twitch credentials!");

		addDefault("twitch-connection-failed", "en", "<danger>Failed to connect to Twitch!");

		addDefault("twitch-failed-get-user-id", "en", "<danger>Failed to get user ID for EventSub: ");

		addDefault("twitch-eventsub-subscription-created", "en", "<success>EventSub subscription created for rewards on channel: ");

		addDefault("twitch-failed-create-eventsub", "en", "<danger>Failed to create EventSub subscription for rewards: ");

		// Translation keys for MonobankCommand
		addDefault("synergy_no_permission", "en", "<danger>No permission!");
		
		addDefault("monobank_link_usage", "en", "<danger>Usage: /monobank link <token>");
		addDefault("monobank_successfully_linked", "en", "<success>Monobank account linked!");
		addDefault("monobank_already_linked", "en", "<danger>Monobank account already linked!");
		addDefault("monobank_successfully_unlinked", "en", "<success>Monobank account unlinked!");
		addDefault("monobank_not_linked", "en", "<danger>No linked Monobank account!");
		
		// Translation keys for plugin.yml commands
		addDefault("command_description_synergy", "en", "Main Synergy plugin command");
		addDefault("command_usage_synergy", "en", new String[] {
		    "<danger>Usage: /synergy <argument>",
		    "",
		    "<secondary>Available arguments:",
		    "<primary>  reload <secondary>- Reload configuration and modules",
		    "<primary>  info   <secondary>- Display plugin information", 
		    "<primary>  help   <secondary>- Show this help message"
		});

		addDefault("command_description_vote", "en", "Vote for the server");
		addDefault("command_usage_vote", "en", new String[] {
		    "<danger>Usage: /vote",
		    "",
		    "<secondary>Vote for our server to receive:",
		    "<primary>• <secondary>Experience points and currency",
		    "<primary>• <secondary>Special voting rewards",
		    "<primary>• <secondary>Help the server grow"
		});

		addDefault("command_description_chat", "en", "Chat management");
		addDefault("command_usage_chat", "en", new String[] {
		    "<danger>Usage: /chat <chat_name> [player]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  chat_name <secondary>- Chat channel to join",
		    "<primary>  player    <secondary>- (Optional) Target player",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /chat global <secondary>- Join global chat",
		    "<primary>  /chat local  <secondary>- Switch to local chat",
		    "<primary>  /chat staff  <secondary>- Access staff chat"
		});

		addDefault("command_description_theme", "en", "Change your theme");
		addDefault("command_usage_theme", "en", new String[] {
		    "<danger>Usage: /theme [theme_name]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  theme_name <secondary>- (Optional) Theme to apply",
		    "",
		    "<secondary>Use '/theme' to see all themes"
		});

		addDefault("command_description_colors", "en", "View available colors");
		addDefault("command_usage_colors", "en", new String[] {
		    "<danger>Usage: /colors",
		    "",
		    "<secondary>This command displays:",
		    "<primary>• <secondary>Available custom color codes",
		    "<primary>• <secondary>Color previews with examples",
		    "<primary>• <secondary>How to use colors in chat",
		    "<primary>• <secondary>Your color permissions"
		});

		addDefault("command_description_emojis", "en", "View available emojis");
		addDefault("command_usage_emojis", "en", new String[] {
		    "<danger>Usage: /emojis",
		    "",
		    "<secondary>This command shows:",
		    "<primary>• <secondary>Complete emoji list",
		    "<primary>• <secondary>Emoji codes and previews",
		    "<primary>• <secondary>How to use emojis in chat",
		    "<primary>• <secondary>Your emoji permissions"
		});

		addDefault("command_description_chatfilter", "en", "Chat filter management");
		addDefault("command_usage_chatfilter", "en", new String[] {
		    "<danger>Usage: /chatfilter <action> <word>",
		    "",
		    "<secondary>Actions:",
		    "<primary>  block  <secondary>- Add word to blocked list",
		    "<primary>  remove <secondary>- Remove word from filter",
		    "<primary>  ignore <secondary>- Add to personal ignore list",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /chatfilter block spam",
		    "<primary>  /chatfilter remove test",
		    "<primary>  /chatfilter ignore annoy"
		});

		addDefault("command_description_discord", "en", "Discord integration");
		addDefault("command_usage_discord", "en", new String[] {
		    "<danger>Usage: /discord <action> [discord_account]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link    <secondary>- Link Minecraft to Discord",
		    "<primary>  unlink  <secondary>- Unlink Discord account", 
		    "<primary>  confirm <secondary>- Confirm linking process",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  discord_account <secondary>- Your Discord username or ID",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /discord link YourUsername",
		    "<primary>  /discord confirm",
		    "<primary>  /discord unlink",
		    "",
		    "<secondary>Join our Discord: <click:open_url:'%INVITE%'><hover:show_text:Click to open><secondary><u>%INVITE%</u></click>"
		});

		addDefault("command_description_twitch", "en", "Twitch integration");
		addDefault("command_usage_twitch", "en", new String[] {
		    "<danger>Usage: /twitch <action> [arguments]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link <channel> <token> <secondary>- Link your Twitch account",
		    "<primary>  unlink                 <secondary>- Unlink your Twitch account", 
		    "<primary>  createreward <reward>  <secondary>- Create channel point reward",
		    "<primary>  removereward <reward>  <secondary>- Remove channel point reward",
		    "<primary>  testreward <reward> <input> <secondary>- Test reward redemption",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  channel <secondary>- Your Twitch channel name",
		    "<primary>  token   <secondary>- Your Twitch access token",
		    "<primary>  reward  <secondary>- Reward name from config",
		    "<primary>  input   <secondary>- Test input for reward",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /twitch link streamer123 your_token",
		    "<primary>  /twitch createreward subscribe",
		    "<primary>  /twitch testreward follow test_input",
		    "",
		    "<secondary>Obtain the access token here: <click:open_url:'https://twitchtokengenerator.com/quick/f7tCmJvs3S'><hover:show_text:Click to open><secondary><u>https://twitchtokengenerator.com/quick/f7tCmJvs3S</u></click>",
		    "<danger>Keep your API token secure!"
		});

		addDefault("command_description_monobank", "en", "Monobank integration");
		addDefault("command_usage_monobank", "en", new String[] {
		    "<danger>Usage: /monobank <action> [token]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link   <secondary>- Link account to Monobank",
		    "<primary>  unlink <secondary>- Remove Monobank integration",
		    "",
		    "<secondary>Arguments:", 
		    "<primary>  token <secondary>- Your Monobank API token",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /monobank link your_token_here",
		    "<primary>  /monobank unlink",
		    "",
		    "<danger>Keep your API token secure!"
		});

		addDefault("command_description_language", "en", "Change your language");
		addDefault("command_usage_language", "en", new String[] {
		    "<danger>Usage: /language [language_code]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  language_code <secondary>- (Optional) Language code",
		    "",
		    "<secondary>Available languages:",
		    "<primary>  en <secondary>- English",
		    "<primary>  ua <secondary>- Ukrainian",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /language ua <secondary>- Switch to Ukrainian",
		    "<primary>  /language    <secondary>- Open language selector"
		});

		addDefault("command_description_pronoun", "en", "Set your pronouns");
		addDefault("command_usage_pronoun", "en", new String[] {
		    "<danger>Usage: /pronoun [pronoun_type]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  pronoun_type <secondary>- (Optional) Preferred pronoun set",
		    "",
		    "<secondary>Available pronouns:",
		    "<primary>  he   <secondary>- He/Him pronouns",
		    "<primary>  she  <secondary>- She/Her pronouns", 
		    "<primary>  they <secondary>- They/Them pronouns",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /pronoun she <secondary>- Set She/Her pronouns"
		});

		// Additional translations for command validation
		addDefault("command-not-player", "en", "<danger>Players only!");
		addDefault("invalid-pronoun", "en", "<danger>Invalid pronoun! Use available options.");
		addDefault("unknown-command", "en", "<danger>Unknown command!");
		
		// Twitch

		addDefault("you-have-no-linked-twitch-accounts", "en", "<danger>No linked Twitch accounts! Link: <secondary><u>/twitch link <channel> <token></u>");
		addDefault("link-twitch-already-linked", "en", "<primary>Account already linked to <secondary>%ARGUMENT%<primary>! Unlink: <secondary><u>/twitch unlink</u>");
		
		// Vote command translations
		addDefault("vote-monitorings-format", "en", "<primary>▶ <click:open_url:%URL%><hover:show_text:Click to vote><secondary><u>%MONITORING%</u></click>");
		addDefault("monitorings-menu", "en", new String[] {
			"<secondary>Vote for our server on these sites:",
			"",
			"%MONITORINGS%",
			"<primary>Thank you for supporting our server!"
		});
		
		// Discord

		addDefault("discord-confirm-link", "en", "Confirm linking to your `%ACCOUNT%` game account. Ignore if not yours.");

		addDefault("discord-link-check-pm", "en", new String[] {
			"<success>Check your private messages and confirm linking.",
			"<secondary>Or go to Discord: <primary><u>%INVITE%</u> <secondary>and use <secondary><u>/link</u>"
		});

		addDefault("discord-use-link-cmd", "en", "<primary>Go to Discord: <primary><u>%INVITE%</u> <primary>and use <secondary><u>/link</u>");

		addDefault("hightlights-comments", "en", "Comments");
		
		addDefault("check-vault-balance", "en", "Check your balance");

		addDefault("vault-balance-title", "en", "**Your balance**");

		addDefault("vault-balance-field", "en", "`%AMOUNT%` votes");

		addDefault("you-have-to-link-account", "en", "Please link your account: /link");

		addDefault("link-discord-already-linked", "en", "<primary>Account already linked to <secondary>%ACCOUNT%<primary>! Unlink it using <secondary><u>/discord unlink</u>");

		addDefault("link-minecraft-already-linked", "en", "<primary>Account already linked to <secondary>%ACCOUNT%<primary>! Unlink it using <secondary><u>/unlink</u>");

		addDefault("you-have-no-linked-accounts", "en", "<danger>No linked accounts! Link: <secondary><u>/discord link {usertag}</u>");

		addDefault("link-minecraft-unlinked", "en", "<success>Account unlinked. Relink: <secondary><u>/link {usertag}</u>");
		
		addDefault("link-discord-unlinked", "en", "<success>Account unlinked. Relink: <secondary><u>/discord link {usertag}</u>");

		addDefault("link-minecraft-title", "en", "Link Minecraft account");
		
		addDefault("unlink-minecraft-title", "en", "Unlink Minecraft account");

		addDefault("link-minecraft-your-username", "en", "Your Minecraft username");

		addDefault("link-discord-confirmation", "en", "<primary>Confirm linking to Discord: <secondary>%ACCOUNT% <primary>using <click:run_command:/discord confirm><hover:show_text:Confirm><secondary><u>/discord confirm</u></click>");

		addDefault("link-minecraft-confirmation", "en", "Confirm your account in-game");

		addDefault("discord-link-success", "en", "<success>Account <secondary>%ACCOUNT%<success> successfully linked!");

		addDefault("online-players-list", "en", "Online players");

		addDefault("create-post", "en", "Create new post");

		addDefault("service-unavailable", "en", "Service unavailable. Try again later");

		addDefault("discord-embed-new", "en", "Create new post");

		addDefault("discord-embed-edit", "en", "Edit existing post");
		
	}
	
    public void addDefault(String key, String lang, String text) {
        new LocalesManager().addDefault(key, lang, text);
    }
    
    public void addDefault(String key, String lang, String[] text) {
        new LocalesManager().addDefault(key, lang, text);
    }
}
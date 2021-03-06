package com.redstoner.modules.mentio;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.nemez.cmdmgr.Command;
import com.redstoner.annotations.AutoRegisterListener;
import com.redstoner.annotations.Commands;
import com.redstoner.annotations.Version;
import com.redstoner.coremods.moduleLoader.ModuleLoader;
import com.redstoner.misc.CommandHolderType;
import com.redstoner.misc.JsonManager;
import com.redstoner.misc.Main;
import com.redstoner.modules.Module;
import com.redstoner.modules.ignore.Ignore;

import net.nemez.chatapi.click.Message;

@Commands(CommandHolderType.File)
@AutoRegisterListener
@Version(major = 5, minor = 0, revision = 0, compatible = 4)
public class Mentio implements Module, Listener
{
	private File mentioLocation = new File(Main.plugin.getDataFolder(), "mentio.json");
	private JSONObject mentios;
	
	@Override
	public boolean onEnable()
	{
		loadMentios();
		return true;
	}
	
	@Override
	public void onDisable()
	{
		saveMentios();
	}
	
	@SuppressWarnings("unchecked")
	@Command(hook = "addmentio")
	public boolean addMentio(CommandSender sender, String trigger)
	{
		Player player = (Player) sender;
		UUID uuid = player.getUniqueId();
		JSONArray playerMentios = (JSONArray) mentios.get(uuid.toString());
		playerMentios = defaultMentio(playerMentios, player);
		if (playerMentios.contains(trigger))
			getLogger().message(sender, true, "You already had that as a mentio!");
		else
		{
			playerMentios.add(trigger);
			getLogger().message(sender, "Successfully added the trigger §e" + trigger + " §7for you!");
			mentios.put(uuid.toString(), playerMentios);
			saveMentios();
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	@Command(hook = "delmentio")
	public boolean delMentio(CommandSender sender, String trigger)
	{
		Player player = (Player) sender;
		UUID uuid = player.getUniqueId();
		JSONArray playerMentios = (JSONArray) mentios.get(uuid.toString());
		playerMentios = defaultMentio(playerMentios, player);
		if (!playerMentios.remove(trigger))
			getLogger().message(sender, true, "You didn't have that as a mentio!");
		else
		{
			getLogger().message(sender, "Successfully removed the trigger §e" + trigger + " §7for you!");
			mentios.put(uuid.toString(), playerMentios);
			saveMentios();
		}
		return true;
	}
	
	@Command(hook = "listmentios")
	public boolean listMentios(CommandSender sender)
	{
		ArrayList<String> message = new ArrayList<>();
		Player player = (Player) sender;
		UUID uuid = player.getUniqueId();
		JSONArray playerMentios = (JSONArray) mentios.get(uuid.toString());
		playerMentios = defaultMentio(playerMentios, player);
		for (Object raw : playerMentios)
		{
			String mentio = (String) raw;
			message.add("&2 -> &e" + mentio);
		}
		getLogger().message(sender, message.toArray(new String[] {}));
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private JSONArray defaultMentio(JSONArray mentios, Player player) {
		if (mentios == null)
		{
			mentios = new JSONArray();
			mentios.add(player.getName());
			
			String displayName = player.getDisplayName().split(" ")[0].replaceAll("§[0-9a-fk-or]", "");
			if (!player.getName().equals(displayName))
				mentios.add(displayName);
		}
		return mentios;
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncPlayerChatEvent event)
	{
		if (event.isCancelled())
			return;
		for (Player player : event.getRecipients())
		{
			if (ModuleLoader.exists("Ignore") ? !Ignore.getIgnoredBy(event.getPlayer()).sendTo(player) : false)
				return;
			UUID uuid = player.getUniqueId();
			JSONArray playerMentios = (JSONArray) mentios.get(uuid.toString());
			playerMentios = defaultMentio(playerMentios, player);
			for (Object raw : playerMentios)
			{
				String mentio = (String) raw;
				if (event.getMessage().toLowerCase().contains(mentio.toLowerCase()))
				{
					event.getRecipients().remove(player);
					String temp = event.getMessage().replaceAll("(?i)" + Pattern.quote(mentio) + ".*", "");
					String lastColorCodes = "§r";
					char lastChar = ' ';
					for (char c : temp.toCharArray())
					{
						if (lastChar == '§')
							lastColorCodes += "§" + c;
						lastChar = c;
					}
					Message m = new Message(player, event.getPlayer());
					m.appendText(event.getFormat().replace("%1$s", event.getPlayer().getDisplayName()).replace("%2$s",
							event.getMessage().replaceFirst("(?i)(" + Pattern.quote(mentio) + ")([^ ]*)",
									"§a§o$1$2" + lastColorCodes)));
					m.send();
					player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1, 1);
					return;
				}
			}
		}
	}
	
	private void loadMentios()
	{
		mentios = JsonManager.getObject(mentioLocation);
		if (mentios == null)
			mentios = new JSONObject();
	}
	
	private void saveMentios()
	{
		JsonManager.save(mentios, mentioLocation);
	}
}

package com.redstoner.modules.damnspam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.nemez.cmdmgr.Command;
import com.redstoner.annotations.AutoRegisterListener;
import com.redstoner.annotations.Commands;
import com.redstoner.annotations.Version;
import com.redstoner.misc.CommandHolderType;
import com.redstoner.misc.Main;
import com.redstoner.modules.Module;

@Commands(CommandHolderType.File)
@AutoRegisterListener
@Version(major = 5, minor = 0, revision = 0, compatible = 4)
public class DamnSpam implements Module, Listener {
	File configFile = new File(Main.plugin.getDataFolder(), "DamnSpam.json");

	Map<String, SpamInput> inputs;
	List<Material> acceptedInputs;

	HashMap<Material, int[][]> attachedBlocks;
	HashMap<Player, SpamInput> players;

	boolean changingInput = false;
	int maxTimeout = 240;

	String timeoutErrorString = "The timeout must be -1 or within 0 and " + maxTimeout;

	private static final BlockFace[] DIRECTIONS = { BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST };

	private static final int[][] LEVER_ATTACHED_BLOCKS = new int[][] { { 0, 7, 8, 15 }, { 5, 6, 13, 14 }, { 4, 12 }, { 3, 11 }, { 2, 10 }, { 1, 9 } };
	private static final int[][] BUTTON_ATTACHED_BLOCKS = new int[][] { { 0, 8 }, { 5, 6, 7, 13, 14, 15 }, { 4, 12 }, { 3, 11 }, { 2, 10 }, { 1, 9 } };

	// @formatter:off
	private static final Material[] BUTTONS = {
			Material.ACACIA_BUTTON,
			Material.BIRCH_BUTTON,
			Material.DARK_OAK_BUTTON,
			Material.JUNGLE_BUTTON,
			Material.OAK_BUTTON,
			Material.SPRUCE_BUTTON,
			Material.STONE_BUTTON
	};
	// @formatter:on

	@Override
	public boolean onEnable() {
		loadInputs();

		acceptedInputs = new ArrayList<Material>();
		acceptedInputs.add(Material.LEVER);

		Collections.addAll(acceptedInputs, BUTTONS);

		attachedBlocks = new HashMap<Material, int[][]>();
		attachedBlocks.put(Material.LEVER, LEVER_ATTACHED_BLOCKS);

		for (Material button : BUTTONS) {
			attachedBlocks.put(button, BUTTON_ATTACHED_BLOCKS);
		}

		players = new HashMap<Player, SpamInput>();
		return true;
	}

	public void loadInputs() {
		inputs = new HashMap<String, SpamInput>();

		try {
			FileReader reader = new FileReader(configFile);
			JSONObject json = (JSONObject) new JSONParser().parse(reader);

			for (Object key : json.keySet()) {
				JSONObject inputData = (JSONObject) json.get(key);
				String uuid = (String) inputData.get("creator");
				Double timeoutOn = (Double) inputData.get("timeout_on");
				Double timeoutOff = (Double) inputData.get("timeout_off");
				Double lastTime = (Double) inputData.get("last_time");

				inputs.put((String) key, new SpamInput(uuid, timeoutOff, timeoutOn, lastTime));
			}
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void saveInputs() {
		JSONObject json = new JSONObject();

		for (String key : inputs.keySet()) {
			JSONObject jsonInput = new JSONObject();
			SpamInput input = inputs.get(key);

			jsonInput.put("creator", input.player);
			jsonInput.put("timeout_on", input.timeoutOn);
			jsonInput.put("timeout_off", input.timeoutOff);
			jsonInput.put("last_time", input.lastTime);

			json.put(key, jsonInput);
		}
		try {
			PrintWriter writer = new PrintWriter(configFile);
			writer.write(json.toJSONString());
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public String locationString(Location loc) {
		return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
	}

	public boolean isAcceptableTimeout(double timeout) {
		return (timeout > 0 && timeout <= maxTimeout) || timeout == -1;
	}

	public boolean canBuild(Player player, Block block) {
		BlockBreakEvent event = new BlockBreakEvent(block, player);
		Bukkit.getPluginManager().callEvent(event);

		return !event.isCancelled();
	}

	@Command(hook = "damnspamSingle")
	public void damnspam(CommandSender sender, double seconds) {
		boolean destroyingInput = false;

		seconds = (double) Math.round(seconds * 100) / 100;

		if (seconds == 0) destroyingInput = true;
		else if (!isAcceptableTimeout(seconds)) {
			getLogger().message(sender, true, "The timeout must be -1 or within 0 and " + maxTimeout);
			return;
		}

		getLogger().message(sender, "Please click the input you would like to set.");
		setPlayer((Player) sender, destroyingInput, seconds, seconds);
	}

	@Command(hook = "damnspamDouble")
	public void damnspam(CommandSender sender, double secondsOff, double secondsOn) {
		boolean destroyingInput = false;

		secondsOn = (double) Math.round(secondsOn * 100) / 100;
		secondsOff = (double) Math.round(secondsOff * 100) / 100;

		if (secondsOn == 0 && secondsOff == 0) {
			destroyingInput = true;
		} else if (!(isAcceptableTimeout(secondsOn) && isAcceptableTimeout(secondsOff))) {
			getLogger().message(sender, true, "The timeout must be -1 or within 0 and " + maxTimeout);
			return;
		}

		getLogger().message(sender, "Please click the input you would like to set.");
		setPlayer((Player) sender, destroyingInput, secondsOff, secondsOn);
	}

	public void setPlayer(Player player, boolean destroying, double timeoutOff, double timeoutOn) {
		SpamInput input = null;

		if (!destroying) {
			input = new SpamInput(player.getUniqueId().toString(), timeoutOff, timeoutOn, 0);
		}

		players.put(player, input);
	}

	public boolean attemptInputRegister(Player player, Block block, Cancellable event) {
		if (players.containsKey(player)) {
			if (!acceptedInputs.contains(block.getType())) {
				getLogger().message(player, true, "That block is not an acceptable input!");
				return true;
			}

			String typeStr = block.getType().toString().toLowerCase().replace("_", " ");
			String locationStr = locationString(block.getLocation());

			changingInput = true;
			boolean buildCheck = canBuild(player, block);
			changingInput = false;

			if (!buildCheck) {
				getLogger().message(player, true, "Something went wrong trying to change the timeout on this " + typeStr + "!");
				event.setCancelled(true);
				return true;
			}

			SpamInput input = players.get(player);

			if (input == null) {
				if (!inputs.containsKey(locationStr)) {
					getLogger().message(player, true, "Something went wrong trying to change the timeout on this " + typeStr + "!");
					event.setCancelled(true);
					return true;
				}

				inputs.remove(locationStr);
				getLogger().message(player, "Successfully removed the timeout for this " + typeStr);
			} else {
				inputs.put(locationStr, players.get(player));
				getLogger().message(player, "Successfully set a timeout for this " + typeStr);
			}

			event.setCancelled(true);
			players.remove(player);
			saveInputs();

			return true;
		}
		return false;
	}

	public void checkBlockBreak(BlockBreakEvent event, Block block) {
		if (!acceptedInputs.contains(block.getType())) return;
		String posStr = locationString(block.getLocation());
		if (!inputs.containsKey(posStr)) return;

		SpamInput input = inputs.get(posStr);
		Player sender = event.getPlayer();

		String typeStr = block.getType().toString().toLowerCase().replace("_", " ");
		String inputStr = (block.getLocation().equals(event.getBlock()) ? "this " + typeStr : "the " + typeStr + " attached to that block");

		if (!sender.isSneaking()) {
			getLogger().message(sender, true, "You cannot destroy " + inputStr);
			getLogger().message(sender, true, "Sneak and break or set the timeout to 0 if you want to remove it.");

			event.setCancelled(true);
			return;
		}

		if (sender.hasPermission("damnspam.admin") || sender.getUniqueId().toString().equals(input.player)) {
			inputs.remove(posStr);
			saveInputs();
			getLogger().message(sender, "Succesfully removed " + inputStr);
		} else {
			getLogger().message(sender, true, "You are not allowed to remove " + inputStr);
			event.setCancelled(true);
		}
	}

	@SuppressWarnings("deprecation")
	public List<Block> getAttachedBlocks(Block block) {
		List<Block> blocks = new ArrayList<Block>();

		for (int i = 0; i < DIRECTIONS.length; i++) {
			Block side = block.getRelative(DIRECTIONS[i]);
			int[][] dvalues = attachedBlocks.get(side.getType());

			if (dvalues != null) {
				boolean onSide = false;

				for (int val : dvalues[i]) {
					if (side.getData() == (byte) val) {
						onSide = true;
						break;
					}
				}

				if (onSide) blocks.add(side);
			}
		}

		return blocks;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onBreak(BlockBreakEvent event) {
		if (changingInput || event.isCancelled()) return;

		boolean register = attemptInputRegister(event.getPlayer(), event.getBlock(), event);

		if (!register) {
			Block block = event.getBlock();
			checkBlockBreak(event, block);

			for (Block affected : getAttachedBlocks(block)) {
				checkBlockBreak(event, affected);
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getClickedBlock() == null) return;
		boolean register = attemptInputRegister(event.getPlayer(), event.getClickedBlock(), event);

		if (!register && event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && !event.isCancelled()) {
			Player sender = event.getPlayer();
			Block block = event.getClickedBlock();
			String posStr = locationString(block.getLocation());
			SpamInput data = inputs.get(posStr);

			if (data != null) {
				String btype = block.getType().toString().toLowerCase().replace("_", " ");
				double checktime = 0;

				if (btype.equals("lever") && block.getData() < 8) checktime = data.timeoutOff;
				else checktime = data.timeoutOn;

				double timeLeft = (data.lastTime + checktime) - ((double) Math.round((double) System.currentTimeMillis() / 10) / 100);
				timeLeft = (double) Math.round(timeLeft * 100) / 100;

				if (checktime == -1) {
					event.setCancelled(true);
					getLogger().message(sender, "This " + btype + " is locked permanently by /damnspam.");
				} else if (timeLeft > 0) {
					event.setCancelled(true);
					getLogger().message(sender, "This " + btype + " has a damnspam timeout of " + checktime + ", with " + timeLeft + " left.");
				} else {
					data.lastTime = (double) Math.round((double) System.currentTimeMillis() / 10) / 100;
				}

				inputs.put(posStr, data);
			}
		}
	}
}

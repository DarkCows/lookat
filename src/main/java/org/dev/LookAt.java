package org.dev;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;

public class LookAt implements ClientModInitializer {
	private static final File FILE = new File("config/locations.json");
	private static final File parentDir = FILE.getParentFile();
	private static final Map<String, float[]> locations = new HashMap<>();
	private static final Gson gson = new Gson();

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->  locations());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("add")
					.then(argument("name", StringArgumentType.string())
					.then(argument("x", FloatArgumentType.floatArg())
					.then(argument("y", FloatArgumentType.floatArg())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						float x = FloatArgumentType.getFloat(context, "x");
						float y = FloatArgumentType.getFloat(context, "y");
						add(name, x, y);
						return 1;
					})))));
			dispatcher.register(literal("locations")
					.executes(context -> {
						locations();
						return 1;
					}));
			dispatcher.register(literal("look")
					.then(argument("x", FloatArgumentType.floatArg(-180, 180))
					.then(argument("y", FloatArgumentType.floatArg(-90, 90))
					.executes(context -> {
						float x = FloatArgumentType.getFloat(context, "x");
						float y = FloatArgumentType.getFloat(context, "y");
						rotatePlayer(x, y);
						return 1;
					}))));
			dispatcher.register(literal("look")
					.then(argument("name", StringArgumentType.string())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						if (locations.containsKey(name)) {
							float[] coords = locations.get(name);
							rotatePlayer(coords[0], coords[1]);
						} else {
							sendMessageToPlayer("message.lookat.not_found", name);
						}
						return 1;
					})));
			dispatcher.register(literal("remove")
					.then(argument("name", StringArgumentType.string())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						remove(name);
						return 1;
					})));
		});
	}

	private void rotatePlayer(float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.setYaw(x);
			client.player.setPitch(y);
		}
	}

	private void remove(String name) {
		if (locations.containsKey(name)) {
			locations.remove(name);
			if (saveLocations()) {
				sendMessageToPlayer("message.lookat.removed", name);
			}
		} else {
			sendMessageToPlayer("message.lookat.not_found", name);
		}
	}

	public void locations() {
		if (FILE.exists()) {
			try (FileReader reader = new FileReader(FILE)) {
				Type type = new TypeToken<Map<String, float[]>>() {}.getType();
				Map<String, float[]> loadedLocations = gson.fromJson(reader, type);

				if (loadedLocations != null) {
					locations.putAll(loadedLocations);
					StringBuilder message = new StringBuilder("Сохраненные точки:\n");
					for (Map.Entry<String, float[]> entry : locations.entrySet()) {
						message.append("Точка ").append(entry.getKey())
								.append(": X=").append(entry.getValue()[0])
								.append(", Y=").append(entry.getValue()[1]).append("\n");
					}
					sendMessageToPlayer(message.toString());
				} else {
					sendMessageToPlayer("message.lookat.empty");
				}
			} catch (IOException e) {
				sendMessageToPlayer("message.lookat.error_to_load");
			}
		} else {
			try {
				if (parentDir != null && !parentDir.exists()) {
					parentDir.mkdirs();
				}
				FILE.createNewFile();
				sendMessageToPlayer("message.lookat.empty");
			} catch (IOException e) {
				sendMessageToPlayer("message.lookat.error_to_create");
			}
		}
	}

	public void add(String name, float x, float y) {
		locations.put(name, new float[]{x, y});
		if (saveLocations()) {
			sendMessageToPlayer("message.lookat.added", name, x, y);
		}
	}

	private boolean saveLocations() {
		try (FileWriter writer = new FileWriter(FILE)) {
			gson.toJson(locations, writer);
			return true;
		} catch (IOException e) {
			sendMessageToPlayer("message.lookat.error");
			return false;
		}
	}

	private void sendMessageToPlayer(String message, Object... args) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable(message, args), false);
		} else {
			System.out.println("message.lookat.player_not_found");
		}
	}
}

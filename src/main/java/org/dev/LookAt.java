package org.dev;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
	private static final Map<String, int[]> locations = new HashMap<>();
	private static final Gson gson = new Gson();

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			locations();
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("add")
					.then(argument("name", StringArgumentType.string())
					.then(argument("x", IntegerArgumentType.integer())
					.then(argument("y", IntegerArgumentType.integer())
					.executes(context -> {
							String name = StringArgumentType.getString(context, "name");
							int x = IntegerArgumentType.getInteger(context, "x");
							int y = IntegerArgumentType.getInteger(context, "y");
							add(name, x, y);
							return 1;
					})))));
			dispatcher.register(literal("locations")
					.executes(context -> {
						locations();
						return 1;
					}));
			dispatcher.register(literal("look")
					.then(argument("x", IntegerArgumentType.integer(-180, 180))
					.then(argument("y", IntegerArgumentType.integer(-90, 90))
					.executes(context -> {
							int x = IntegerArgumentType.getInteger(context, "x");
							int y = IntegerArgumentType.getInteger(context, "y");
							rotatePlayer(x, y);
							return 1;
					}))));
			dispatcher.register(literal("look")
					.then(argument("name", StringArgumentType.string())
					.executes(context -> {
							String name = StringArgumentType.getString(context, "name");
							if (locations.containsKey(name)) {
								int[] coords = locations.get(name);
								rotatePlayer(coords[0], coords[1]);
							} else {
								sendMessageToPlayer("§8Точка '" + name + "' не найдена");
							}
							return 1;
					})));
			dispatcher.register(literal("remove")
					.then(argument("name", StringArgumentType.string())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "name");
						removeLocation(name);
						return 1;
					})));
		});
	}

	private void rotatePlayer(float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.setYaw(x);
			client.player.setPitch(y);
		} else {
			System.out.println("Игрок не найден");
		}
	}

	private void removeLocation(String name) {
		if (locations.containsKey(name)) {
			locations.remove(name);
			try (FileWriter writer = new FileWriter(FILE)) {
				gson.toJson(locations, writer);
				sendMessageToPlayer("§cТочка '" + name + "' удалена.");
			} catch (IOException e) {
				sendMessageToPlayer("§4Ошибка при сохранении точек.");
			}
		} else {
			sendMessageToPlayer("§8Точка '" + name + "' не найдена.");
		}
	}

	public void locations() {
		if (FILE.exists()) {
			try (FileReader reader = new FileReader(FILE)) {
				Type type = new TypeToken<Map<String, int[]>>() {}.getType();
				Map<String, int[]> loadedLocations = gson.fromJson(reader, type);

				if (loadedLocations != null) {
					locations.putAll(loadedLocations);
					StringBuilder message = new StringBuilder("Сохраненные точки:\n");
					for (Map.Entry<String, int[]> entry : locations.entrySet()) {
						message.append("Точка ").append(entry.getKey())
								.append(": X=").append(entry.getValue()[0])
								.append(", Y=").append(entry.getValue()[1]).append("\n");
					}
					sendMessageToPlayer(message.toString());
				} else {
					sendMessageToPlayer("§7Буфер направлений пуст, создайте новую точку через /add или используйте /look.");
				}
			} catch (IOException e) {
				sendMessageToPlayer("§4Ошибка при загрузке точек. Попробуйте снова.");
			}
		} else {
			try {
				if (parentDir != null && !parentDir.exists()) {
					parentDir.mkdirs();
				}
				FILE.createNewFile();
				sendMessageToPlayer("§7Буфер направлений пуст, создайте новую точку через /add или используйте /look.");
			} catch (IOException e) {
				sendMessageToPlayer("§4Ошибка при создании файла. Попробуйте снова.");
			}
		}
	}

	public void add(String name, int x, int y) {
		locations.put(name, new int[]{x, y});
		if (parentDir != null && !parentDir.exists()) {
			parentDir.mkdirs();
		}
		try (FileWriter writer = new FileWriter(FILE)) {
			gson.toJson(locations, writer);
			sendMessageToPlayer("§aТочка " + name + " сохранена: " + x + ", " + y);
		} catch (IOException e) {
			sendMessageToPlayer("§4Ошибка при сохранении точек. Попробуйте снова.");
		}
	}

	private void sendMessageToPlayer(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.of(message), false);
		} else {
			System.out.println("Игрок не найден");
		}
	}
}

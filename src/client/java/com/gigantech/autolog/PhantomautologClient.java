package com.gigantech.autolog;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.text.Text;

public class PhantomautologClient implements ClientModInitializer {
	// Global variable to store autoleave state
	public static boolean autoleave = true;

	@Override
	public void onInitializeClient() {
		// Register the autoleave command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("autoleave")
				.then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
					.executes(this::setAutoleave))
				.executes(this::getAutoleave));
		});

		// Register client tick event to check for phantoms
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null && client.player != null) {
				if (autoleave) {
					checkForPhantomsAndDisconnect(client);
				}
			}
		});
	}

	private int setAutoleave(CommandContext<FabricClientCommandSource> context) {
		boolean enabled = BoolArgumentType.getBool(context, "enabled");
		autoleave = enabled;
		
		context.getSource().sendFeedback(Text.literal("Autoleave set to: " + enabled));
		Phantomautolog.LOGGER.info("Autoleave set to: {}", enabled);
		
		return 1;
	}

	private int getAutoleave(CommandContext<FabricClientCommandSource> context) {
		context.getSource().sendFeedback(Text.literal("Autoleave is currently: " + autoleave));
		return 1;
	}

	private void checkForPhantomsAndDisconnect(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;

		// Check for phantom entities in the world
		boolean phantomsFound = world.getEntitiesByClass(PhantomEntity.class, client.player.getBoundingBox().expand(60.0), entity -> true).size() > 0;
		
		if (phantomsFound) {
			Phantomautolog.LOGGER.info("Phantoms detected! Auto-disconnecting from server...");
			
			// Disconnect from server
			client.execute(() -> {
				if (client.getNetworkHandler() != null) {
					client.getNetworkHandler().getConnection().disconnect(Text.literal("i saw phantoms, too scary, me leave"));
				}
			});
		}
	}
}
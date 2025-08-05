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
	public static boolean autoleave = false;

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
				checkForPhantomsAndForceAutoleave(client);
				
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

	private void checkForPhantomsAndForceAutoleave(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;

		// Get time since last sleep (in ticks)
		int timeSinceLastSleep = client.player.getSleepTimer();
		
		// Check for phantom entities in the world
		boolean phantomsFound = world.getEntitiesByClass(PhantomEntity.class, client.player.getBoundingBox().expand(50.0), entity -> true).size() > 0;
		
		// Force autoleave to true if phantoms are present AND sleep timer > 72000 ticks
		if (phantomsFound && timeSinceLastSleep > 72000) {
			if (!autoleave) {
				autoleave = true;
				Phantomautolog.LOGGER.info("Phantoms detected with sleep timer at {} ticks! Forcing autoleave to true.", timeSinceLastSleep);
			}
		}
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
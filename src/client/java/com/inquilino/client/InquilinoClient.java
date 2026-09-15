package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import com.inquilino.InquilinoMod;
import com.inquilino.ModRegistry;
import com.inquilino.client.render.TenantModel;
import com.inquilino.client.render.TenantRenderer;
import com.inquilino.net.HorrorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.TitleScreen;

public class InquilinoClient implements ClientModInitializer {
	private static boolean warned;

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(TenantModel.LAYER, TenantModel::createBodyLayer);
		EntityRendererRegistry.register(ModRegistry.TENANT, TenantRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(HorrorPayload.TYPE, (payload, context) -> HorrorEffects.handle(context.client(), payload));
		HudElementRegistry.addLast(InquilinoMod.id("horror"), HorrorEffects::render);
		HudElementRegistry.addLast(InquilinoMod.id("vhs"), VhsCamera::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HorrorEffects.tick(client);
			CameraMotion.tick(client);
			if (!warned && !InquilinoConfig.warningSeen && client.gui.screen() instanceof TitleScreen title) {
				warned = true;
				client.gui.setScreen(new WarningScreen(title));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(HorrorEffects::reset));
	}
}

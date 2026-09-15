package com.inquilino;

import com.inquilino.haunt.HauntCommands;
import com.inquilino.haunt.HauntDirector;
import com.inquilino.net.HorrorPayload;
import com.inquilino.net.SignalPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InquilinoMod implements ModInitializer {
	public static final String MOD_ID = "inquilino";
	public static final Logger LOGGER = LoggerFactory.getLogger("O Inquilino");

	@Override
	public void onInitialize() {
		InquilinoConfig.load();
		ModRegistry.init();

		PayloadTypeRegistry.clientboundPlay().register(HorrorPayload.TYPE, HorrorPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SignalPayload.TYPE, SignalPayload.CODEC);

		HauntDirector.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HauntCommands.register(dispatcher));

		LOGGER.info("Este mundo já tinha um morador.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

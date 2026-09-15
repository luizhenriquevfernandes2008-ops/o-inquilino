package com.inquilino.net;

import com.inquilino.InquilinoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: avisa que algo aconteceu na tela do jogador (ex.: fechou a falsa desconexão). */
public record SignalPayload(String signal) implements CustomPacketPayload {
	public static final Type<SignalPayload> TYPE = new Type<>(InquilinoMod.id("signal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SignalPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SignalPayload::signal,
		SignalPayload::new
	);

	public static final String DISCONNECT_CLOSED = "disconnect_closed";
	public static final String ENDING_CLOSED = "ending_closed";

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

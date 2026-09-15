package com.inquilino.net;

import com.inquilino.InquilinoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: dispara um efeito de tela/meta no cliente.
 *
 * @param effect id do efeito (ver {@link Effects})
 * @param text   chave de tradução ou texto livre
 * @param arg    texto extra (argumento de formatação)
 * @param ticks  duração
 * @param x      alvo opcional
 */
public record HorrorPayload(String effect, String text, String arg, int ticks, double x, double y, double z) implements CustomPacketPayload {
	public static final Type<HorrorPayload> TYPE = new Type<>(InquilinoMod.id("horror"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HorrorPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, HorrorPayload::effect,
		ByteBufCodecs.STRING_UTF8, HorrorPayload::text,
		ByteBufCodecs.STRING_UTF8, HorrorPayload::arg,
		ByteBufCodecs.VAR_INT, HorrorPayload::ticks,
		ByteBufCodecs.DOUBLE, HorrorPayload::x,
		ByteBufCodecs.DOUBLE, HorrorPayload::y,
		ByteBufCodecs.DOUBLE, HorrorPayload::z,
		HorrorPayload::new
	);

	public static HorrorPayload of(String effect, String text, String arg, int ticks) {
		return new HorrorPayload(effect, text, arg, ticks, 0, 0, 0);
	}

	public static HorrorPayload of(String effect, int ticks) {
		return new HorrorPayload(effect, "", "", ticks, 0, 0, 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Ids dos efeitos. */
	public static final class Effects {
		public static final String STATIC = "static";
		public static final String FACE = "face";
		public static final String JUMPSCARE = "jumpscare";
		public static final String TEXT = "text";
		public static final String WINDOW_TITLE = "title";
		public static final String LOOK_AT = "look_at";
		public static final String FAKE_DISCONNECT = "disconnect";
		public static final String WRITE_FILE = "file";
		public static final String PHASE = "phase";
		public static final String SHAKE = "shake";
		public static final String BLACKOUT = "blackout";

		private Effects() {
		}
	}
}

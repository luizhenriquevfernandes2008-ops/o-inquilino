package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import com.inquilino.InquilinoMod;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Você está gravando tudo. Filtro de fita VHS no mundo + a interface de uma filmadora velha:
 * REC piscando, contador da fita, bateria, data e hora, cantos do visor.
 */
public final class VhsCamera {
	private static final Identifier CALM = InquilinoMod.id("vhs_calmo");
	private static final Identifier MEDIUM = InquilinoMod.id("vhs_medio");
	private static final Identifier HEAVY = InquilinoMod.id("vhs_forte");
	private static final RandomSource RANDOM = RandomSource.create();
	private static long recordingSince = -1;

	private VhsCamera() {
	}

	public static void reset() {
		recordingSince = -1;
	}

	/** Qual versão do filtro usar agora (quanto mais perto ele está, pior a imagem). */
	public static @Nullable Identifier currentChain() {
		Minecraft mc = Minecraft.getInstance();
		if (!InquilinoConfig.vhs || mc.level == null || mc.player == null) {
			return null;
		}
		float glitch = HorrorEffects.glitchLevel();
		if (glitch > 0.6F && InquilinoConfig.flashes) {
			return HEAVY;
		}
		if (glitch > 0.22F) {
			return MEDIUM;
		}
		return CALM;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!InquilinoConfig.vhs || mc.player == null) {
			return;
		}
		long now = Util.getMillis();
		if (recordingSince < 0) {
			recordingSince = now;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int m = 10;
		float glitch = HorrorEffects.glitchLevel();
		int jx = glitch > 0.5F && RANDOM.nextInt(4) == 0 ? RANDOM.nextInt(5) - 2 : 0;
		int white = ARGB.color(215, 235, 235, 230);
		int dim = ARGB.color(150, 235, 235, 230);

		// cantos do visor
		int len = 16;
		corner(g, m, m, len, 1, 1, dim);
		corner(g, w - m, m, len, -1, 1, dim);
		corner(g, m, h - m, len, 1, -1, dim);
		corner(g, w - m, h - m, len, -1, -1, dim);

		// ● REC
		int x = m + 8 + jx;
		int y = m + 7;
		if ((now / 650) % 2 == 0) {
			g.fill(x, y + 1, x + 6, y + 7, ARGB.color(255, 225, 25, 25));
			g.fill(x + 1, y, x + 5, y + 8, ARGB.color(255, 225, 25, 25));
		}
		g.text(font, "REC", x + 10, y, white, true);

		// contador da fita
		long secs = (now - recordingSince) / 1000;
		String counter = String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
		g.text(font, counter, x, y + 13, dim, true);

		// bateria + velocidade da fita
		int bx = w - m - 30 + jx;
		int by = m + 7;
		g.outline(bx, by, 20, 9, white);
		g.fill(bx + 20, by + 3, bx + 22, by + 6, white);
		int bars = HorrorEffects.phase() >= 3 ? 1 : HorrorEffects.phase() >= 2 ? 2 : 3;
		if (bars == 1 && (now / 400) % 2 == 0) {
			bars = 0; // bateria acabando
		}
		for (int i = 0; i < bars; i++) {
			g.fill(bx + 2 + i * 6, by + 2, bx + 6 + i * 6, by + 7, white);
		}
		g.text(font, "SP", bx - font.width("SP") - 6, by + 1, white, true);

		// data e hora (do seu relógio de verdade)
		LocalDateTime dt = LocalDateTime.now();
		Locale locale = locale(mc);
		String date = dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", locale)).replace(".", "").toUpperCase(locale);
		String time = dt.format(DateTimeFormatter.ofPattern("HH:mm:ss", locale));
		int dx = w - m - 8 + jx;
		int dy = h - m - 27;
		g.text(font, time, dx - font.width(time), dy, white, true);
		g.text(font, date, dx - font.width(date), dy + 11, white, true);

		// perda de sinal quando ele está muito perto
		if (glitch > 0.75F && (now / 180) % 3 != 0) {
			String tracking = "TRACKING";
			g.text(font, tracking, m + 8 + RANDOM.nextInt(3), h / 2 - 4, ARGB.color(200, 235, 235, 230), true);
		}
	}

	private static void corner(GuiGraphicsExtractor g, int x, int y, int len, int sx, int sy, int color) {
		int x2 = x + sx * len;
		int y2 = y + sy * len;
		int row = sy > 0 ? y : y - 1;
		int col = sx > 0 ? x : x - 1;
		g.fill(Math.min(x, x2), row, Math.max(x, x2), row + 1, color);
		g.fill(col, Math.min(y, y2), col + 1, Math.max(y, y2), color);
	}

	private static Locale locale(Minecraft mc) {
		String code = mc.getLanguageManager().getSelected();
		String[] parts = code.split("_");
		return parts.length > 1 ? Locale.of(parts[0], parts[1].toUpperCase(Locale.ROOT)) : Locale.of(parts[0]);
	}
}

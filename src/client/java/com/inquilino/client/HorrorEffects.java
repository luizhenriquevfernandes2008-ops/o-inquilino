package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import com.inquilino.InquilinoMod;
import com.inquilino.ModRegistry;
import com.inquilino.entity.TenantEntity;
import com.inquilino.net.HorrorPayload;
import com.inquilino.net.HorrorPayload.Effects;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Todos os efeitos que acontecem na tela do jogador. */
public final class HorrorEffects {
	private static final Identifier STATIC_TEX = InquilinoMod.id("textures/gui/static.png");
	private static final Identifier FACE_TEX = InquilinoMod.id("textures/gui/jumpscare.png");
	private static final Identifier VIGNETTE_TEX = InquilinoMod.id("textures/gui/vignette.png");
	private static final RandomSource RANDOM = RandomSource.create();

	private static int phase;
	private static int staticTicks;
	private static int staticMax = 1;
	private static int faceTicks;
	private static int faceMax = 1;
	private static boolean jumpscare;
	private static @Nullable Component text;
	private static int textTicks;
	private static int textMax = 1;
	private static int titleTicks;
	private static @Nullable Vec3 lookTarget;
	private static int lookTicks;
	private static int shakeTicks;
	private static int blackoutTicks;
	private static int blackoutMax = 1;
	private static float proximity;
	private static int heartbeatIn;
	private static boolean hunting;
	private static float fogCurrent = 999.0F;
	private static float fogDark;
	private static @Nullable AmbienceSound ambience;

	private HorrorEffects() {
	}

	public static int phase() {
		return phase;
	}

	public static float proximity() {
		return proximity;
	}

	/** 0..1: quão ruim está o sinal da câmera agora. */
	public static float glitchLevel() {
		float g = proximity;
		if (staticTicks > 0) {
			g = Math.max(g, 0.4F + 0.6F * staticTicks / (float) staticMax);
		}
		if (hunting) {
			g = Math.max(g, 0.7F);
		}
		if (phase >= 3) {
			g = Math.max(g, 0.25F);
		}
		return g;
	}

	/** Distância da neblina (ou -1 para a neblina normal do jogo). */
	public static float fogDistance() {
		return InquilinoConfig.fog && fogCurrent < 900.0F ? fogCurrent : -1.0F;
	}

	public static float fogDarkness() {
		return fogDark;
	}

	public static boolean shouldMuteMusic() {
		Minecraft mc = Minecraft.getInstance();
		return InquilinoConfig.muteMusic && mc.level != null && phase >= 1 && phase < 4;
	}

	public static void reset() {
		fogCurrent = 999.0F;
		hunting = false;
		if (ambience != null) {
			ambience.setTarget(0);
			ambience = null;
		}
		VhsCamera.reset();
		staticTicks = faceTicks = textTicks = lookTicks = shakeTicks = blackoutTicks = 0;
		lookTarget = null;
		text = null;
		proximity = 0;
		if (titleTicks > 0) {
			titleTicks = 0;
			Minecraft.getInstance().updateTitle();
		}
	}

	// ================================================================== pacotes

	public static void handle(Minecraft mc, HorrorPayload p) {
		switch (p.effect()) {
			case Effects.PHASE -> phase = p.ticks();
			case Effects.STATIC -> {
				int ticks = InquilinoConfig.flashes ? p.ticks() : Math.max(1, p.ticks() / 2);
				staticTicks = Math.max(staticTicks, ticks);
				staticMax = Math.max(1, staticTicks);
			}
			case Effects.FACE -> {
				if (InquilinoConfig.flashes) {
					faceTicks = p.ticks();
					faceMax = Math.max(1, p.ticks());
					jumpscare = false;
				} else {
					staticTicks = Math.max(staticTicks, 8);
					staticMax = staticTicks;
				}
			}
			case Effects.JUMPSCARE -> {
				faceTicks = p.ticks();
				faceMax = Math.max(1, p.ticks());
				jumpscare = true;
				shakeTicks = p.ticks();
				staticTicks = p.ticks() + 10;
				staticMax = staticTicks;
				playLocal(ModRegistry.SCREAM.value(), 0.7F, 1.0F);
				playLocal(SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 0.8F);
			}
			case Effects.TEXT -> {
				text = Component.translatable(p.text(), p.arg());
				textTicks = p.ticks();
				textMax = Math.max(1, p.ticks());
			}
			case Effects.WINDOW_TITLE -> {
				String title = Language.getInstance().getOrDefault(p.text());
				mc.getWindow().setTitle(safeFormat(title, p.arg()));
				titleTicks = p.ticks();
			}
			case Effects.LOOK_AT -> {
				lookTarget = new Vec3(p.x(), p.y(), p.z());
				lookTicks = p.ticks();
			}
			case Effects.SHAKE -> shakeTicks = Math.max(shakeTicks, p.ticks());
			case Effects.BLACKOUT -> {
				blackoutTicks = p.ticks();
				blackoutMax = Math.max(1, p.ticks());
			}
			case Effects.FAKE_DISCONNECT -> mc.gui.setScreen(new FakeDisconnectScreen(
				Component.translatable(p.text(), p.arg()), p.ticks() == 1
			));
			case Effects.WRITE_FILE -> ArgFiles.write(mc, p.text(), p.arg());
			default -> InquilinoMod.LOGGER.debug("efeito desconhecido {}", p.effect());
		}
	}

	static String safeFormat(String template, String arg) {
		try {
			return String.format(template, arg);
		} catch (Exception e) {
			return template;
		}
	}

	private static void playLocal(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	// ================================================================== tick

	public static void tick(Minecraft mc) {
		if (titleTicks > 0 && --titleTicks == 0) {
			mc.updateTitle();
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (staticTicks > 0) {
			staticTicks--;
		}
		if (faceTicks > 0) {
			faceTicks--;
		}
		if (textTicks > 0) {
			textTicks--;
		}
		if (blackoutTicks > 0) {
			blackoutTicks--;
		}

		// algo vira a sua cabeça
		if (lookTicks > 0 && lookTarget != null) {
			lookTicks--;
			Vec3 eye = player.getEyePosition();
			Vec3 d = lookTarget.subtract(eye);
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
			float wantPitch = (float) (-(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG));
			float k = 0.16F;
			player.setYRot(player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * k);
			player.setXRot(Mth.clamp(player.getXRot() + (wantPitch - player.getXRot()) * k, -90.0F, 90.0F));
			if (lookTicks == 0) {
				lookTarget = null;
			}
		}
		if (shakeTicks > 0) {
			shakeTicks--;
			float s = InquilinoConfig.flashes ? 1.6F : 0.6F;
			player.setYRot(player.getYRot() + (RANDOM.nextFloat() - 0.5F) * s);
			player.setXRot(Mth.clamp(player.getXRot() + (RANDOM.nextFloat() - 0.5F) * s, -90.0F, 90.0F));
		}

		// presença: quanto mais perto ele está, pior
		List<TenantEntity> near = mc.level.getEntitiesOfClass(TenantEntity.class, player.getBoundingBox().inflate(28.0));
		float target = 0;
		TenantEntity closest = null;
		for (TenantEntity t : near) {
			float dist = player.distanceTo(t);
			float p = 1.0F - Mth.clamp((dist - 3.0F) / 25.0F, 0.0F, 1.0F);
			if (p > target) {
				target = p;
				closest = t;
			}
		}
		proximity += (target - proximity) * 0.08F;
		hunting = closest != null && closest.getMode() == TenantEntity.Mode.HUNT;

		// neblina: fecha com a fase, com a noite, e muito quando ele está caçando
		boolean night = mc.level.isDarkOutside();
		float fogTarget = switch (phase) {
			case 1 -> night ? 90.0F : 999.0F;
			case 2 -> night ? 46.0F : 150.0F;
			case 3 -> night ? 26.0F : 80.0F;
			default -> 999.0F;
		};
		if (hunting) {
			fogTarget = Math.min(fogTarget, 18.0F);
		}
		if (blackoutTicks > 0) {
			fogTarget = Math.min(fogTarget, 7.0F);
		}
		fogCurrent += (fogTarget - fogCurrent) * (fogTarget < fogCurrent ? 0.025F : 0.008F);
		fogDark = Mth.clamp(1.0F - fogCurrent / 160.0F, 0.0F, 1.0F) * (night ? 0.95F : 0.45F);

		// zumbido grave de fundo
		float amb = phase >= 1 && phase < 4 ? 0.12F + phase * 0.1F + proximity * 0.4F : 0.0F;
		if (amb > 0 && (ambience == null || ambience.isStopped())) {
			ambience = new AmbienceSound();
			mc.getSoundManager().play(ambience);
		}
		if (ambience != null) {
			ambience.setTarget(amb);
		}

		if (closest != null && (closest.getMode() == TenantEntity.Mode.HUNT || target > 0.55F)) {
			if (--heartbeatIn <= 0) {
				float dist = player.distanceTo(closest);
				heartbeatIn = (int) Mth.clamp(dist * 1.1F, 7, 26);
				playLocal(ModRegistry.HEARTBEAT.value(), 1.0F, Mth.clamp(1.2F - dist / 25.0F, 0.3F, 1.0F));
			}
		}
	}

	// ================================================================== HUD

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();

		// vinheta: aumenta com a fase, com a noite e com a proximidade dele
		float vignette = switch (phase) {
			case 0 -> 0.0F;
			case 1 -> 0.12F;
			case 2 -> 0.28F;
			case 3 -> 0.42F;
			default -> 0.1F;
		};
		if (mc.level != null && mc.level.isDarkOutside()) {
			vignette += phase >= 1 ? 0.12F : 0.0F;
		}
		vignette = Mth.clamp(vignette + proximity * 0.6F, 0.0F, 1.0F) * (InquilinoConfig.vhs ? 0.55F : 1.0F);
		if (vignette > 0.01F) {
			g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE_TEX, 0, 0, 0, 0, w, h, 256, 256, 256, 256, ARGB.white(vignette));
		}

		if (blackoutTicks > 0) {
			float t = blackoutTicks / (float) blackoutMax;
			float a = t > 0.85F ? (1 - t) / 0.15F : t < 0.25F ? t / 0.25F : 1.0F;
			g.fill(0, 0, w, h, ARGB.black(a * 0.94F));
		}

		// estática
		float st = 0;
		if (staticTicks > 0) {
			st = Math.max(st, 0.25F + 0.55F * (staticTicks / (float) staticMax));
		}
		st = Math.max(st, proximity > 0.4F ? (proximity - 0.4F) * (InquilinoConfig.vhs ? 0.12F : 0.3F) : 0);
		if (!InquilinoConfig.flashes) {
			st = Math.min(st, 0.25F);
		}
		if (st > 0.01F) {
			float u = RANDOM.nextInt(128);
			float v = RANDOM.nextInt(160);
			g.blit(RenderPipelines.GUI_TEXTURED, STATIC_TEX, 0, 0, u, v, w, h, 128, 96, 256, 256, ARGB.white(st));
		}

		// rosto
		if (faceTicks > 0) {
			float t = faceTicks / (float) faceMax;
			// no susto, o rosto começa inteiro na tela e avança na sua direção
			int size = (int) (Math.min(w, h) * (jumpscare ? 1.05F + (1 - t) * 0.9F : 1.0F));
			int ox = jumpscare ? RANDOM.nextInt(13) - 6 : 0;
			int oy = jumpscare ? RANDOM.nextInt(13) - 6 : 0;
			float alpha = jumpscare ? 1.0F : 0.85F;
			if (jumpscare && InquilinoConfig.flashes && faceTicks % 4 == 0) {
				alpha = 0.55F;
			}
			g.fill(0, 0, w, h, ARGB.black(jumpscare ? 1.0F : 0.6F));
			g.blit(RenderPipelines.GUI_TEXTURED, FACE_TEX, (w - size) / 2 + ox, (h - size) / 2 + oy, 0, 0, size, size, 256, 256, 256, 256, ARGB.white(alpha));
		}

		// texto subliminar
		if (textTicks > 0 && text != null) {
			float t = textTicks / (float) textMax;
			float a = textMax <= 4 ? 1.0F : Mth.sin(t * Mth.PI);
			int color = ARGB.color((int) (a * 255), 170, 20, 20);
			g.pose().pushMatrix();
			g.pose().translate(w / 2.0F, h / 2.0F - 10);
			g.pose().scale(3.0F);
			g.centeredText(mc.font, text, 0, -4, color);
			g.pose().popMatrix();
		}
	}
}

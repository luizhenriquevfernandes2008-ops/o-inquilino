package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;

/**
 * Mostra o seu próprio corpo em primeira pessoa (olhe para baixo: tronco, pernas, pés).
 * A cabeça e os braços ficam escondidos — os braços do jogo continuam aparecendo normalmente.
 */
public final class FirstPersonBody {
	/** Marca o render state do jogador local quando ele está sendo desenhado em primeira pessoa. */
	public static final RenderStateDataKey<Boolean> SELF = RenderStateDataKey.create(() -> "inquilino:first_person_self");

	private FirstPersonBody() {
	}

	public static boolean active(Camera camera) {
		Minecraft mc = Minecraft.getInstance();
		return camera.entity() == mc.player && active();
	}

	public static boolean active() {
		if (!InquilinoConfig.bodyVisible) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || p.isSpectator() || p.isSleeping() || p.isPassenger() || !mc.options.getCameraType().isFirstPerson()) {
			return false;
		}
		Pose pose = p.getPose();
		return pose == Pose.STANDING || pose == Pose.CROUCHING;
	}
}

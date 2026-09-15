package com.inquilino.client.mixin;

import com.inquilino.client.HorrorEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A neblina fecha ao seu redor conforme a assombração piora. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void inquilino$closeIn(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		float end = HorrorEffects.fogDistance();
		if (end <= 0) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float start = end * 0.08F;
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.skyEnd = Math.min(fog.skyEnd, end);
		fog.cloudEnd = Math.min(fog.cloudEnd, end);
		float dark = HorrorEffects.fogDarkness();
		fog.color.set(
			fog.color.x * (1 - dark) + 0.015F * dark,
			fog.color.y * (1 - dark) + 0.015F * dark,
			fog.color.z * (1 - dark) + 0.02F * dark,
			1.0F
		);
	}
}

package com.inquilino.client.mixin;

import com.inquilino.client.FirstPersonBody;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Em primeira pessoa, o seu próprio corpo também é desenhado. */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
	@Redirect(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
	private boolean inquilino$drawOwnBody(Camera camera) {
		return camera.isDetached() || FirstPersonBody.active(camera);
	}
}

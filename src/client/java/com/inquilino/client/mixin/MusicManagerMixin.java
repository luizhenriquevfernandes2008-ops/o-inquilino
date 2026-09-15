package com.inquilino.client.mixin;

import com.inquilino.client.HorrorEffects;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nada de música calma enquanto ele estiver no seu mundo. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void inquilino$silence(CallbackInfo ci) {
		if (HorrorEffects.shouldMuteMusic()) {
			((MusicManager) (Object) this).stopPlaying();
			ci.cancel();
		}
	}
}

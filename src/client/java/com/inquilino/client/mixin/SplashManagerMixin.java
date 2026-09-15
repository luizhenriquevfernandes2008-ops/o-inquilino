package com.inquilino.client.mixin;

import com.inquilino.InquilinoConfig;
import java.util.Random;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Às vezes a frase amarela do menu não é do jogo. */
@Mixin(SplashManager.class)
public class SplashManagerMixin {
	@Unique
	private static final Random INQUILINO_RANDOM = new Random();
	@Unique
	private static final int INQUILINO_SPLASHES = 9;

	@Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
	private void inquilino$splash(CallbackInfoReturnable<SplashRenderer> cir) {
		boolean remembers = InquilinoConfig.remembers();
		if (remembers && INQUILINO_RANDOM.nextInt(3) > 0) {
			cir.setReturnValue(new SplashRenderer(Component.translatable("splash.inquilino.remember", InquilinoConfig.rememberedName()).withColor(0xC02020)));
		} else if (INQUILINO_RANDOM.nextInt(3) == 0) {
			cir.setReturnValue(new SplashRenderer(Component.translatable("splash.inquilino." + INQUILINO_RANDOM.nextInt(INQUILINO_SPLASHES)).withColor(0xC02020)));
		}
	}
}

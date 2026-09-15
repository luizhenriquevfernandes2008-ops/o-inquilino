package com.inquilino.client.mixin;

import com.inquilino.client.CameraMotion;
import com.inquilino.client.VhsCamera;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	/** Câmera de mão (aplicada ao mundo e à mão). */
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void inquilino$handheld(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (cameraState.entityRenderState.isPlayer) {
			CameraMotion.apply(
				poseStack,
				cameraState.entityRenderState.backwardsInterpolatedWalkDistance,
				cameraState.entityRenderState.bob,
				this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)
			);
		}
	}

	/** Filtro VHS sobre o mundo (antes do HUD). */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void inquilino$vhs(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		Identifier chain = VhsCamera.currentChain();
		if (chain == null) {
			return;
		}
		PostChain postChain = this.minecraft.getShaderManager().getPostChain(chain, LevelTargetBundle.MAIN_TARGETS);
		if (postChain != null) {
			postChain.process(this.mainRenderTarget, this.resourcePool);
		}
	}
}

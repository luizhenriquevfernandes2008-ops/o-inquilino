package com.inquilino.client.mixin;

import com.inquilino.client.FirstPersonBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void inquilino$markSelf(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		boolean self = entity == Minecraft.getInstance().player && FirstPersonBody.active();
		state.setData(FirstPersonBody.SELF, self);
		if (self) {
			// nada de capacete na frente da câmera nem itens flutuando nos braços escondidos
			state.headEquipment = ItemStack.EMPTY;
			state.headItem.clear();
			state.heldOnHead.clear();
			state.rightHandItemState.clear();
			state.leftHandItemState.clear();
			state.nameTag = null;
		}
	}

	/** Afasta o corpo para trás da câmera, para você ver o peito e as pernas ao olhar para baixo. */
	@Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
	private void inquilino$offsetSelf(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
		if (Boolean.TRUE.equals(state.getData(FirstPersonBody.SELF))) {
			float yaw = state.bodyRot * Mth.DEG_TO_RAD;
			float back = 0.13F + 0.05F * Math.max(0.0F, state.xRot) / 90.0F;
			Vec3 offset = new Vec3(Mth.sin(yaw) * back, 0.0, -Mth.cos(yaw) * back);
			cir.setReturnValue(cir.getReturnValue().add(offset));
		}
	}
}

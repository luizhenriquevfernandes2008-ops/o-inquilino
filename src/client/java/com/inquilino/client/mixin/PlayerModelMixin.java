package com.inquilino.client.mixin;

import com.inquilino.client.FirstPersonBody;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No seu próprio corpo em primeira pessoa, a cabeça e os braços ficam escondidos. */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void inquilino$hideHeadAndArms(AvatarRenderState state, CallbackInfo ci) {
		if (Boolean.TRUE.equals(state.getData(FirstPersonBody.SELF))) {
			HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
			model.head.visible = false;
			model.rightArm.visible = false;
			model.leftArm.visible = false;
		}
	}
}

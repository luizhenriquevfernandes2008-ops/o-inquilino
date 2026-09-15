package com.inquilino.client.render;

import com.inquilino.entity.TenantEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TenantRenderState extends LivingEntityRenderState {
	public TenantEntity.Mode mode = TenantEntity.Mode.WATCH;
	public int idSeed;
}

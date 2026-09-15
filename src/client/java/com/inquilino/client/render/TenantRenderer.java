package com.inquilino.client.render;

import com.inquilino.InquilinoMod;
import com.inquilino.entity.TenantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class TenantRenderer extends MobRenderer<TenantEntity, TenantRenderState, TenantModel> {
	private static final Identifier TEXTURE = InquilinoMod.id("textures/entity/inquilino.png");
	private static final RenderType EYES = RenderTypes.eyes(InquilinoMod.id("textures/entity/inquilino_eyes.png"));
	private final RandomSource random = RandomSource.create();

	public TenantRenderer(EntityRendererProvider.Context context) {
		// sem sombra: ele não está exatamente aqui
		super(context, new TenantModel(context.bakeLayer(TenantModel.LAYER)), 0.0F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public Identifier getTextureLocation(TenantRenderState state) {
		return TEXTURE;
	}

	@Override
	public TenantRenderState createRenderState() {
		return new TenantRenderState();
	}

	@Override
	public void extractRenderState(TenantEntity entity, TenantRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.mode = entity.getMode();
		state.idSeed = entity.getId();
	}

	@Override
	public Vec3 getRenderOffset(TenantRenderState state) {
		Vec3 offset = super.getRenderOffset(state);
		if (state.mode == TenantEntity.Mode.HUNT) {
			double d = 0.035;
			return offset.add(this.random.nextGaussian() * d, 0.0, this.random.nextGaussian() * d);
		}
		return offset;
	}
}

package com.inquilino.client.render;

import com.inquilino.InquilinoMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Um humanoide alto demais: pernas e tronco esticados, braços que passam dos joelhos.
 * A cabeça fica inclinada, como se estivesse tentando entender você.
 */
public class TenantModel extends EntityModel<TenantRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(InquilinoMod.id("inquilino"), "main");

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public TenantModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 14.0F, 4.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -1.5F, -1.5F, 3.0F, 16.0F, 3.0F), PartPose.offset(-5.5F, -2.5F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -1.5F, -1.5F, 3.0F, 16.0F, 3.0F), PartPose.offset(5.5F, -2.5F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F), PartPose.offset(-1.9F, 10.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F), PartPose.offset(1.9F, 10.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(TenantRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;

		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;

		switch (state.mode) {
			case WATCH, LURK, APPROACH -> {
				// imóvel demais. só a cabeça inclina.
				this.head.zRot = 0.34F + Mth.sin(t * 0.03F) * 0.04F;
				this.rightArm.xRot = Mth.sin(t * 0.045F) * 0.025F;
				this.leftArm.xRot = -Mth.sin(t * 0.045F) * 0.025F;
				this.rightArm.zRot = 0.06F;
				this.leftArm.zRot = -0.06F;
			}
			case STALK -> {
				this.head.zRot = 0.18F;
				this.head.xRot += 0.25F;
				this.rightLeg.xRot = Mth.cos(pos * 0.5F) * 1.0F * speed;
				this.leftLeg.xRot = Mth.cos(pos * 0.5F + Mth.PI) * 1.0F * speed;
				this.rightArm.xRot = Mth.cos(pos * 0.5F + Mth.PI) * 0.4F * speed;
				this.leftArm.xRot = Mth.cos(pos * 0.5F) * 0.4F * speed;
				this.body.xRot = 0.12F;
			}
			case HUNT -> {
				// correndo com os braços para frente, cabeça tremendo
				this.head.zRot = Mth.sin(t * 3.1F) * 0.18F;
				this.head.xRot += Mth.cos(t * 2.3F) * 0.12F;
				this.rightLeg.xRot = Mth.cos(pos * 0.6662F) * 1.7F * speed;
				this.leftLeg.xRot = Mth.cos(pos * 0.6662F + Mth.PI) * 1.7F * speed;
				this.rightArm.xRot = -1.45F + Mth.cos(pos * 0.6662F) * 0.35F * speed;
				this.leftArm.xRot = -1.45F + Mth.cos(pos * 0.6662F + Mth.PI) * 0.35F * speed;
				this.rightArm.zRot = 0.1F;
				this.leftArm.zRot = -0.1F;
				this.body.xRot = 0.3F;
			}
		}

		// espasmo: de vez em quando a cabeça estala para o lado
		int cycle = (int) (t + state.idSeed * 17) % 71;
		if (cycle < 2) {
			this.head.zRot += (state.idSeed % 2 == 0 ? 1 : -1) * 0.95F;
			this.head.yRot += 0.4F;
		}
	}
}

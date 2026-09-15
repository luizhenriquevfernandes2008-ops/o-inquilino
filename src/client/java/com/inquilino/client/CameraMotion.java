package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Câmera de mão: a cabeça balança com os passos, inclina quando você anda de lado ou vira
 * rápido, afunda quando você cai, e nunca fica totalmente parada (respiração, mão tremendo).
 * Quando ele está perto, a mão treme mais.
 */
public final class CameraMotion {
	private static float strafe;
	private static float strafeO;
	private static float yawVel;
	private static float yawVelO;
	private static float landing;
	private static float landingO;
	private static float lastYaw;
	private static double lastFall;
	private static boolean wasOnGround = true;

	private CameraMotion() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		strafeO = strafe;
		yawVelO = yawVel;
		landingO = landing;

		// velocidade lateral em relação ao olhar
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 v = p.getDeltaMovement();
		double lateral = v.x * -Mth.cos(yaw) + v.z * -Mth.sin(yaw);
		strafe += ((float) lateral * 10.0F - strafe) * 0.22F;

		float dyaw = Mth.wrapDegrees(p.getYRot() - lastYaw);
		lastYaw = p.getYRot();
		yawVel += (Mth.clamp(dyaw, -40.0F, 40.0F) - yawVel) * 0.3F;

		if (p.onGround() && !wasOnGround && lastFall > 0.6) {
			landing = Math.min(1.0F, (float) lastFall * 0.1F + 0.3F);
		}
		landing *= 0.8F;
		lastFall = p.fallDistance;
		wasOnGround = p.onGround();
	}

	/** Chamado ao montar a matriz da câmera (mundo e mão). */
	public static void apply(PoseStack pose, float walkDistance, float bob, float partialTicks) {
		if (!InquilinoConfig.cameraMotion) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float t = p.tickCount + partialTicks;
		float s = Mth.lerp(partialTicks, strafeO, strafe);
		float yv = Mth.lerp(partialTicks, yawVelO, yawVel);
		float land = Mth.lerp(partialTicks, landingO, landing);
		float fear = HorrorEffects.proximity();

		// passos mais pesados que o normal (como alguém segurando uma câmera)
		float step = walkDistance * Mth.PI;
		float extraBob = bob * 1.4F;
		pose.translate(Mth.sin(step) * extraBob * 0.25F, -Math.abs(Mth.cos(step)) * extraBob * 0.35F, 0.0F);

		// respiração e deriva da mão
		float breathe = Mth.sin(t * 0.055F) * (0.45F + fear * 0.6F);
		float driftYaw = Mth.sin(t * 0.031F) * 0.3F + Mth.sin(t * 0.017F + 1.3F) * 0.25F;
		float driftRoll = Mth.sin(t * 0.023F + 0.7F) * 0.35F;
		// tremor de medo / corrida
		float tremor = fear * fear * 0.9F + (p.isSprinting() ? 0.25F : 0.0F);
		float shakeX = (Mth.sin(t * 2.7F) + Mth.sin(t * 4.3F + 1.1F)) * 0.5F * tremor;
		float shakeY = (Mth.sin(t * 3.1F + 0.4F) + Mth.sin(t * 5.9F)) * 0.5F * tremor;

		float roll = -s * 2.4F + Mth.clamp(-yv * 0.14F, -5.0F, 5.0F) + driftRoll + Mth.sin(step) * extraBob * 2.0F;
		pose.translate(0.0F, -land * 0.09F, 0.0F);
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.mulPose(Axis.XP.rotationDegrees(breathe + land * 7.0F + shakeY + Math.abs(Mth.cos(step)) * extraBob * 2.0F));
		pose.mulPose(Axis.YP.rotationDegrees(driftYaw + shakeX));
	}
}

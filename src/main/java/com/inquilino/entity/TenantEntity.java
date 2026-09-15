package com.inquilino.entity;

import com.inquilino.ModRegistry;
import com.inquilino.haunt.HauntDirector;
import com.inquilino.haunt.HauntState;
import com.inquilino.haunt.HeldLight;
import com.inquilino.haunt.WorldUtil;
import com.inquilino.net.HorrorPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * O Inquilino. Não é salvo no mundo: ele só existe enquanto alguém está olhando para o lugar certo.
 */
public class TenantEntity extends Monster {
	public enum Mode {
		/** Parado ao longe, encarando. Some ao ser encarado ou se o jogador chegar perto. */
		WATCH,
		/** Segue por trás, fora da visão. Some quando é visto (às vezes com um susto). */
		STALK,
		/** Corre na direção do jogador. */
		HUNT,
		/** Parado, controlado por uma sequência de eventos. */
		LURK,
		/** Não some quando visto. Toda vez que você desvia o olhar, ele está mais perto. */
		APPROACH
	}

	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(TenantEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID targetId;
	private int life;
	private int maxLife = 20 * 60;
	private int seenTicks;
	private int unseenTicks;
	private int steps;
	private int maxSteps = 99;
	private boolean everSeen;
	private boolean vanishing;
	private boolean jumpOnSight;
	private boolean huntTurnedStalk;
	private int vanishAfterSeen;

	public TenantEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 1000.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 96.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, Mode.WATCH.ordinal());
	}

	public Mode getMode() {
		int i = this.entityData.get(DATA_MODE);
		return Mode.values()[Mth.clamp(i, 0, Mode.values().length - 1)];
	}

	public void setMode(Mode mode) {
		this.entityData.set(DATA_MODE, mode.ordinal());
		this.seenTicks = 0;
		this.unseenTicks = 0;
	}

	public void setup(ServerPlayer target, Mode mode, int lifetimeTicks) {
		this.targetId = target.getUUID();
		this.maxLife = lifetimeTicks;
		this.life = 0;
		this.setMode(mode);
		this.faceTowards(target);
	}

	/** Se for visto, dá o susto na hora. */
	public TenantEntity jumpOnSight() {
		this.jumpOnSight = true;
		return this;
	}

	/** No modo LURK: some depois de ser encarado por tantos ticks. */
	public TenantEntity vanishAfterSeen(int ticks) {
		this.vanishAfterSeen = ticks;
		return this;
	}

	/** Quantas vezes ele pode se aproximar antes de desistir (modo APPROACH). */
	public TenantEntity maxSteps(int steps) {
		this.maxSteps = steps;
		return this;
	}

	public void faceTowards(Entity e) {
		double dx = e.getX() - this.getX();
		double dz = e.getZ() - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
		this.yRotO = yaw;
		this.yBodyRotO = yaw;
		this.yHeadRotO = yaw;
	}

	public @Nullable ServerPlayer target() {
		if (this.targetId == null || !(this.level() instanceof ServerLevel sl)) {
			return null;
		}
		ServerPlayer p = sl.getServer().getPlayerList().getPlayer(this.targetId);
		return p != null && p.level() == sl && p.isAlive() && !p.isSpectator() ? p : null;
	}

	// ------------------------------------------------------------------ comportamento

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || this.vanishing) {
			return;
		}
		ServerPlayer target = this.target();
		if (target == null) {
			this.discard();
			return;
		}
		if (++this.life > this.maxLife) {
			if (this.getMode() == Mode.HUNT && !this.huntTurnedStalk) {
				// a caçada acabou, mas ele não foi embora: continua atrás de você
				this.huntTurnedStalk = true;
				this.setMode(Mode.STALK);
				this.life = 0;
				this.maxLife = 20 * 45;
				this.getNavigation().stop();
				return;
			}
			this.vanish(target, false);
			return;
		}

		double dist = this.distanceTo(target);
		boolean seen = this.isSeenBy(target, dist);
		this.getLookControl().setLookAt(target, 360.0F, 360.0F);

		// no susto "atrás de você", basta ele entrar no seu campo de visão
		boolean glimpsed = seen || (WorldUtil.viewDot(target, this.getEyePosition()) > 0.72 && target.hasLineOfSight(this));
		if (glimpsed && this.jumpOnSight) {
			this.scare(target, true);
			return;
		}

		switch (this.getMode()) {
			case WATCH -> {
				this.getNavigation().stop();
				this.seenTicks = seen ? this.seenTicks + 1 : Math.max(0, this.seenTicks - 1);
				if (this.seenTicks == 1) {
					HauntDirector.onTenantSeen(target, this, dist);
				}
				if (this.seenTicks > 22 || dist < 10.0) {
					this.vanish(target, true);
				}
			}
			case STALK -> this.tickStalk(target, dist, seen);
			case HUNT -> this.tickHunt(level, target, dist);
			case APPROACH -> this.tickApproach(level, target, dist, seen);
			case LURK -> {
				this.getNavigation().stop();
				if (seen && ++this.seenTicks == 1) {
					HauntDirector.onTenantSeen(target, this, dist);
				}
				if (this.vanishAfterSeen > 0 && this.seenTicks > this.vanishAfterSeen) {
					this.vanish(target, true);
				}
			}
		}
	}

	private void tickStalk(ServerPlayer target, double dist, boolean seen) {
		if (seen) {
			this.getNavigation().stop();
			if (++this.seenTicks == 1) {
				HauntDirector.onTenantSeen(target, this, dist);
				// perto demais: susto
				if (dist < 7 && this.random.nextBoolean()) {
					this.scare(target, false);
					return;
				}
			}
			if (this.seenTicks > 6) {
				this.vanish(target, true);
			}
		} else {
			this.seenTicks = 0;
			if (this.life % 50 == 0 && dist > 3.0) {
				this.getNavigation().moveTo(target, 0.8);
			}
			if (dist < 2.5) {
				WorldUtil.playTo(target, ModRegistry.BREATH, target.getEyePosition().add(WorldUtil.flatLook(target).scale(-0.6)), 1.0F, 0.6F);
				this.vanish(target, false);
			}
		}
	}

	private void tickHunt(ServerLevel level, ServerPlayer target, double dist) {
		if (this.life % 6 == 0) {
			// a luz na sua mão atrasa ele
			double speed = HeldLight.holdsLight(target) ? 1.0 : 1.24;
			this.getNavigation().moveTo(target, speed);
		}
		if (this.life % 12 == 0) {
			this.extinguishLightsAround(level, 7);
		}
		if (this.life % 30 == 0) {
			WorldUtil.playTo(target, ModRegistry.VOICES, this.position(), 1.3F, 0.5F + this.random.nextFloat() * 0.2F);
		}
		if (this.life % 50 == 25 && dist > 12) {
			// o som dos passos dele, pesado, para você saber que ainda está vindo
			WorldUtil.playTo(target, ModRegistry.KNOCK, this.position(), 0.7F, 0.5F);
		}
		// se ficar preso muito longe, ele pula para perto de novo, fora da sua visão
		if (this.life % 100 == 0 && dist > 28) {
			BlockPos near = WorldUtil.hiddenSpot(target, 14, 20, true);
			if (near != null) {
				this.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, this.getYRot(), 0);
			}
		}
		if (dist < 1.8 && this.hasLineOfSight(target)) {
			this.catchPlayer(level, target);
		}
	}

	/** Estilo "o homem da neblina": cada vez que você desvia o olhar, ele está mais perto. */
	private void tickApproach(ServerLevel level, ServerPlayer target, double dist, boolean seen) {
		this.getNavigation().stop();
		if (seen) {
			if (!this.everSeen) {
				this.everSeen = true;
				HauntDirector.onTenantSeen(target, this, dist);
			}
			this.seenTicks++;
			this.unseenTicks = 0;
			if (dist < 3.6) {
				// você se virou e ele estava aqui
				this.catchPlayer(level, target);
				return;
			}
			if (this.seenTicks > 20 * 8) {
				// você encarou até ele desistir. por enquanto.
				this.vanish(target, true);
			}
			return;
		}
		this.unseenTicks++;
		boolean lookedAway = this.seenTicks > 0 && this.unseenTicks == 14;
		boolean creep = this.unseenTicks > 20 * 5 && this.unseenTicks % 50 == 0;
		if (lookedAway || creep) {
			this.seenTicks = 0;
			if (this.steps >= this.maxSteps) {
				this.vanish(target, false);
				return;
			}
			this.stepCloser(level, target, dist);
		}
		if (dist < 3.6 && this.unseenTicks > 40) {
			WorldUtil.playTo(target, ModRegistry.BREATH, this.getEyePosition(), 1.0F, 0.5F);
			this.catchPlayer(level, target);
		}
	}

	private void stepCloser(ServerLevel level, ServerPlayer target, double dist) {
		double newDist = Math.max(2.4, dist * 0.58 - 1.0);
		Vec3 dir = this.position().subtract(target.position()).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		for (int i = 0; i < 6; i++) {
			double spread = (this.random.nextDouble() - 0.5) * 0.6 * i;
			Vec3 d = dir.yRot((float) spread);
			Vec3 p = target.position().add(d.scale(newDist));
			BlockPos pos = WorldUtil.findStandPos(level, p.x, target.getY(), p.z, false);
			if (pos != null) {
				this.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
				this.faceTowards(target);
				this.steps++;
				float vol = newDist < 8 ? 0.8F : 0.4F;
				WorldUtil.playTo(target, ModRegistry.VANISH, this.position(), vol, 0.5F + this.random.nextFloat() * 0.2F);
				if (newDist < 8) {
					WorldUtil.send(target, HorrorPayload.of(HorrorPayload.Effects.STATIC, "", "", 5));
				}
				return;
			}
		}
	}

	/** Parecido com o Enderman: o jogador está olhando diretamente para ele? */
	public boolean isSeenBy(ServerPlayer player, double dist) {
		Vec3 eye = new Vec3(this.getX(), this.getEyeY(), this.getZ());
		double threshold = dist > 20 ? 0.992 : dist > 8 ? 0.975 : 0.9;
		return WorldUtil.viewDot(player, eye) > threshold && player.hasLineOfSight(this);
	}

	/** Susto sem dano: o rosto na tela e o grito. */
	private void scare(ServerPlayer target, boolean full) {
		WorldUtil.send(target, HorrorPayload.of(full ? HorrorPayload.Effects.JUMPSCARE : HorrorPayload.Effects.FACE, full ? 14 : 5));
		WorldUtil.playTo(target, ModRegistry.SCREAM, target.getEyePosition(), full ? 1.6F : 1.0F, 1.1F);
		this.vanish(target, false);
	}

	private void catchPlayer(ServerLevel level, ServerPlayer target) {
		int phase = HauntState.get(level.getServer()).phase;
		WorldUtil.send(target, HorrorPayload.of(HorrorPayload.Effects.JUMPSCARE, 18));
		WorldUtil.playTo(target, ModRegistry.SCREAM, target.getEyePosition(), 2.0F, 1.0F);
		DamageSource source = level.damageSources().source(ModRegistry.TENANT_DAMAGE, this);
		float damage = phase >= HauntState.PHASE_HUNT ? 10.0F : phase >= HauntState.PHASE_INTRUSION ? 6.0F : 3.0F;
		target.hurtServer(level, source, damage);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
		target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false));
		boolean stole = false;
		if (phase >= HauntState.PHASE_HUNT && this.random.nextBoolean()) {
			// ele arranca a luz da sua mão
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = target.getItemInHand(hand);
				if (HeldLight.emission(held) > 0) {
					target.setItemInHand(hand, ItemStack.EMPTY);
					stole = true;
				}
			}
		}
		HauntDirector.onCaught(target, stole);
		this.vanish(target, false);
	}

	private void extinguishLightsAround(ServerLevel level, int radius) {
		BlockPos center = this.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
			BlockState s = level.getBlockState(p);
			if (isTorch(s)) {
				level.removeBlock(p, false);
				level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, this.getSoundSource(), 0.4F, 0.6F);
				return;
			}
		}
	}

	public static boolean isTorch(BlockState s) {
		return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
			|| s.is(Blocks.COPPER_TORCH) || s.is(Blocks.COPPER_WALL_TORCH) || s.is(Blocks.REDSTONE_TORCH) || s.is(Blocks.REDSTONE_WALL_TORCH);
	}

	/** Some. Se foi visto, com um estalo e estática na tela. */
	public void vanish(@Nullable ServerPlayer target, boolean seen) {
		if (this.vanishing) {
			return;
		}
		this.vanishing = true;
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.1, this.getZ(), 18, 0.25, 0.8, 0.25, 0.01);
			level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.1, this.getZ(), 6, 0.2, 0.6, 0.2, 0.02);
			if (target != null && seen) {
				WorldUtil.playTo(target, ModRegistry.VANISH, this.position(), 0.9F, 0.8F + this.random.nextFloat() * 0.3F);
				WorldUtil.send(target, HorrorPayload.of(HorrorPayload.Effects.STATIC, "", "", 8));
			}
		}
		this.discard();
	}

	// ------------------------------------------------------------------ imortalidade

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof ServerPlayer player && !this.vanishing) {
			// ele odeia ser tocado
			WorldUtil.playTo(player, ModRegistry.SCREAM, this.position(), 1.0F, 1.6F);
			WorldUtil.send(player, HorrorPayload.of(HorrorPayload.Effects.STATIC, "", "", 14));
			HauntDirector.onTenantHit(player);
			this.vanish(player, false);
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPersistenceRequired() {
		return true;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return false;
	}

	@Override
	public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}
}

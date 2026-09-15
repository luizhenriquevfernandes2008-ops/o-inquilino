package com.inquilino.haunt;

import com.inquilino.InquilinoConfig;
import com.inquilino.net.HorrorPayload;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Utilidades de mundo: visão do jogador, posições escondidas, sons privados. */
public final class WorldUtil {
	private WorldUtil() {
	}

	// ------------------------------------------------------------------ visão

	/** Cosseno do ângulo entre a visão do jogador e o ponto. */
	public static double viewDot(ServerPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = point.subtract(eye);
		if (to.lengthSqr() < 1.0E-4) {
			return 1.0;
		}
		return player.getViewVector(1.0F).dot(to.normalize());
	}

	/** O ponto está no campo de visão (cone amplo, ~70°) e sem blocos no caminho? */
	public static boolean inView(ServerPlayer player, Vec3 point) {
		return viewDot(player, point) > 0.35 && hasLineOfSight(player, point);
	}

	public static boolean hasLineOfSight(ServerPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 1.0;
	}

	/** Direção horizontal para onde o jogador olha. */
	public static Vec3 flatLook(ServerPlayer player) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Ponto a {@code dist} blocos do jogador, desviado {@code angleDeg} graus da direção do olhar. */
	public static Vec3 around(ServerPlayer player, double dist, double angleDeg) {
		float yaw = (float) ((player.getYRot() + angleDeg) * Mth.DEG_TO_RAD);
		return player.position().add(-Mth.sin(yaw) * dist, 0, Mth.cos(yaw) * dist);
	}

	public static Vec3 behind(ServerPlayer player, double dist, RandomSource random) {
		return around(player, dist, 180 + (random.nextDouble() - 0.5) * 70);
	}

	// ------------------------------------------------------------------ posições

	/** O jogador está a céu aberto? */
	public static boolean underSky(ServerPlayer player) {
		return player.level().canSeeSky(player.blockPosition().above());
	}

	/**
	 * Encontra um lugar onde uma criatura de 2 blocos consiga ficar em pé, perto de (x, z),
	 * procurando a partir da altura {@code yHint}. Se {@code surface}, usa a superfície do terreno.
	 */
	public static @Nullable BlockPos findStandPos(ServerLevel level, double x, double yHint, double z, boolean surface) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		if (!level.hasChunkAt(new BlockPos(bx, 0, bz))) {
			return null;
		}
		if (surface) {
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(bx, 0, bz));
			if (canStand(level, top)) {
				return top;
			}
		}
		int y0 = Mth.floor(yHint);
		for (int dy = 0; dy <= 8; dy++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				BlockPos p = new BlockPos(bx, y0 + dy * sign, bz);
				if (canStand(level, p)) {
					return p;
				}
			}
		}
		return null;
	}

	public static boolean canStand(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet).isEmpty()
			&& level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty();
	}

	/** Procura um ponto de apoio fora da visão do jogador, a uma distância entre min e max. */
	public static @Nullable BlockPos hiddenSpot(ServerPlayer player, double min, double max, boolean requireHidden) {
		ServerLevel level = player.level();
		RandomSource r = player.getRandom();
		boolean surface = underSky(player);
		for (int i = 0; i < 24; i++) {
			double dist = min + r.nextDouble() * (max - min);
			Vec3 p = behind(player, dist, r);
			if (i > 12) {
				p = around(player, dist, r.nextDouble() * 360);
			}
			BlockPos pos = findStandPos(level, p.x, player.getY(), p.z, surface);
			if (pos == null) {
				continue;
			}
			Vec3 center = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
			if (Math.abs(pos.getY() - player.getY()) > 10) {
				continue;
			}
			if (!requireHidden || !inView(player, center)) {
				return pos;
			}
		}
		return null;
	}

	/** Um ponto visível (no limite do campo de visão), para ele ser notado aos poucos. */
	public static @Nullable BlockPos peripheralSpot(ServerPlayer player, double min, double max) {
		ServerLevel level = player.level();
		RandomSource r = player.getRandom();
		boolean surface = underSky(player);
		for (int i = 0; i < 30; i++) {
			double dist = min + r.nextDouble() * (max - min);
			double angle = (r.nextBoolean() ? 1 : -1) * (25 + r.nextDouble() * 45);
			Vec3 p = around(player, dist, angle);
			BlockPos pos = findStandPos(level, p.x, player.getY(), p.z, surface);
			if (pos == null || Math.abs(pos.getY() - player.getY()) > 24) {
				continue;
			}
			if (hasLineOfSight(player, Vec3.atBottomCenterOf(pos).add(0, 1.8, 0))) {
				return pos;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ sons privados

	/** Toca um som que só este jogador ouve. */
	public static void playTo(ServerPlayer player, Holder<SoundEvent> sound, Vec3 at, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void playTo(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
		playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), at, volume, pitch);
	}

	// ------------------------------------------------------------------ cliente

	public static void send(ServerPlayer player, HorrorPayload payload) {
		if (ServerPlayNetworking.canSend(player, HorrorPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	// ------------------------------------------------------------------ identidade

	/**
	 * O nome que ele usa para chamar o jogador. Em mundos locais, pode ser o nome de usuário do
	 * computador (nunca é enviado para lugar nenhum; o servidor integrado roda na mesma máquina).
	 */
	public static String realName(ServerPlayer player) {
		if (InquilinoConfig.useComputerName && !player.level().getServer().isDedicatedServer()) {
			String os = System.getProperty("user.name");
			if (os != null && !os.isBlank()) {
				return os;
			}
		}
		return player.getGameProfile().name();
	}

	public static boolean portuguese(ServerPlayer player) {
		return player.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("pt");
	}
}

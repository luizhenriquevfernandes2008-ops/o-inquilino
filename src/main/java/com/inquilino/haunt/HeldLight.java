package com.inquilino.haunt;

import com.inquilino.InquilinoConfig;
import com.inquilino.entity.TenantEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Luz dinâmica: segurar uma tocha (em qualquer mão) ilumina ao seu redor.
 * Funciona colocando um bloco de luz invisível na altura da sua cabeça, que acompanha você.
 * Quando o Inquilino está perto, a chama da sua tocha treme e enfraquece.
 */
public final class HeldLight {
	private static final Map<UUID, Placed> PLACED = new HashMap<>();

	private record Placed(ResourceKey<Level> dimension, BlockPos pos, int level) {
	}

	private HeldLight() {
	}

	/** Quanta luz o item emite quando segurado (0 = nenhuma). */
	public static int emission(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return 15;
		}
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState().getLightEmission();
		}
		return 0;
	}

	public static int heldEmission(ServerPlayer p) {
		return Math.max(emission(p.getMainHandItem()), emission(p.getOffhandItem()));
	}

	public static boolean holdsLight(ServerPlayer p) {
		return heldEmission(p) >= 7;
	}

	public static void tick(MinecraftServer server, @Nullable HauntState state) {
		if (!InquilinoConfig.dynamicLight) {
			clearAll(server);
			return;
		}
		if (server.getTickCount() % 2 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			update(p, state);
		}
		// jogadores que saíram
		Iterator<Map.Entry<UUID, Placed>> it = PLACED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Placed> e = it.next();
			if (server.getPlayerList().getPlayer(e.getKey()) == null) {
				remove(server, e.getValue());
				it.remove();
			}
		}
	}

	private static void update(ServerPlayer p, @Nullable HauntState state) {
		int level = p.isSpectator() || !p.isAlive() ? 0 : heldEmission(p);
		if (level > 0) {
			level = flicker(p, level, state);
		}
		Placed old = PLACED.get(p.getUUID());
		BlockPos target = level > 0 ? findSpot(p.level(), p) : null;

		if (old != null && (target == null || !old.pos.equals(target) || old.dimension != p.level().dimension() || old.level != level)) {
			remove(p.level().getServer(), old);
			PLACED.remove(p.getUUID());
			old = null;
		}
		if (target != null && old == null && level > 0) {
			ServerLevel sl = p.level();
			sl.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level), Block.UPDATE_CLIENTS);
			PLACED.put(p.getUUID(), new Placed(sl.dimension(), target, level));
		}
	}

	/** Perto dele, a chama falha. */
	private static int flicker(ServerPlayer p, int level, @Nullable HauntState state) {
		if (state == null || state.phase < HauntState.PHASE_PRESENCE || state.phase >= HauntState.PHASE_ENDED) {
			return level;
		}
		List<TenantEntity> near = p.level().getEntitiesOfClass(TenantEntity.class, p.getBoundingBox().inflate(22));
		if (near.isEmpty()) {
			return level;
		}
		double dist = 99;
		boolean hunting = false;
		for (TenantEntity t : near) {
			dist = Math.min(dist, t.distanceTo(p));
			hunting |= t.getMode() == TenantEntity.Mode.HUNT;
		}
		var r = p.getRandom();
		if (hunting && dist < 12 && r.nextInt(3) == 0) {
			return 0; // apagou por um instante
		}
		int drop = (int) Math.max(0, (22 - dist) / 3);
		return Math.max(1, level - r.nextInt(drop + 1));
	}

	private static @Nullable BlockPos findSpot(ServerLevel level, ServerPlayer p) {
		BlockPos head = BlockPos.containing(p.getEyePosition());
		for (BlockPos pos : new BlockPos[]{head, p.blockPosition(), head.above()}) {
			BlockState s = level.getBlockState(pos);
			if (s.isAir() || s.is(Blocks.LIGHT)) {
				if (s.is(Blocks.LIGHT) && !isOurs(p, pos)) {
					continue; // bloco de luz do próprio jogador (modo criativo)
				}
				return pos.immutable();
			}
		}
		return null;
	}

	private static boolean isOurs(ServerPlayer p, BlockPos pos) {
		Placed placed = PLACED.get(p.getUUID());
		return placed != null && placed.pos.equals(pos);
	}

	private static void remove(MinecraftServer server, Placed placed) {
		ServerLevel level = server.getLevel(placed.dimension);
		if (level != null && level.isLoaded(placed.pos) && level.getBlockState(placed.pos).is(Blocks.LIGHT)) {
			level.setBlock(placed.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public static void clearAll(MinecraftServer server) {
		for (Placed placed : PLACED.values()) {
			remove(server, placed);
		}
		PLACED.clear();
	}

	public static void clear(ServerPlayer p) {
		Placed placed = PLACED.remove(p.getUUID());
		if (placed != null) {
			remove(p.level().getServer(), placed);
		}
	}
}

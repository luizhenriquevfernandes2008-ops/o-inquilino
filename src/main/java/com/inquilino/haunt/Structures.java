package com.inquilino.haunt;

import com.inquilino.ModRegistry;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Tudo que ele constrói. */
public final class Structures {
	private Structures() {
	}

	private static final int FLAGS = Block.UPDATE_ALL;

	// ------------------------------------------------------------------ placas e baús

	/** Coloca uma placa em pé, virada para {@code lookFrom}. */
	public static boolean placeSign(ServerLevel level, BlockPos pos, Vec3 lookFrom, SignText text) {
		if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
			return false;
		}
		double dx = lookFrom.x - (pos.getX() + 0.5);
		double dz = lookFrom.z - (pos.getZ() + 0.5);
		// yaw de quem olha a placa (do observador para a placa) + 180 = frente virada para o observador
		float yawFromViewer = (float) (Mth.atan2(-dz, -dx) * Mth.RAD_TO_DEG) - 90.0F;
		int rotation = RotationSegment.convertToSegment(yawFromViewer + 180.0F);
		level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), FLAGS);
		return writeSign(level, pos, text);
	}

	public static boolean placeWallSign(ServerLevel level, BlockPos pos, Direction facing, SignText text) {
		level.setBlock(pos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), FLAGS);
		return writeSign(level, pos, text);
	}

	private static boolean writeSign(ServerLevel level, BlockPos pos, SignText text) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof SignBlockEntity sign) {
			sign.setText(text.setColor(DyeColor.WHITE), true);
			sign.setText(text.setColor(DyeColor.WHITE), false);
			sign.setWaxed(true);
			sign.setChanged();
			BlockState state = level.getBlockState(pos);
			level.sendBlockUpdated(pos, state, state, FLAGS);
			return true;
		}
		return false;
	}

	public static boolean placeChest(ServerLevel level, BlockPos pos, Direction facing, ItemStack... items) {
		if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
			return false;
		}
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			int slot = 13;
			for (ItemStack item : items) {
				chest.setItem(slot++, item);
			}
			chest.setChanged();
			return true;
		}
		return false;
	}

	public static Direction facingTowards(BlockPos from, Vec3 to) {
		return Direction.getApproximateNearest(to.x - (from.getX() + 0.5), 0, to.z - (from.getZ() + 0.5));
	}

	// ------------------------------------------------------------------ pintura

	public static boolean placePainting(ServerLevel level, BlockPos near, ResourceKey<PaintingVariant> variantKey) {
		Optional<Holder.Reference<PaintingVariant>> variant = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).get(variantKey);
		if (variant.isEmpty()) {
			return false;
		}
		for (int i = 0; i < 40; i++) {
			BlockPos p = near.offset(level.getRandom().nextInt(9) - 4, level.getRandom().nextInt(3), level.getRandom().nextInt(9) - 4);
			if (!level.getBlockState(p).isAir()) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (!level.getBlockState(p.relative(d.getOpposite())).isSolid()) {
					continue;
				}
				Painting painting = new Painting(level, p, d, variant.get());
				if (painting.survives()) {
					level.addFreshEntity(painting);
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ o quarto

	/**
	 * Constrói o quarto enterrado. {@code column} tem apenas x/z válidos.
	 * Retorna a posição do canto do chão interno (o "centro" do final).
	 */
	public static BlockPos buildRoom(ServerLevel level, BlockPos column, String realName) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		int floorY = Math.max(level.getMinY() + 8, surface - 13);
		BlockPos o = new BlockPos(column.getX(), floorY, column.getZ());

		BlockState wall = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
		// casca 7x5x7 (interior 5x3x5)
		for (int x = -1; x <= 5; x++) {
			for (int y = -1; y <= 3; y++) {
				for (int z = -1; z <= 5; z++) {
					BlockPos p = o.offset(x, y, z);
					boolean shell = x == -1 || x == 5 || y == -1 || y == 3 || z == -1 || z == 5;
					level.setBlock(p, shell ? (y == -1 ? floor : wall) : Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		// poço com escada até a superfície (coluna x=2, z=0, apoiada na parede z=-1)
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
		for (int y = 0; y < surface - floorY; y++) {
			BlockPos p = o.offset(2, y, 0);
			if (y >= 3) {
				level.setBlock(p.north(), wall, FLAGS);
				level.setBlock(p.south(), wall, FLAGS);
				level.setBlock(p.east(), wall, FLAGS);
				level.setBlock(p.west(), wall, FLAGS);
			}
			level.setBlock(p, ladder, FLAGS);
		}
		// placa na superfície: "desça"
		BlockPos signPos = new BlockPos(o.getX() + 2, 0, o.getZ() + 2);
		signPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, signPos);
		placeSign(level, signPos, Vec3.atCenterOf(o.offset(2, surface - floorY, -4)), Lore.sign("desca", realName));

		// mobília
		BlockState bedFoot = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		level.setBlock(o.offset(0, 0, 3), bedFoot, FLAGS);
		level.setBlock(o.offset(0, 0, 4), bedFoot.setValue(BedBlock.PART, BedPart.HEAD), FLAGS);
		placeChest(level, o.offset(4, 0, 4), Direction.NORTH, Lore.diary(6, realName));
		level.setBlock(o.offset(4, 0, 0), Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
		placeWallSign(level, o.offset(2, 1, 4), Direction.NORTH, Lore.sign("bem_vindo", realName));
		level.setBlock(o.offset(0, 2, 0), Blocks.COBWEB.defaultBlockState(), FLAGS);
		level.setBlock(o.offset(4, 2, 2), Blocks.COBWEB.defaultBlockState(), FLAGS);
		level.setBlock(o.offset(1, 2, 4), Blocks.COBWEB.defaultBlockState(), FLAGS);
		Optional<Holder.Reference<PaintingVariant>> hall = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).get(ModRegistry.PAINTING_HALLWAY);
		hall.ifPresent(v -> {
			Painting painting = new Painting(level, o.offset(4, 1, 2), Direction.WEST, v);
			if (painting.survives()) {
				level.addFreshEntity(painting);
			}
		});
		return o;
	}

	public static boolean insideRoom(BlockPos origin, Vec3 pos) {
		return pos.x >= origin.getX() && pos.x < origin.getX() + 5
			&& pos.z >= origin.getZ() && pos.z < origin.getZ() + 5
			&& pos.y >= origin.getY() - 0.5 && pos.y < origin.getY() + 3;
	}

	// ------------------------------------------------------------------ palavra no chão

	private static final Map<Character, String[]> FONT = Map.ofEntries(
		Map.entry('A', new String[]{"###", "#.#", "###", "#.#", "#.#"}),
		Map.entry('E', new String[]{"###", "#..", "##.", "#..", "###"}),
		Map.entry('H', new String[]{"#.#", "#.#", "###", "#.#", "#.#"}),
		Map.entry('I', new String[]{"###", ".#.", ".#.", ".#.", "###"}),
		Map.entry('K', new String[]{"#.#", "##.", "#..", "##.", "#.#"}),
		Map.entry('L', new String[]{"#..", "#..", "#..", "#..", "###"}),
		Map.entry('M', new String[]{"#.#", "###", "###", "#.#", "#.#"}),
		Map.entry('N', new String[]{"##.", "#.#", "#.#", "#.#", "#.#"}),
		Map.entry('O', new String[]{"###", "#.#", "#.#", "#.#", "###"}),
		Map.entry('S', new String[]{"###", "#..", "###", "..#", "###"}),
		Map.entry('T', new String[]{"###", ".#.", ".#.", ".#.", ".#."}),
		Map.entry('U', new String[]{"#.#", "#.#", "#.#", "#.#", "###"}),
		Map.entry('V', new String[]{"#.#", "#.#", "#.#", "#.#", ".#."}),
		Map.entry('Y', new String[]{"#.#", "#.#", ".#.", ".#.", ".#."})
	);

	/** Escreve uma palavra gigante no chão, legível de onde o jogador está. */
	public static boolean writeGroundWord(ServerLevel level, ServerPlayer player, String word) {
		Vec3 look = WorldUtil.flatLook(player);
		Direction f = Direction.getApproximateNearest(look.x, 0, look.z);
		Direction right = f.getClockWise();
		int width = word.length() * 4 - 1;
		double dist = 16 + player.getRandom().nextInt(8);
		BlockPos base = BlockPos.containing(player.position().add(look.scale(dist)));
		// canto superior-esquerdo (mais longe, à esquerda)
		BlockPos start = base.relative(right, -width / 2).relative(f, 4);
		int placed = 0;
		for (int i = 0; i < word.length(); i++) {
			String[] glyph = FONT.get(word.charAt(i));
			if (glyph == null) {
				continue;
			}
			for (int row = 0; row < 5; row++) {
				for (int col = 0; col < 3; col++) {
					if (glyph[row].charAt(col) != '#') {
						continue;
					}
					BlockPos column = start.relative(right, i * 4 + col).relative(f, -row);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
					BlockPos p = new BlockPos(column.getX(), y, column.getZ());
					BlockState s = level.getBlockState(p);
					if (s.isSolid() && !s.hasBlockEntity() && Math.abs(y - player.getY()) < 12) {
						level.setBlock(p, Blocks.SOUL_SOIL.defaultBlockState(), FLAGS);
						placed++;
					}
				}
			}
		}
		return placed > 10;
	}

	// ------------------------------------------------------------------ árvore morta

	public static boolean killTree(ServerLevel level, ServerPlayer player) {
		for (int attempt = 0; attempt < 30; attempt++) {
			Vec3 p = WorldUtil.around(player, 18 + level.getRandom().nextInt(20), level.getRandom().nextInt(360));
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(p));
			if (!level.getBlockState(top.below()).is(BlockTags.LEAVES)) {
				continue;
			}
			BlockPos trunk = null;
			double best = Double.MAX_VALUE;
			for (BlockPos q : BlockPos.betweenClosed(top.offset(-3, -12, -3), top.offset(3, -1, 3))) {
				if (level.getBlockState(q).is(BlockTags.LOGS)) {
					double d = q.distSqr(top);
					if (d < best) {
						best = d;
						trunk = q.immutable();
					}
				}
			}
			if (trunk == null || WorldUtil.inView(player, Vec3.atCenterOf(trunk))) {
				continue;
			}
			int removed = 0;
			for (BlockPos q : BlockPos.betweenClosed(trunk.offset(-4, -3, -4), trunk.offset(4, 6, 4))) {
				if (level.getBlockState(q).is(BlockTags.LEAVES)) {
					level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					removed++;
				}
			}
			if (removed > 8) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ túnel 2x1

	public static @Nullable BlockPos digTunnel(ServerLevel level, ServerPlayer player) {
		Vec3 back = WorldUtil.flatLook(player).scale(-1);
		Direction dir = Direction.getApproximateNearest(back.x, 0, back.z);
		if (player.getRandom().nextBoolean()) {
			dir = player.getRandom().nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
		}
		BlockPos start = player.blockPosition().relative(dir, 2);
		int dug = 0;
		BlockPos last = start;
		for (int i = 0; i < 24; i++) {
			BlockPos p = start.relative(dir, i);
			for (int h = 0; h < 2; h++) {
				BlockPos q = p.above(h);
				BlockState s = level.getBlockState(q);
				if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.TUFF)) {
					level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					dug++;
				}
			}
			last = p;
		}
		if (dug < 12) {
			return null;
		}
		if (level.getBlockState(last.below()).isSolid()) {
			level.setBlock(last, Blocks.REDSTONE_TORCH.defaultBlockState(), FLAGS);
		}
		return last;
	}
}

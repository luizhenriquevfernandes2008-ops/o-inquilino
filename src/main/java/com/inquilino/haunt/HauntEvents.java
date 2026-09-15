package com.inquilino.haunt;

import com.inquilino.InquilinoConfig;
import com.inquilino.ModRegistry;
import com.inquilino.entity.TenantEntity;
import com.inquilino.mixin.MannequinAccessor;
import com.inquilino.net.HorrorPayload;
import com.inquilino.net.HorrorPayload.Effects;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Cada susto é um evento. O diretor escolhe qual acontece e quando. */
public final class HauntEvents {
	private HauntEvents() {
	}

	/** Um evento: fase mínima, peso de sorteio, recarga em segundos. */
	public record Event(String id, int minPhase, int weight, int cooldownSec, BiPredicate<ServerPlayer, HauntState> action) {
	}

	public static final Map<String, Event> ALL = new LinkedHashMap<>();

	static {
		// ---- fase 0: silêncio (curta: só o suficiente para você perceber que tem algo errado)
		add("passos", 0, 10, 45, HauntEvents::footsteps);
		add("tochas", 0, 3, 120, (p, s) -> torchesOut(p, s.phase >= 2 ? 6 : 2));
		add("porta", 0, 4, 90, HauntEvents::door);
		add("mineracao", 0, 6, 90, HauntEvents::mining);
		// ---- fase 1: presença
		add("observador", 1, 10, 60, HauntEvents::watcher);
		add("aproximacao", 1, 9, 90, HauntEvents::approach);
		add("batidas", 1, 6, 100, HauntEvents::knock);
		add("sussurro", 1, 5, 50, HauntEvents::whisper);
		add("respiracao", 1, 5, 70, HauntEvents::breathing);
		add("diario", 1, 8, 120, HauntEvents::diary);
		add("entrou", 1, 3, 400, HauntEvents::fakeJoin);
		add("chat", 1, 4, 60, HauntEvents::chat);
		add("hora", 1, 6, 99999, HauntEvents::realTime);
		add("pintura", 1, 2, 600, HauntEvents::painting);
		add("perseguidor", 1, 7, 70, HauntEvents::stalker);
		// ---- fase 2: intrusão
		add("atras", 2, 7, 90, HauntEvents::rightBehind);
		add("vidro", 2, 7, 120, HauntEvents::window);
		add("trancado", 2, 6, 240, HauntEvents::lockedIn);
		add("placa", 2, 6, 90, HauntEvents::sign);
		add("mimica", 2, 5, 90, HauntEvents::mimic);
		add("glitch", 2, 5, 60, HauntEvents::glitch);
		add("titulo", 2, 3, 200, HauntEvents::windowTitle);
		add("subliminar", 2, 4, 80, HauntEvents::subliminal);
		add("olhe", 2, 5, 150, HauntEvents::lookAtMe);
		add("doppelganger", 2, 4, 300, HauntEvents::doppelganger);
		add("palavra", 2, 3, 400, HauntEvents::groundWord);
		add("arvore", 2, 3, 300, HauntEvents::deadTree);
		add("tunel", 2, 3, 300, HauntEvents::tunnel);
		add("bilhete", 2, 4, 180, HauntEvents::note);
		add("disco", 2, 2, 600, HauntEvents::disc11);
		add("em_casa", 2, 3, 400, HauntEvents::atHome);
		add("nome", 2, 6, 99999, HauntEvents::computerName);
		add("cacada", 2, 8, 100, HauntEvents::hunt);
		// ---- fase 3: caça
		add("apagao", 3, 5, 150, HauntEvents::blackout);
		add("desconexao", 3, 3, 600, HauntEvents::fakeDisconnect);
	}

	private static void add(String id, int minPhase, int weight, int cooldown, BiPredicate<ServerPlayer, HauntState> action) {
		ALL.put(id, new Event(id, minPhase, weight, cooldown, action));
	}

	// ================================================================== sons

	/** Passos se aproximando por trás. Param se você se virar. */
	static boolean footsteps(ServerPlayer p, HauntState s) {
		RandomSource r = p.getRandom();
		double angle = 180 + (r.nextDouble() - 0.5) * 50;
		int steps = 5 + r.nextInt(4);
		for (int i = 0; i < steps; i++) {
			final int step = i;
			Scheduler.later(1 + i * 8, () -> {
				if (p.isRemoved()) {
					return;
				}
				double dist = 13.0 - step * (10.0 / steps);
				Vec3 at = WorldUtil.around(p, dist, angle).add(0, 0.1, 0);
				if (WorldUtil.viewDot(p, at) > 0.55) {
					return; // você olhou. ele parou.
				}
				BlockPos under = BlockPos.containing(at).below();
				BlockState floor = p.level().getBlockState(under);
				if (floor.isAir()) {
					floor = p.level().getBlockState(p.blockPosition().below());
				}
				WorldUtil.playTo(p, floor.getSoundType().getStepSound(), at, 0.55F, 0.85F);
			});
		}
		return true;
	}

	static boolean whisper(ServerPlayer p, HauntState s) {
		Vec3 ear = p.getEyePosition().add(WorldUtil.flatLook(p).scale(-1.2)).add(0, -0.2, 0);
		WorldUtil.playTo(p, ModRegistry.WHISPER, ear, 0.8F, 0.75F + p.getRandom().nextFloat() * 0.2F);
		return true;
	}

	static boolean breathing(ServerPlayer p, HauntState s) {
		for (int i = 0; i < 3; i++) {
			Scheduler.later(1 + i * 34, () -> {
				Vec3 neck = p.getEyePosition().add(WorldUtil.flatLook(p).scale(-0.8));
				WorldUtil.playTo(p, ModRegistry.BREATH, neck, 0.7F, 0.55F);
			});
		}
		return true;
	}

	/** Três batidas numa porta perto de você. Depois mais três, mais fortes. */
	static boolean knock(ServerPlayer p, HauntState s) {
		BlockPos door = findNear(p, 20, st -> st.is(BlockTags.WOODEN_DOORS));
		if (door == null) {
			return false;
		}
		Vec3 at = Vec3.atCenterOf(door);
		int[] times = {1, 7, 13, 60, 65, 70};
		for (int i = 0; i < times.length; i++) {
			float vol = i < 3 ? 0.6F : 1.0F;
			Scheduler.later(times[i], () -> WorldUtil.playTo(p, ModRegistry.KNOCK, at, vol, 0.9F + p.getRandom().nextFloat() * 0.1F));
		}
		return true;
	}

	static boolean disc11(ServerPlayer p, HauntState s) {
		Vec3 far = WorldUtil.around(p, 28, p.getRandom().nextInt(360));
		WorldUtil.playTo(p, SoundEvents.MUSIC_DISC_11, far, 1.0F, 1.0F);
		return true;
	}

	// ================================================================== ambiente

	static boolean torchesOut(ServerPlayer p, int max) {
		ServerLevel level = p.level();
		List<BlockPos> found = new ArrayList<>();
		BlockPos c = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-20, -6, -20), c.offset(20, 6, 20))) {
			if (TenantEntity.isTorch(level.getBlockState(q)) && !WorldUtil.inView(p, Vec3.atCenterOf(q))) {
				found.add(q.immutable());
				if (found.size() >= max) {
					break;
				}
			}
		}
		if (found.isEmpty()) {
			return false;
		}
		for (int i = 0; i < found.size(); i++) {
			BlockPos q = found.get(i);
			Scheduler.later(1 + i * 14, () -> {
				if (TenantEntity.isTorch(level.getBlockState(q))) {
					level.removeBlock(q, false);
					WorldUtil.playTo(p, SoundEvents.FIRE_EXTINGUISH, Vec3.atCenterOf(q), 0.35F, 0.7F);
				}
			});
		}
		return true;
	}

	static boolean door(ServerPlayer p, HauntState s) {
		BlockPos door = findNear(p, 16, st -> st.is(BlockTags.WOODEN_DOORS) && st.getBlock() instanceof DoorBlock d && !d.isOpen(st));
		if (door == null) {
			return false;
		}
		BlockState st = p.level().getBlockState(door);
		if (st.getBlock() instanceof DoorBlock d) {
			d.setOpen(null, p.level(), st, door, true);
			return true;
		}
		return false;
	}

	static boolean painting(ServerPlayer p, HauntState s) {
		// perto da sua cama; se não houver, em algum lugar atrás de você
		BlockPos near = bedOf(p);
		if (near == null || near.distSqr(p.blockPosition()) > 40 * 40) {
			near = WorldUtil.hiddenSpot(p, 3, 6, true);
		}
		return near != null && Structures.placePainting(p.level(), near, ModRegistry.PAINTING_PORTRAIT);
	}

	static boolean groundWord(ServerPlayer p, HauntState s) {
		if (!WorldUtil.underSky(p) || p.level().dimension() != Level.OVERWORLD) {
			return false;
		}
		String[] words = WorldUtil.portuguese(p) ? new String[]{"SAIA", "MEU", "OLHE", "VOLTE"} : new String[]{"LEAVE", "MINE", "LOOK", "HOME"};
		String word = words[p.getRandom().nextInt(words.length)];
		if (Structures.writeGroundWord(p.level(), p, word)) {
			Scheduler.later(40, () -> WorldUtil.playTo(p, ModRegistry.DRONE, p.position().add(0, 8, 0), 0.8F, 0.5F));
			return true;
		}
		return false;
	}

	static boolean deadTree(ServerPlayer p, HauntState s) {
		return WorldUtil.underSky(p) && Structures.killTree(p.level(), p);
	}

	static boolean tunnel(ServerPlayer p, HauntState s) {
		if (WorldUtil.underSky(p) || p.getY() > 60) {
			return false;
		}
		BlockPos end = Structures.digTunnel(p.level(), p);
		if (end != null) {
			WorldUtil.playTo(p, SoundEvents.STONE_BREAK, Vec3.atCenterOf(end), 0.6F, 0.5F);
			return true;
		}
		return false;
	}

	// ================================================================== presença

	public static @Nullable TenantEntity spawnTenant(ServerPlayer p, BlockPos at, TenantEntity.Mode mode, int life) {
		ServerLevel level = p.level();
		TenantEntity t = ModRegistry.TENANT.create(level, EntitySpawnReason.EVENT);
		if (t == null) {
			return null;
		}
		t.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		t.setup(p, mode, life);
		level.addFreshEntity(t);
		HauntDirector.notePresence(p);
		return t;
	}

	static boolean watcher(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.peripheralSpot(p, 32, 58);
		if (at == null) {
			return false;
		}
		// a partir da fase 2, metade das vezes ele não some quando você olha
		TenantEntity.Mode mode = s.phase >= 2 && p.getRandom().nextBoolean() ? TenantEntity.Mode.APPROACH : TenantEntity.Mode.WATCH;
		return spawnTenant(p, at, mode, 20 * 120) != null;
	}

	/** Ele não some. Cada vez que você desvia o olhar, está mais perto. */
	static boolean approach(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.peripheralSpot(p, 24, 40);
		if (at == null) {
			at = WorldUtil.hiddenSpot(p, 20, 30, false);
		}
		if (at == null) {
			return false;
		}
		TenantEntity t = spawnTenant(p, at, TenantEntity.Mode.APPROACH, 20 * 150);
		if (t != null && s.phase < 2) {
			t.maxSteps(2); // na fase 1 é só um aviso
		}
		return t != null;
	}

	/** Bem atrás de você. Se você virar, ele está lá. */
	static boolean rightBehind(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.hiddenSpot(p, 1.8, 2.8, true);
		if (at == null) {
			return false;
		}
		TenantEntity t = spawnTenant(p, at, TenantEntity.Mode.LURK, 20 * 5);
		if (t == null) {
			return false;
		}
		t.jumpOnSight();
		WorldUtil.playTo(p, ModRegistry.BREATH, t.getEyePosition(), 1.0F, 0.5F);
		Scheduler.later(40, () -> WorldUtil.playTo(p, ModRegistry.BREATH, t.getEyePosition(), 1.0F, 0.45F));
		Scheduler.later(96, () -> {
			if (!t.isRemoved()) {
				p.sendSystemMessage(Lore.tenantSays("chat.inquilino.almost", WorldUtil.realName(p)));
			}
		});
		return true;
	}

	/** Um rosto do lado de fora da janela. */
	static boolean window(ServerPlayer p, HauntState s) {
		if (WorldUtil.underSky(p)) {
			return false;
		}
		ServerLevel level = p.level();
		BlockPos c = p.blockPosition();
		for (BlockPos g : BlockPos.betweenClosed(c.offset(-10, -1, -10), c.offset(10, 3, 10))) {
			BlockState gs = level.getBlockState(g);
			if (!isGlass(gs)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos inside = g.relative(d.getOpposite());
				if (!level.getBlockState(inside).isAir()) {
					continue;
				}
				// o rosto na altura do vidro: pés 1 ou 2 blocos abaixo
				BlockPos stand = null;
				for (int down = 1; down <= 2; down++) {
					BlockPos candidate = g.relative(d).below(down);
					if (WorldUtil.canStand(level, candidate) && level.canSeeSky(candidate.above(2))) {
						stand = candidate;
						break;
					}
				}
				if (stand == null) {
					continue;
				}
				double dist = Math.sqrt(stand.distSqr(c));
				if (dist < 3 || dist > 11) {
					continue;
				}
				if (WorldUtil.inView(p, Vec3.atCenterOf(stand.above()))) {
					continue;
				}
				TenantEntity t = spawnTenant(p, stand.immutable(), TenantEntity.Mode.LURK, 20 * 25);
				if (t == null) {
					return false;
				}
				t.vanishAfterSeen(30);
				BlockPos glass = g.immutable();
				for (int i = 0; i < 3; i++) {
					Scheduler.later(10 + i * 9, () -> WorldUtil.playTo(p, SoundEvents.GLASS_HIT, Vec3.atCenterOf(glass), 1.0F, 0.6F));
				}
				return true;
			}
		}
		return false;
	}

	private static boolean isGlass(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	/** As portas trancam. Você está preso com ele. */
	static boolean lockedIn(ServerPlayer p, HauntState s) {
		if (WorldUtil.underSky(p) || (!p.level().isDarkOutside() && s.phase < 3)) {
			return false;
		}
		BlockPos door = findNear(p, 12, st -> st.is(BlockTags.WOODEN_DOORS));
		if (door == null) {
			return false;
		}
		BlockState st = p.level().getBlockState(door);
		if (st.getBlock() instanceof DoorBlock d && d.isOpen(st)) {
			d.setOpen(null, p.level(), st, door, false);
		}
		HauntDirector.lockDoors(p, 20 * 45);
		Vec3 at = Vec3.atCenterOf(door);
		WorldUtil.playTo(p, SoundEvents.IRON_DOOR_CLOSE, at, 1.0F, 0.5F);
		p.sendSystemMessage(Component.translatable("actionbar.inquilino.locked").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
		int[] knocks = {30, 36, 42, 90, 94, 98, 102, 106, 170, 172, 174, 176, 178, 180};
		for (int k : knocks) {
			Scheduler.later(k, () -> WorldUtil.playTo(p, ModRegistry.KNOCK, at, k > 160 ? 1.4F : 0.9F, 0.8F + p.getRandom().nextFloat() * 0.2F));
		}
		Scheduler.later(200, () -> torchesOut(p, 12));
		Scheduler.later(300, () -> {
			BlockPos behind = WorldUtil.hiddenSpot(p, 2.5, 4.5, true);
			if (behind != null) {
				TenantEntity t = spawnTenant(p, behind, TenantEntity.Mode.LURK, 20 * 12);
				if (t != null) {
					t.jumpOnSight();
					WorldUtil.playTo(p, ModRegistry.WHISPER, t.getEyePosition(), 1.0F, 0.6F);
				}
			}
		});
		return true;
	}

	/** Alguém minerando do outro lado da parede. */
	static boolean mining(ServerPlayer p, HauntState s) {
		if (WorldUtil.underSky(p)) {
			return false;
		}
		Vec3 at = WorldUtil.behind(p, 9 + p.getRandom().nextInt(5), p.getRandom()).add(0, 1, 0);
		int hits = 5 + p.getRandom().nextInt(5);
		for (int i = 0; i < hits; i++) {
			Scheduler.later(1 + i * 6, () -> WorldUtil.playTo(p, SoundEvents.STONE_HIT, at, 0.8F, 0.8F));
		}
		Scheduler.later(hits * 6 + 4, () -> WorldUtil.playTo(p, SoundEvents.STONE_BREAK, at, 1.0F, 0.8F));
		if (s.phase >= 1) {
			Scheduler.later(hits * 6 + 30, () -> footsteps(p, s));
		}
		return true;
	}

	static boolean stalker(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.hiddenSpot(p, 7, 12, true);
		if (at == null) {
			return false;
		}
		TenantEntity t = spawnTenant(p, at, TenantEntity.Mode.STALK, 20 * 60);
		if (t != null) {
			Scheduler.later(30, () -> WorldUtil.playTo(p, ModRegistry.BREATH, t.position().add(0, 1.8, 0), 0.5F, 0.6F));
		}
		return t != null;
	}

	/** Algo vira a sua cabeça. */
	static boolean lookAtMe(ServerPlayer p, HauntState s) {
		for (int i = 0; i < 12; i++) {
			BlockPos at = WorldUtil.hiddenSpot(p, 9, 14, true);
			if (at == null) {
				continue;
			}
			Vec3 eye = Vec3.atBottomCenterOf(at).add(0, 2.0, 0);
			if (!WorldUtil.hasLineOfSight(p, eye)) {
				continue;
			}
			TenantEntity t = spawnTenant(p, at, TenantEntity.Mode.LURK, 20 * 8);
			if (t == null) {
				return false;
			}
			WorldUtil.playTo(p, ModRegistry.WHISPER, p.getEyePosition(), 1.0F, 0.6F);
			WorldUtil.send(p, new HorrorPayload(Effects.LOOK_AT, "", "", 36, eye.x, eye.y, eye.z));
			Scheduler.later(52, () -> {
				WorldUtil.playTo(p, ModRegistry.STING, t.position(), 1.0F, 0.7F);
				t.vanish(p, true);
			});
			return true;
		}
		return false;
	}

	/** Uma cópia sua, parada, olhando para você. */
	static boolean doppelganger(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.peripheralSpot(p, 16, 28);
		if (at == null) {
			return false;
		}
		ServerLevel level = p.level();
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		MannequinAccessor acc = (MannequinAccessor) m;
		acc.inquilino$setProfile(ResolvableProfile.createResolved(p.getGameProfile()));
		acc.inquilino$setImmovable(true);
		acc.inquilino$setHideDescription(true);
		m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
		float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0F;
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.addTag(HauntDirector.DOPPEL_TAG);
		m.setInvulnerable(true);
		level.addFreshEntity(m);
		HauntDirector.trackDoppel(p, m);
		return true;
	}

	/** Quando você volta para casa, ele está do lado da sua cama. */
	static boolean atHome(ServerPlayer p, HauntState s) {
		BlockPos bed = bedOf(p);
		if (bed == null || bed.distSqr(p.blockPosition()) < 48 * 48) {
			return false;
		}
		HauntDirector.armHomeVisit(p, bed);
		return true;
	}

	static boolean hunt(ServerPlayer p, HauntState s) {
		boolean dark = (p.level().isDarkOutside() && WorldUtil.underSky(p)) || p.level().getMaxLocalRawBrightness(p.blockPosition()) < 7
			|| (s.phase >= 3 && !WorldUtil.underSky(p));
		if (!dark) {
			return false;
		}
		BlockPos at = WorldUtil.hiddenSpot(p, 22, 30, true);
		if (at == null) {
			at = WorldUtil.hiddenSpot(p, 14, 20, false);
		}
		if (at == null) {
			return false;
		}
		final BlockPos spawn = at;
		WorldUtil.playTo(p, ModRegistry.VOICES, Vec3.atCenterOf(spawn), 1.5F, 0.45F);
		WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", 16));
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 10, 0, false, false));
		Scheduler.later(30, () -> {
			if (!p.isRemoved()) {
				spawnTenant(p, spawn, TenantEntity.Mode.HUNT, 20 * 30);
				HauntDirector.startHunt(p, 20 * 30);
			}
		});
		return true;
	}

	static boolean blackout(ServerPlayer p, HauntState s) {
		torchesOut(p, 12);
		WorldUtil.send(p, HorrorPayload.of(Effects.BLACKOUT, 80));
		WorldUtil.playTo(p, ModRegistry.DRONE, p.getEyePosition(), 1.0F, 0.5F);
		Scheduler.later(70, () -> {
			BlockPos at = WorldUtil.peripheralSpot(p, 8, 14);
			if (at != null) {
				spawnTenant(p, at, TenantEntity.Mode.WATCH, 20 * 20);
			}
		});
		return true;
	}

	static boolean fakeDisconnect(ServerPlayer p, HauntState s) {
		WorldUtil.send(p, HorrorPayload.of(Effects.FAKE_DISCONNECT, "disconnect.inquilino.reason." + p.getRandom().nextInt(3), p.getGameProfile().name(), 0));
		// enquanto você lê a tela, algo respira bem atrás de você
		for (int i = 0; i < 3; i++) {
			Scheduler.later(60 + i * 45, () -> WorldUtil.playTo(p, ModRegistry.BREATH, p.getEyePosition().add(WorldUtil.flatLook(p).scale(-0.7)), 0.9F, 0.5F));
		}
		return true;
	}

	// ================================================================== objetos

	static boolean diary(ServerPlayer p, HauntState s) {
		int max = s.phase == 1 ? 2 : s.phase == 2 ? 4 : 5;
		if (s.nextDiary > max || s.nextDiary > 5) {
			return false;
		}
		BlockPos at = WorldUtil.hiddenSpot(p, 3, 6, true);
		if (at == null) {
			return false;
		}
		int volume = s.nextDiary;
		ItemStack book = Lore.diary(volume, WorldUtil.realName(p));
		if (!Structures.placeChest(p.level(), at, Structures.facingTowards(at, p.position()), book)) {
			return false;
		}
		WorldUtil.playTo(p, SoundEvents.CHEST_CLOSE, Vec3.atCenterOf(at), 0.5F, 0.8F);
		s.nextDiary++;
		s.setDirty();
		if (volume == 5) {
			HauntDirector.revealRoom(p, s);
		}
		return true;
	}

	static boolean sign(ServerPlayer p, HauntState s) {
		BlockPos at = WorldUtil.hiddenSpot(p, 3, 7, true);
		if (at == null) {
			return false;
		}
		String id = Lore.SIGNS[p.getRandom().nextInt(Lore.SIGNS.length)];
		Object arg = switch (id) {
			case "hora" -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			default -> WorldUtil.realName(p);
		};
		return Structures.placeSign(p.level(), at, p.getEyePosition(), Lore.sign(id, arg));
	}

	static boolean note(ServerPlayer p, HauntState s) {
		ItemStack paper = Lore.note(p.getRandom(), WorldUtil.realName(p));
		if (!p.getInventory().add(paper)) {
			p.drop(paper, false);
		}
		WorldUtil.playTo(p, SoundEvents.BOOK_PAGE_TURN, p.position(), 0.6F, 0.7F);
		return true;
	}

	// ================================================================== chat / meta

	static boolean fakeJoin(ServerPlayer p, HauntState s) {
		var list = p.level().getServer().getPlayerList();
		list.broadcastSystemMessage(Lore.joined(Lore.NAME), false);
		int stay = 20 * (25 + p.getRandom().nextInt(50));
		Scheduler.later(stay / 2, () -> p.sendSystemMessage(Lore.tenantSays(Lore.pick(s.phase >= 2 ? "p2" : "p1", s.phase >= 2 ? Lore.CHAT_P2 : Lore.CHAT_P1, p.getRandom()), WorldUtil.realName(p))));
		Scheduler.later(stay, () -> list.broadcastSystemMessage(Lore.left(Lore.NAME), false));
		return true;
	}

	static boolean chat(ServerPlayer p, HauntState s) {
		String key = switch (s.phase) {
			case 1 -> Lore.pick("p1", Lore.CHAT_P1, p.getRandom());
			case 2 -> Lore.pick("p2", Lore.CHAT_P2, p.getRandom());
			default -> Lore.pick("p3", Lore.CHAT_P3, p.getRandom());
		};
		p.sendSystemMessage(Lore.tenantSays(key, WorldUtil.realName(p)));
		return true;
	}

	/** Você "diz" coisas que nunca digitou. */
	static boolean mimic(ServerPlayer p, HauntState s) {
		String learned = HauntDirector.learnedMessage(p);
		if (learned != null && p.getRandom().nextBoolean()) {
			// ele aprendeu com o que você escreveu
			p.sendSystemMessage(Lore.chat(Lore.NAME, Component.literal(learned)));
			return true;
		}
		String key = Lore.pick("mimic", Lore.CHAT_MIMIC, p.getRandom());
		p.sendSystemMessage(Lore.chat(p.getGameProfile().name(), Component.translatable(key)));
		return true;
	}

	/** Se for madrugada de verdade, ele percebe. */
	static boolean realTime(ServerPlayer p, HauntState s) {
		LocalTime now = LocalTime.now();
		if (now.getHour() >= 5 && now.getHour() < 23) {
			return false;
		}
		if (!HauntDirector.sessionOnce(p, "hora")) {
			return false;
		}
		p.sendSystemMessage(Lore.tenantSays("chat.inquilino.time", now.format(DateTimeFormatter.ofPattern("HH:mm")), WorldUtil.realName(p)));
		return true;
	}

	/** O nome de verdade. */
	static boolean computerName(ServerPlayer p, HauntState s) {
		String real = WorldUtil.realName(p);
		if (real.equalsIgnoreCase(p.getGameProfile().name()) || !s.mark("nome_revelado")) {
			return false;
		}
		p.sendSystemMessage(Lore.tenantSays("chat.inquilino.name.0", real));
		Scheduler.later(70, () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.name.1", real)));
		Scheduler.later(80, () -> WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", 10)));
		return true;
	}

	static boolean glitch(ServerPlayer p, HauntState s) {
		int ticks = 12 + p.getRandom().nextInt(20);
		WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", ticks));
		WorldUtil.playTo(p, ModRegistry.STATIC, p.getEyePosition(), 0.7F, 1.0F);
		if (p.getRandom().nextInt(3) == 0) {
			Scheduler.later(ticks / 2, () -> WorldUtil.send(p, HorrorPayload.of(Effects.FACE, 2)));
		}
		return true;
	}

	static boolean subliminal(ServerPlayer p, HauntState s) {
		String key = "subliminal.inquilino." + p.getRandom().nextInt(Lore.SUBLIMINAL);
		WorldUtil.send(p, HorrorPayload.of(Effects.TEXT, key, WorldUtil.realName(p), InquilinoConfig.flashes ? 3 : 30));
		return true;
	}

	static boolean windowTitle(ServerPlayer p, HauntState s) {
		String key = "window.inquilino." + p.getRandom().nextInt(Lore.WINDOW);
		WorldUtil.send(p, HorrorPayload.of(Effects.WINDOW_TITLE, key, WorldUtil.realName(p), 20 * 6));
		return true;
	}

	// ================================================================== util

	interface StatePredicate {
		boolean test(BlockState state);
	}

	static @Nullable BlockPos findNear(ServerPlayer p, int radius, StatePredicate predicate) {
		BlockPos c = p.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-radius, -4, -radius), c.offset(radius, 4, radius))) {
			if (predicate.test(p.level().getBlockState(q))) {
				double d = q.distSqr(c);
				if (d < bestDist && d > 4) {
					bestDist = d;
					best = q.immutable();
				}
			}
		}
		return best;
	}

	public static @Nullable BlockPos bedOf(ServerPlayer p) {
		ServerPlayer.RespawnConfig config = p.getRespawnConfig();
		if (config == null || config.respawnData().dimension() != p.level().dimension()) {
			return null;
		}
		BlockPos pos = config.respawnData().pos();
		return p.level().getBlockState(pos).is(BlockTags.BEDS) ? pos : null;
	}
}

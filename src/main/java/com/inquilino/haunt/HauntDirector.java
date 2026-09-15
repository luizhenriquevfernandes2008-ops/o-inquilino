package com.inquilino.haunt;

import com.inquilino.InquilinoConfig;
import com.inquilino.InquilinoMod;
import com.inquilino.ModRegistry;
import com.inquilino.entity.TenantEntity;
import com.inquilino.net.HorrorPayload;
import com.inquilino.net.HorrorPayload.Effects;
import com.inquilino.net.SignalPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * O diretor da assombração: controla as fases, o ritmo dos sustos, o quarto e o final.
 */
public final class HauntDirector {
	public static final String DOPPEL_TAG = "inquilino_copia";

	/** Ticks para cada fase (antes do multiplicador de ritmo). */
	private static final long[] PHASE_AT = {0L, 20L * 60, 20L * 60 * 5, 20L * 60 * 12};

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final List<Doppel> DOPPELS = new ArrayList<>();

	private HauntDirector() {
	}

	/** Estado de sessão por jogador (não persiste). */
	private static final class Track {
		int nextEvent = 20 * 20;
		int nextAmbient = 20 * 12;
		int nextVoice = 20 * 70;
		int huntTicks;
		long lockedUntil;
		long lastPresence;
		String lastEvent = "";
		@Nullable Vec3 lastPos;
		int stillTicks;
		final Deque<String> learned = new ArrayDeque<>();
		final Map<String, Long> lastFired = new HashMap<>();
		final Set<String> once = new HashSet<>();
		@Nullable BlockPos homeVisit;
		@Nullable TenantEntity endingTenant;
		long lastSleepSign = -99999;
	}

	private record Doppel(UUID player, Mannequin entity, long bornTick, int[] seen) {
	}

	private static Track track(ServerPlayer p) {
		return TRACKS.computeIfAbsent(p.getUUID(), u -> new Track());
	}

	// ================================================================== init

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HauntDirector::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(HeldLight::clearAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Scheduler.clear();
			TRACKS.clear();
			DOPPELS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HeldLight.clear(handler.getPlayer());
			TRACKS.remove(handler.getPlayer().getUUID());
		});
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> onPlayerChat(sender, message.signedContent()));
		EntitySleepEvents.START_SLEEPING.register((entity, bedPos) -> {
			if (entity instanceof ServerPlayer p) {
				onSleep(p);
			}
		});
		// portas trancadas
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level.getBlockState(hit.getBlockPos()).getBlock() instanceof DoorBlock) {
				Track t = TRACKS.get(sp.getUUID());
				if (t != null && t.lockedUntil > sp.level().getServer().getTickCount()) {
					sp.sendSystemMessage(Component.translatable("actionbar.inquilino.door_stuck").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
					WorldUtil.playTo(sp, SoundEvents.WOODEN_DOOR_CLOSE, Vec3.atCenterOf(hit.getBlockPos()), 0.8F, 0.5F);
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		// cópias esquecidas em chunks salvos somem ao carregar
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(DOPPEL_TAG) && DOPPELS.stream().noneMatch(d -> d.entity == entity)) {
				entity.discard();
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, bedPos) -> {
			if (entity instanceof ServerPlayer p) {
				onWake(p, bedPos);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SignalPayload.TYPE, (payload, ctx) -> onSignal(ctx.player(), payload.signal()));
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		Scheduler.tick();
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isSpectator() && p.isAlive()) {
				players.add(p);
			}
		}
		tickDoppels(server);
		HeldLight.tick(server, players.isEmpty() ? null : HauntState.get(server));
		if (players.isEmpty()) {
			return;
		}
		HauntState st = HauntState.get(server);
		if (st.ticks == 0 && InquilinoConfig.remembers() && st.mark("lembrado")) {
			// ele já conhece você de outro mundo
			st.setPhase(HauntState.PHASE_PRESENCE);
			String name = InquilinoConfig.rememberedName();
			Scheduler.later(20 * 50, () -> {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					p.sendSystemMessage(Lore.tenantSays("chat.inquilino.remember", name.isEmpty() ? WorldUtil.realName(p) : name));
				}
			});
		}
		st.ticks++;
		if (st.ticks % 200 == 0) {
			st.setDirty();
		}
		updatePhase(server, st);

		for (ServerPlayer p : players) {
			tickPlayer(p, st);
		}
	}

	private static void updatePhase(MinecraftServer server, HauntState st) {
		if (st.phase >= HauntState.PHASE_HUNT) {
			return;
		}
		int target = st.phase;
		for (int i = PHASE_AT.length - 1; i > st.phase; i--) {
			if (st.ticks >= (long) (PHASE_AT[i] / InquilinoConfig.pace)) {
				target = i;
				break;
			}
		}
		if (target != st.phase) {
			setPhase(server, st, target);
		}
	}

	public static void setPhase(MinecraftServer server, HauntState st, int phase) {
		int old = st.phase;
		st.setPhase(phase);
		InquilinoMod.LOGGER.info("[Inquilino] fase {} -> {}", old, phase);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			WorldUtil.send(p, HorrorPayload.of(Effects.PHASE, phase));
			if (phase == HauntState.PHASE_PRESENCE && old < phase) {
				// o primeiro encontro não demora
				Scheduler.later(20 * 12, () -> fireById(p, "observador"));
			}
			if (phase == HauntState.PHASE_INTRUSION && old < phase) {
				Scheduler.later(20 * 25, () -> fireById(p, "aproximacao"));
				WorldUtil.send(p, HorrorPayload.of(Effects.WRITE_FILE, "leia", WorldUtil.realName(p), 0));
				Scheduler.later(20 * 40, () -> {
					p.sendSystemMessage(Lore.tenantSays("chat.inquilino.files", WorldUtil.realName(p)));
					WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", 10));
				});
			}
			if (phase == HauntState.PHASE_HUNT && old < phase) {
				WorldUtil.send(p, HorrorPayload.of(Effects.WINDOW_TITLE, "window.inquilino.hunt", WorldUtil.realName(p), 20 * 8));
				WorldUtil.playTo(p, ModRegistry.DRONE, p.getEyePosition(), 1.0F, 0.4F);
			}
		}
	}

	private static void tickPlayer(ServerPlayer p, HauntState st) {
		Track t = track(p);
		long now = p.level().getServer().getTickCount();

		if (st.phase >= HauntState.PHASE_ENDED) {
			if (--t.nextAmbient <= 0) {
				t.nextAmbient = scaled(20 * 150, 20 * 300, p.getRandom());
				ambient(p, st);
			}
			return;
		}

		if (t.huntTicks > 0) {
			t.huntTicks--;
		}
		// parado por muito tempo?
		if (t.lastPos != null && t.lastPos.distanceToSqr(p.position()) < 0.01) {
			t.stillTicks++;
		} else {
			t.stillTicks = 0;
		}
		t.lastPos = p.position();

		if (now % 20 == 0) {
			checkRoom(p, st, t);
			checkHomeVisit(p, t);
		}
		if (st.has("final_iniciado")) {
			return;
		}

		if (--t.nextAmbient <= 0) {
			t.nextAmbient = switch (st.phase) {
				case 0 -> scaled(20 * 15, 20 * 30, p.getRandom());
				case 1 -> scaled(20 * 10, 20 * 22, p.getRandom());
				case 2 -> scaled(20 * 8, 20 * 18, p.getRandom());
				default -> scaled(20 * 6, 20 * 14, p.getRandom());
			};
			ambient(p, st);
		}
		if (st.phase >= HauntState.PHASE_PRESENCE && --t.nextVoice <= 0) {
			t.nextVoice = switch (st.phase) {
				case 1 -> scaled(20 * 35, 20 * 70, p.getRandom());
				case 2 -> scaled(20 * 25, 20 * 50, p.getRandom());
				default -> scaled(20 * 18, 20 * 35, p.getRandom());
			};
			speak(p, st, t);
		}
		if (--t.nextEvent <= 0 && t.huntTicks <= 0) {
			t.nextEvent = switch (st.phase) {
				case 0 -> scaled(20 * 25, 20 * 45, p.getRandom());
				case 1 -> scaled(20 * 18, 20 * 35, p.getRandom());
				case 2 -> scaled(20 * 12, 20 * 26, p.getRandom());
				default -> scaled(20 * 9, 20 * 20, p.getRandom());
			};
			// ele nunca fica longe por muito tempo
			boolean absent = st.phase >= HauntState.PHASE_INTRUSION && now - t.lastPresence > 20L * 75;
			if (absent && fireById(p, pickOne(p.getRandom(), "aproximacao", "perseguidor", "observador", "atras"))) {
				t.lastFired.put("presenca", now);
			} else {
				runRandomEvent(p, st, t, now);
			}
		}
	}

	private static int scaled(int min, int max, RandomSource r) {
		return Math.max(20, (int) ((min + r.nextInt(max - min + 1)) / InquilinoConfig.pace));
	}

	private static void runRandomEvent(ServerPlayer p, HauntState st, Track t, long now) {
		List<HauntEvents.Event> pool = new ArrayList<>();
		int total = 0;
		for (HauntEvents.Event e : HauntEvents.ALL.values()) {
			if (e.minPhase() > st.phase || e.id().equals(t.lastEvent)) {
				continue;
			}
			Long last = t.lastFired.get(e.id());
			if (last != null && now - last < e.cooldownSec() * 20L / InquilinoConfig.pace) {
				continue;
			}
			pool.add(e);
			total += weight(e, st);
		}
		for (int attempt = 0; attempt < 6 && !pool.isEmpty(); attempt++) {
			int roll = p.getRandom().nextInt(Math.max(1, total));
			HauntEvents.Event chosen = pool.get(0);
			for (HauntEvents.Event e : pool) {
				roll -= weight(e, st);
				if (roll < 0) {
					chosen = e;
					break;
				}
			}
			if (fire(p, st, chosen)) {
				t.lastFired.put(chosen.id(), now);
				t.lastEvent = chosen.id();
				return;
			}
			pool.remove(chosen);
			total -= weight(chosen, st);
		}
	}

	/** Eventos da fase atual pesam mais que os antigos. */
	private static int weight(HauntEvents.Event e, HauntState st) {
		return e.minPhase() == st.phase ? e.weight() * 2 : e.weight();
	}

	public static boolean fire(ServerPlayer p, HauntState st, HauntEvents.Event e) {
		try {
			boolean ok = e.action().test(p, st);
			if (ok) {
				InquilinoMod.LOGGER.debug("[Inquilino] evento {} em {}", e.id(), p.getGameProfile().name());
			}
			return ok;
		} catch (Exception ex) {
			InquilinoMod.LOGGER.error("Evento {} falhou", e.id(), ex);
			return false;
		}
	}

	// ================================================================== ambiente

	private static void ambient(ServerPlayer p, HauntState st) {
		RandomSource r = p.getRandom();
		Vec3 somewhere = WorldUtil.around(p, 8 + r.nextInt(10), r.nextInt(360)).add(0, r.nextInt(5) - 2, 0);
		int roll = r.nextInt(st.phase >= 2 ? 5 : st.phase >= 1 ? 4 : 2);
		switch (roll) {
			case 0 -> WorldUtil.playTo(p, SoundEvents.AMBIENT_CAVE, somewhere, 0.9F, 0.8F + r.nextFloat() * 0.3F);
			case 1 -> WorldUtil.playTo(p, SoundEvents.WOOD_STEP, somewhere, 0.4F, 0.5F);
			case 2 -> WorldUtil.playTo(p, ModRegistry.DRONE, somewhere, 0.7F, 0.6F);
			case 3 -> WorldUtil.playTo(p, SoundEvents.WOODEN_DOOR_CLOSE, somewhere, 0.35F, 0.7F);
			default -> WorldUtil.playTo(p, ModRegistry.VOICES, somewhere.add(0, -6, 0), 0.5F, 0.5F);
		}
	}

	// ================================================================== reações

	/** Primeira vez que o jogador vê o Inquilino em cada encontro. */
	public static void onTenantSeen(ServerPlayer p, TenantEntity t, double dist) {
		WorldUtil.playTo(p, ModRegistry.STING, t.position(), dist < 16 ? 1.0F : 0.6F, 0.8F);
		if (dist < 16) {
			WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", 6));
		}
		HauntState st = HauntState.get(p.level().getServer());
		if (st.mark("visto_primeira_vez")) {
			Scheduler.later(60, () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.first_seen", WorldUtil.realName(p))));
		} else if (p.getRandom().nextInt(3) == 0) {
			Scheduler.later(30 + p.getRandom().nextInt(40), () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.seen." + p.getRandom().nextInt(4), WorldUtil.realName(p))));
		}
	}

	public static void onCaught(ServerPlayer p, boolean stoleLight) {
		track(p).huntTicks = 0;
		if (stoleLight) {
			Scheduler.later(60, () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.stole", WorldUtil.realName(p))));
		} else {
			Scheduler.later(80, () -> p.sendSystemMessage(Lore.tenantSays(Lore.pick("caught", Lore.CHAT_CAUGHT, p.getRandom()), WorldUtil.realName(p))));
		}
	}

	public static void notePresence(ServerPlayer p) {
		track(p).lastPresence = p.level().getServer().getTickCount();
	}

	public static void lockDoors(ServerPlayer p, int ticks) {
		track(p).lockedUntil = p.level().getServer().getTickCount() + ticks;
	}

	public static boolean fireById(ServerPlayer p, String id) {
		HauntEvents.Event e = HauntEvents.ALL.get(id);
		if (e == null || p.isRemoved()) {
			return false;
		}
		HauntState st = HauntState.get(p.level().getServer());
		if (st.phase >= HauntState.PHASE_ENDED || st.has("final_iniciado")) {
			return false;
		}
		boolean ok = fire(p, st, e);
		if (ok) {
			track(p).lastFired.put(id, (long) p.level().getServer().getTickCount());
		}
		return ok;
	}

	private static String pickOne(RandomSource r, String... ids) {
		return ids[r.nextInt(ids.length)];
	}

	/** Uma mensagem que você escreveu, que ele aprendeu. */
	public static @Nullable String learnedMessage(ServerPlayer p) {
		Track t = TRACKS.get(p.getUUID());
		if (t == null || t.learned.isEmpty()) {
			return null;
		}
		List<String> list = new ArrayList<>(t.learned);
		return list.get(p.getRandom().nextInt(list.size()));
	}

	// ================================================================== voz

	/** Ele fala. Quase sempre sobre o que você está fazendo agora. */
	private static void speak(ServerPlayer p, HauntState st, Track t) {
		List<String> ctx = new ArrayList<>();
		ServerLevel level = p.level();
		boolean sky = WorldUtil.underSky(p);
		if (level.getMaxLocalRawBrightness(p.blockPosition()) < 4) {
			ctx.add("dark");
		}
		if (!sky && p.getY() < 50) {
			ctx.add("cave");
		}
		if (HeldLight.holdsLight(p) && level.isDarkOutside()) {
			ctx.add("torch");
		}
		if (p.getHealth() < 8) {
			ctx.add("hurt");
		}
		if (p.isSprinting()) {
			ctx.add("run");
		}
		if (t.stillTicks > 20 * 8) {
			ctx.add("still");
		}
		BlockPos bed = HauntEvents.bedOf(p);
		if (bed != null && bed.distSqr(p.blockPosition()) < 16 * 16) {
			ctx.add("home");
		}
		if (sky && level.isDarkOutside()) {
			ctx.add("night");
		}
		String key;
		if (!ctx.isEmpty() && p.getRandom().nextInt(10) < 7) {
			String c = ctx.get(p.getRandom().nextInt(ctx.size()));
			key = "chat.inquilino.ctx." + c + "." + p.getRandom().nextInt(Lore.CTX_LINES);
		} else {
			key = switch (st.phase) {
				case 1 -> Lore.pick("p1", Lore.CHAT_P1, p.getRandom());
				case 2 -> Lore.pick("p2", Lore.CHAT_P2, p.getRandom());
				default -> Lore.pick("p3", Lore.CHAT_P3, p.getRandom());
			};
		}
		p.sendSystemMessage(Lore.tenantSays(key, WorldUtil.realName(p)));
		if (st.phase >= HauntState.PHASE_INTRUSION && p.getRandom().nextInt(3) == 0) {
			WorldUtil.playTo(p, ModRegistry.WHISPER, p.getEyePosition().add(WorldUtil.flatLook(p).scale(-1.0)), 0.6F, 0.7F);
		}
	}

	/** Você escreveu no chat. Ele leu. */
	private static void onPlayerChat(ServerPlayer p, String raw) {
		String msg = raw.trim();
		if (msg.isEmpty()) {
			return;
		}
		Track t = track(p);
		t.learned.addLast(msg.length() > 60 ? msg.substring(0, 60) : msg);
		while (t.learned.size() > 6) {
			t.learned.removeFirst();
		}
		HauntState st = HauntState.get(p.level().getServer());
		if (st.phase >= HauntState.PHASE_ENDED) {
			return;
		}
		int chance = st.phase == 0 ? 2 : 8;
		if (p.getRandom().nextInt(10) >= chance) {
			return;
		}
		String lower = msg.toLowerCase(Locale.ROOT);
		String key;
		String arg = WorldUtil.realName(p);
		if (lower.contains("quem") || lower.contains("who")) {
			key = "chat.inquilino.reply.who." + p.getRandom().nextInt(3);
		} else if (lower.contains("sai") || lower.contains("embora") || lower.contains("leave") || lower.contains("go away") || lower.contains("para")) {
			key = "chat.inquilino.reply.leave." + p.getRandom().nextInt(3);
		} else if (lower.startsWith("oi") || lower.startsWith("ola") || lower.startsWith("olá") || lower.startsWith("hi") || lower.startsWith("hello") || lower.startsWith("hey")) {
			key = "chat.inquilino.reply.hi." + p.getRandom().nextInt(3);
		} else if (lower.contains("socorro") || lower.contains("ajuda") || lower.contains("help")) {
			key = "chat.inquilino.reply.help." + p.getRandom().nextInt(3);
		} else if (lower.contains("?")) {
			key = "chat.inquilino.reply.question." + p.getRandom().nextInt(5);
		} else {
			key = "chat.inquilino.reply.echo." + p.getRandom().nextInt(3);
			arg = lower;
		}
		String finalKey = key;
		String finalArg = arg;
		Scheduler.later(30 + p.getRandom().nextInt(60), () -> p.sendSystemMessage(Lore.tenantSays(finalKey, finalArg)));
	}

	/** Deitar na cama não é seguro. */
	private static void onSleep(ServerPlayer p) {
		HauntState st = HauntState.get(p.level().getServer());
		if (st.phase < HauntState.PHASE_INTRUSION || st.phase >= HauntState.PHASE_ENDED || p.getRandom().nextFloat() > 0.6F) {
			return;
		}
		Scheduler.later(45, () -> {
			if (!p.isSleeping()) {
				return;
			}
			p.stopSleepInBed(true, true);
			WorldUtil.send(p, HorrorPayload.of(Effects.JUMPSCARE, 14));
			WorldUtil.playTo(p, ModRegistry.SCREAM, p.getEyePosition(), 1.6F, 0.9F);
			BlockPos at = WorldUtil.hiddenSpot(p, 2.5, 4, false);
			if (at != null) {
				HauntEvents.spawnTenant(p, at, TenantEntity.Mode.WATCH, 20 * 4);
			}
			Scheduler.later(50, () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.sleep", WorldUtil.realName(p))));
		});
	}

	public static void onTenantHit(ServerPlayer p) {
		track(p).huntTicks = 0;
		Scheduler.later(40, () -> p.sendSystemMessage(Lore.tenantSays(Lore.pick("hit", Lore.CHAT_HIT, p.getRandom()), WorldUtil.realName(p))));
	}

	public static void startHunt(ServerPlayer p, int ticks) {
		track(p).huntTicks = ticks;
		WorldUtil.send(p, HorrorPayload.of(Effects.SHAKE, 10));
	}

	public static boolean sessionOnce(ServerPlayer p, String key) {
		return track(p).once.add(key);
	}

	private static void onJoin(ServerPlayer p) {
		HauntState st = HauntState.get(p.level().getServer());
		Scheduler.later(20, () -> {
			WorldUtil.send(p, HorrorPayload.of(Effects.PHASE, st.phase));
			if (st.phase >= HauntState.PHASE_INTRUSION) {
				WorldUtil.send(p, HorrorPayload.of(Effects.WRITE_FILE, "leia", WorldUtil.realName(p), 0));
			}
			if (st.roomPos != null && st.nextDiary > 5) {
				WorldUtil.send(p, HorrorPayload.of(Effects.WRITE_FILE, "memoria", st.roomPos.getX() + "," + st.roomPos.getZ(), 0));
			}
		});
	}

	/** Ao acordar: ele esteve aqui. */
	private static void onWake(ServerPlayer p, BlockPos bed) {
		HauntState st = HauntState.get(p.level().getServer());
		Track t = track(p);
		long now = p.level().getServer().getTickCount();
		if (st.phase < HauntState.PHASE_PRESENCE || st.phase >= HauntState.PHASE_ENDED || now - t.lastSleepSign < 20L * 60 * 8) {
			return;
		}
		if (p.getRandom().nextFloat() > 0.65F) {
			return;
		}
		t.lastSleepSign = now;
		Scheduler.later(10, () -> {
			ServerLevel level = p.level();
			for (BlockPos q : BlockPos.betweenClosed(bed.offset(-2, 0, -2), bed.offset(2, 1, 2))) {
				if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isSolid() && q.distSqr(p.blockPosition()) > 1) {
					Structures.placeSign(level, q.immutable(), p.getEyePosition(), Lore.sign("dormindo", WorldUtil.realName(p)));
					break;
				}
			}
			if (st.phase >= HauntState.PHASE_INTRUSION) {
				BlockPos at = WorldUtil.hiddenSpot(p, 3, 5, false);
				if (at != null) {
					TenantEntity tenant = HauntEvents.spawnTenant(p, at, TenantEntity.Mode.WATCH, 20 * 6);
					if (tenant != null) {
						WorldUtil.playTo(p, ModRegistry.BREATH, tenant.position().add(0, 1.8, 0), 0.8F, 0.55F);
					}
				}
			}
		});
	}

	// ================================================================== cópias (manequins)

	public static void trackDoppel(ServerPlayer p, Mannequin m) {
		DOPPELS.add(new Doppel(p.getUUID(), m, p.level().getServer().getTickCount(), new int[1]));
	}

	private static void tickDoppels(MinecraftServer server) {
		Iterator<Doppel> it = DOPPELS.iterator();
		while (it.hasNext()) {
			Doppel d = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(d.player);
			Mannequin m = d.entity;
			boolean remove = m.isRemoved() || p == null || p.level() != m.level() || server.getTickCount() - d.bornTick > 20 * 50;
			if (!remove) {
				double dist = p.distanceTo(m);
				Vec3 head = m.getEyePosition();
				boolean looking = WorldUtil.viewDot(p, head) > 0.985 && WorldUtil.hasLineOfSight(p, head);
				if (looking && d.seen[0]++ == 0) {
					WorldUtil.playTo(p, ModRegistry.STING, m.position(), 0.7F, 0.6F);
				}
				// a cópia sempre vira a cabeça para você
				double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
				float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
				m.setYHeadRot(yaw);
				if (d.seen[0] > 40 || dist < 8) {
					remove = true;
					final ServerPlayer target = p;
					Scheduler.later(60, () -> target.sendSystemMessage(Lore.chat(target.getGameProfile().name(), net.minecraft.network.chat.Component.translatable("chat.inquilino.doppel"))));
				}
			}
			if (remove) {
				if (!m.isRemoved() && m.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 16, 0.2, 0.7, 0.2, 0.01);
					m.discard();
				}
				it.remove();
			}
		}
	}

	// ================================================================== visita à casa

	public static void armHomeVisit(ServerPlayer p, BlockPos bed) {
		track(p).homeVisit = bed;
	}

	private static void checkHomeVisit(ServerPlayer p, Track t) {
		if (t.homeVisit == null) {
			return;
		}
		BlockPos bed = t.homeVisit;
		double d = Math.sqrt(bed.distSqr(p.blockPosition()));
		if (d < 40 && d > 16 && p.level().hasChunkAt(bed)) {
			t.homeVisit = null;
			BlockPos stand = null;
			for (BlockPos q : BlockPos.betweenClosed(bed.offset(-2, 0, -2), bed.offset(2, 1, 2))) {
				if (WorldUtil.canStand(p.level(), q)) {
					stand = q.immutable();
					break;
				}
			}
			if (stand != null) {
				HauntEvents.spawnTenant(p, stand, TenantEntity.Mode.WATCH, 20 * 120);
			}
		}
	}

	// ================================================================== o quarto e o final

	/** Chamado ao entregar o diário 5: escolhe onde o quarto está enterrado. */
	public static void revealRoom(ServerPlayer p, HauntState st) {
		if (st.roomPos == null) {
			RandomSource r = p.getRandom();
			double angle = r.nextDouble() * Math.PI * 2;
			double dist = 140 + r.nextInt(80);
			BlockPos spawn = p.level().getServer().overworld().getRespawnData().pos();
			BlockPos base = p.level().dimension() == Level.OVERWORLD ? p.blockPosition() : spawn;
			st.roomPos = new BlockPos((int) (base.getX() + Math.cos(angle) * dist), 0, (int) (base.getZ() + Math.sin(angle) * dist));
			st.setDirty();
		}
		String coords = st.roomPos.getX() + "," + st.roomPos.getZ();
		Scheduler.later(20 * 20, () -> {
			WorldUtil.send(p, HorrorPayload.of(Effects.WRITE_FILE, "memoria", coords, 0));
			p.sendSystemMessage(Lore.tenantSays("chat.inquilino.memory", WorldUtil.realName(p)));
		});
	}

	private static void checkRoom(ServerPlayer p, HauntState st, Track t) {
		if (st.roomPos == null || p.level().dimension() != Level.OVERWORLD) {
			return;
		}
		ServerLevel level = p.level();
		if (!st.has("quarto_construido")) {
			double dx = p.getX() - st.roomPos.getX(), dz = p.getZ() - st.roomPos.getZ();
			if (dx * dx + dz * dz < 56 * 56 && level.hasChunkAt(st.roomPos)) {
				st.roomPos = Structures.buildRoom(level, st.roomPos, WorldUtil.realName(p));
				st.mark("quarto_construido");
				st.setDirty();
			}
			return;
		}
		if (!st.has("final_iniciado") && Structures.insideRoom(st.roomPos, p.position())) {
			st.mark("final_iniciado");
			startEnding(p, st, t);
		}
	}

	private static void startEnding(ServerPlayer p, HauntState st, Track t) {
		BlockPos o = st.roomPos;
		ServerLevel level = p.level();
		Vec3 center = Vec3.atCenterOf(o.offset(2, 1, 2));
		WorldUtil.playTo(p, SoundEvents.MUSIC_DISC_11, center, 1.4F, 1.0F);
		Scheduler.later(80, () -> {
			level.setBlockAndUpdate(o.offset(4, 0, 0), Blocks.AIR.defaultBlockState());
			WorldUtil.playTo(p, SoundEvents.FIRE_EXTINGUISH, Vec3.atCenterOf(o.offset(4, 0, 0)), 0.8F, 0.5F);
			WorldUtil.send(p, HorrorPayload.of(Effects.STATIC, "", "", 24));
		});
		Scheduler.later(130, () -> {
			BlockPos stand = o.offset(1, 0, 1);
			if (!WorldUtil.canStand(level, stand)) {
				stand = o.offset(3, 0, 2);
			}
			t.endingTenant = HauntEvents.spawnTenant(p, stand, TenantEntity.Mode.LURK, 20 * 120);
			WorldUtil.playTo(p, ModRegistry.BREATH, Vec3.atCenterOf(stand).add(0, 1, 0), 1.0F, 0.5F);
		});
		Scheduler.later(170, () -> {
			if (t.endingTenant != null) {
				Vec3 eye = t.endingTenant.getEyePosition();
				WorldUtil.send(p, new HorrorPayload(Effects.LOOK_AT, "", "", 40, eye.x, eye.y, eye.z));
			}
		});
		Scheduler.later(250, () -> WorldUtil.send(p, HorrorPayload.of(Effects.FAKE_DISCONNECT, "disconnect.inquilino.ending", WorldUtil.realName(p), 1)));
		// se o cliente nunca responder, termina assim mesmo
		Scheduler.later(20 * 90, () -> {
			if (!st.has("final_concluido")) {
				finishEnding(p, st, t);
			}
		});
	}

	private static void onSignal(ServerPlayer p, String signal) {
		HauntState st = HauntState.get(p.level().getServer());
		Track t = track(p);
		switch (signal) {
			case SignalPayload.ENDING_CLOSED -> {
				if (st.has("final_iniciado") && !st.has("final_concluido")) {
					TenantEntity tenant = t.endingTenant;
					if (tenant != null && !tenant.isRemoved()) {
						Vec3 front = p.getEyePosition().add(WorldUtil.flatLook(p).scale(1.3));
						tenant.snapTo(front.x, p.getY(), front.z, 0, 0);
						tenant.faceTowards(p);
					}
					Scheduler.later(14, () -> {
						WorldUtil.send(p, HorrorPayload.of(Effects.JUMPSCARE, 22));
						WorldUtil.playTo(p, ModRegistry.SCREAM, p.getEyePosition(), 2.0F, 0.8F);
						if (t.endingTenant != null) {
							t.endingTenant.vanish(p, false);
						}
					});
					Scheduler.later(40, () -> finishEnding(p, st, t));
				}
			}
			case SignalPayload.DISCONNECT_CLOSED -> {
				Vec3 front = p.position().add(WorldUtil.flatLook(p).scale(2.2));
				BlockPos at = WorldUtil.findStandPos(p.level(), front.x, p.getY(), front.z, false);
				if (at != null) {
					TenantEntity tenant = HauntEvents.spawnTenant(p, at, TenantEntity.Mode.LURK, 60);
					Scheduler.later(22, () -> {
						WorldUtil.send(p, HorrorPayload.of(Effects.FACE, 6));
						WorldUtil.playTo(p, ModRegistry.SCREAM, p.getEyePosition(), 1.4F, 1.1F);
						if (tenant != null) {
							tenant.vanish(p, false);
						}
					});
				}
			}
			default -> {
			}
		}
	}

	private static void finishEnding(ServerPlayer p, HauntState st, Track t) {
		if (!st.mark("final_concluido")) {
			return;
		}
		st.setPhase(HauntState.PHASE_ENDED);
		st.endedAt = st.ticks;
		st.setDirty();
		InquilinoConfig.remember(WorldUtil.realName(p));
		MinecraftServer server = p.level().getServer();
		server.getPlayerList().broadcastSystemMessage(Lore.left(Lore.NAME), false);
		WorldUtil.send(p, HorrorPayload.of(Effects.PHASE, HauntState.PHASE_ENDED));
		WorldUtil.send(p, HorrorPayload.of(Effects.WRITE_FILE, "ultimo", WorldUtil.realName(p), 0));
		Scheduler.later(20 * 6, () -> WorldUtil.send(p, HorrorPayload.of(Effects.TEXT, "subliminal.inquilino.ending", WorldUtil.realName(p), 20 * 5)));
		Scheduler.later(20 * 25, () -> p.sendSystemMessage(Lore.tenantSays("chat.inquilino.ending", WorldUtil.realName(p))));
	}
}

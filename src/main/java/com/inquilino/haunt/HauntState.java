package com.inquilino.haunt;

import com.inquilino.InquilinoMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Estado persistente da assombração, salvo junto com o mundo. */
public class HauntState extends SavedData {
	public static final int PHASE_SILENCE = 0;
	public static final int PHASE_PRESENCE = 1;
	public static final int PHASE_INTRUSION = 2;
	public static final int PHASE_HUNT = 3;
	public static final int PHASE_ENDED = 4;

	public static final Codec<HauntState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("ticks", 0L).forGetter(s -> s.ticks),
		Codec.INT.optionalFieldOf("phase", 0).forGetter(s -> s.phase),
		Codec.INT.optionalFieldOf("diary", 1).forGetter(s -> s.nextDiary),
		Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(s -> new ArrayList<>(s.flags)),
		BlockPos.CODEC.optionalFieldOf("room").forGetter(s -> Optional.ofNullable(s.roomPos)),
		Codec.LONG.optionalFieldOf("ended_at", 0L).forGetter(s -> s.endedAt)
	).apply(i, HauntState::new));

	public static final SavedDataType<HauntState> TYPE = new SavedDataType<>(
		InquilinoMod.id("assombracao"), HauntState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	/** Ticks de jogo com alguém online neste mundo. */
	public long ticks;
	public int phase;
	/** Próxima página do diário a ser entregue (1..5). A 6 fica no quarto. */
	public int nextDiary;
	public final Set<String> flags = new HashSet<>();
	public @Nullable BlockPos roomPos;
	public long endedAt;

	public HauntState() {
		this(0L, 0, 1, List.of(), Optional.empty(), 0L);
	}

	private HauntState(long ticks, int phase, int nextDiary, List<String> flags, Optional<BlockPos> room, long endedAt) {
		this.ticks = ticks;
		this.phase = phase;
		this.nextDiary = nextDiary;
		this.flags.addAll(flags);
		this.roomPos = room.orElse(null);
		this.endedAt = endedAt;
	}

	public static HauntState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean has(String flag) {
		return this.flags.contains(flag);
	}

	/** Marca a flag; retorna true se ela ainda não existia. */
	public boolean mark(String flag) {
		boolean added = this.flags.add(flag);
		if (added) {
			this.setDirty();
		}
		return added;
	}

	public void unmark(String flag) {
		if (this.flags.remove(flag)) {
			this.setDirty();
		}
	}

	public void setPhase(int phase) {
		this.phase = phase;
		this.setDirty();
	}
}

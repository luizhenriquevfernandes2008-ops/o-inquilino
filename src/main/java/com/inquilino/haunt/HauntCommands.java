package com.inquilino.haunt;

import com.inquilino.entity.TenantEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /inquilino — ferramentas para testar (precisa de permissão de operador).
 * <ul>
 *   <li>/inquilino invocar — coloca ele parado na sua frente por 30s</li>
 *   <li>/inquilino fase &lt;0-4&gt;</li>
 *   <li>/inquilino evento &lt;id&gt;</li>
 *   <li>/inquilino estado</li>
 *   <li>/inquilino resetar</li>
 * </ul>
 */
public final class HauntCommands {
	private HauntCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("inquilino")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("fase")
				.then(Commands.argument("fase", IntegerArgumentType.integer(0, 4)).executes(HauntCommands::phase)))
			.then(Commands.literal("evento")
				.then(Commands.argument("id", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HauntEvents.ALL.keySet(), builder))
					.executes(HauntCommands::event)))
			.then(Commands.literal("invocar").executes(HauntCommands::summon))
			.then(Commands.literal("estado").executes(HauntCommands::status))
			.then(Commands.literal("resetar").executes(HauntCommands::reset)));
	}

	private static int phase(CommandContext<CommandSourceStack> ctx) {
		int phase = IntegerArgumentType.getInteger(ctx, "fase");
		HauntState st = HauntState.get(ctx.getSource().getServer());
		HauntDirector.setPhase(ctx.getSource().getServer(), st, phase);
		ctx.getSource().sendSuccess(() -> Component.literal("[Inquilino] fase = " + phase), true);
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "id");
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		HauntEvents.Event e = HauntEvents.ALL.get(id);
		if (e == null) {
			ctx.getSource().sendFailure(Component.literal("[Inquilino] evento desconhecido: " + id));
			return 0;
		}
		boolean ok = HauntDirector.fire(p, HauntState.get(ctx.getSource().getServer()), e);
		ctx.getSource().sendSuccess(() -> Component.literal("[Inquilino] " + id + (ok ? " ok" : " não pôde acontecer aqui")), false);
		return ok ? 1 : 0;
	}

	/** Coloca ele parado na sua frente por 30 segundos. */
	private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Vec3 front = p.position().add(WorldUtil.flatLook(p).scale(4));
		BlockPos at = WorldUtil.findStandPos(p.level(), front.x, p.getY(), front.z, false);
		if (at == null || HauntEvents.spawnTenant(p, at, TenantEntity.Mode.LURK, 20 * 30) == null) {
			ctx.getSource().sendFailure(Component.literal("[Inquilino] não há espaço na sua frente"));
			return 0;
		}
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		HauntState st = HauntState.get(ctx.getSource().getServer());
		String room = st.roomPos == null ? "?" : st.roomPos.getX() + " " + st.roomPos.getY() + " " + st.roomPos.getZ();
		ctx.getSource().sendSuccess(() -> Component.literal(
			"[Inquilino] fase=" + st.phase + " minutos=" + (st.ticks / 1200) + " proximo_diario=" + st.nextDiary + " quarto=" + room + " flags=" + st.flags
		), false);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		HauntState st = HauntState.get(ctx.getSource().getServer());
		st.ticks = 0;
		st.nextDiary = 1;
		st.flags.clear();
		st.roomPos = null;
		st.endedAt = 0;
		HauntDirector.setPhase(ctx.getSource().getServer(), st, 0);
		ctx.getSource().sendSuccess(() -> Component.literal("[Inquilino] estado reiniciado. ele esqueceu. por enquanto."), true);
		return 1;
	}
}

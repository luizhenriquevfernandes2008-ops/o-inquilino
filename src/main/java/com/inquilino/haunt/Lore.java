package com.inquilino.haunt;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Todo o texto vive nos arquivos de idioma (assets/inquilino/lang). Aqui ficam só as chaves e
 * quantas variações existem de cada categoria.
 */
public final class Lore {
	public static final String NAME = "Inquilino";

	/** Quantidade de falas por categoria (chat.inquilino.&lt;categoria&gt;.&lt;n&gt;). */
	public static final int CHAT_P1 = 8;
	public static final int CHAT_P2 = 10;
	public static final int CHAT_P3 = 7;
	public static final int CHAT_MIMIC = 8;
	public static final int CHAT_HIT = 4;
	public static final int CHAT_CAUGHT = 4;
	public static final int SUBLIMINAL = 7;
	public static final int WINDOW = 5;
	public static final int NOTES = 7;
	public static final int CTX_LINES = 3;

	/** Placas: id -> número de linhas. */
	public static final String[] SIGNS = {"oi", "meu_quarto", "nao_deveria", "dormindo", "nome", "atras", "casa", "hora", "luz", "porta"};

	/** Diário: número de páginas de cada volume (1..6). */
	private static final int[] DIARY_PAGES = {0, 2, 2, 2, 2, 3, 3};

	private Lore() {
	}

	public static String pick(String category, int count, RandomSource random) {
		return "chat.inquilino." + category + "." + random.nextInt(count);
	}

	/** Uma fala dele no chat, no formato vanilla "&lt;nome&gt; mensagem". */
	public static Component chat(String speaker, Component message) {
		return Component.translatable("chat.type.text", Component.literal(speaker), message);
	}

	public static Component tenantSays(String key, Object... args) {
		return chat(NAME, Component.translatable(key, args));
	}

	public static Component joined(String name) {
		return Component.translatable("multiplayer.player.joined", Component.literal(name)).withStyle(ChatFormatting.YELLOW);
	}

	public static Component left(String name) {
		return Component.translatable("multiplayer.player.left", Component.literal(name)).withStyle(ChatFormatting.YELLOW);
	}

	/** Texto de placa: quatro linhas traduzidas, com argumento opcional (%s). */
	public static SignText sign(String id, Object arg) {
		SignText text = new SignText();
		for (int line = 0; line < 4; line++) {
			MutableComponent c = Component.translatable("sign.inquilino." + id + "." + (line + 1), arg);
			text = text.setMessage(line, c);
		}
		return text;
	}

	/** Uma página do diário do T. */
	public static ItemStack diary(int volume, String playerName) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();
		int count = volume < DIARY_PAGES.length ? DIARY_PAGES[volume] : 1;
		for (int p = 1; p <= count; p++) {
			pages.add(Filterable.passThrough(Component.translatable("book.inquilino." + volume + ".page." + p, playerName)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("casa_" + volume), "T.", 3, pages, true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("book.inquilino." + volume + ".title").setStyle(Style.EMPTY.withItalic(false).withColor(0xB8B0A0)));
		return book;
	}

	/** Um bilhete amassado que aparece no inventário. */
	public static ItemStack note(RandomSource random, String name) {
		ItemStack paper = new ItemStack(Items.PAPER);
		int n = random.nextInt(NOTES);
		paper.set(DataComponents.CUSTOM_NAME, Component.translatable("note.inquilino." + n, name).setStyle(Style.EMPTY.withItalic(false).withColor(0xD8D2C4)));
		paper.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("note.inquilino.signed").setStyle(Style.EMPTY.withItalic(true).withColor(0x6A6A6A))
		)));
		return paper;
	}
}

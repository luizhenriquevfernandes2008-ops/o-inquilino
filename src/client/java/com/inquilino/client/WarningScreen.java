package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * Aviso de primeira execução, disfarçado de terminal de recuperação de arquivos.
 * O texto é "digitado" aos poucos.
 */
public class WarningScreen extends Screen {
	private static final int LINES = 9;
	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private final List<List<FormattedCharSequence>> paragraphs = new ArrayList<>();

	public WarningScreen(Screen parent) {
		super(Component.translatable("warning.inquilino.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.paragraphs.clear();
		int maxWidth = Math.min(420, this.width - 40);
		for (int i = 0; i < LINES; i++) {
			this.paragraphs.add(this.font.split(Component.translatable("warning.inquilino.line." + i), maxWidth));
		}
		int y = this.height - 34;
		this.addRenderableWidget(Button.builder(Component.translatable("warning.inquilino.enter"), b -> this.accept(true))
			.bounds(this.width / 2 - 154, y, 150, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("warning.inquilino.enter_no_flashes"), b -> this.accept(false))
			.bounds(this.width / 2 + 4, y, 150, 20).build());
	}

	private void accept(boolean flashes) {
		InquilinoConfig.flashes = flashes;
		InquilinoConfig.warningSeen = true;
		InquilinoConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, this.width, this.height, 0xFF050505);
		// linhas de varredura
		for (int y = 0; y < this.height; y += 3) {
			graphics.fill(0, y, this.width, y + 1, 0x22000000);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		long elapsed = Util.getMillis() - this.openedAt;
		int x = Math.max(20, this.width / 2 - 210);
		// encolhe o texto se a janela for pequena, para não cobrir os botões
		int total = 0;
		for (List<FormattedCharSequence> para : this.paragraphs) {
			total += para.size() * 10 + 5;
		}
		float scale = Math.min(1.0F, (this.height - 60.0F) / Math.max(1, total));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, 14);
		graphics.pose().scale(scale);
		int y = 0;
		int budget = (int) (elapsed / 18); // caracteres "digitados"
		outer:
		for (List<FormattedCharSequence> para : this.paragraphs) {
			for (FormattedCharSequence line : para) {
				if (budget <= 0) {
					break outer;
				}
				int len = length(line);
				if (len <= budget) {
					graphics.text(this.font, line, 0, y, 0xFFB8C4B0, false);
				} else {
					FormattedCharSequence part = truncate(line, budget);
					graphics.text(this.font, part, 0, y, 0xFFB8C4B0, false);
					if ((elapsed / 400) % 2 == 0) {
						graphics.text(this.font, "_", this.font.width(part), y, 0xFFB8C4B0, false);
					}
				}
				budget -= Math.max(1, len);
				y += 10;
			}
			y += 5;
		}
		graphics.pose().popMatrix();
		if ((elapsed / 2500) % 7 == 3) {
			graphics.fill(0, (int) ((elapsed / 7) % this.height), this.width, (int) ((elapsed / 7) % this.height) + 2, ARGB.white(0.08F));
		}
	}

	private static int length(FormattedCharSequence seq) {
		int[] n = {0};
		seq.accept((index, style, codePoint) -> {
			n[0]++;
			return true;
		});
		return n[0];
	}

	private static FormattedCharSequence truncate(FormattedCharSequence seq, int max) {
		return sink -> {
			int[] n = {0};
			return seq.accept((index, style, codePoint) -> {
				if (n[0]++ >= max) {
					return false;
				}
				return sink.accept(index, style, codePoint);
			});
		};
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}

package com.inquilino.client;

import com.inquilino.net.SignalPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Idêntica à tela de "Conexão perdida" do jogo. Mas o mundo continua rodando atrás dela.
 * E quando você clica em voltar... você ainda está lá.
 */
public class FakeDisconnectScreen extends Screen {
	private final Component reason;
	private final boolean ending;
	private final LinearLayout layout = LinearLayout.vertical();
	private boolean signaled;

	public FakeDisconnectScreen(Component reason, boolean ending) {
		super(Component.translatable("disconnect.lost"));
		this.reason = reason;
		this.ending = ending;
	}

	@Override
	protected void init() {
		this.minecraft.getSoundManager().stop();
		this.layout.defaultCellSetting().alignHorizontallyCenter().padding(10);
		this.layout.addChild(new StringWidget(this.title, this.font));
		this.layout.addChild(new MultiLineTextWidget(this.reason, this.font).setMaxWidth(this.width - 50).setCentered(true));
		this.layout.defaultCellSetting().padding(2);
		this.layout.addChild(Button.builder(Component.translatable("gui.toTitle"), b -> this.onClose()).width(200).build());
		this.layout.arrangeElements();
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// fundo opaco: o mundo (e ele) continuam lá atrás, mas você não vê
		graphics.fill(0, 0, this.width, this.height, 0xFF0B0B0B);
		Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
		if (!this.signaled && ClientPlayNetworking.canSend(SignalPayload.TYPE)) {
			this.signaled = true;
			ClientPlayNetworking.send(new SignalPayload(this.ending ? SignalPayload.ENDING_CLOSED : SignalPayload.DISCONNECT_CLOSED));
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

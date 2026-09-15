package com.inquilino.client;

import com.inquilino.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Um zumbido grave e contínuo que nunca para enquanto ele estiver por perto. */
public class AmbienceSound extends AbstractTickableSoundInstance {
	private float target;

	public AmbienceSound() {
		super(ModRegistry.AMBIENCE.value(), SoundSource.AMBIENT, RandomSource.create());
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01F;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	public void setTarget(float target) {
		this.target = target;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			this.stop();
			return;
		}
		this.volume += (this.target - this.volume) * 0.03F;
		if (this.target <= 0.0F && this.volume < 0.01F) {
			this.stop();
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}

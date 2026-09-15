package com.inquilino.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dá acesso aos setters privados do manequim, para criar a cópia do jogador. */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
	@Invoker("setProfile")
	void inquilino$setProfile(ResolvableProfile profile);

	@Invoker("setImmovable")
	void inquilino$setImmovable(boolean immovable);

	@Invoker("setHideDescription")
	void inquilino$setHideDescription(boolean hide);
}

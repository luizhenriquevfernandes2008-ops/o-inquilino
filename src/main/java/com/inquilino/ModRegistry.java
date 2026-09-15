package com.inquilino;

import com.inquilino.entity.TenantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

public final class ModRegistry {
	private ModRegistry() {
	}

	public static final ResourceKey<EntityType<?>> TENANT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InquilinoMod.id("inquilino"));

	public static final EntityType<TenantEntity> TENANT = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		TENANT_KEY,
		EntityType.Builder.of(TenantEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 2.2F)
			.eyeHeight(2.0F)
			.clientTrackingRange(10)
			.fireImmune()
			.noSave()
			.noLootTable()
			.build(TENANT_KEY)
	);

	// Sons: todos são remixes de arquivos vanilla definidos em assets/inquilino/sounds.json
	public static final Holder<SoundEvent> WHISPER = sound("whisper");
	public static final Holder<SoundEvent> VOICES = sound("voices");
	public static final Holder<SoundEvent> BREATH = sound("breath");
	public static final Holder<SoundEvent> KNOCK = sound("knock");
	public static final Holder<SoundEvent> STING = sound("sting");
	public static final Holder<SoundEvent> HEARTBEAT = sound("heartbeat");
	public static final Holder<SoundEvent> DRONE = sound("drone");
	public static final Holder<SoundEvent> STATIC = sound("static");
	public static final Holder<SoundEvent> SCREAM = sound("scream");
	public static final Holder<SoundEvent> VANISH = sound("vanish");
	public static final Holder<SoundEvent> AMBIENCE = sound("ambience");

	public static final ResourceKey<DamageType> TENANT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InquilinoMod.id("tenant"));
	public static final ResourceKey<PaintingVariant> PAINTING_PORTRAIT = ResourceKey.create(Registries.PAINTING_VARIANT, InquilinoMod.id("retrato"));
	public static final ResourceKey<PaintingVariant> PAINTING_HALLWAY = ResourceKey.create(Registries.PAINTING_VARIANT, InquilinoMod.id("corredor"));

	private static Holder<SoundEvent> sound(String name) {
		Identifier id = InquilinoMod.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TENANT, TenantEntity.createAttributes());
	}
}

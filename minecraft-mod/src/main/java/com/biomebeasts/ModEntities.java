package com.biomebeasts;

import com.biomebeasts.entity.BearEntity;
import com.biomebeasts.entity.BeastEntity;
import com.biomebeasts.entity.BeastMonsterEntity;
import com.biomebeasts.entity.ReefSharkEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/** One unique mob per biome. */
public final class ModEntities {
	// --- Neutral land animals (fight back when hurt) ---
	/** Forest, birch forest. */
	public static final EntityType<BearEntity> BEAR = register("bear", SpawnGroup.CREATURE, BearEntity::new, 1.4F, 1.4F);
	/** Savanna. */
	public static final EntityType<BeastEntity> LION = register("lion", SpawnGroup.CREATURE, BeastEntity::new, 1.1F, 1.1F);
	/** Taiga. */
	public static final EntityType<BeastEntity> ELK = register("elk", SpawnGroup.CREATURE, BeastEntity::new, 1.3F, 1.9F);
	/** Badlands. */
	public static final EntityType<BeastEntity> CANYON_BOAR = register("canyon_boar", SpawnGroup.CREATURE, BeastEntity::new, 1.0F, 1.0F);
	/** Plains. */
	public static final EntityType<BeastEntity> BISON = register("bison", SpawnGroup.CREATURE, BeastEntity::new, 1.5F, 1.9F);

	// --- Hostile predators ---
	/** Desert. Poisonous sting. */
	public static final EntityType<BeastMonsterEntity> SAND_SCORPION = register("sand_scorpion", SpawnGroup.MONSTER,
			(t, w) -> new BeastMonsterEntity(t, w, StatusEffects.POISON, 100, 0), 1.3F, 0.8F);
	/** Snowy biomes. Chilling touch. */
	public static final EntityType<BeastMonsterEntity> YETI = register("yeti", SpawnGroup.MONSTER,
			(t, w) -> new BeastMonsterEntity(t, w, StatusEffects.SLOWNESS, 100, 1), 0.9F, 2.7F);
	/** Jungle. */
	public static final EntityType<BeastMonsterEntity> JAGUAR = register("jaguar", SpawnGroup.MONSTER,
			BeastMonsterEntity::new, 0.9F, 0.9F);
	/** Swamp. */
	public static final EntityType<BeastMonsterEntity> BOG_HAG = register("bog_hag", SpawnGroup.MONSTER,
			(t, w) -> new BeastMonsterEntity(t, w, StatusEffects.WEAKNESS, 200, 0), 0.6F, 1.95F);
	/** Stony peaks, windswept hills. */
	public static final EntityType<BeastMonsterEntity> STONE_TROLL = register("stone_troll", SpawnGroup.MONSTER,
			BeastMonsterEntity::new, 1.0F, 3.1F);
	/** Dark forest. Blinds its prey. */
	public static final EntityType<BeastMonsterEntity> WENDIGO = register("wendigo", SpawnGroup.MONSTER,
			(t, w) -> new BeastMonsterEntity(t, w, StatusEffects.DARKNESS, 160, 0), 0.7F, 2.4F);

	// --- Ocean ---
	public static final EntityType<ReefSharkEntity> REEF_SHARK = register("reef_shark", SpawnGroup.WATER_CREATURE,
			ReefSharkEntity::new, 1.6F, 0.9F);

	private ModEntities() {
	}

	private static <T extends MobEntity> EntityType<T> register(String name, SpawnGroup group,
			EntityType.EntityFactory<T> factory, float width, float height) {
		return Registry.register(Registries.ENTITY_TYPE, Identifier.of(BiomeBeasts.MOD_ID, name),
				FabricEntityTypeBuilder.create(group, factory)
						.dimensions(EntityDimensions.fixed(width, height))
						.build());
	}

	@SafeVarargs
	private static void spawn(EntityType<?> type, SpawnGroup group, int weight, int min, int max, RegistryKey<Biome>... biomes) {
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biomes), group, type, weight, min, max);
	}

	private static <T extends MobEntity> void landSpawnRules(EntityType<T> type, SpawnRestriction.SpawnPredicate<T> predicate) {
		SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, predicate);
	}

	/** Called once at mod init: loads the class (registering the types) and wires attributes + spawns. */
	public static void init() {
		// Attributes
		FabricDefaultAttributeRegistry.register(BEAR, BearEntity.createBearAttributes());
		FabricDefaultAttributeRegistry.register(LION, BeastEntity.createAttributes(30, 0.33, 6, 0.5));
		FabricDefaultAttributeRegistry.register(ELK, BeastEntity.createAttributes(30, 0.25, 5, 0.8));
		FabricDefaultAttributeRegistry.register(CANYON_BOAR, BeastEntity.createAttributes(22, 0.30, 5, 0.5));
		FabricDefaultAttributeRegistry.register(BISON, BeastEntity.createAttributes(40, 0.22, 8, 1.2));

		FabricDefaultAttributeRegistry.register(SAND_SCORPION, BeastMonsterEntity.createAttributes(16, 0.28, 4, 0));
		FabricDefaultAttributeRegistry.register(YETI, BeastMonsterEntity.createAttributes(40, 0.22, 6, 1.0));
		FabricDefaultAttributeRegistry.register(JAGUAR, BeastMonsterEntity.createAttributes(20, 0.38, 5, 0));
		FabricDefaultAttributeRegistry.register(BOG_HAG, BeastMonsterEntity.createAttributes(24, 0.24, 4, 0));
		FabricDefaultAttributeRegistry.register(STONE_TROLL, BeastMonsterEntity.createAttributes(60, 0.20, 9, 1.5));
		FabricDefaultAttributeRegistry.register(WENDIGO, BeastMonsterEntity.createAttributes(26, 0.30, 6, 0));

		FabricDefaultAttributeRegistry.register(REEF_SHARK, ReefSharkEntity.createAttributes());

		// Spawn placement rules
		landSpawnRules(BEAR, MobEntity::canMobSpawn);
		landSpawnRules(LION, MobEntity::canMobSpawn);
		landSpawnRules(ELK, MobEntity::canMobSpawn);
		landSpawnRules(CANYON_BOAR, MobEntity::canMobSpawn);
		landSpawnRules(BISON, MobEntity::canMobSpawn);
		landSpawnRules(SAND_SCORPION, HostileEntity::canSpawnIgnoreLightLevel);
		landSpawnRules(YETI, HostileEntity::canSpawnIgnoreLightLevel);
		landSpawnRules(JAGUAR, HostileEntity::canSpawnIgnoreLightLevel);
		landSpawnRules(BOG_HAG, HostileEntity::canSpawnIgnoreLightLevel);
		landSpawnRules(STONE_TROLL, HostileEntity::canSpawnIgnoreLightLevel);
		landSpawnRules(WENDIGO, HostileEntity::canSpawnIgnoreLightLevel);
		SpawnRestriction.register(REEF_SHARK, SpawnLocationTypes.IN_WATER, Heightmap.Type.OCEAN_FLOOR,
				(type, world, reason, pos, random) -> world.getDifficulty() != Difficulty.PEACEFUL
						&& WaterCreatureEntity.canSpawn(type, world, reason, pos, random));

		// Biome spawns
		spawn(BEAR, SpawnGroup.CREATURE, 8, 1, 2,
				BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST);
		spawn(SAND_SCORPION, SpawnGroup.MONSTER, 20, 1, 3, BiomeKeys.DESERT);
		spawn(YETI, SpawnGroup.MONSTER, 15, 1, 2,
				BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA, BiomeKeys.ICE_SPIKES, BiomeKeys.FROZEN_PEAKS);
		spawn(JAGUAR, SpawnGroup.MONSTER, 15, 1, 2,
				BiomeKeys.JUNGLE, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BAMBOO_JUNGLE);
		spawn(BOG_HAG, SpawnGroup.MONSTER, 15, 1, 1, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);
		spawn(LION, SpawnGroup.CREATURE, 8, 1, 3,
				BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA);
		spawn(ELK, SpawnGroup.CREATURE, 8, 2, 4,
				BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
		spawn(STONE_TROLL, SpawnGroup.MONSTER, 10, 1, 1,
				BiomeKeys.STONY_PEAKS, BiomeKeys.JAGGED_PEAKS, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);
		spawn(REEF_SHARK, SpawnGroup.WATER_CREATURE, 5, 1, 2,
				BiomeKeys.OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.COLD_OCEAN, BiomeKeys.DEEP_COLD_OCEAN,
				BiomeKeys.LUKEWARM_OCEAN, BiomeKeys.DEEP_LUKEWARM_OCEAN);
		spawn(WENDIGO, SpawnGroup.MONSTER, 20, 1, 1, BiomeKeys.DARK_FOREST);
		spawn(CANYON_BOAR, SpawnGroup.CREATURE, 10, 2, 4,
				BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.WOODED_BADLANDS);
		spawn(BISON, SpawnGroup.CREATURE, 8, 2, 5,
				BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW);
	}
}

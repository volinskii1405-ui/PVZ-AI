package com.biomebeasts;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class ModItems {
	// --- Bear drops ---
	public static final Item BEAR_STEAK = register("bear_steak", new Item(new Item.Settings()
			.food(new FoodComponent.Builder().nutrition(9).saturationModifier(0.9F).build())));
	public static final Item BEAR_HIDE = register("bear_hide", new Item(new Item.Settings()));

	// --- Bear hide armor ---
	public static final RegistryEntry<ArmorMaterial> BEAR_ARMOR_MATERIAL = registerArmorMaterial();
	private static final int DURABILITY_MULTIPLIER = 20; // leather 5, iron 15, diamond 33

	public static final Item BEAR_HELMET = register("bear_helmet", new ArmorItem(BEAR_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
			new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(DURABILITY_MULTIPLIER))));
	public static final Item BEAR_CHESTPLATE = register("bear_chestplate", new ArmorItem(BEAR_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
			new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(DURABILITY_MULTIPLIER))));
	public static final Item BEAR_LEGGINGS = register("bear_leggings", new ArmorItem(BEAR_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
			new Item.Settings().maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(DURABILITY_MULTIPLIER))));
	public static final Item BEAR_BOOTS = register("bear_boots", new ArmorItem(BEAR_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
			new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(DURABILITY_MULTIPLIER))));

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(BiomeBeasts.MOD_ID, name), item);
	}

	private static RegistryEntry<ArmorMaterial> registerArmorMaterial() {
		Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 2);
		defense.put(ArmorItem.Type.LEGGINGS, 5);
		defense.put(ArmorItem.Type.CHESTPLATE, 6);
		defense.put(ArmorItem.Type.HELMET, 2);
		defense.put(ArmorItem.Type.BODY, 6);

		Identifier id = Identifier.of(BiomeBeasts.MOD_ID, "bear");
		return Registry.registerReference(Registries.ARMOR_MATERIAL, id, new ArmorMaterial(
				defense,
				12,
				SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
				() -> Ingredient.ofItems(BEAR_HIDE),
				List.of(new ArmorMaterial.Layer(id)),
				0.5F,
				0.1F));
	}

	private static void spawnEgg(String name, EntityType<? extends net.minecraft.entity.mob.MobEntity> type, int primary, int secondary) {
		register(name + "_spawn_egg", new SpawnEggItem(type, primary, secondary, new Item.Settings()));
	}

	public static void init() {
		spawnEgg("bear", ModEntities.BEAR, 0x5A3A22, 0x2B1A0E);
		spawnEgg("sand_scorpion", ModEntities.SAND_SCORPION, 0xC9A45C, 0x6B4A1F);
		spawnEgg("yeti", ModEntities.YETI, 0xEAF4FA, 0x8FB4CC);
		spawnEgg("jaguar", ModEntities.JAGUAR, 0xD8A63A, 0x2B2410);
		spawnEgg("bog_hag", ModEntities.BOG_HAG, 0x4F6B3A, 0x2A2E1C);
		spawnEgg("lion", ModEntities.LION, 0xCDA25E, 0x7A4A1C);
		spawnEgg("elk", ModEntities.ELK, 0x7B5A3C, 0xD9C7A0);
		spawnEgg("stone_troll", ModEntities.STONE_TROLL, 0x7D7D7D, 0x3B3B3B);
		spawnEgg("reef_shark", ModEntities.REEF_SHARK, 0x6F8CA0, 0xE6EEF2);
		spawnEgg("wendigo", ModEntities.WENDIGO, 0x2E2A2A, 0xE0DDD0);
		spawnEgg("canyon_boar", ModEntities.CANYON_BOAR, 0x9A4E2E, 0x3D1E12);
		spawnEgg("bison", ModEntities.BISON, 0x3A2A20, 0x8A6A4A);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(e -> e.add(BEAR_STEAK));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e -> e.add(BEAR_HIDE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
			e.add(BEAR_HELMET);
			e.add(BEAR_CHESTPLATE);
			e.add(BEAR_LEGGINGS);
			e.add(BEAR_BOOTS);
		});
	}
}

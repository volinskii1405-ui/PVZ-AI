package dev.actest.module;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * Временный модификатор атрибута игрока на клиенте (в NBT не сохраняется).
 * Сервер иногда присылает атрибуты заново и стирает клиентские модификаторы,
 * а при возрождении игрок пересоздаётся — поэтому модули вызывают apply() каждый тик.
 */
final class AttributeBoost {
	private final RegistryEntry<EntityAttribute> attribute;
	private final Identifier id;

	AttributeBoost(RegistryEntry<EntityAttribute> attribute, String name) {
		this.attribute = attribute;
		this.id = Identifier.of("actest", name);
	}

	/** Довести значение атрибута до target (без учёта других модификаторов): модификатор = target − база. */
	void apply(ClientPlayerEntity player, double target) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		double bonus = target - instance.getBaseValue();
		EntityAttributeModifier current = instance.getModifier(id);
		if (current != null && current.value() == bonus) {
			return;
		}
		instance.removeModifier(id);
		instance.addTemporaryModifier(new EntityAttributeModifier(id, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
	}

	void remove(ClientPlayerEntity player) {
		if (player == null) {
			return;
		}
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}

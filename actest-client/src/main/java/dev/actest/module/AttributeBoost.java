package dev.actest.module;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * Временный модификатор атрибута игрока, доводящий значение до заданного.
 */
final class AttributeBoost {
   private final RegistryEntry<EntityAttribute> attribute;
   private final Identifier id;

   AttributeBoost(RegistryEntry<EntityAttribute> attribute, String name) {
      this.attribute = attribute;
      this.id = Identifier.of("actest", name);
   }

   void apply(ClientPlayerEntity player, double target) {
      EntityAttributeInstance instance = player.getAttributeInstance(this.attribute);
      if (instance != null) {
         double bonus = target - instance.getBaseValue();
         EntityAttributeModifier current = instance.getModifier(this.id);
         if (current == null || current.value() != bonus) {
            instance.removeModifier(this.id);
            instance.addTemporaryModifier(new EntityAttributeModifier(this.id, bonus, Operation.ADD_VALUE));
         }
      }
   }

   void remove(ClientPlayerEntity player) {
      if (player != null) {
         EntityAttributeInstance instance = player.getAttributeInstance(this.attribute);
         if (instance != null) {
            instance.removeModifier(this.id);
         }
      }
   }
}

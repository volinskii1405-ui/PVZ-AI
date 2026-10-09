package dev.actest.module;

import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Reach: повышает атрибуты дальности взаимодействия с сущностями и блоками.
 */
public final class ReachModule extends AbstractModule {
   private final AttributeBoost entityRange = new AttributeBoost(EntityAttributes.ENTITY_INTERACTION_RANGE, "reach_entity");
   private final AttributeBoost blockRange = new AttributeBoost(EntityAttributes.BLOCK_INTERACTION_RANGE, "reach_block");

   public ReachModule() {
      super("Reach", "key.actest.reach", 75);
   }

   @Override
   public String getHudInfo() {
      return String.format(Locale.ROOT, "%.1f", ActestConfig.get().reach.entityRange);
   }

   @Override
   public void onTick(MinecraftClient client) {
      ActestConfig.Reach cfg = ActestConfig.get().reach;
      this.entityRange.apply(client.player, cfg.entityRange);
      if (cfg.blocks) {
         this.blockRange.apply(client.player, cfg.blockRange);
      } else {
         this.blockRange.remove(client.player);
      }
   }

   @Override
   protected void onDisable() {
      this.entityRange.remove(MinecraftClient.getInstance().player);
      this.blockRange.remove(MinecraftClient.getInstance().player);
   }
}

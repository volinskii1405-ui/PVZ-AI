package dev.actest.module;

import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Step: повышает атрибут высоты шага (ваниль 0.6) временным модификатором.
 */
public final class StepModule extends AbstractModule {
   private final AttributeBoost boost = new AttributeBoost(EntityAttributes.STEP_HEIGHT, "step");

   public StepModule() {
      super("Step", "key.actest.step", 74);
   }

   @Override
   public String getHudInfo() {
      return String.format(Locale.ROOT, "%.1f", ActestConfig.get().step.height);
   }

   @Override
   public void onTick(MinecraftClient client) {
      this.boost.apply(client.player, ActestConfig.get().step.height);
   }

   @Override
   protected void onDisable() {
      this.boost.remove(MinecraftClient.getInstance().player);
   }
}

package dev.actest.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/**
 * Общий интерфейс модуля. Новый модуль = класс с этим интерфейсом (обычно через AbstractModule)
 * + одна строка register(...) в ModuleManager.
 */
public interface Module {
   String getName();

   KeyBinding getKeyBinding();

   boolean isEnabled();

   void setEnabled(boolean e);

   default void toggle() {
      this.setEnabled(!this.isEnabled());
   }

   default void onTick(MinecraftClient client) {
   }

   default void onRender(DrawContext context, float tickDelta) {
   }

   default String getHudInfo() {
      return "";
   }
}

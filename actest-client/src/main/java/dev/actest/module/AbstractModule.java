package dev.actest.module;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

/**
 * Базовая реализация модуля: имя, клавиша (регистрируется в «Управлении» → AC Test Client)
 * и флаг включения с хуками onEnable/onDisable.
 */
public abstract class AbstractModule implements Module {
   private final String name;
   private final KeyBinding keyBinding;
   private boolean enabled;

   protected AbstractModule(String name, String keyTranslation, int defaultKey) {
      this.name = name;
      this.keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(keyTranslation, Type.KEYSYM, defaultKey, "category.actest"));
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public KeyBinding getKeyBinding() {
      return this.keyBinding;
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (enabled) {
            this.onEnable();
         } else {
            this.onDisable();
         }
      }
   }

   protected void onEnable() {
   }

   protected void onDisable() {
   }
}

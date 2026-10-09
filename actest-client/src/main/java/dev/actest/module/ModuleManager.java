package dev.actest.module;

import dev.actest.config.ActestConfig;
import dev.actest.gui.ActestScreen;
import dev.actest.render.ModuleListHud;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Хранит модули, обрабатывает их клавиши, вызывает onTick/onRender и выключает всё,
 * если текущий сервер не входит в allowedServers.
 */
public final class ModuleManager {
   private final List<Module> modules = new ArrayList<>();
   private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
   private final KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.actest.menu", Type.KEYSYM, 344, "category.actest"));
   private final MovementLog movementLog = new MovementLog();
   private int configCheckTimer;

   public ModuleManager() {
      this.register(new SpeedModule());
      this.register(new FlyModule());
      this.register(new NoFallModule());
      this.register(new NoSlowModule());
      this.register(new StepModule());
      this.register(new JesusModule());
      this.register(new BlinkModule());
      this.register(new ScaffoldModule());
      this.register(new FastBreakModule());
      this.register(new ChestStealerModule());
      this.register(new ReachModule());
      this.register(new KillAuraModule());
      this.register(new AutoClickerModule());
      this.register(new CriticalsModule());
      this.register(new VelocityModule());
      this.register(new AutoTotemModule());
      this.register(new WallhackModule());
   }

   private void register(Module module) {
      this.modules.add(module);
      this.byType.put((Class<? extends Module>)module.getClass(), module);
   }

   public List<Module> all() {
      return Collections.unmodifiableList(this.modules);
   }

   public <T extends Module> T get(Class<T> type) {
      return type.cast(this.byType.get(type));
   }

   public KeyBinding getMenuKey() {
      return this.menuKey;
   }

   public boolean canEnableModules(MinecraftClient client) {
      return ServerGuard.isAllowed(client);
   }

   public void onTick(MinecraftClient client) {
      if (++this.configCheckTimer >= 20) {
         this.configCheckTimer = 0;
         ActestConfig.reloadIfChanged();
      }

      while (this.menuKey.wasPressed()) {
         if (client.currentScreen == null) {
            client.setScreen(new ActestScreen(this));
         }
      }

      boolean allowed = ServerGuard.isAllowed(client);

      for (Module module : this.modules) {
         while (module.getKeyBinding().wasPressed()) {
            if (!module.isEnabled() && !allowed) {
               notify(
                  client, Text.literal(module.getName() + ": сервер не в allowedServers (config/actest.json)").formatted(Formatting.RED)
               );
            } else {
               module.toggle();
               notify(
                  client,
                  Text.literal(module.getName() + (module.isEnabled() ? ": ВКЛ" : ": выкл"))
                     .formatted(module.isEnabled() ? Formatting.GREEN : Formatting.GRAY)
               );
            }
         }

         if (module.isEnabled()) {
            if (allowed) {
               module.onTick(client);
            } else {
               module.setEnabled(false);
            }
         }
      }

      if (allowed && ActestConfig.get().debugLog) {
         this.movementLog.tick(client.player, this.modules, this.get(NoFallModule.class));
      } else {
         this.movementLog.reset();
      }
   }

   public void onHudRender(DrawContext context, float tickDelta) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null && !client.options.hudHidden) {
         for (Module module : this.modules) {
            if (module.isEnabled()) {
               module.onRender(context, tickDelta);
            }
         }

         ModuleListHud.render(context, client.textRenderer, this.modules);
      }
   }

   private static void notify(MinecraftClient client, Text text) {
      if (client.player != null) {
         client.player.sendMessage(text, true);
      }
   }
}

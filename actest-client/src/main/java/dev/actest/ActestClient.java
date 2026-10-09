package dev.actest;

import dev.actest.config.ActestConfig;
import dev.actest.module.ModuleManager;
import dev.actest.render.Projection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа клиентского мода: загружает конфиг, создаёт модули и подписывается
 * на события Fabric API (тик клиента, рендер мира и HUD).
 */
public final class ActestClient implements ClientModInitializer {
   public static final String MOD_ID = "actest";
   public static final String KEY_CATEGORY = "category.actest";
   public static final Logger LOGGER = LoggerFactory.getLogger("actest");
   private static ModuleManager modules;

   public void onInitializeClient() {
      ActestConfig.load();
      modules = new ModuleManager();
      ClientTickEvents.END_CLIENT_TICK.register(modules::onTick);
      WorldRenderEvents.LAST.register(Projection::capture);
      HudRenderCallback.EVENT.register((context, tickCounter) -> modules.onHudRender(context, tickCounter.getTickDelta(true)));
      LOGGER.info("AC Test Client загружен, конфиг: {}", ActestConfig.path());
   }

   public static ModuleManager modules() {
      return modules;
   }
}

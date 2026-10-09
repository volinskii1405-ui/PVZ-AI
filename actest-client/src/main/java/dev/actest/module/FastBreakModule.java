package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.lang.reflect.Field;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;

/**
 * FastBreak — досрочно завершает ломание блока.
 *
 * Ванилла копит прогресс ломания в ClientPlayerInteractionManager.currentBreakingProgress и, дойдя до 1.0,
 * шлёт PlayerActionC2SPacket STOP_DESTROY_BLOCK. Модуль, увидев прогресс >= breakAt, выставляет 1.0 —
 * на следующем тике ванилла сама отправляет STOP и ломает блок. noDelay обнуляет ванильную паузу
 * в 5 тиков между блоками. Поля приватные — доступ через reflection (имя intermediary в собранном моде,
 * имя Yarn при запуске из IDE).
 */
public final class FastBreakModule extends AbstractModule {
   private static final Field PROGRESS = findField("field_3715", "currentBreakingProgress");
   private static final Field COOLDOWN = findField("field_3716", "blockBreakingCooldown");

   public FastBreakModule() {
      super("FastBreak", "key.actest.fastbreak", -1); // без клавиши по умолчанию
   }

   @Override
   public String getHudInfo() {
      ActestConfig.FastBreak cfg = ActestConfig.get().fastBreak;
      return String.format(Locale.ROOT, "%.0f%%%s", cfg.breakAt * 100.0, cfg.noDelay ? " nd" : "");
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerInteractionManager manager = client.interactionManager;
      if (manager == null || PROGRESS == null || client.player.isCreative()) {
         return;
      }

      ActestConfig.FastBreak cfg = ActestConfig.get().fastBreak;
      try {
         if (cfg.noDelay && COOLDOWN != null && COOLDOWN.getInt(manager) > 0) {
            COOLDOWN.setInt(manager, 0);
         }

         if (manager.isBreakingBlock()) {
            float progress = PROGRESS.getFloat(manager);
            if (progress >= cfg.breakAt && progress < 1.0F) {
               PROGRESS.setFloat(manager, 1.0F);
               if (ActestConfig.get().debugLog) {
                  ActestClient.LOGGER.info(String.format(
                     Locale.ROOT, "[FastBreak] age=%d progress=%.3f breakAt=%.2f", client.player.age, progress, cfg.breakAt
                  ));
               }
            }
         }
      } catch (IllegalAccessException e) {
         ActestClient.LOGGER.warn("FastBreak: нет доступа к полям ClientPlayerInteractionManager", e);
         this.setEnabled(false);
      }
   }

   private static Field findField(String... names) {
      for (String name : names) {
         try {
            Field field = ClientPlayerInteractionManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (ReflectiveOperationException | RuntimeException ignored) {
            // пробуем следующее имя
         }
      }

      ActestClient.LOGGER.warn("FastBreak: поле {} не найдено, модуль работать не будет", names[0]);
      return null;
   }
}

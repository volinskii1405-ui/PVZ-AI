package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.HitResult;

/**
 * AutoClicker — «нажимает» ЛКМ/ПКМ с заданным случайным CPS.
 *
 * Клик эмулируется так же, как его регистрирует сама игра: KeyBinding.onKeyPressed() для клавиши
 * атаки/использования. На следующем тике ванилла обрабатывает нажатие как обычный клик
 * (doAttack / doItemUse) — со взмахом, штрафом за промах и т.д.
 */
public final class AutoClickerModule extends AbstractModule {
   /** Больше кликов за один тик (50 мс) не делаем, даже если CPS очень высокий. */
   private static final int MAX_CLICKS_PER_TICK = 3;

   private final Random random = new Random();
   private final AutoClickerModule.Clicker left = new AutoClickerModule.Clicker();
   private final AutoClickerModule.Clicker right = new AutoClickerModule.Clicker();

   public AutoClickerModule() {
      super("AutoClicker", "key.actest.autoclicker", 66); // B
   }

   @Override
   public String getHudInfo() {
      ActestConfig.AutoClicker cfg = ActestConfig.get().autoClicker;
      String buttons = (cfg.left ? "L" : "") + (cfg.right ? "R" : "");
      return String.format(Locale.ROOT, "%.0f-%.0f %s", Math.min(cfg.minCps, cfg.maxCps), Math.max(cfg.minCps, cfg.maxCps), buttons);
   }

   @Override
   protected void onEnable() {
      this.left.reset();
      this.right.reset();
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      ActestConfig.AutoClicker cfg = ActestConfig.get().autoClicker;
      boolean noScreen = client.currentScreen == null && !player.isSpectator();
      double minCps = Math.min(cfg.minCps, cfg.maxCps);
      double maxCps = Math.max(cfg.minCps, cfg.maxCps);

      KeyBinding attackKey = client.options.attackKey;
      boolean leftActive = noScreen
         && cfg.left
         && (!cfg.holdOnly || attackKey.isPressed())
         && !player.isUsingItem()
         && !(cfg.ignoreBlocks && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK);
      this.click(attackKey, "L", this.left.tick(leftActive, minCps, maxCps, this.random), player, cfg);

      KeyBinding useKey = client.options.useKey;
      boolean rightActive = noScreen && cfg.right && (!cfg.holdOnly || useKey.isPressed());
      this.click(useKey, "R", this.right.tick(rightActive, minCps, maxCps, this.random), player, cfg);
   }

   private void click(KeyBinding key, String button, int clicks, ClientPlayerEntity player, ActestConfig.AutoClicker cfg) {
      for (int i = 0; i < clicks; i++) {
         KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(key));
      }

      if (clicks > 0 && ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(Locale.ROOT, "[AutoClicker] age=%d button=%s clicks=%d", player.age, button, clicks));
      }
   }

   /** Планировщик кликов одной кнопки: интервалы случайные в [1000/maxCps, 1000/minCps] мс. */
   private static final class Clicker {
      private double budgetMs;
      private double intervalMs = -1.0;

      void reset() {
         this.budgetMs = 0.0;
         this.intervalMs = -1.0;
      }

      int tick(boolean active, double minCps, double maxCps, Random random) {
         if (!active) {
            this.reset();
            return 0;
         }

         if (this.intervalMs < 0.0) {
            // Первый «авто» клик — через интервал после начала: сам зажим кнопки ванилла уже засчитала.
            this.intervalMs = nextInterval(minCps, maxCps, random);
         }

         this.budgetMs += 50.0;
         int clicks = 0;

         while (this.budgetMs >= this.intervalMs && clicks < MAX_CLICKS_PER_TICK) {
            this.budgetMs -= this.intervalMs;
            this.intervalMs = nextInterval(minCps, maxCps, random);
            clicks++;
         }

         this.budgetMs = Math.min(this.budgetMs, this.intervalMs);
         return clicks;
      }

      private static double nextInterval(double minCps, double maxCps, Random random) {
         double cps = minCps + random.nextDouble() * (maxCps - minCps);
         return 1000.0 / cps;
      }
   }
}

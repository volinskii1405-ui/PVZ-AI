package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * ChestStealer — выгребает открытый сундук / бочку / эндер-сундук / шалкер.
 *
 * Shift-клик (ClickSlotC2SPacket, QUICK_MOVE) по каждому непустому слоту контейнера через
 * ClientPlayerInteractionManager.clickSlot() — ровно то, что делает игрок мышью, только быстрее.
 */
public final class ChestStealerModule extends AbstractModule {
   /** syncId обрабатываемого контейнера; -1 — контейнер не открыт. */
   private int syncId = -1;
   private int timer;
   private int nextSlot;
   private int taken;

   public ChestStealerModule() {
      super("ChestStealer", "key.actest.cheststealer", -1); // без клавиши по умолчанию
   }

   @Override
   public String getHudInfo() {
      ActestConfig.ChestStealer cfg = ActestConfig.get().chestStealer;
      return String.format(Locale.ROOT, "d%d s%d", cfg.delay, cfg.startDelay);
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      ScreenHandler handler = player.currentScreenHandler;
      int containerSlots = containerSlots(handler);
      if (containerSlots <= 0 || client.interactionManager == null) {
         this.syncId = -1;
         return;
      }

      ActestConfig.ChestStealer cfg = ActestConfig.get().chestStealer;
      if (handler.syncId != this.syncId) {
         // Открыт новый контейнер: ждём startDelay (заодно успевает прийти его содержимое).
         this.syncId = handler.syncId;
         this.timer = cfg.startDelay;
         this.nextSlot = 0;
         this.taken = 0;
      }

      if (this.timer > 0) {
         this.timer--;
         return;
      }

      while (this.nextSlot < containerSlots) {
         Slot slot = handler.getSlot(this.nextSlot);
         if (!slot.hasStack()) {
            this.nextSlot++;
            continue;
         }

         client.interactionManager.clickSlot(handler.syncId, this.nextSlot, 0, SlotActionType.QUICK_MOVE, player);
         this.taken++;
         if (ActestConfig.get().debugLog) {
            ActestClient.LOGGER.info(String.format(
               Locale.ROOT, "[ChestStealer] age=%d syncId=%d slot=%d taken=%d", player.age, handler.syncId, this.nextSlot, this.taken
            ));
         }

         // Предмет остался в слоте — инвентарь полон, дальше брать некуда.
         this.nextSlot = slot.hasStack() ? containerSlots : this.nextSlot + 1;
         if (cfg.delay > 0) {
            this.timer = cfg.delay;
            return;
         }
      }

      if (cfg.autoClose) {
         player.closeHandledScreen(); // CloseHandledScreenC2SPacket + закрыть экран
         this.syncId = -1;
      }
   }

   /** Сколько слотов в начале списка принадлежат контейнеру (дальше идёт инвентарь игрока). */
   private static int containerSlots(ScreenHandler handler) {
      if (handler instanceof GenericContainerScreenHandler generic) {
         return generic.getRows() * 9;
      }

      return handler instanceof ShulkerBoxScreenHandler ? 27 : 0;
   }
}

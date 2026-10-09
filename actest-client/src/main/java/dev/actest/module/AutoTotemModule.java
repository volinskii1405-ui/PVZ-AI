package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

/**
 * AutoTotem — держит тотем бессмертия во второй руке.
 *
 * Перекладывание — обычные клики по инвентарю игрока (ClickSlotC2SPacket, syncId 0),
 * отправленные через ClientPlayerInteractionManager.clickSlot(), без открытия экрана инвентаря.
 */
public final class AutoTotemModule extends AbstractModule {
   private static final Identifier TOTEM_ID = Identifier.ofVanilla("totem_of_undying");
   /** Индекс слота второй руки в PlayerScreenHandler. */
   private static final int OFFHAND_HANDLER_SLOT = 45;
   /** Для SlotActionType.SWAP номер «кнопки» 40 = вторая рука (как клавиша F в открытом инвентаре). */
   private static final int OFFHAND_SWAP_BUTTON = 40;

   /** Сколько тиков подряд во второй руке нет тотема (для задержки «реакции»). */
   private int missingTicks;

   public AutoTotemModule() {
      super("AutoTotem", "key.actest.autototem", 89); // Y
   }

   @Override
   public String getHudInfo() {
      ActestConfig.AutoTotem cfg = ActestConfig.get().autoTotem;
      String mode = cfg.mode == ActestConfig.AutoTotem.Mode.HEALTH ? String.format(Locale.ROOT, "<=%.0fHP", cfg.health) : "ALWAYS";
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      int count = player == null ? 0 : countTotems(player);
      return String.format(Locale.ROOT, "%s %s x%d", mode, cfg.method, count);
   }

   @Override
   protected void onEnable() {
      this.missingTicks = 0;
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player.isCreative() || player.isSpectator() || isTotem(player.getOffHandStack())) {
         this.missingTicks = 0;
         return;
      }

      ActestConfig.AutoTotem cfg = ActestConfig.get().autoTotem;
      if (cfg.mode == ActestConfig.AutoTotem.Mode.HEALTH && player.getHealth() + player.getAbsorptionAmount() > cfg.health) {
         this.missingTicks = 0;
         return;
      }

      // Клики по инвентарю игрока сервер примет, только если не открыт другой контейнер (сундук и т.п.),
      // а курсор пуст — иначе мы перемешаем то, что игрок держит мышью.
      PlayerScreenHandler handler = player.playerScreenHandler;
      if (player.currentScreenHandler != handler || !handler.getCursorStack().isEmpty()) {
         return;
      }

      if (this.missingTicks++ < cfg.delay) {
         return;
      }

      int slot = findTotem(player.getInventory());
      if (slot < 0) {
         return;
      }

      // Инвентарь игрока: 0-8 хотбар, 9-35 основной. В PlayerScreenHandler хотбар — это слоты 36-44.
      int handlerSlot = slot < 9 ? 36 + slot : slot;
      int syncId = handler.syncId;
      switch (cfg.method) {
         case SWAP:
            // Один пакет: SWAP слота с тотемом и второй руки.
            client.interactionManager.clickSlot(syncId, handlerSlot, OFFHAND_SWAP_BUTTON, SlotActionType.SWAP, player);
            break;
         case PICKUP:
            // Классика: взять тотем → положить во вторую руку → вернуть старый предмет на место тотема.
            client.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(syncId, OFFHAND_HANDLER_SLOT, 0, SlotActionType.PICKUP, player);
            if (!handler.getCursorStack().isEmpty()) {
               client.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.PICKUP, player);
            }
      }

      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT,
            "[AutoTotem] age=%d slot=%d method=%s waited=%d hp=%.1f",
            player.age,
            handlerSlot,
            cfg.method,
            this.missingTicks - 1,
            player.getHealth() + player.getAbsorptionAmount()
         ));
      }

      this.missingTicks = 0;
   }

   private static int findTotem(PlayerInventory inventory) {
      for (int i = 0; i < 36; i++) {
         if (isTotem(inventory.getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   private static int countTotems(ClientPlayerEntity player) {
      int count = 0;
      PlayerInventory inventory = player.getInventory();
      for (int i = 0; i < 36; i++) {
         if (isTotem(inventory.getStack(i))) {
            count++;
         }
      }

      return count + (isTotem(player.getOffHandStack()) ? 1 : 0);
   }

   /** Тотем определяется по id предмета minecraft:totem_of_undying. */
   private static boolean isTotem(ItemStack stack) {
      return !stack.isEmpty() && stack.getRegistryEntry().matchesId(TOTEM_ID);
   }
}

package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Scaffold — ставит блок под ноги, когда игрок сходит с края.
 *
 * Установка — обычный PlayerInteractBlockC2SPacket через ClientPlayerInteractionManager.interactBlock():
 * клик по грани соседнего блока (снизу или сбоку), как если бы игрок сам навёл прицел и нажал ПКМ.
 * Блок берётся из хотбара; при необходимости слот временно переключается (UpdateSelectedSlotC2SPacket).
 */
public final class ScaffoldModule extends AbstractModule {
   /** Смещения к соседям целевой позиции: снизу, север, юг, запад, восток. */
   private static final int[][] NEIGHBORS = {{0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
   /** Грань соседа, по которой кликаем (смотрит на целевую позицию). */
   private static final Direction[] FACES = {Direction.UP, Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};

   private int cooldown;
   /** PACKET: сервер «смотрит» на точку установки — на следующем тике без установки вернём настоящий взгляд. */
   private boolean restoreLook;

   public ScaffoldModule() {
      super("Scaffold", "key.actest.scaffold", 73); // I
   }

   @Override
   public String getHudInfo() {
      ActestConfig.Scaffold cfg = ActestConfig.get().scaffold;
      return String.format(Locale.ROOT, "%s d%d", cfg.rotation, cfg.delay);
   }

   @Override
   protected void onEnable() {
      this.cooldown = 0;
      this.restoreLook = false;
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         this.sendRealLookIfNeeded(player);
      }
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      ClientWorld world = client.world;
      if (client.interactionManager == null || client.currentScreen != null || player.isSpectator() || player.getAbilities().flying) {
         this.sendRealLookIfNeeded(player);
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         this.sendRealLookIfNeeded(player);
         return;
      }

      // Целевая позиция — блок прямо под ногами.
      BlockPos target = BlockPos.ofFloored(player.getX(), player.getY() - 1.0, player.getZ());
      if (!world.getBlockState(target).isReplaceable()) {
         this.sendRealLookIfNeeded(player);
         return;
      }

      PlayerInventory inventory = player.getInventory();
      int slot = findBlockSlot(inventory, world, target);
      if (slot < 0) {
         this.sendRealLookIfNeeded(player);
         return;
      }

      int tx = target.getX();
      int ty = target.getY();
      int tz = target.getZ();
      int side = -1;
      BlockPos neighbor = null;

      for (int i = 0; i < NEIGHBORS.length; i++) {
         BlockPos candidate = BlockPos.ofFloored(tx + NEIGHBORS[i][0], ty + NEIGHBORS[i][1], tz + NEIGHBORS[i][2]);
         if (!world.getBlockState(candidate).isReplaceable()) {
            side = i;
            neighbor = candidate;
            break;
         }
      }

      if (neighbor == null) {
         this.sendRealLookIfNeeded(player); // в воздухе без опоры ванилла ставить не даёт
         return;
      }

      // Точка клика — центр общей грани соседа и целевой позиции.
      Vec3d hit = new Vec3d(
         tx + 0.5 + NEIGHBORS[side][0] * 0.5,
         ty + 0.5 + NEIGHBORS[side][1] * 0.5,
         tz + 0.5 + NEIGHBORS[side][2] * 0.5
      );
      ActestConfig.Scaffold cfg = ActestConfig.get().scaffold;
      float[] rotation = Rotations.to(player.getEyePos(), hit);
      rotation[0] = Rotations.continuousYaw(player.getYaw(), rotation[0]);
      if (cfg.rotation == ActestConfig.Scaffold.Rotation.PACKET) {
         Rotations.sendLook(player, rotation[0], rotation[1]);
         this.restoreLook = true;
      }

      int previousSlot = inventory.selectedSlot;
      inventory.selectedSlot = slot;
      ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hit, FACES[side], neighbor, false));
      boolean placed = result instanceof ActionResult.Success;
      if (placed && cfg.swing) {
         player.swingHand(Hand.MAIN_HAND);
      }

      if (cfg.switchBack) {
         // Сервер получит возврат слота на следующем тике (ClientPlayerInteractionManager.tick → syncSelectedSlot).
         inventory.selectedSlot = previousSlot;
      }

      this.cooldown = cfg.delay;
      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT,
            "[Scaffold] age=%d pos=%d,%d,%d against=%s slot=%d->%d placed=%b rot=%s yaw=%.2f pitch=%.2f",
            player.age,
            tx,
            ty,
            tz,
            FACES[side],
            previousSlot,
            slot,
            placed,
            cfg.rotation,
            rotation[0],
            rotation[1]
         ));
      }
   }

   /** Слот хотбара (0–8) с полноценным блоком: сначала текущий, потом по порядку. */
   private static int findBlockSlot(PlayerInventory inventory, ClientWorld world, BlockPos pos) {
      if (isFullBlock(inventory.getStack(inventory.selectedSlot), world, pos)) {
         return inventory.selectedSlot;
      }

      for (int i = 0; i < 9; i++) {
         if (isFullBlock(inventory.getStack(i), world, pos)) {
            return i;
         }
      }

      return -1;
   }

   private static boolean isFullBlock(ItemStack stack, ClientWorld world, BlockPos pos) {
      return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().getDefaultState().isFullCube(world, pos);
   }

   private void sendRealLookIfNeeded(ClientPlayerEntity player) {
      if (this.restoreLook) {
         this.restoreLook = false;
         Rotations.sendLook(player, player.getYaw(), player.getPitch());
      }
   }
}

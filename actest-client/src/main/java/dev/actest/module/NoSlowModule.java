package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * NoSlow: скорость ходьбы без замедления от использования предметов и от песка душ/мёда.
 */
public final class NoSlowModule extends AbstractModule {
   public NoSlowModule() {
      super("NoSlow", "key.actest.noslow", InputUtil.UNKNOWN_KEY.getCode());
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player.isOnGround() && !player.hasVehicle() && !player.getAbilities().flying && !player.isSpectator()) {
         Vec3d dir = MovementUtil.inputDirection(client.options, player.getYaw());
         if (dir != null) {
            ActestConfig.NoSlow cfg = ActestConfig.get().noSlow;
            boolean usingItem = cfg.items && player.isUsingItem();
            boolean slowBlock = cfg.blocks && isOnSlowBlock(player);
            if (usingItem || slowBlock) {
               SpeedModule speed = ActestClient.modules().get(SpeedModule.class);
               if (speed == null || !speed.canApply(player)) {
                  double accel = MovementUtil.groundAccel(player);
                  double vanillaAccel = usingItem ? accel * 0.2 : accel;
                  MovementUtil.setHorizontal(player, dir, 2.2026431718061676 * accel - vanillaAccel);
               }
            }
         }
      }
   }

   private static boolean isOnSlowBlock(ClientPlayerEntity player) {
      BlockPos feet = player.getBlockPos();
      BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 0.5000001, player.getZ());
      return player.getWorld().getBlockState(feet).getBlock().getVelocityMultiplier() < 1.0F
         || player.getWorld().getBlockState(below).getBlock().getVelocityMultiplier() < 1.0F;
   }
}

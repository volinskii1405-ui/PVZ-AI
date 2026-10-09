package dev.actest.module;

import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;

/**
 * Speed: GROUND — увеличивает скорость ходьбы по земле, BHOP — автопрыжок с разгоном.
 * Направление берётся из WASD и yaw камеры; вертикальная скорость (прыжок, гравитация) не трогается.
 */
public final class SpeedModule extends AbstractModule {
   private static final double SPRINT_JUMP_BOOST = 0.2;

   public SpeedModule() {
      super("Speed", "key.actest.speed", 82);
   }

   @Override
   public String getHudInfo() {
      ActestConfig.Speed cfg = ActestConfig.get().speed;
      return String.format(Locale.ROOT, "%s x%.2f", cfg.mode, cfg.multiplier);
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (this.canApply(player)) {
         Vec3d dir = MovementUtil.inputDirection(client.options, player.getYaw());
         if (dir != null) {
            ActestConfig.Speed cfg = ActestConfig.get().speed;
            double accel = MovementUtil.groundAccel(player);
            switch (cfg.mode) {
               case GROUND:
                  tickGround(player, dir, accel, cfg.multiplier);
                  break;
               case BHOP:
                  tickBhop(player, dir, accel, cfg.multiplier);
            }
         }
      }
   }

   boolean canApply(ClientPlayerEntity player) {
      return this.isEnabled()
         && !player.isSpectator()
         && !player.getAbilities().flying
         && !player.hasVehicle()
         && !player.isTouchingWater()
         && !player.isInLava()
         && !player.isClimbing()
         && !player.isUsingItem()
         && player.getPose() == EntityPose.STANDING;
   }

   private static void tickGround(ClientPlayerEntity player, Vec3d dir, double accel, double mult) {
      if (player.isOnGround()) {
         MovementUtil.setHorizontal(player, dir, (2.2026431718061676 * mult - 1.0) * accel);
      }
   }

   private static void tickBhop(ClientPlayerEntity player, Vec3d dir, double accel, double mult) {
      if (player.isOnGround()) {
         player.jump();
         double jumpSpeed = 1.2026431718061676 * accel + (player.isSprinting() ? 0.2 : 0.0);
         MovementUtil.setHorizontal(player, dir, jumpSpeed * mult);
      } else {
         Vec3d v = player.getVelocity();
         MovementUtil.setHorizontal(player, dir, Math.hypot(v.x, v.z));
      }
   }
}

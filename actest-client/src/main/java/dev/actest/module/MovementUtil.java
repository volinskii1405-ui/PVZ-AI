package dev.actest.module;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

/**
 * Общая математика движения: направление ввода WASD и ванильные коэффициенты ускорения/трения.
 */
final class MovementUtil {
   static final double GROUND_DRAG = 0.546;
   static final double STEADY_VELOCITY_FACTOR = 1.2026431718061676;
   static final double STEADY_STEP_FACTOR = 2.2026431718061676;
   static final double INPUT_FACTOR = 0.98;
   static final double USING_ITEM_FACTOR = 0.2;

   private MovementUtil() {
   }

   static double groundAccel(ClientPlayerEntity player) {
      return (double)player.getMovementSpeed() * 0.98;
   }

   static void setHorizontal(ClientPlayerEntity player, Vec3d dir, double speed) {
      Vec3d v = player.getVelocity();
      player.setVelocity(dir.x * speed, v.y, dir.z * speed);
   }

   static Vec3d inputDirection(GameOptions options, float yawDegrees) {
      double forward = (double)((options.forwardKey.isPressed() ? 1 : 0) - (options.backKey.isPressed() ? 1 : 0));
      double strafe = (double)((options.leftKey.isPressed() ? 1 : 0) - (options.rightKey.isPressed() ? 1 : 0));
      if (forward == 0.0 && strafe == 0.0) {
         return null;
      } else {
         double length = Math.sqrt(forward * forward + strafe * strafe);
         forward /= length;
         strafe /= length;
         double yaw = Math.toRadians((double)yawDegrees);
         double sin = Math.sin(yaw);
         double cos = Math.cos(yaw);
         return new Vec3d(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
      }
   }
}

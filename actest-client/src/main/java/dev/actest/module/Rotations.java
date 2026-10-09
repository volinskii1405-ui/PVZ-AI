package dev.actest.module;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/** Общая математика поворотов (KillAura, Scaffold) и «тихий» поворот пакетом. */
final class Rotations {
   private Rotations() {
   }

   /** Углы yaw/pitch (как у Minecraft: yaw 0 = +Z, pitch вниз положительный), чтобы смотреть из from в to. */
   static float[] to(Vec3d from, Vec3d to) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
      return new float[]{yaw, pitch};
   }

   /**
    * yaw игрока не нормализован (может быть 1234°) — возвращает эквивалент targetYaw, ближайший к currentYaw,
    * чтобы не было ложного скачка на 360° ни в пакете, ни в интерполяции камеры.
    */
   static float continuousYaw(float currentYaw, float targetYaw) {
      return currentYaw + wrapDegrees(targetYaw - currentYaw);
   }

   static float angleBetween(float yaw1, float pitch1, float yaw2, float pitch2) {
      float dYaw = Math.abs(wrapDegrees(yaw2 - yaw1));
      float dPitch = Math.abs(pitch2 - pitch1);
      return (float)Math.sqrt(dYaw * dYaw + dPitch * dPitch);
   }

   static float wrapDegrees(float degrees) {
      float wrapped = degrees % 360.0F;
      if (wrapped >= 180.0F) {
         wrapped -= 360.0F;
      }

      if (wrapped < -180.0F) {
         wrapped += 360.0F;
      }

      return wrapped;
   }

   /** Отдельный PlayerMoveC2SPacket.LookAndOnGround: сервер «видит» этот поворот, камера игрока не двигается. */
   static void sendLook(ClientPlayerEntity player, float yaw, float pitch) {
      player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
   }
}

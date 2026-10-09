package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Velocity — ослабляет отбрасывание (knockback).
 *
 * Сервер присылает скорость пакетом EntityVelocityUpdateS2CPacket (удары, стрелы и т.п.) или добавку
 * к скорости в ExplosionS2CPacket (взрывы, заряды ветра). Миксин вызывает этот модуль сразу после того,
 * как клиент применил скорость, и модуль масштабирует её по горизонтали/вертикали.
 * Сервер своё отбрасывание не меняет — он видит, что игрок сместился меньше ожидаемого.
 */
public final class VelocityModule extends AbstractModule {
   /** Скорость игрока до обработки пакета взрыва (взрыв ДОБАВЛЯЕТ скорость, а не заменяет). */
   private Vec3d beforeExplosion;

   public VelocityModule() {
      super("Velocity", "key.actest.velocity", 90); // Z
   }

   @Override
   public String getHudInfo() {
      ActestConfig.Velocity cfg = ActestConfig.get().velocity;
      return String.format(Locale.ROOT, "H%.0f%% V%.0f%%", cfg.horizontal, cfg.vertical);
   }

   /** После EntityVelocityUpdateS2CPacket: скорость уже заменена присланной. */
   public void afterVelocityPacket(int entityId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (!this.isEnabled() || player == null || entityId != player.getId()) {
         return;
      }

      ActestConfig.Velocity cfg = ActestConfig.get().velocity;
      Vec3d v = player.getVelocity();
      this.apply(player, "packet", 0.0, 0.0, 0.0, v.x, v.y, v.z, cfg);
   }

   /** Перед ExplosionS2CPacket: запоминаем скорость, чтобы потом выделить добавку от взрыва. */
   public void beforeExplosion() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      this.beforeExplosion = this.isEnabled() && player != null && ActestConfig.get().velocity.explosions ? player.getVelocity() : null;
   }

   /** После ExplosionS2CPacket: ослабляем только добавку от взрыва. */
   public void afterExplosion() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      Vec3d before = this.beforeExplosion;
      this.beforeExplosion = null;
      if (before == null || player == null || !this.isEnabled()) {
         return;
      }

      Vec3d after = player.getVelocity();
      this.apply(
         player,
         "explosion",
         before.x,
         before.y,
         before.z,
         after.x - before.x,
         after.y - before.y,
         after.z - before.z,
         ActestConfig.get().velocity
      );
   }

   private void apply(ClientPlayerEntity player, String source, double baseX, double baseY, double baseZ, double kbX, double kbY, double kbZ, ActestConfig.Velocity cfg) {
      double h = cfg.horizontal / 100.0;
      double vert = cfg.vertical / 100.0;
      player.setVelocity(baseX + kbX * h, baseY + kbY * vert, baseZ + kbZ * h);
      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT,
            "[Velocity] age=%d source=%s server=(%.4f, %.4f, %.4f) applied=(%.4f, %.4f, %.4f)",
            player.age,
            source,
            kbX,
            kbY,
            kbZ,
            kbX * h,
            kbY * vert,
            kbZ * h
         ));
      }
   }
}

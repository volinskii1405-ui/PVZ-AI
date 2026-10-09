package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Criticals — критический удар, стоя на земле.
 *
 * Перед каждым ударом (ручным, KillAura, AutoClicker) уходят два PlayerMoveC2SPacket.PositionAndOnGround:
 * y + height и снова y, оба с onGround=false. Сервер видит «падение» на height блоков без касания земли
 * и засчитывает крит. Вызывается из миксина в начале ClientPlayerInteractionManager.attackEntity().
 */
public final class CriticalsModule extends AbstractModule {
   public CriticalsModule() {
      super("Criticals", "key.actest.criticals", 85); // U
   }

   @Override
   public String getHudInfo() {
      return String.format(Locale.ROOT, "+%.4f", ActestConfig.get().criticals.height);
   }

   public void beforeAttack(PlayerEntity attacker, Entity target) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (!this.isEnabled() || player == null || attacker != player || !(target instanceof LivingEntity)) {
         return;
      }

      // Условия ванильного крита, которые клиент может выполнить пакетами; в воде/лаве, на лестнице,
      // верхом и в спринте ванилла крит не засчитывает — тогда пакеты не шлём.
      if (!player.isOnGround()
         || player.isTouchingWater()
         || player.isInLava()
         || player.isClimbing()
         || player.hasVehicle()
         || player.isSprinting()) {
         return;
      }

      ActestConfig.Criticals cfg = ActestConfig.get().criticals;
      if (cfg.onlyWhenCharged && player.getAttackCooldownProgress(0.5F) < 0.9F) {
         return; // ванилла критует только заряженный (>90%) удар
      }

      double x = player.getX();
      double y = player.getY();
      double z = player.getZ();
      boolean collision = player.horizontalCollision;
      player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + cfg.height, z, false, collision));
      player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, collision));
      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT, "[Criticals] age=%d target=%s y=%.4f height=%.4f", player.age, target.getName().getString(), y, cfg.height
         ));
      }
   }
}

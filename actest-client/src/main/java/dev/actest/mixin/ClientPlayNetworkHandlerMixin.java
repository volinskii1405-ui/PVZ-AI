package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.module.ModuleManager;
import dev.actest.module.VelocityModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для Velocity: перехват скорости, которую сервер задаёт игроку (удары) и добавляет (взрывы).
 */
@Mixin({ClientPlayNetworkHandler.class})
public abstract class ClientPlayNetworkHandlerMixin {
   @Inject(
      method = {"onEntityVelocityUpdate"},
      at = {@At("TAIL")}
   )
   private void actest$afterVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      // До TAIL доходит только вызов в главном потоке (из сетевого метод перебрасывается туда сам).
      VelocityModule velocity = velocity();
      if (velocity != null) {
         velocity.afterVelocityPacket(packet.getEntityId());
      }
   }

   @Inject(
      method = {"onExplosion"},
      at = {@At("HEAD")}
   )
   private void actest$beforeExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
      // HEAD вызывается дважды: в сетевом потоке (дальше метод уходит в главный) и в главном — нужен второй.
      VelocityModule velocity = velocity();
      if (velocity != null && MinecraftClient.getInstance().isOnThread()) {
         velocity.beforeExplosion();
      }
   }

   @Inject(
      method = {"onExplosion"},
      at = {@At("TAIL")}
   )
   private void actest$afterExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
      VelocityModule velocity = velocity();
      if (velocity != null) {
         velocity.afterExplosion();
      }
   }

   private static VelocityModule velocity() {
      ModuleManager modules = ActestClient.modules();
      return modules == null ? null : modules.get(VelocityModule.class);
   }
}

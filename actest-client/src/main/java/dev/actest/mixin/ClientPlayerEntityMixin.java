package dev.actest.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.actest.ActestClient;
import dev.actest.module.ModuleManager;
import dev.actest.module.NoFallModule;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для NoFall: отслеживание падения и подмена onGround в sendMovementPackets().
 */
@Mixin({ClientPlayerEntity.class})
public abstract class ClientPlayerEntityMixin {
   @Inject(
      method = {"sendMovementPackets"},
      at = {@At("HEAD")}
   )
   private void actest$trackFall(CallbackInfo ci) {
      NoFallModule noFall = noFall();
      if (noFall != null) {
         noFall.beforeMovementPackets((ClientPlayerEntity)this);
      }
   }

   @ModifyExpressionValue(
      method = {"sendMovementPackets"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"
      )}
   )
   private boolean actest$spoofOnGround(boolean original) {
      if (original) {
         return true;
      } else {
         NoFallModule noFall = noFall();
         return noFall != null && noFall.shouldSpoofGround((ClientPlayerEntity)this);
      }
   }

   private static NoFallModule noFall() {
      ModuleManager modules = ActestClient.modules();
      return modules == null ? null : modules.get(NoFallModule.class);
   }
}

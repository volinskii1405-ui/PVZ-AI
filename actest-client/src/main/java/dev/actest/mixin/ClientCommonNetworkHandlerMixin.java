package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.module.BlinkModule;
import dev.actest.module.ModuleManager;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для Blink: перехват исходящих пакетов (ClientPlayNetworkHandler наследует этот sendPacket).
 */
@Mixin({ClientCommonNetworkHandler.class})
public abstract class ClientCommonNetworkHandlerMixin {
   @Inject(
      method = {"sendPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void actest$blink(Packet<?> packet, CallbackInfo ci) {
      ModuleManager modules = ActestClient.modules();
      BlinkModule blink = modules == null ? null : modules.get(BlinkModule.class);
      if (blink != null && blink.hold(packet)) {
         ci.cancel();
      }
   }
}

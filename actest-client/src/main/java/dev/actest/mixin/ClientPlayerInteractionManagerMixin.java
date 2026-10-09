package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.module.CriticalsModule;
import dev.actest.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для Criticals: пакеты «подскока» перед каждым ударом (ручным, KillAura, AutoClicker).
 */
@Mixin({ClientPlayerInteractionManager.class})
public abstract class ClientPlayerInteractionManagerMixin {
   @Inject(
      method = {"attackEntity"},
      at = {@At("HEAD")}
   )
   private void actest$criticals(PlayerEntity player, Entity target, CallbackInfo ci) {
      ModuleManager modules = ActestClient.modules();
      CriticalsModule criticals = modules == null ? null : modules.get(CriticalsModule.class);
      if (criticals != null) {
         criticals.beforeAttack(player, target);
      }
   }
}

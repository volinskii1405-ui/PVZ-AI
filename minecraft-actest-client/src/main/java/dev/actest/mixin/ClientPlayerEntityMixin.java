package dev.actest.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.actest.ActestClient;
import dev.actest.module.ModuleManager;
import dev.actest.module.NoFallModule;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NoFall / SPOOF: в ClientPlayerEntity#sendMovementPackets значение isOnGround()
 * идёт в поле onGround пакетов PlayerMoveC2SPacket (Full / PositionAndOnGround /
 * LookAndOnGround / OnGroundOnly). Подменяем его на true, пока NoFall этого хочет.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@ModifyExpressionValue(method = "sendMovementPackets",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
	private boolean actest$spoofOnGround(boolean original) {
		if (original) {
			return true;
		}
		ModuleManager modules = ActestClient.modules();
		NoFallModule noFall = modules == null ? null : modules.get(NoFallModule.class);
		return noFall != null && noFall.shouldSpoofGround((ClientPlayerEntity) (Object) this);
	}
}

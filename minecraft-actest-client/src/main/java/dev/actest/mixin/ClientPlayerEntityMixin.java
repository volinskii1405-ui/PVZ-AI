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
 * NoFall. В ClientPlayerEntity#sendMovementPackets значение isOnGround() идёт в поле
 * onGround пакетов PlayerMoveC2SPacket (Full / PositionAndOnGround / LookAndOnGround /
 * OnGroundOnly) — проверено по байткоду 1.21.4.
 *  - HEAD: NoFall обновляет свою высоту падения (движение этого тика уже применено);
 *  - каждый isOnGround(): в режиме SPOOF подменяем значение на true.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void actest$trackFall(CallbackInfo ci) {
		NoFallModule noFall = noFall();
		if (noFall != null) {
			noFall.beforeMovementPackets((ClientPlayerEntity) (Object) this);
		}
	}

	@ModifyExpressionValue(method = "sendMovementPackets",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
	private boolean actest$spoofOnGround(boolean original) {
		if (original) {
			return true;
		}
		NoFallModule noFall = noFall();
		return noFall != null && noFall.shouldSpoofGround((ClientPlayerEntity) (Object) this);
	}

	private static NoFallModule noFall() {
		ModuleManager modules = ActestClient.modules();
		return modules == null ? null : modules.get(NoFallModule.class);
	}
}

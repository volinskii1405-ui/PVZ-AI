package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import dev.actest.module.ModuleManager;
import dev.actest.module.WallhackModule;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity#getTeamColorValue — цвет контура свечения (по умолчанию цвет команды или белый).
 * Для подсвеченных WH игроков подменяем его на цвет из конфига.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void actest$outlineColor(CallbackInfoReturnable<Integer> cir) {
		ModuleManager modules = ActestClient.modules();
		if (modules == null) {
			return;
		}
		WallhackModule wallhack = modules.get(WallhackModule.class);
		if (wallhack != null && wallhack.shouldGlow((Entity) (Object) this)) {
			cir.setReturnValue(ActestConfig.get().wallhackColor());
		}
	}
}

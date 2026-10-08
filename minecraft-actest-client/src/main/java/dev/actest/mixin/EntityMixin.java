package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.module.ModuleManager;
import dev.actest.module.WallhackModule;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity#getTeamColorValue — цвет контура свечения (по умолчанию цвет команды или белый).
 * Для подсвеченных WH сущностей подменяем его на цвет их группы из конфига.
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
		int color = wallhack == null ? -1 : wallhack.glowColor((Entity) (Object) this);
		if (color >= 0) {
			cir.setReturnValue(color);
		}
	}
}

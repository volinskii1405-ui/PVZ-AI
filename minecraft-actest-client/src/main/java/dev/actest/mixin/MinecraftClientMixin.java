package dev.actest.mixin;

import dev.actest.ActestClient;
import dev.actest.module.ModuleManager;
import dev.actest.module.WallhackModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MinecraftClient#hasOutline решает, рисовать ли сущности контур свечения
 * (ваниль: эффект Glowing или наблюдатель с зажатой клавишей подсветки игроков).
 * Контур рисуется в отдельный framebuffer без depth test и поэтому виден сквозь стены.
 * Мы возвращаем true для целей WH — на сервер при этом ничего не уходит.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void actest$forceOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		ModuleManager modules = ActestClient.modules();
		if (modules == null) {
			return;
		}
		WallhackModule wallhack = modules.get(WallhackModule.class);
		if (wallhack != null && wallhack.shouldGlow(entity)) {
			cir.setReturnValue(true);
		}
	}
}

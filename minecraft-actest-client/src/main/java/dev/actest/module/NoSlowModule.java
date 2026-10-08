package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static dev.actest.module.MovementUtil.STEADY_STEP_FACTOR;
import static dev.actest.module.MovementUtil.USING_ITEM_FACTOR;

/**
 * NoSlow: ходьба без замедления.
 *  - items: при использовании предмета (еда, зелья, лук, арбалет, щит, трезубец…)
 *    ваниль умножает ввод на 0.2;
 *  - blocks: песок душ и блок мёда умножают скорость после каждого шага на 0.4.
 * Как и Speed, в конце тика выставляем горизонтальную скорость так, чтобы смещение
 * на следующем тике было как при обычной ходьбе. Работает только на земле.
 */
public final class NoSlowModule extends AbstractModule {
	public NoSlowModule() {
		// По умолчанию без клавиши — включается из меню (клавишу можно назначить в «Управлении»)
		super("NoSlow", "key.actest.noslow", InputUtil.UNKNOWN_KEY.getCode());
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (!player.isOnGround() || player.hasVehicle() || player.getAbilities().flying || player.isSpectator()) {
			return;
		}
		Vec3d dir = MovementUtil.inputDirection(client.options, player.getYaw());
		if (dir == null) {
			return;
		}

		ActestConfig.NoSlow cfg = ActestConfig.get().noSlow;
		boolean usingItem = cfg.items && player.isUsingItem();
		boolean slowBlock = cfg.blocks && isOnSlowBlock(player);
		if (!usingItem && !slowBlock) {
			return;
		}
		// Если Speed сейчас работает, он уже задал скорость (и сам перекрывает замедление блока)
		SpeedModule speed = ActestClient.modules().get(SpeedModule.class);
		if (speed != null && speed.canApply(player)) {
			return;
		}

		// Ваниль на следующем тике добавит своё (возможно, замедленное) ускорение — учитываем его
		double accel = MovementUtil.groundAccel(player);
		double vanillaAccel = usingItem ? accel * USING_ITEM_FACTOR : accel;
		MovementUtil.setHorizontal(player, dir, STEADY_STEP_FACTOR * accel - vanillaAccel);
	}

	/** Стоит ли игрок на блоке, замедляющем движение (как в Entity#getVelocityMultiplier). */
	private static boolean isOnSlowBlock(ClientPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 0.5000001, player.getZ());
		return player.getWorld().getBlockState(feet).getBlock().getVelocityMultiplier() < 1.0f
				|| player.getWorld().getBlockState(below).getBlock().getVelocityMultiplier() < 1.0f;
	}
}

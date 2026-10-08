package dev.actest.module;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

/**
 * Общая математика движения для модулей.
 *
 * Ванильная физика на обычном блоке (скользкость 0.6):
 *  - за тик к скорости добавляется ускорение accel = movementSpeed * 0.98 * (замедление ввода)
 *    (movementSpeed = 0.1 шагом, 0.13 спринтом, плюс эффекты Speed/Slowness);
 *  - игрок смещается на velocity, затем velocity *= 0.6 * 0.91 = 0.546.
 * Отсюда установившаяся скорость = accel * 0.546 / (1 - 0.546) ≈ 1.2026 * accel,
 * а смещение за тик = accel / (1 - 0.546) ≈ 2.2026 * accel
 * (0.2158 блока/тик шагом и 0.2806 спринтом — известные ванильные 4.317 и 5.612 м/с).
 */
final class MovementUtil {
	/** Ванильное трение на обычном блоке: скользкость 0.6 * 0.91. */
	static final double GROUND_DRAG = 0.6 * 0.91;
	/** Установившаяся velocity / accel ≈ 1.2026. */
	static final double STEADY_VELOCITY_FACTOR = GROUND_DRAG / (1.0 - GROUND_DRAG);
	/** Смещение за тик / accel ≈ 2.2026. */
	static final double STEADY_STEP_FACTOR = 1.0 / (1.0 - GROUND_DRAG);
	/** Ваниль умножает ввод на 0.98 (по диагонали выходит ~2% больше — не учитываем). */
	static final double INPUT_FACTOR = 0.98;
	/** Ванильное замедление ввода при использовании предмета (еда, лук, щит…). */
	static final double USING_ITEM_FACTOR = 0.2;

	private MovementUtil() {
	}

	/** Ускорение от ввода за тик на земле без замедлений. */
	static double groundAccel(ClientPlayerEntity player) {
		return player.getMovementSpeed() * INPUT_FACTOR;
	}

	/** Меняем только X/Z; Y берём текущий, чтобы не ломать гравитацию и прыжок. */
	static void setHorizontal(ClientPlayerEntity player, Vec3d dir, double speed) {
		Vec3d v = player.getVelocity();
		player.setVelocity(dir.x * speed, v.y, dir.z * speed);
	}

	/**
	 * Единичный вектор движения в мировых координатах по WASD и углу взгляда (yaw),
	 * та же формула, что в Entity#movementInputToVelocity. null — если WASD не нажаты.
	 */
	static Vec3d inputDirection(GameOptions options, float yawDegrees) {
		double forward = (options.forwardKey.isPressed() ? 1 : 0) - (options.backKey.isPressed() ? 1 : 0);
		double strafe = (options.leftKey.isPressed() ? 1 : 0) - (options.rightKey.isPressed() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return null;
		}
		double length = Math.sqrt(forward * forward + strafe * strafe);
		forward /= length;
		strafe /= length;

		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3d(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
	}
}

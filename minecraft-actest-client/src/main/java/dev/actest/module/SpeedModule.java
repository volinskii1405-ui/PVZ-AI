package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Speed: увеличивает горизонтальную скорость игрока в multiplier раз
 * относительно ванильной. Трогает только X/Z скорости — Y (гравитация,
 * прыжок, падение) остаётся ванильным.
 *
 * Как устроена ванильная физика на обычном блоке (скользкость 0.6):
 *  - за тик к скорости добавляется ускорение accel = movementSpeed * 0.98
 *    (movementSpeed = 0.1 шагом, 0.13 спринтом, плюс эффекты Speed/Slowness);
 *  - игрок смещается на velocity, затем velocity *= 0.6 * 0.91 = 0.546.
 * Отсюда установившаяся скорость = accel * 0.546 / (1 - 0.546) ≈ 1.2026 * accel,
 * а смещение за тик = accel / (1 - 0.546) ≈ 2.2026 * accel
 * (0.2158 блока/тик шагом и 0.2806 спринтом — известные ванильные 4.317 и 5.612 м/с).
 */
public final class SpeedModule extends AbstractModule {
	/** Ванильное трение на обычном блоке: скользкость 0.6 * 0.91. */
	private static final double GROUND_DRAG = 0.6 * 0.91;
	/** Установившаяся velocity / accel ≈ 1.2026. */
	private static final double STEADY_VELOCITY_FACTOR = GROUND_DRAG / (1.0 - GROUND_DRAG);
	/** Смещение за тик / accel ≈ 2.2026. */
	private static final double STEADY_STEP_FACTOR = 1.0 / (1.0 - GROUND_DRAG);
	/** Ваниль умножает ввод на 0.98 (по диагонали выходит ~2% больше — не учитываем). */
	private static final double INPUT_FACTOR = 0.98;
	/** Ванильная добавка к горизонтальной скорости при прыжке во время спринта. */
	private static final double SPRINT_JUMP_BOOST = 0.2;

	/** Позиция на прошлом тике — только для debugLog. */
	private Vec3d lastPos;

	public SpeedModule() {
		super("Speed", "key.actest.speed", GLFW.GLFW_KEY_R);
	}

	@Override
	public String getHudInfo() {
		ActestConfig.Speed cfg = ActestConfig.get().speed;
		return String.format(Locale.ROOT, "%s x%.2f", cfg.mode, cfg.multiplier);
	}

	@Override
	protected void onEnable() {
		lastPos = null;
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ActestConfig.Speed cfg = ActestConfig.get().speed;
		if (cfg.debugLog) {
			logStep(player);
		}
		if (!canApply(player)) {
			return;
		}
		Vec3d dir = inputDirection(client.options, player.getYaw());
		if (dir == null) {
			return; // WASD не нажаты — не вмешиваемся, ваниль сама затормозит
		}

		double accel = player.getMovementSpeed() * INPUT_FACTOR;
		switch (cfg.mode) {
			case GROUND -> tickGround(player, dir, accel, cfg.multiplier);
			case BHOP -> tickBhop(player, dir, accel, cfg.multiplier);
		}
	}

	/**
	 * (а) Прямое изменение velocity на земле.
	 * На следующем тике ваниль сама добавит accel по направлению ввода, поэтому
	 * ставим (2.2026 * mult - 1) * accel: итоговое смещение = ванильное * mult.
	 */
	private static void tickGround(ClientPlayerEntity player, Vec3d dir, double accel, double mult) {
		if (!player.isOnGround()) {
			return; // в воздухе не трогаем: прыжок и падение полностью ванильные
		}
		setHorizontal(player, dir, (STEADY_STEP_FACTOR * mult - 1.0) * accel);
	}

	/**
	 * (б) Bhop: на каждом касании земли автопрыжок, горизонтальная скорость
	 * прыжка = ванильная скорость прыжка с разбега * mult. В воздухе модуль
	 * скорости не растёт, но разворачивается за WASD (air strafe).
	 */
	private static void tickBhop(ClientPlayerEntity player, Vec3d dir, double accel, double mult) {
		if (player.isOnGround()) {
			// Ванильный прыжок: вертикальная скорость 0.42 (+ Jump Boost), счётчики статистики и т.п.
			player.jump();
			double jumpSpeed = STEADY_VELOCITY_FACTOR * accel + (player.isSprinting() ? SPRINT_JUMP_BOOST : 0.0);
			setHorizontal(player, dir, jumpSpeed * mult);
		} else {
			Vec3d v = player.getVelocity();
			setHorizontal(player, dir, Math.hypot(v.x, v.z));
		}
	}

	/** Меняем только X/Z; Y берём текущий, чтобы не ломать гравитацию и прыжок. */
	private static void setHorizontal(ClientPlayerEntity player, Vec3d dir, double speed) {
		Vec3d v = player.getVelocity();
		player.setVelocity(dir.x * speed, v.y, dir.z * speed);
	}

	/** Обычная ходьба: не в воде/лаве, не на лестнице, не в полёте, не верхом, не ест и т.д. */
	private static boolean canApply(ClientPlayerEntity player) {
		return !player.isSpectator()
				&& !player.getAbilities().flying
				&& !player.hasVehicle()
				&& !player.isTouchingWater()
				&& !player.isInLava()
				&& !player.isClimbing()
				&& !player.isUsingItem()
				// STANDING исключает присед, плавание, ползание и полёт на элитрах
				&& player.getPose() == EntityPose.STANDING;
	}

	/**
	 * Единичный вектор движения в мировых координатах по WASD и углу взгляда (yaw),
	 * та же формула, что в Entity#movementInputToVelocity. null — если WASD не нажаты.
	 */
	private static Vec3d inputDirection(GameOptions options, float yawDegrees) {
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

	/**
	 * Смещение за прошедший тик. Вызывается в конце тика, после отправки пакета движения,
	 * поэтому значения совпадают с тем, что сервер получил в PlayerMoveC2SPacket этого тика.
	 */
	private void logStep(ClientPlayerEntity player) {
		Vec3d pos = player.getPos();
		if (lastPos != null) {
			ActestClient.LOGGER.info(String.format(Locale.ROOT,
					"[Speed] age=%d dXZ=%.4f dY=%.4f onGround=%b sprint=%b",
					player.age, Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z), pos.y - lastPos.y,
					player.isOnGround(), player.isSprinting()));
		}
		lastPos = pos;
	}
}

package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static dev.actest.module.MovementUtil.STEADY_STEP_FACTOR;
import static dev.actest.module.MovementUtil.STEADY_VELOCITY_FACTOR;

/**
 * Speed: увеличивает горизонтальную скорость игрока в multiplier раз
 * относительно ванильной. Трогает только X/Z скорости — Y (гравитация,
 * прыжок, падение) остаётся ванильным. Физика — см. {@link MovementUtil}.
 */
public final class SpeedModule extends AbstractModule {
	/** Ванильная добавка к горизонтальной скорости при прыжке во время спринта. */
	private static final double SPRINT_JUMP_BOOST = 0.2;

	public SpeedModule() {
		super("Speed", "key.actest.speed", GLFW.GLFW_KEY_R);
	}

	@Override
	public String getHudInfo() {
		ActestConfig.Speed cfg = ActestConfig.get().speed;
		return String.format(Locale.ROOT, "%s x%.2f", cfg.mode, cfg.multiplier);
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (!canApply(player)) {
			return;
		}
		Vec3d dir = MovementUtil.inputDirection(client.options, player.getYaw());
		if (dir == null) {
			return; // WASD не нажаты — не вмешиваемся, ваниль сама затормозит
		}

		ActestConfig.Speed cfg = ActestConfig.get().speed;
		double accel = MovementUtil.groundAccel(player);
		switch (cfg.mode) {
			case GROUND -> tickGround(player, dir, accel, cfg.multiplier);
			case BHOP -> tickBhop(player, dir, accel, cfg.multiplier);
		}
	}

	/** Применим ли Speed сейчас (используется и NoSlow, чтобы не перебивать друг друга). */
	boolean canApply(ClientPlayerEntity player) {
		return isEnabled()
				&& !player.isSpectator()
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
	 * (а) Прямое изменение velocity на земле.
	 * На следующем тике ваниль сама добавит accel по направлению ввода, поэтому
	 * ставим (2.2026 * mult - 1) * accel: итоговое смещение = ванильное * mult.
	 */
	private static void tickGround(ClientPlayerEntity player, Vec3d dir, double accel, double mult) {
		if (!player.isOnGround()) {
			return; // в воздухе не трогаем: прыжок и падение полностью ванильные
		}
		MovementUtil.setHorizontal(player, dir, (STEADY_STEP_FACTOR * mult - 1.0) * accel);
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
			MovementUtil.setHorizontal(player, dir, jumpSpeed * mult);
		} else {
			Vec3d v = player.getVelocity();
			MovementUtil.setHorizontal(player, dir, Math.hypot(v.x, v.z));
		}
	}
}

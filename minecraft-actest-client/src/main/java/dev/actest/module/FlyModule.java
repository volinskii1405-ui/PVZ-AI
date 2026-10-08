package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Fly: полёт без креатива.
 *  - MOTION: каждый тик выставляем скорость целиком — по WASD с заданной скоростью,
 *    Space вверх, Shift вниз, без клавиш — зависание (Δy = 0).
 *  - GLIDE: ванильное движение, но скорость падения ограничена glideSpeed.
 * Скорость ставится в конце тика и применяется к движению на следующем тике
 * (гравитация вычитается уже после перемещения, поэтому зависание точное).
 *
 * Важно: ванильный сервер сам кикает за «полёт» через 80 тиков, если в server.properties
 * allow-flight=false. Чтобы проверить свой античит, а не ванильную проверку, поставьте true.
 */
public final class FlyModule extends AbstractModule {
	public FlyModule() {
		super("Fly", "key.actest.fly", GLFW.GLFW_KEY_G);
	}

	@Override
	public String getHudInfo() {
		return ActestConfig.get().fly.mode.name();
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player.isSpectator() || player.getAbilities().flying || player.hasVehicle()) {
			return; // креативный/наблюдательский полёт и езда — ванильные
		}
		ActestConfig.Fly cfg = ActestConfig.get().fly;
		switch (cfg.mode) {
			case MOTION -> tickMotion(client.options, player, cfg);
			case GLIDE -> tickGlide(player, cfg);
		}
	}

	private static void tickMotion(GameOptions options, ClientPlayerEntity player, ActestConfig.Fly cfg) {
		Vec3d dir = MovementUtil.inputDirection(options, player.getYaw());
		double vx = dir == null ? 0.0 : dir.x * cfg.speed;
		double vz = dir == null ? 0.0 : dir.z * cfg.speed;
		double vy = 0.0;
		if (options.jumpKey.isPressed()) {
			vy += cfg.verticalSpeed;
		}
		if (options.sneakKey.isPressed()) {
			vy -= cfg.verticalSpeed;
		}
		player.setVelocity(vx, vy, vz);
	}

	private static void tickGlide(ClientPlayerEntity player, ActestConfig.Fly cfg) {
		Vec3d v = player.getVelocity();
		if (!player.isOnGround() && v.y < -cfg.glideSpeed) {
			player.setVelocity(v.x, -cfg.glideSpeed, v.z);
		}
	}
}

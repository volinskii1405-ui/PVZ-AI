package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.glfw.GLFW;

/**
 * NoFall: сервер считает урон от падения по накопленной дистанции падения и
 * сбрасывает её, когда в пакете движения приходит onGround=true. Если сообщать
 * «стою на земле», пока дистанция ещё меньше 3 блоков, урона не будет.
 *  - SPOOF: в обычных пакетах движения onGround подменяется на true
 *    (см. ClientPlayerEntityMixin → shouldSpoofGround).
 *  - PACKET: обычные пакеты честные, но каждый тик падения дополнительно
 *    отправляется PlayerMoveC2SPacket.OnGroundOnly(true).
 *
 * Высоту падения модуль считает сам по координатам между отправками пакетов
 * (beforeMovementPackets), не полагаясь на клиентский Entity#fallDistance.
 * Срабатывает после FALL_THRESHOLD блоков падения (урон начинается после 3).
 */
public final class NoFallModule extends AbstractModule {
	private static final double FALL_THRESHOLD = 2.0;

	/** Высота, пройденная вниз с последнего касания земли (по координатам). */
	private double fallen;
	private double lastY = Double.NaN;

	/** Что модуль сделал на этом тике — читает и сбрасывает лог движения. */
	private boolean spoofedThisTick;
	private boolean packetSentThisTick;

	public NoFallModule() {
		super("NoFall", "key.actest.nofall", GLFW.GLFW_KEY_N);
	}

	@Override
	public String getHudInfo() {
		return ActestConfig.get().noFall.mode.name();
	}

	@Override
	protected void onEnable() {
		fallen = 0;
		lastY = Double.NaN;
	}

	/**
	 * Вызывается из mixin'а в начале sendMovementPackets — уже после движения этого тика.
	 * Обновляет высоту падения: на земле, в полёте, в воде и на лестнице — обнуляем.
	 */
	public void beforeMovementPackets(ClientPlayerEntity player) {
		double y = player.getY();
		if (player.isOnGround() || player.getAbilities().flying || player.isTouchingWater()
				|| player.isInLava() || player.isClimbing() || player.hasVehicle()) {
			fallen = 0;
		} else if (!Double.isNaN(lastY) && y < lastY) {
			fallen += lastY - y;
		}
		lastY = y;
	}

	/** Высота падения для лога движения. */
	double fallen() {
		return fallen;
	}

	private boolean isFalling(ClientPlayerEntity player) {
		return !player.isOnGround() && fallen > FALL_THRESHOLD && !player.isSpectator();
	}

	/** Вызывается из mixin'а при сборке каждого пакета движения: подменять ли onGround на true. */
	public boolean shouldSpoofGround(ClientPlayerEntity player) {
		boolean spoof = isEnabled() && ActestConfig.get().noFall.mode == ActestConfig.NoFall.Mode.SPOOF && isFalling(player);
		spoofedThisTick |= spoof;
		return spoof;
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (ActestConfig.get().noFall.mode == ActestConfig.NoFall.Mode.PACKET && isFalling(player)) {
			player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
			packetSentThisTick = true;
		}
	}

	/** Для лога движения: "SPOOF" / "PACKET" / "-" — и сброс флагов на следующий тик. */
	String consumeTickAction() {
		String action = spoofedThisTick ? "SPOOF" : packetSentThisTick ? "PACKET" : "-";
		spoofedThisTick = false;
		packetSentThisTick = false;
		return action;
	}
}

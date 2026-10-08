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
 * Срабатывает после FALL_THRESHOLD блоков падения (урон начинается после 3).
 */
public final class NoFallModule extends AbstractModule {
	private static final float FALL_THRESHOLD = 2.0f;

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

	/** Падает ли игрок достаточно долго, чтобы вмешиваться. */
	private static boolean isFalling(ClientPlayerEntity player) {
		return !player.isOnGround()
				&& player.fallDistance > FALL_THRESHOLD
				&& !player.getAbilities().flying
				&& !player.isSpectator();
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

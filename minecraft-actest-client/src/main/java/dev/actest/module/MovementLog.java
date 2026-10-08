package dev.actest.module;

import dev.actest.ActestClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Лог движения (включается в меню «Прочее → Интерфейс» или debugLog в конфиге).
 * Пишется в конце тика, после отправки пакета движения, поэтому dXZ/dY совпадают с тем,
 * что сервер получил в PlayerMoveC2SPacket этого тика. Пример строки:
 * [Move] age=812 dXZ=0.4209 dY=0.0000 ground=true fall=0.00 using=false nofall=- active=Speed
 */
final class MovementLog {
	private Vec3d lastPos;

	void tick(ClientPlayerEntity player, List<Module> modules, NoFallModule noFall) {
		Vec3d pos = player.getPos();
		String noFallAction = noFall != null ? noFall.consumeTickAction() : "-";
		if (lastPos != null) {
			StringJoiner active = new StringJoiner(",");
			for (Module module : modules) {
				if (module.isEnabled()) {
					active.add(module.getName());
				}
			}
			ActestClient.LOGGER.info(String.format(Locale.ROOT,
					"[Move] age=%d dXZ=%.4f dY=%.4f ground=%b fall=%.2f using=%b nofall=%s active=%s",
					player.age, Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z), pos.y - lastPos.y,
					player.isOnGround(), player.fallDistance, player.isUsingItem(), noFallAction, active));
		}
		lastPos = pos;
	}

	void reset() {
		lastPos = null;
	}
}

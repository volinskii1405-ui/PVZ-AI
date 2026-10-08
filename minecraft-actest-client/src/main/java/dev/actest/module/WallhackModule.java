package dev.actest.module;

import dev.actest.config.ActestConfig;
import dev.actest.render.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * WH / ESP: подсветка других игроков сквозь стены.
 *  - GLOW: ванильный контур, как от эффекта свечения, но только у нас на клиенте
 *    (см. MinecraftClientMixin и EntityMixin). Контур рисуется отдельным проходом
 *    без учёта глубины, поэтому виден сквозь блоки.
 *  - BOX: 2D-рамка вокруг хитбокса, нарисованная в HUD поверх всего мира.
 *  - BOTH: оба варианта.
 * Ник и дистанция рисуются в HUD над хитбоксом в любом режиме.
 */
public final class WallhackModule extends AbstractModule {
	private static final int OUTLINE_BLACK = 0xFF000000;
	private static final int LABEL_BACKGROUND = 0x80000000;

	public WallhackModule() {
		super("WH", "key.actest.wallhack", GLFW.GLFW_KEY_H);
	}

	@Override
	public String getHudInfo() {
		return ActestConfig.get().wallhack.mode.name();
	}

	/** Вызывается из MinecraftClientMixin для каждой сущности каждый кадр — должно быть дёшево. */
	public boolean shouldGlow(Entity entity) {
		if (!isEnabled()) {
			return false;
		}
		ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
		return cfg.mode != ActestConfig.Wallhack.Mode.BOX && isTarget(entity, cfg);
	}

	/**
	 * Цель — любой живой игрок, кроме нас и наблюдателей, в пределах maxDistance.
	 * Проверка на AbstractClientPlayerEntity отсекает серверные копии сущностей
	 * встроенного сервера в одиночной игре (у них класс ServerPlayerEntity).
	 */
	private static boolean isTarget(Entity entity, ActestConfig.Wallhack cfg) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null
				&& entity instanceof AbstractClientPlayerEntity other
				&& other != self
				&& other.isAlive()
				&& !other.isSpectator()
				&& self.squaredDistanceTo(other) <= cfg.maxDistance * cfg.maxDistance;
	}

	@Override
	public void onRender(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || !Projection.isReady()) {
			return;
		}
		ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
		boolean drawBoxes = cfg.mode != ActestConfig.Wallhack.Mode.GLOW;
		boolean drawLabels = cfg.showNames || cfg.showDistance;
		if (!drawBoxes && !drawLabels) {
			return;
		}

		int color = 0xFF000000 | ActestConfig.get().wallhackColor();
		for (AbstractClientPlayerEntity target : client.world.getPlayers()) {
			if (!isTarget(target, cfg)) {
				continue;
			}
			int[] rect = Projection.projectBox(interpolatedBox(target, tickDelta));
			if (rect == null) {
				continue; // игрок позади камеры
			}
			if (drawBoxes) {
				drawBox(context, rect, color);
			}
			if (drawLabels) {
				drawLabel(context, client, target, rect, cfg);
			}
		}
	}

	/** Хитбокс в интерполированной позиции, чтобы рамка не дёргалась между тиками. */
	private static Box interpolatedBox(Entity entity, float tickDelta) {
		Vec3d lerped = entity.getLerpedPos(tickDelta);
		return entity.getBoundingBox().offset(lerped.subtract(entity.getPos()));
	}

	/** Цветная рамка с чёрной обводкой снаружи и изнутри — читается на любом фоне. */
	private static void drawBox(DrawContext context, int[] r, int color) {
		strokeRect(context, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, OUTLINE_BLACK);
		strokeRect(context, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, OUTLINE_BLACK);
		strokeRect(context, r[0], r[1], r[2], r[3], color);
	}

	/** Контур прямоугольника толщиной 1 px (x2/y2 — не включительно). */
	private static void strokeRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		if (x2 - x1 < 2 || y2 - y1 < 2) {
			return;
		}
		context.fill(x1, y1, x2, y1 + 1, color);         // верх
		context.fill(x1, y2 - 1, x2, y2, color);         // низ
		context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color); // лево
		context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color); // право
	}

	/** "Ник 12.3m" над рамкой. */
	private static void drawLabel(DrawContext context, MinecraftClient client, AbstractClientPlayerEntity target, int[] r,
			ActestConfig.Wallhack cfg) {
		MutableText text = Text.empty();
		if (cfg.showNames) {
			text.append(Text.literal(target.getName().getString()).formatted(Formatting.WHITE));
		}
		if (cfg.showDistance) {
			if (cfg.showNames) {
				text.append(" ");
			}
			String distance = String.format(Locale.ROOT, "%.1fm", client.player.distanceTo(target));
			text.append(Text.literal(distance).formatted(Formatting.YELLOW));
		}

		TextRenderer textRenderer = client.textRenderer;
		int centerX = (r[0] + r[2]) / 2;
		int y = r[1] - textRenderer.fontHeight - 3;
		int halfWidth = textRenderer.getWidth(text) / 2;
		context.fill(centerX - halfWidth - 2, y - 1, centerX + halfWidth + 2, y + textRenderer.fontHeight, LABEL_BACKGROUND);
		context.drawCenteredTextWithShadow(textRenderer, text, centerX, y, 0xFFFFFFFF);
	}
}

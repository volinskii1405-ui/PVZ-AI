package dev.actest.module;

import dev.actest.config.ActestConfig;
import dev.actest.config.ActestConfig.Wallhack.Target;
import dev.actest.render.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * WH / ESP: подсветка сущностей сквозь стены. Три группы, у каждой свой
 * переключатель и цвет: игроки, враждебные мобы, мирные мобы.
 *  - GLOW: ванильный контур, как от эффекта свечения, но только у нас на клиенте
 *    (см. MinecraftClientMixin и EntityMixin). Контур рисуется отдельным проходом
 *    без учёта глубины, поэтому виден сквозь блоки.
 *  - BOX: 2D-рамка вокруг хитбокса, нарисованная в HUD поверх всего мира.
 *  - BOTH: оба варианта.
 * Имя и дистанция рисуются в HUD над хитбоксом в любом режиме.
 */
public final class WallhackModule extends AbstractModule {
	private static final int OUTLINE_BLACK = 0xFF000000;
	private static final int LABEL_BACKGROUND = 0x80000000;

	public WallhackModule() {
		super("WH", "key.actest.wallhack", GLFW.GLFW_KEY_H);
	}

	@Override
	public String getHudInfo() {
		ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
		StringBuilder info = new StringBuilder(cfg.mode.name());
		if (cfg.players) info.append(" P");
		if (cfg.hostileMobs) info.append(" H");
		if (cfg.passiveMobs) info.append(" M");
		return info.toString();
	}

	/** Вызывается из MinecraftClientMixin для каждой сущности каждый кадр — должно быть дёшево. */
	public boolean shouldGlow(Entity entity) {
		return glowColor(entity) >= 0;
	}

	/** Цвет контура 0xRRGGBB для сущности или -1, если её не подсвечиваем контуром. */
	public int glowColor(Entity entity) {
		if (!isEnabled()) {
			return -1;
		}
		ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
		if (cfg.mode == ActestConfig.Wallhack.Mode.BOX) {
			return -1;
		}
		Target target = classify(entity, cfg);
		return target == null ? -1 : cfg.color(target);
	}

	/**
	 * К какой группе относится сущность, или null, если её не подсвечиваем.
	 * Учитываются только сущности клиентского мира: в одиночной игре те же классы
	 * используются встроенным сервером, и их копии трогать нельзя.
	 */
	private static Target classify(Entity entity, ActestConfig.Wallhack cfg) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self == null || entity == self || !entity.isAlive() || !entity.getWorld().isClient()) {
			return null;
		}

		Target target;
		if (entity instanceof AbstractClientPlayerEntity player) {
			if (player.isSpectator()) {
				return null;
			}
			target = Target.PLAYERS;
		} else if (entity instanceof MobEntity) {
			// Monster — маркер всех враждебных мобов, включая слаймов, гастов, шалкеров и хоглинов
			target = entity instanceof Monster ? Target.HOSTILE : Target.PASSIVE;
		} else {
			return null; // стойки для брони, предметы, стрелы и т.п.
		}

		if (!cfg.shows(target) || self.squaredDistanceTo(entity) > cfg.maxDistance * cfg.maxDistance) {
			return null;
		}
		return target;
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

		for (Entity entity : client.world.getEntities()) {
			Target target = classify(entity, cfg);
			if (target == null) {
				continue;
			}
			int[] rect = Projection.projectBox(interpolatedBox(entity, tickDelta));
			if (rect == null) {
				continue; // сущность позади камеры
			}
			if (drawBoxes) {
				drawBox(context, rect, 0xFF000000 | cfg.color(target));
			}
			if (drawLabels) {
				drawLabel(context, client, entity, rect, cfg);
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

	/** "Имя 12.3m" над рамкой: ник игрока или название моба (или его имя с бирки). */
	private static void drawLabel(DrawContext context, MinecraftClient client, Entity target, int[] r,
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

package dev.actest.render;

import dev.actest.config.ActestConfig;
import dev.actest.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** HUD-список включённых модулей: "Speed GROUND x1.50", "WH GLOW". Позиция — hud.x / hud.y в конфиге. */
public final class ModuleListHud {
	private static final int BACKGROUND = 0x90000000;

	private ModuleListHud() {
	}

	public static void render(DrawContext context, TextRenderer textRenderer, List<Module> modules) {
		ActestConfig.Hud cfg = ActestConfig.get().hud;
		if (!cfg.enabled) {
			return;
		}
		int y = cfg.y;
		for (Module module : modules) {
			if (!module.isEnabled()) {
				continue;
			}
			MutableText line = Text.literal(module.getName()).formatted(Formatting.GREEN);
			String info = module.getHudInfo();
			if (!info.isEmpty()) {
				line.append(Text.literal(" " + info).formatted(Formatting.GRAY));
			}
			int width = textRenderer.getWidth(line);
			context.fill(cfg.x - 2, y - 1, cfg.x + width + 2, y + textRenderer.fontHeight, BACKGROUND);
			context.drawTextWithShadow(textRenderer, line, cfg.x, y, 0xFFFFFFFF);
			y += textRenderer.fontHeight + 2;
		}
	}
}

package dev.actest.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Общая основа самописных виджетов меню. */
public abstract class BaseWidget extends ClickableWidget {
	protected BaseWidget(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
	}

	protected static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	/** Y для вертикального центрирования строки текста внутри виджета. */
	protected int textY() {
		return getY() + (height - 8) / 2;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}

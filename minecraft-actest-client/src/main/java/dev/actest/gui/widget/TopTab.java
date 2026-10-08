package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Вкладка категории в шапке меню: у выбранной — акцентное подчёркивание, при наведении текст светлеет. */
public final class TopTab extends BaseWidget {
	private final String label;
	private final BooleanSupplier selected;
	private final Runnable onPress;
	private final Animator highlight;

	public TopTab(int x, int y, int height, String label, BooleanSupplier selected, Runnable onPress) {
		super(x, y, font().getWidth(label) + 16, height, Text.literal(label));
		this.label = label;
		this.selected = selected;
		this.onPress = onPress;
		this.highlight = new Animator(selected.getAsBoolean() ? 1f : 0f);
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean isSelected = selected.getAsBoolean();
		float t = highlight.update(isSelected ? 1f : (isHovered() ? 0.45f : 0f), 18f);
		if (isHovered() && !isSelected) {
			context.fill(getX(), getY(), getX() + width, getY() + height, 0x0CFFFFFF);
		}
		context.drawText(font(), label, getX() + 8, textY(), Theme.lerpColor(t, Theme.TEXT_MUTED, Theme.TEXT), false);
		if (isSelected) {
			// Подчёркивание растягивается от центра по мере анимации
			int half = Math.round((width - 8) / 2f * t);
			int center = getX() + width / 2;
			context.fill(center - half, getY() + height - 2, center + half, getY() + height, Theme.ACCENT);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		onPress.run();
	}
}

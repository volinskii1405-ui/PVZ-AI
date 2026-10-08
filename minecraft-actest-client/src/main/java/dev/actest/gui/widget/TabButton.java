package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Вкладка в боковой панели: акцентная полоска слева у выбранной и точка-индикатор модуля справа. */
public final class TabButton extends BaseWidget {
	private final String label;
	private final BooleanSupplier selected;
	/** Горит ли модуль этой вкладки (null — индикатора нет). */
	private final BooleanSupplier indicator;
	private final Runnable onPress;
	private final Animator hover = new Animator(0f);

	public TabButton(int x, int y, int width, int height, String label, BooleanSupplier selected,
			BooleanSupplier indicator, Runnable onPress) {
		super(x, y, width, height, Text.literal(label));
		this.label = label;
		this.selected = selected;
		this.indicator = indicator;
		this.onPress = onPress;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean isSelected = selected.getAsBoolean();
		float t = hover.update(isSelected ? 1f : (isHovered() ? 0.5f : 0f), 18f);
		if (t > 0.01f) {
			context.fill(getX(), getY(), getX() + width, getY() + height, Theme.lerpColor(t, 0x00FFFFFF, 0x16FFFFFF));
		}
		if (isSelected) {
			context.fill(getX(), getY() + 2, getX() + 2, getY() + height - 2, Theme.ACCENT);
		}
		int textColor = isSelected ? Theme.TEXT : Theme.lerpColor(t * 2f, Theme.TEXT_MUTED, Theme.TEXT);
		context.drawText(font(), label, getX() + 12, textY(), textColor, false);

		if (indicator != null) {
			int dotX = getX() + width - 12;
			int dotY = getY() + height / 2 - 2;
			Theme.roundRect(context, dotX, dotY, dotX + 5, dotY + 5, 2,
					indicator.getAsBoolean() ? Theme.GOOD : Theme.TEXT_DISABLED);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		onPress.run();
	}
}

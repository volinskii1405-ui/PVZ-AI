package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Цветной квадратик: образец цвета группы или готовый цвет палитры. Выбранный — с белой рамкой. */
public final class ColorChip extends BaseWidget {
	private final IntSupplier color;
	private final BooleanSupplier selected;
	private final Runnable onPress;

	public ColorChip(int x, int y, int size, Text name, IntSupplier color, BooleanSupplier selected, Runnable onPress) {
		super(x, y, size, size, name);
		this.color = color;
		this.selected = selected;
		this.onPress = onPress;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x1 = getX();
		int y1 = getY();
		int x2 = x1 + width;
		int y2 = y1 + height;
		if (selected.getAsBoolean()) {
			Theme.roundRect(context, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 3, 0xFFFFFFFF);
			Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 2, Theme.PANEL | 0xFF000000);
		} else if (isHovered()) {
			Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 2, 0x80FFFFFF);
		}
		Theme.roundRect(context, x1, y1, x2, y2, 2, 0xFF000000 | color.getAsInt());
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		onPress.run();
	}
}

package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Сегментный переключатель «[A | B | C]» с подсветкой, которая плавно переезжает к выбранному. */
public final class Segmented<T> extends BaseWidget {
	private final T[] values;
	private final Function<T, String> labels;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final Animator slide;

	public Segmented(int x, int y, int width, int height, Text name, T[] values, Function<T, String> labels,
			Supplier<T> getter, Consumer<T> setter) {
		super(x, y, width, height, name);
		this.values = values;
		this.labels = labels;
		this.getter = getter;
		this.setter = setter;
		this.slide = new Animator(selectedIndex());
	}

	private int selectedIndex() {
		T current = getter.get();
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) return i;
		}
		return 0;
	}

	private float segmentWidth() {
		return width / (float) values.length;
	}

	private int indexAt(double mouseX) {
		int i = (int) ((mouseX - getX()) / segmentWidth());
		return Math.max(0, Math.min(values.length - 1, i));
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = getX();
		int y = getY();
		float seg = segmentWidth();
		int selected = selectedIndex();
		Theme.roundRect(context, x, y, x + width, y + height, 3, Theme.CONTROL);

		// Подсветка наведённого (не выбранного) сегмента
		int hovered = isHovered() ? indexAt(mouseX) : -1;
		if (hovered >= 0 && hovered != selected) {
			int hx = x + Math.round(hovered * seg);
			Theme.roundRect(context, hx + 1, y + 1, x + Math.round((hovered + 1) * seg) - 1, y + height - 1, 3, Theme.CONTROL_HOVER);
		}

		// Акцентная «плашка» выбранного сегмента
		float pos = slide.update(selected, 16f);
		int sx = x + Math.round(pos * seg);
		Theme.roundRect(context, sx + 1, y + 1, sx + Math.round(seg) - 1, y + height - 1, 3, Theme.ACCENT);

		for (int i = 0; i < values.length; i++) {
			String label = labels.apply(values[i]);
			int center = x + Math.round((i + 0.5f) * seg);
			int color = i == selected ? 0xFFFFFFFF : (i == hovered ? Theme.TEXT : Theme.TEXT_MUTED);
			context.drawText(font(), label, center - font().getWidth(label) / 2, textY(), color, false);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setter.accept(values[indexAt(mouseX)]);
	}
}

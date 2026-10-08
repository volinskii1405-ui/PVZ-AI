package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** Ползунок: тонкая дорожка с акцентной заливкой, ручка и значение справа. Тянется мышью. */
public class Slider extends BaseWidget {
	/** Место справа под текст значения. */
	private static final int VALUE_WIDTH = 46;

	private final double min;
	private final double max;
	private final double step;
	private final DoubleSupplier getter;
	private final DoubleConsumer setter;
	private final DoubleFunction<String> format;

	public Slider(int x, int y, int width, int height, Text name, double min, double max, double step,
			DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format) {
		super(x, y, width, height, name);
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.format = format;
	}

	protected int trackLeft() {
		return getX() + 3;
	}

	protected int trackRight() {
		return getX() + width - VALUE_WIDTH - 3;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		double value = getter.getAsDouble();
		float t = (float) Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
		int left = trackLeft();
		int right = trackRight();
		int centerY = getY() + height / 2;

		renderTrack(context, left, right, centerY, t);

		int knobX = left + Math.round(t * (right - left));
		int knobColor = isHovered() || isFocused() ? 0xFFFFFFFF : 0xFFDDE2EA;
		Theme.roundRect(context, knobX - 3, centerY - 5, knobX + 3, centerY + 5, 2, knobColor);

		String text = format.apply(value);
		context.drawText(font(), text, getX() + width - font().getWidth(text), textY(), Theme.TEXT, false);
	}

	/** Дорожка: фон + заливка до ручки. Переопределяется у ползунка оттенка. */
	protected void renderTrack(DrawContext context, int left, int right, int centerY, float t) {
		Theme.roundRect(context, left, centerY - 2, right, centerY + 2, 2, Theme.CONTROL_HOVER);
		int filled = left + Math.round(t * (right - left));
		if (filled > left + 1) {
			Theme.roundRect(context, left, centerY - 2, filled, centerY + 2, 2, Theme.ACCENT);
		}
	}

	private void setFromMouse(double mouseX) {
		double t = Math.max(0.0, Math.min(1.0, (mouseX - trackLeft()) / (double) (trackRight() - trackLeft())));
		double raw = min + t * (max - min);
		double snapped = min + Math.round((raw - min) / step) * step;
		snapped = Math.max(min, Math.min(max, snapped));
		setter.accept(Math.round(snapped * 10000.0) / 10000.0);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setFromMouse(mouseX);
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
		setFromMouse(mouseX);
	}

	/** Без щелчка на каждое касание — как у ванильного ползунка. */
	@Override
	public void playDownSound(SoundManager soundManager) {
	}
}

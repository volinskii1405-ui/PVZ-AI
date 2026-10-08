package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Переключатель-«таблетка» с плавно едущим кружком. */
public final class ToggleSwitch extends BaseWidget {
	public static final int WIDTH = 22;
	public static final int HEIGHT = 12;

	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;
	private final Animator knob;

	public ToggleSwitch(int x, int y, Text name, BooleanSupplier getter, Consumer<Boolean> setter) {
		super(x, y, WIDTH, HEIGHT, name);
		this.getter = getter;
		this.setter = setter;
		this.knob = new Animator(getter.getAsBoolean() ? 1f : 0f);
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		float t = knob.update(getter.getAsBoolean() ? 1f : 0f, 18f);
		int off = isHovered() && active ? Theme.CONTROL_HOVER : Theme.CONTROL;
		int track = active ? Theme.lerpColor(t, off, Theme.ACCENT) : Theme.CONTROL;
		Theme.roundRect(context, getX(), getY(), getX() + width, getY() + height, 6, track);

		int size = height - 4;
		int knobX = getX() + 2 + Math.round(t * (width - 4 - size));
		Theme.roundRect(context, knobX, getY() + 2, knobX + size, getY() + 2 + size, 4,
				active ? 0xFFFFFFFF : Theme.TEXT_DISABLED);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setter.accept(!getter.getAsBoolean());
	}
}

package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Компактная кнопка-флажок с текстом: акцентная, когда включена. */
public final class PillToggle extends BaseWidget {
	private final String label;
	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;
	private final Animator fade;

	public PillToggle(int x, int y, int height, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		super(x, y, font().getWidth(label) + 14, height, Text.literal(label));
		this.label = label;
		this.getter = getter;
		this.setter = setter;
		this.fade = new Animator(getter.getAsBoolean() ? 1f : 0f);
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		float t = fade.update(getter.getAsBoolean() ? 1f : 0f, 18f);
		int off = isHovered() ? Theme.CONTROL_HOVER : Theme.CONTROL;
		Theme.roundRect(context, getX(), getY(), getX() + width, getY() + height, 3, Theme.lerpColor(t, off, Theme.ACCENT));
		int textColor = Theme.lerpColor(t, Theme.TEXT_MUTED, 0xFFFFFFFF);
		context.drawText(font(), label, getX() + 7, textY(), textColor, false);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setter.accept(!getter.getAsBoolean());
	}
}

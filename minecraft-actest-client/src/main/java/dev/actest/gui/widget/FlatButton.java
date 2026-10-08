package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Плоская кнопка со скруглением; при наведении светлеет. */
public final class FlatButton extends BaseWidget {
	private final String label;
	private final Runnable onPress;
	private final Animator hover = new Animator(0f);

	public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
		super(x, y, width, height, Text.literal(label));
		this.label = label;
		this.onPress = onPress;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		float t = hover.update(isHovered() ? 1f : 0f, 20f);
		Theme.roundRect(context, getX(), getY(), getX() + width, getY() + height, 3,
				Theme.lerpColor(t, Theme.CONTROL, Theme.CONTROL_HOVER));
		context.drawText(font(), label, getX() + (width - font().getWidth(label)) / 2, textY(),
				Theme.lerpColor(t, Theme.TEXT_MUTED, Theme.TEXT), false);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		onPress.run();
	}
}

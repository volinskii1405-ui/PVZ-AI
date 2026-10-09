package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;

public class Slider extends BaseWidget {
   private static final int VALUE_WIDTH = 46;
   private final double min;
   private final double max;
   private final double step;
   private final DoubleSupplier getter;
   private final DoubleConsumer setter;
   private final DoubleFunction<String> format;

   public Slider(
      int x,
      int y,
      int width,
      int height,
      Text name,
      double min,
      double max,
      double step,
      DoubleSupplier getter,
      DoubleConsumer setter,
      DoubleFunction<String> format
   ) {
      super(x, y, width, height, name);
      this.min = min;
      this.max = max;
      this.step = step;
      this.getter = getter;
      this.setter = setter;
      this.format = format;
   }

   protected int trackLeft() {
      return this.getX() + 3;
   }

   protected int trackRight() {
      return this.getX() + this.width - 46 - 3;
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      double value = this.getter.getAsDouble();
      float t = (float)Math.max(0.0, Math.min(1.0, (value - this.min) / (this.max - this.min)));
      int left = this.trackLeft();
      int right = this.trackRight();
      int centerY = this.getY() + this.height / 2;
      this.renderTrack(context, left, right, centerY, t);
      int knobX = left + Math.round(t * (float)(right - left));
      int knobColor = !this.isHovered() && !this.isFocused() ? 0xFFDDE2EA : -1;
      Theme.roundRect(context, knobX - 3, centerY - 5, knobX + 3, centerY + 5, 2, knobColor);
      String text = this.format.apply(value);
      context.drawText(font(), text, this.getX() + this.width - font().getWidth(text), this.textY(), 0xFFE6E9EF, false);
   }

   protected void renderTrack(DrawContext context, int left, int right, int centerY, float t) {
      Theme.roundRect(context, left, centerY - 2, right, centerY + 2, 2, 0xFF323A4B);
      int filled = left + Math.round(t * (float)(right - left));
      if (filled > left + 1) {
         Theme.roundRect(context, left, centerY - 2, filled, centerY + 2, 2, 0xFF5B8CFF);
      }
   }

   private void setFromMouse(double mouseX) {
      double t = Math.max(0.0, Math.min(1.0, (mouseX - (double)this.trackLeft()) / (double)(this.trackRight() - this.trackLeft())));
      double raw = this.min + t * (this.max - this.min);
      double snapped = this.min + (double)Math.round((raw - this.min) / this.step) * this.step;
      snapped = Math.max(this.min, Math.min(this.max, snapped));
      this.setter.accept((double)Math.round(snapped * 10000.0) / 10000.0);
   }

   public void onClick(double mouseX, double mouseY) {
      this.setFromMouse(mouseX);
   }

   protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
      this.setFromMouse(mouseX);
   }

   public void playDownSound(SoundManager soundManager) {
   }
}

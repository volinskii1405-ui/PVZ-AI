package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class HueSlider extends Slider {
   public HueSlider(int x, int y, int width, int height, Text name, IntSupplier rgbGetter, IntConsumer rgbSetter) {
      super(
         x,
         y,
         width,
         height,
         name,
         0.0,
         360.0,
         1.0,
         () -> hueOf(rgbGetter.getAsInt()),
         hue -> rgbSetter.accept(Color.HSBtoRGB((float)(hue / 360.0), 1.0F, 1.0F) & 0xFFFFFF),
         hue -> String.format("#%06X", rgbGetter.getAsInt() & 0xFFFFFF)
      );
   }

   private static double hueOf(int rgb) {
      float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
      return (double)hsb[0] * 360.0;
   }

   @Override
   protected void renderTrack(DrawContext context, int left, int right, int centerY, float t) {
      Theme.roundRect(context, left - 1, centerY - 4, right + 1, centerY + 4, 2, 0xFF272E3B);
      int span = Math.max(1, right - left);

      for (int x = left; x < right; x++) {
         int rgb = Color.HSBtoRGB((float)(x - left) / (float)span, 1.0F, 1.0F);
         context.fill(x, centerY - 3, x + 1, centerY + 3, 0xFF000000 | rgb);
      }
   }
}

package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class Segmented<T> extends BaseWidget {
   private final T[] values;
   private final Function<T, String> labels;
   private final Supplier<T> getter;
   private final Consumer<T> setter;
   private final Animator slide;

   public Segmented(int x, int y, int width, int height, Text name, T[] values, Function<T, String> labels, Supplier<T> getter, Consumer<T> setter) {
      super(x, y, width, height, name);
      this.values = values;
      this.labels = labels;
      this.getter = getter;
      this.setter = setter;
      this.slide = new Animator((float)this.selectedIndex());
   }

   private int selectedIndex() {
      T current = this.getter.get();

      for (int i = 0; i < this.values.length; i++) {
         if (this.values[i] == current) {
            return i;
         }
      }

      return 0;
   }

   private float segmentWidth() {
      return (float)this.width / (float)this.values.length;
   }

   private int indexAt(double mouseX) {
      int i = (int)((mouseX - (double)this.getX()) / (double)this.segmentWidth());
      return Math.max(0, Math.min(this.values.length - 1, i));
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      float seg = this.segmentWidth();
      int selected = this.selectedIndex();
      Theme.roundRect(context, x, y, x + this.width, y + this.height, 3, 0xFF272E3B);
      int hovered = this.isHovered() ? this.indexAt((double)mouseX) : -1;
      if (hovered >= 0 && hovered != selected) {
         int hx = x + Math.round((float)hovered * seg);
         Theme.roundRect(context, hx + 1, y + 1, x + Math.round((float)(hovered + 1) * seg) - 1, y + this.height - 1, 3, 0xFF323A4B);
      }

      float pos = this.slide.update((float)selected, 16.0F);
      int sx = x + Math.round(pos * seg);
      Theme.roundRect(context, sx + 1, y + 1, sx + Math.round(seg) - 1, y + this.height - 1, 3, 0xFF5B8CFF);

      for (int i = 0; i < this.values.length; i++) {
         String label = this.labels.apply(this.values[i]);
         int center = x + Math.round(((float)i + 0.5F) * seg);
         int color = i == selected ? -1 : (i == hovered ? 0xFFE6E9EF : 0xFF8B93A3);
         context.drawText(font(), label, center - font().getWidth(label) / 2, this.textY(), color, false);
      }
   }

   public void onClick(double mouseX, double mouseY) {
      this.setter.accept(this.values[this.indexAt(mouseX)]);
   }
}

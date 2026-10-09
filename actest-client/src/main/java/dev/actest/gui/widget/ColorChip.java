package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      int x1 = this.getX();
      int y1 = this.getY();
      int x2 = x1 + this.width;
      int y2 = y1 + this.height;
      if (this.selected.getAsBoolean()) {
         Theme.roundRect(context, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 3, -1);
         Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 2, 0xFF161A22);
      } else if (this.isHovered()) {
         Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 2, 0x80FFFFFF);
      }

      Theme.roundRect(context, x1, y1, x2, y2, 2, 0xFF000000 | this.color.getAsInt());
   }

   public void onClick(double mouseX, double mouseY) {
      this.onPress.run();
   }
}

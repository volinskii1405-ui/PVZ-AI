package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class TopTab extends BaseWidget {
   private final String label;
   private final BooleanSupplier selected;
   private final Runnable onPress;
   private final Animator highlight;

   public TopTab(int x, int y, int height, String label, BooleanSupplier selected, Runnable onPress) {
      super(x, y, font().getWidth(label) + 16, height, Text.literal(label));
      this.label = label;
      this.selected = selected;
      this.onPress = onPress;
      this.highlight = new Animator(selected.getAsBoolean() ? 1.0F : 0.0F);
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean isSelected = this.selected.getAsBoolean();
      float t = this.highlight.update(isSelected ? 1.0F : (this.isHovered() ? 0.45F : 0.0F), 18.0F);
      if (this.isHovered() && !isSelected) {
         context.fill(
            this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x0CFFFFFF
         );
      }

      context.drawText(font(), this.label, this.getX() + 8, this.textY(), Theme.lerpColor(t, 0xFF8B93A3, 0xFFE6E9EF), false);
      if (isSelected) {
         int half = Math.round((float)(this.width - 8) / 2.0F * t);
         int center = this.getX() + this.width / 2;
         context.fill(center - half, this.getY() + this.height - 2, center + half, this.getY() + this.height, 0xFF5B8CFF);
      }
   }

   public void onClick(double mouseX, double mouseY) {
      this.onPress.run();
   }
}

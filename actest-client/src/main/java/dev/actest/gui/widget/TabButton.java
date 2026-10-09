package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class TabButton extends BaseWidget {
   private final String label;
   private final BooleanSupplier selected;
   private final BooleanSupplier indicator;
   private final Runnable onPress;
   private final Animator hover = new Animator(0.0F);

   public TabButton(int x, int y, int width, int height, String label, BooleanSupplier selected, BooleanSupplier indicator, Runnable onPress) {
      super(x, y, width, height, Text.literal(label));
      this.label = label;
      this.selected = selected;
      this.indicator = indicator;
      this.onPress = onPress;
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean isSelected = this.selected.getAsBoolean();
      float t = this.hover.update(isSelected ? 1.0F : (this.isHovered() ? 0.5F : 0.0F), 18.0F);
      if (t > 0.01F) {
         context.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height,
            Theme.lerpColor(t, 0xFFFFFF, 0x16FFFFFF)
         );
      }

      if (isSelected) {
         context.fill(this.getX(), this.getY() + 2, this.getX() + 2, this.getY() + this.height - 2, 0xFF5B8CFF);
      }

      int textColor = isSelected ? 0xFFE6E9EF : Theme.lerpColor(t * 2.0F, 0xFF8B93A3, 0xFFE6E9EF);
      context.drawText(font(), this.label, this.getX() + 12, this.textY(), textColor, false);
      if (this.indicator != null) {
         int dotX = this.getX() + this.width - 12;
         int dotY = this.getY() + this.height / 2 - 2;
         Theme.roundRect(context, dotX, dotY, dotX + 5, dotY + 5, 2, this.indicator.getAsBoolean() ? 0xFF4ADE80 : 0xFF586070);
      }
   }

   public void onClick(double mouseX, double mouseY) {
      this.onPress.run();
   }
}

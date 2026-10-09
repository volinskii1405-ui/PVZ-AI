package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class FlatButton extends BaseWidget {
   private final String label;
   private final Runnable onPress;
   private final Animator hover = new Animator(0.0F);

   public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
      super(x, y, width, height, Text.literal(label));
      this.label = label;
      this.onPress = onPress;
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      float t = this.hover.update(this.isHovered() ? 1.0F : 0.0F, 20.0F);
      Theme.roundRect(
         context,
         this.getX(),
         this.getY(),
         this.getX() + this.width,
         this.getY() + this.height,
         3,
         Theme.lerpColor(t, 0xFF272E3B, 0xFF323A4B)
      );
      context.drawText(
         font(),
         this.label,
         this.getX() + (this.width - font().getWidth(this.label)) / 2,
         this.textY(),
         Theme.lerpColor(t, 0xFF8B93A3, 0xFFE6E9EF),
         false
      );
   }

   public void onClick(double mouseX, double mouseY) {
      this.onPress.run();
   }
}

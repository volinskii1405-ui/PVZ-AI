package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
      this.fade = new Animator(getter.getAsBoolean() ? 1.0F : 0.0F);
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      float t = this.fade.update(this.getter.getAsBoolean() ? 1.0F : 0.0F, 18.0F);
      int off = this.isHovered() ? 0xFF323A4B : 0xFF272E3B;
      Theme.roundRect(
         context,
         this.getX(),
         this.getY(),
         this.getX() + this.width,
         this.getY() + this.height,
         3,
         Theme.lerpColor(t, off, 0xFF5B8CFF)
      );
      int textColor = Theme.lerpColor(t, 0xFF8B93A3, -1);
      context.drawText(font(), this.label, this.getX() + 7, this.textY(), textColor, false);
   }

   public void onClick(double mouseX, double mouseY) {
      this.setter.accept(!this.getter.getAsBoolean());
   }
}

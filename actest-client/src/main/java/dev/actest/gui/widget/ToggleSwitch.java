package dev.actest.gui.widget;

import dev.actest.gui.Theme;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ToggleSwitch extends BaseWidget {
   public static final int WIDTH = 22;
   public static final int HEIGHT = 12;
   private final BooleanSupplier getter;
   private final Consumer<Boolean> setter;
   private final Animator knob;

   public ToggleSwitch(int x, int y, Text name, BooleanSupplier getter, Consumer<Boolean> setter) {
      super(x, y, 22, 12, name);
      this.getter = getter;
      this.setter = setter;
      this.knob = new Animator(getter.getAsBoolean() ? 1.0F : 0.0F);
   }

   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      float t = this.knob.update(this.getter.getAsBoolean() ? 1.0F : 0.0F, 18.0F);
      int off = this.isHovered() && this.active ? 0xFF323A4B : 0xFF272E3B;
      int track = this.active ? Theme.lerpColor(t, off, 0xFF5B8CFF) : 0xFF272E3B;
      Theme.roundRect(
         context, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 6, track
      );
      int size = this.height - 4;
      int knobX = this.getX() + 2 + Math.round(t * (float)(this.width - 4 - size));
      Theme.roundRect(context, knobX, this.getY() + 2, knobX + size, this.getY() + 2 + size, 4, this.active ? -1 : 0xFF586070);
   }

   public void onClick(double mouseX, double mouseY) {
      this.setter.accept(!this.getter.getAsBoolean());
   }
}

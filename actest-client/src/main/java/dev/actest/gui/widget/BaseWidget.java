package dev.actest.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public abstract class BaseWidget extends ClickableWidget {
   protected BaseWidget(int x, int y, int width, int height, Text message) {
      super(x, y, width, height, message);
   }

   protected static TextRenderer font() {
      return MinecraftClient.getInstance().textRenderer;
   }

   protected int textY() {
      return this.getY() + (this.height - 8) / 2;
   }

   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
      this.appendDefaultNarrations(builder);
   }
}

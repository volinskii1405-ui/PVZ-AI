package dev.actest.render;

import dev.actest.config.ActestConfig;
import dev.actest.module.Module;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * HUD-список включённых модулей в углу экрана.
 */
public final class ModuleListHud {
   private static final int BACKGROUND = 0x90000000;

   private ModuleListHud() {
   }

   public static void render(DrawContext context, TextRenderer textRenderer, List<Module> modules) {
      ActestConfig.Hud cfg = ActestConfig.get().hud;
      if (cfg.enabled) {
         int y = cfg.y;

         for (Module module : modules) {
            if (module.isEnabled()) {
               MutableText line = Text.literal(module.getName()).formatted(Formatting.GREEN);
               String info = module.getHudInfo();
               if (!info.isEmpty()) {
                  line.append(Text.literal(" " + info).formatted(Formatting.GRAY));
               }

               int width = textRenderer.getWidth(line);
               context.fill(cfg.x - 2, y - 1, cfg.x + width + 2, y + 9, 0x90000000);
               context.drawTextWithShadow(textRenderer, line, cfg.x, y, -1);
               y += 9 + 2;
            }
         }
      }
   }
}

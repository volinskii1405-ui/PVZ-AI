package dev.actest.module;

import dev.actest.config.ActestConfig;
import dev.actest.render.Projection;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * WH (ESP): GLOW — ванильный контур (glowing) сквозь стены с цветом группы,
 * BOX — 2D-рамки поверх HUD, BOTH — оба; плюс подписи с ником и дистанцией.
 */
public final class WallhackModule extends AbstractModule {
   private static final int OUTLINE_BLACK = 0xFF000000;
   private static final int LABEL_BACKGROUND = Integer.MIN_VALUE;

   public WallhackModule() {
      super("WH", "key.actest.wallhack", 72);
   }

   @Override
   public String getHudInfo() {
      ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
      StringBuilder info = new StringBuilder(cfg.mode.name());
      if (cfg.players) {
         info.append(" P");
      }

      if (cfg.hostileMobs) {
         info.append(" H");
      }

      if (cfg.passiveMobs) {
         info.append(" M");
      }

      return info.toString();
   }

   public boolean shouldGlow(Entity entity) {
      return this.glowColor(entity) >= 0;
   }

   public int glowColor(Entity entity) {
      if (!this.isEnabled()) {
         return -1;
      } else {
         ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
         if (cfg.mode == ActestConfig.Wallhack.Mode.BOX) {
            return -1;
         } else {
            ActestConfig.Wallhack.Target target = classify(entity, cfg);
            return target == null ? -1 : cfg.color(target);
         }
      }
   }

   private static ActestConfig.Wallhack.Target classify(Entity entity, ActestConfig.Wallhack cfg) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self != null && entity != self && entity.isAlive() && entity.getWorld().isClient()) {
         ActestConfig.Wallhack.Target target;
         if (entity instanceof AbstractClientPlayerEntity player) {
            if (player.isSpectator()) {
               return null;
            }

            target = ActestConfig.Wallhack.Target.PLAYERS;
         } else {
            if (!(entity instanceof MobEntity)) {
               return null;
            }

            target = entity instanceof Monster ? ActestConfig.Wallhack.Target.HOSTILE : ActestConfig.Wallhack.Target.PASSIVE;
         }

         return cfg.shows(target) && !(self.squaredDistanceTo(entity) > cfg.maxDistance * cfg.maxDistance) ? target : null;
      } else {
         return null;
      }
   }

   @Override
   public void onRender(DrawContext context, float tickDelta) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && client.player != null && Projection.isReady()) {
         ActestConfig.Wallhack cfg = ActestConfig.get().wallhack;
         boolean drawBoxes = cfg.mode != ActestConfig.Wallhack.Mode.GLOW;
         boolean drawLabels = cfg.showNames || cfg.showDistance;
         if (drawBoxes || drawLabels) {
            for (Entity entity : client.world.getEntities()) {
               ActestConfig.Wallhack.Target target = classify(entity, cfg);
               if (target != null) {
                  int[] rect = Projection.projectBox(interpolatedBox(entity, tickDelta));
                  if (rect != null) {
                     if (drawBoxes) {
                        drawBox(context, rect, 0xFF000000 | cfg.color(target));
                     }

                     if (drawLabels) {
                        drawLabel(context, client, entity, rect, cfg);
                     }
                  }
               }
            }
         }
      }
   }

   private static Box interpolatedBox(Entity entity, float tickDelta) {
      Vec3d lerped = entity.getLerpedPos(tickDelta);
      return entity.getBoundingBox().offset(lerped.subtract(entity.getPos()));
   }

   private static void drawBox(DrawContext context, int[] r, int color) {
      strokeRect(context, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 0xFF000000);
      strokeRect(context, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0xFF000000);
      strokeRect(context, r[0], r[1], r[2], r[3], color);
   }

   private static void strokeRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
      if (x2 - x1 >= 2 && y2 - y1 >= 2) {
         context.fill(x1, y1, x2, y1 + 1, color);
         context.fill(x1, y2 - 1, x2, y2, color);
         context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
         context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
      }
   }

   private static void drawLabel(DrawContext context, MinecraftClient client, Entity target, int[] r, ActestConfig.Wallhack cfg) {
      MutableText text = Text.empty();
      if (cfg.showNames) {
         text.append(Text.literal(target.getName().getString()).formatted(Formatting.WHITE));
      }

      if (cfg.showDistance) {
         if (cfg.showNames) {
            text.append(" ");
         }

         String distance = String.format(Locale.ROOT, "%.1fm", client.player.distanceTo(target));
         text.append(Text.literal(distance).formatted(Formatting.YELLOW));
      }

      TextRenderer textRenderer = client.textRenderer;
      int centerX = (r[0] + r[2]) / 2;
      int y = r[1] - 9 - 3;
      int halfWidth = textRenderer.getWidth(text) / 2;
      context.fill(centerX - halfWidth - 2, y - 1, centerX + halfWidth + 2, y + 9, Integer.MIN_VALUE);
      context.drawCenteredTextWithShadow(textRenderer, text, centerX, y, -1);
   }
}

package dev.actest.module;

import dev.actest.ActestClient;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Отладочный лог движения: одна строка [Move] на тик в logs/latest.log (включается debugLog).
 */
final class MovementLog {
   private Vec3d lastPos;

   void tick(ClientPlayerEntity player, List<Module> modules, NoFallModule noFall) {
      Vec3d pos = player.getPos();
      String noFallAction = noFall != null ? noFall.consumeTickAction() : "-";
      if (this.lastPos != null) {
         StringJoiner active = new StringJoiner(",");

         for (Module module : modules) {
            if (module.isEnabled()) {
               active.add(module.getName());
            }
         }

         ActestClient.LOGGER
            .info(
               String.format(
                  Locale.ROOT,
                  "[Move] age=%d dXZ=%.4f dY=%.4f ground=%b fall=%.2f nofallFall=%.2f using=%b nofall=%s active=%s",
                  player.age,
                  Math.hypot(pos.x - this.lastPos.x, pos.z - this.lastPos.z),
                  pos.y - this.lastPos.y,
                  player.isOnGround(),
                  player.fallDistance,
                  noFall != null ? noFall.fallen() : 0.0,
                  player.isUsingItem(),
                  noFallAction,
                  active
               )
            );
      }

      this.lastPos = pos;
   }

   void reset() {
      this.lastPos = null;
   }
}

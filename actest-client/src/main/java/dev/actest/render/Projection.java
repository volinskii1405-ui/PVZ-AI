package dev.actest.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Проекция мировых координат в экранные (для рамок и подписей WH) по матрицам последнего кадра.
 */
public final class Projection {
   private static final Matrix4f VIEW = new Matrix4f();
   private static final Matrix4f PROJECTION = new Matrix4f();
   private static Vec3d cameraPos = Vec3d.ZERO;
   private static boolean ready;

   private Projection() {
   }

   public static void capture(WorldRenderContext context) {
      VIEW.set(context.positionMatrix());
      PROJECTION.set(context.projectionMatrix());
      cameraPos = context.camera().getPos();
      ready = true;
   }

   public static boolean isReady() {
      return ready;
   }

   public static Vector2f toScreen(double x, double y, double z) {
      Vector4f clip = new Vector4f((float)(x - cameraPos.x), (float)(y - cameraPos.y), (float)(z - cameraPos.z), 1.0F);
      VIEW.transform(clip);
      PROJECTION.transform(clip);
      if (clip.w <= 0.05F) {
         return null;
      } else {
         Window window = MinecraftClient.getInstance().getWindow();
         float ndcX = clip.x / clip.w;
         float ndcY = clip.y / clip.w;
         return new Vector2f((ndcX * 0.5F + 0.5F) * (float)window.getScaledWidth(), (0.5F - ndcY * 0.5F) * (float)window.getScaledHeight());
      }
   }

   public static int[] projectBox(Box box) {
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;

      for (int i = 0; i < 8; i++) {
         Vector2f p = toScreen(
            (i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ
         );
         if (p == null) {
            return null;
         }

         minX = Math.min(minX, p.x);
         minY = Math.min(minY, p.y);
         maxX = Math.max(maxX, p.x);
         maxY = Math.max(maxY, p.y);
      }

      return new int[]{(int)Math.floor((double)minX), (int)Math.floor((double)minY), (int)Math.ceil((double)maxX), (int)Math.ceil((double)maxY)};
   }
}

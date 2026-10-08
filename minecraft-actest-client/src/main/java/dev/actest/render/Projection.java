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
 * Перевод мировых координат в экранные (координаты GUI для DrawContext).
 * Матрицы берутся из того же кадра, что и картинка мира, поэтому рамки
 * совпадают с моделями игроков, включая покачивание камеры и FOV спринта.
 */
public final class Projection {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3d cameraPos = Vec3d.ZERO;
	private static boolean ready;

	private Projection() {
	}

	/** WorldRenderEvents.LAST: копируем матрицы (ваниль потом их переиспользует). */
	public static void capture(WorldRenderContext context) {
		VIEW.set(context.positionMatrix());       // поворот камеры
		PROJECTION.set(context.projectionMatrix()); // перспектива (FOV, bobbing)
		cameraPos = context.camera().getPos();
		ready = true;
	}

	public static boolean isReady() {
		return ready;
	}

	/** Точка мира → (x, y) в координатах GUI; null, если точка позади камеры. */
	public static Vector2f toScreen(double x, double y, double z) {
		// Ваниль рендерит мир относительно камеры, поэтому сначала вычитаем её позицию
		Vector4f clip = new Vector4f(
				(float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z), 1.0f);
		VIEW.transform(clip);
		PROJECTION.transform(clip);
		if (clip.w <= 0.05f) {
			return null;
		}
		Window window = MinecraftClient.getInstance().getWindow();
		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		return new Vector2f(
				(ndcX * 0.5f + 0.5f) * window.getScaledWidth(),
				(0.5f - ndcY * 0.5f) * window.getScaledHeight());
	}

	/**
	 * Проецирует 8 углов хитбокса и возвращает описывающий 2D-прямоугольник
	 * {x1, y1, x2, y2}; null, если хотя бы один угол позади камеры.
	 */
	public static int[] projectBox(Box box) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			Vector2f p = toScreen(
					(i & 1) == 0 ? box.minX : box.maxX,
					(i & 2) == 0 ? box.minY : box.maxY,
					(i & 4) == 0 ? box.minZ : box.maxZ);
			if (p == null) {
				return null;
			}
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
		}
		return new int[] {(int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY)};
	}
}

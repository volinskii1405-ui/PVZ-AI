package dev.actest.gui.widget;

/** Плавное приближение значения к цели, не зависящее от FPS. */
final class Animator {
	private float value;
	private long lastNanos = -1;

	Animator(float initial) {
		this.value = initial;
	}

	/**
	 * @param speed чем больше, тем быстрее (≈ 1/секунды до почти полного совпадения × 4)
	 */
	float update(float target, float speed) {
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		value += (target - value) * Math.min(1f, dt * speed);
		if (Math.abs(target - value) < 0.002f) {
			value = target;
		}
		return value;
	}
}

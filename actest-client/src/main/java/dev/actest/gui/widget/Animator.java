package dev.actest.gui.widget;

final class Animator {
   private float value;
   private long lastNanos = -1L;

   Animator(float initial) {
      this.value = initial;
   }

   float update(float target, float speed) {
      long now = System.nanoTime();
      float dt = this.lastNanos < 0L ? 0.0F : Math.min(0.1F, (float)(now - this.lastNanos) / 1.0E9F);
      this.lastNanos = now;
      this.value = this.value + (target - this.value) * Math.min(1.0F, dt * speed);
      if (Math.abs(target - this.value) < 0.002F) {
         this.value = target;
      }

      return this.value;
   }
}

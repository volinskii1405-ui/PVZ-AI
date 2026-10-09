package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Blink — задерживает пакеты движения.
 *
 * Пока модуль включён, PlayerMoveC2SPacket не уходят на сервер, а копятся в очереди (миксин на
 * ClientCommonNetworkHandler.sendPacket). Для сервера игрок замирает на месте; при выключении — или каждые
 * releaseTicks тиков — вся очередь отправляется пачкой, и сервер разом получает весь пройденный путь.
 * Остальные пакеты (атаки, клики, keep-alive) уходят как обычно.
 */
public final class BlinkModule extends AbstractModule {
   private final List<Packet<?>> held = new ArrayList<>();
   /** Во время отправки очереди наш же перехват должен пропускать пакеты. */
   private boolean flushing;
   private int heldTicks;
   private Vec3d serverPos;

   public BlinkModule() {
      super("Blink", "key.actest.blink", 79); // O
   }

   @Override
   public String getHudInfo() {
      synchronized (this.held) {
         return String.format(Locale.ROOT, "%d пак. %.1fс", this.held.size(), this.heldTicks / 20.0);
      }
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      synchronized (this.held) {
         this.held.clear();
      }

      this.heldTicks = 0;
      this.serverPos = player == null ? null : player.getPos();
   }

   @Override
   protected void onDisable() {
      this.flush(MinecraftClient.getInstance().player, "disable");
   }

   /** Вызывается миксином для каждого исходящего пакета. true — пакет задержан (не отправлять). */
   public boolean hold(Packet<?> packet) {
      if (!this.isEnabled() || this.flushing || !(packet instanceof PlayerMoveC2SPacket)) {
         return false;
      }

      synchronized (this.held) {
         this.held.add(packet);
      }

      return true;
   }

   @Override
   public void onTick(MinecraftClient client) {
      ActestConfig.Blink cfg = ActestConfig.get().blink;
      this.heldTicks++;
      if (this.heldTicks >= cfg.maxTicks) {
         this.setEnabled(false); // защита: слишком долгое удержание — выключаемся (onDisable отправит очередь)
      } else if (cfg.releaseTicks > 0 && this.heldTicks % cfg.releaseTicks == 0) {
         this.flush(client.player, "pulse");
      }
   }

   private void flush(ClientPlayerEntity player, String reason) {
      List<Packet<?>> packets;
      synchronized (this.held) {
         packets = new ArrayList<>(this.held);
         this.held.clear();
      }

      if (player == null || packets.isEmpty()) {
         return; // вышли из мира — отправлять уже некуда
      }

      this.flushing = true;
      try {
         for (Packet<?> packet : packets) {
            player.networkHandler.sendPacket(packet);
         }
      } finally {
         this.flushing = false;
      }

      Vec3d now = player.getPos();
      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT,
            "[Blink] age=%d reason=%s packets=%d heldTicks=%d jump=%.3f",
            player.age,
            reason,
            packets.size(),
            this.heldTicks,
            this.serverPos == null ? 0.0 : now.distanceTo(this.serverPos)
         ));
      }

      this.serverPos = now;
   }
}

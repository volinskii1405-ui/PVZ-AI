package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;

/**
 * NoFall: SPOOF — onGround=true в обычных пакетах движения во время падения,
 * PACKET — дополнительный PlayerMoveC2SPacket.OnGroundOnly(true) каждый тик падения.
 */
public final class NoFallModule extends AbstractModule {
   private static final double FALL_THRESHOLD = 2.0;
   private double fallen;
   private double lastY = Double.NaN;
   private boolean spoofedThisTick;
   private boolean packetSentThisTick;

   public NoFallModule() {
      super("NoFall", "key.actest.nofall", 78);
   }

   @Override
   public String getHudInfo() {
      return ActestConfig.get().noFall.mode.name();
   }

   @Override
   protected void onEnable() {
      this.fallen = 0.0;
      this.lastY = Double.NaN;
   }

   public void beforeMovementPackets(ClientPlayerEntity player) {
      double y = player.getY();
      if (player.isOnGround()
         || player.getAbilities().flying
         || player.isTouchingWater()
         || player.isInLava()
         || player.isClimbing()
         || player.hasVehicle()) {
         this.fallen = 0.0;
      } else if (!Double.isNaN(this.lastY) && y < this.lastY) {
         this.fallen = this.fallen + (this.lastY - y);
      }

      this.lastY = y;
   }

   double fallen() {
      return this.fallen;
   }

   private boolean isFalling(ClientPlayerEntity player) {
      return !player.isOnGround() && this.fallen > 2.0 && !player.isSpectator();
   }

   public boolean shouldSpoofGround(ClientPlayerEntity player) {
      boolean spoof = this.isEnabled() && ActestConfig.get().noFall.mode == ActestConfig.NoFall.Mode.SPOOF && this.isFalling(player);
      this.spoofedThisTick |= spoof;
      return spoof;
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (ActestConfig.get().noFall.mode == ActestConfig.NoFall.Mode.PACKET && this.isFalling(player)) {
         player.networkHandler.sendPacket(new OnGroundOnly(true, player.horizontalCollision));
         this.packetSentThisTick = true;
      }
   }

   String consumeTickAction() {
      String action = this.spoofedThisTick ? "SPOOF" : (this.packetSentThisTick ? "PACKET" : "-");
      this.spoofedThisTick = false;
      this.packetSentThisTick = false;
      return action;
   }
}

package dev.actest.module;

import dev.actest.ActestClient;
import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * KillAura — автоматическая атака ближайшей подходящей цели в радиусе.
 *
 * Удар отправляется ровно тем же путём, что и клик мышью в ванилле:
 * ClientPlayerInteractionManager.attackEntity() → PlayerInteractEntityC2SPacket (ATTACK),
 * затем взмах рукой → HandSwingC2SPacket. Отличается только то, КОГДА и КУДА игрок смотрит.
 */
public final class KillAuraModule extends AbstractModule {
   /** Накопитель «долей удара» для режима CPS: +cps/20 каждый тик, удар при >= 1. */
   private double attackBudget;
   /** В режиме PACKET сервер «смотрит» на цель — на следующем тике без удара вернём ему настоящий поворот. */
   private boolean restoreLook;

   public KillAuraModule() {
      super("KillAura", "key.actest.killaura", 86); // V
   }

   @Override
   public String getHudInfo() {
      ActestConfig.KillAura cfg = ActestConfig.get().killAura;
      String timing = cfg.waitCooldown ? "CD" : String.format(Locale.ROOT, "%.0fcps", cfg.cps);
      return String.format(Locale.ROOT, "%s %.1f %s", cfg.rotation, cfg.range, timing);
   }

   @Override
   protected void onEnable() {
      this.attackBudget = 0.0;
      this.restoreLook = false;
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         this.sendRealLookIfNeeded(player);
      }
   }

   @Override
   public void onTick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || client.currentScreen != null || player.isSpectator() || player.isDead()) {
         // В открытом меню / в спектаторе / мёртвым ванилла бить не даёт — и мы не бьём.
         this.attackBudget = 0.0;
         this.sendRealLookIfNeeded(player);
         return;
      }

      ActestConfig.KillAura cfg = ActestConfig.get().killAura;
      LivingEntity target = this.findTarget(client, player, cfg);
      if (target == null) {
         this.attackBudget = 0.0;
         this.sendRealLookIfNeeded(player);
         return;
      }

      float[] rotation = Rotations.to(player.getEyePos(), aimPoint(player.getEyePos(), target.getBoundingBox()));
      // Углы, которые сервер считает текущими на момент удара (для лога).
      float serverYaw = player.getYaw();
      float serverPitch = player.getPitch();
      rotation[0] = Rotations.continuousYaw(player.getYaw(), rotation[0]);
      if (cfg.rotation == ActestConfig.KillAura.Rotation.CLIENT) {
         // Камера поворачивается на цель; поворот уйдёт на сервер обычным пакетом движения
         // следующего тика, и только ПОСЛЕ этого бьём — как живой игрок «навёлся и кликнул».
         // Бьём, когда уже отправленный серверу взгляд (текущие yaw/pitch) попадает в хитбокс.
         boolean aimed = lookingAt(player, target.getBoundingBox());
         player.setYaw(rotation[0]);
         player.setPitch(rotation[1]);
         if (!aimed) {
            return;
         }
      }

      if (!this.readyToAttack(player, cfg)) {
         this.sendRealLookIfNeeded(player);
         return;
      }

      if (cfg.rotation == ActestConfig.KillAura.Rotation.PACKET) {
         // «Тихий» поворот: камера на месте, но прямо перед ударом серверу уходит
         // PlayerMoveC2SPacket.LookAndOnGround с углами на цель.
         Rotations.sendLook(player, rotation[0], rotation[1]);
         this.restoreLook = true;
         serverYaw = rotation[0];
         serverPitch = rotation[1];
      }

      if (ActestConfig.get().debugLog) {
         ActestClient.LOGGER.info(String.format(
            Locale.ROOT,
            "[KillAura] age=%d target=%s dist=%.3f yaw=%.2f pitch=%.2f rot=%s cooldown=%.2f walls=%b",
            player.age,
            target.getName().getString(),
            Math.sqrt(squaredDistanceToBox(player.getEyePos(), target.getBoundingBox())),
            serverYaw,
            serverPitch,
            cfg.rotation,
            player.getAttackCooldownProgress(0.5F),
            !player.canSee(target)
         ));
      }

      client.interactionManager.attackEntity(player, target);
      if (cfg.swing) {
         player.swingHand(Hand.MAIN_HAND);
      }
   }

   /** Пора ли бить: либо по ванильному кулдауну атаки (1.9+), либо с фиксированным CPS. */
   private boolean readyToAttack(ClientPlayerEntity player, ActestConfig.KillAura cfg) {
      if (cfg.waitCooldown) {
         return player.getAttackCooldownProgress(0.5F) >= 1.0F;
      }

      this.attackBudget = Math.min(this.attackBudget + cfg.cps / 20.0, 1.0);
      if (this.attackBudget >= 1.0) {
         this.attackBudget -= 1.0;
         return true;
      }

      return false;
   }

   private LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity player, ActestConfig.KillAura cfg) {
      Vec3d eye = player.getEyePos();
      double rangeSq = cfg.range * cfg.range;
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;

      for (Entity entity : client.world.getEntities()) {
         if (!(entity instanceof LivingEntity living) || !isValidTarget(living, player, cfg)) {
            continue;
         }

         // Дистанция — от глаз до ближайшей точки хитбокса, как её считает ванильный сервер.
         double distSq = squaredDistanceToBox(eye, living.getBoundingBox());
         if (distSq > rangeSq) {
            continue;
         }

         if (!cfg.throughWalls && !player.canSee(living)) {
            continue;
         }

         double score = switch (cfg.priority) {
            case DISTANCE -> distSq;
            case HEALTH -> living.getHealth();
            case ANGLE -> {
               float[] rot = Rotations.to(eye, aimPoint(eye, living.getBoundingBox()));
               yield Rotations.angleBetween(player.getYaw(), player.getPitch(), rot[0], rot[1]);
            }
         };
         if (score < bestScore) {
            bestScore = score;
            best = living;
         }
      }

      return best;
   }

   private static boolean isValidTarget(LivingEntity entity, ClientPlayerEntity self, ActestConfig.KillAura cfg) {
      if (entity == self || !entity.isAlive() || entity.isDead()) {
         return false;
      }

      if (entity instanceof PlayerEntity other) {
         // Креатив и спектатор урон не получают — бить их бессмысленно.
         return cfg.players && !other.isSpectator() && !other.isCreative();
      }

      if (entity instanceof MobEntity) {
         return entity instanceof Monster ? cfg.hostileMobs : cfg.passiveMobs;
      }

      return false; // стойки для брони и прочее не трогаем
   }

   /** Точка хитбокса, ближайшая к глазам (немного внутрь от краёв). */
   private static Vec3d aimPoint(Vec3d eye, Box box) {
      double inset = 0.05;
      return new Vec3d(
         clamp(eye.x, box.minX + inset, box.maxX - inset),
         clamp(eye.y, box.minY + inset, box.maxY - inset),
         clamp(eye.z, box.minZ + inset, box.maxZ - inset)
      );
   }

   /** Пересекает ли луч взгляда игрока хитбокс (метод «слэбов» для AABB). */
   private static boolean lookingAt(ClientPlayerEntity player, Box box) {
      Vec3d eye = player.getEyePos();
      double yaw = Math.toRadians(player.getYaw());
      double pitch = Math.toRadians(player.getPitch());
      double[] origin = {eye.x, eye.y, eye.z};
      double[] dir = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
      double[] min = {box.minX, box.minY, box.minZ};
      double[] max = {box.maxX, box.maxY, box.maxZ};
      double tMin = 0.0;
      double tMax = 64.0;

      for (int i = 0; i < 3; i++) {
         if (Math.abs(dir[i]) < 1.0E-9) {
            if (origin[i] < min[i] || origin[i] > max[i]) {
               return false;
            }
         } else {
            double t1 = (min[i] - origin[i]) / dir[i];
            double t2 = (max[i] - origin[i]) / dir[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
               return false;
            }
         }
      }

      return true;
   }

   private static double squaredDistanceToBox(Vec3d p, Box box) {
      double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double clamp(double value, double min, double max) {
      return min > max ? (min + max) / 2.0 : Math.max(min, Math.min(max, value));
   }

   private void sendRealLookIfNeeded(ClientPlayerEntity player) {
      if (this.restoreLook) {
         this.restoreLook = false;
         Rotations.sendLook(player, player.getYaw(), player.getPitch());
      }
   }

}

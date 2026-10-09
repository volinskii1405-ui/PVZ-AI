package dev.actest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.actest.ActestClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Конфиг config/actest.json (Gson). Перечитывается автоматически раз в секунду, если файл изменился.
 */
public final class ActestConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("actest.json");
   private static ActestConfig instance = new ActestConfig();
   private static long lastModified = Long.MIN_VALUE;
   public List<String> allowedServers = new ArrayList<>(List.of("localhost", "127.0.0.1"));
   public boolean allowSingleplayer = true;
   public boolean debugLog = false;
   public ActestConfig.Speed speed = new ActestConfig.Speed();
   public ActestConfig.Fly fly = new ActestConfig.Fly();
   public ActestConfig.NoFall noFall = new ActestConfig.NoFall();
   public ActestConfig.NoSlow noSlow = new ActestConfig.NoSlow();
   public ActestConfig.Step step = new ActestConfig.Step();
   public ActestConfig.Reach reach = new ActestConfig.Reach();
   public ActestConfig.Wallhack wallhack = new ActestConfig.Wallhack();
   public ActestConfig.KillAura killAura = new ActestConfig.KillAura();
   public ActestConfig.AutoTotem autoTotem = new ActestConfig.AutoTotem();
   public ActestConfig.Hud hud = new ActestConfig.Hud();

   public static ActestConfig get() {
      return instance;
   }

   public static Path path() {
      return PATH;
   }

   public static void load() {
      if (Files.notExists(PATH)) {
         instance = new ActestConfig();
         instance.sanitize();
         save();
      } else {
         read();
      }

      lastModified = modifiedTime();
   }

   public static void reloadIfChanged() {
      long modified = modifiedTime();
      if (modified != lastModified) {
         lastModified = modified;
         if (read()) {
            ActestClient.LOGGER.info("Конфиг перечитан: {}", PATH);
         }
      }
   }

   public static void save() {
      try {
         Files.createDirectories(PATH.getParent());

         try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
         }

         lastModified = modifiedTime();
      } catch (IOException e) {
         ActestClient.LOGGER.error("Не удалось сохранить {}", PATH, e);
      }
   }

   private static boolean read() {
      ActestConfig loaded = null;
      boolean missingSections = false;

      try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
         JsonElement json = JsonParser.parseReader(reader);
         if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            // Конфиг от версии 1.0.0: секций новых модулей ещё нет — допишем их со значениями по умолчанию.
            missingSections = !object.has("killAura") || !object.has("autoTotem");
            loaded = (ActestConfig)GSON.fromJson(object, ActestConfig.class);
         }
      } catch (JsonParseException | IllegalStateException | IOException e) {
         ActestClient.LOGGER.error("Ошибка в {}: {} — оставлены прежние настройки", PATH, e.getMessage());
         return false;
      }

      if (loaded == null) {
         loaded = new ActestConfig();
      }

      loaded.sanitize();
      instance = loaded;
      if (missingSections) {
         save();
         ActestClient.LOGGER.info("В {} добавлены секции killAura и autoTotem", PATH);
      }

      return true;
   }

   private static long modifiedTime() {
      try {
         return Files.getLastModifiedTime(PATH).toMillis();
      } catch (IOException e) {
         return Long.MIN_VALUE;
      }
   }

   private void sanitize() {
      if (this.allowedServers == null) {
         this.allowedServers = new ArrayList<>();
      }

      if (this.speed == null) {
         this.speed = new ActestConfig.Speed();
      }

      if (this.speed.mode == null) {
         this.speed.mode = ActestConfig.Speed.Mode.GROUND;
      }

      this.speed.multiplier = clamp(this.speed.multiplier, 1.0, 5.0, 1.0);
      if (this.fly == null) {
         this.fly = new ActestConfig.Fly();
      }

      if (this.fly.mode == null) {
         this.fly.mode = ActestConfig.Fly.Mode.MOTION;
      }

      this.fly.speed = clamp(this.fly.speed, 0.1, 2.0, 0.5);
      this.fly.verticalSpeed = clamp(this.fly.verticalSpeed, 0.1, 2.0, 0.4);
      this.fly.glideSpeed = clamp(this.fly.glideSpeed, 0.01, 0.3, 0.05);
      if (this.noFall == null) {
         this.noFall = new ActestConfig.NoFall();
      }

      if (this.noFall.mode == null) {
         this.noFall.mode = ActestConfig.NoFall.Mode.SPOOF;
      }

      if (this.noSlow == null) {
         this.noSlow = new ActestConfig.NoSlow();
      }

      if (this.step == null) {
         this.step = new ActestConfig.Step();
      }

      this.step.height = clamp(this.step.height, 0.6, 3.0, 1.0);
      if (this.reach == null) {
         this.reach = new ActestConfig.Reach();
      }

      this.reach.entityRange = clamp(this.reach.entityRange, 3.0, 8.0, 4.0);
      this.reach.blockRange = clamp(this.reach.blockRange, 4.5, 8.0, 6.0);
      if (this.wallhack == null) {
         this.wallhack = new ActestConfig.Wallhack();
      }

      if (this.wallhack.mode == null) {
         this.wallhack.mode = ActestConfig.Wallhack.Mode.GLOW;
      }

      if (Double.isNaN(this.wallhack.maxDistance) || this.wallhack.maxDistance < 1.0) {
         this.wallhack.maxDistance = 1.0;
      }

      this.wallhack.parseColors();
      if (this.killAura == null) {
         this.killAura = new ActestConfig.KillAura();
      }

      if (this.killAura.rotation == null) {
         this.killAura.rotation = ActestConfig.KillAura.Rotation.PACKET;
      }

      if (this.killAura.priority == null) {
         this.killAura.priority = ActestConfig.KillAura.Priority.DISTANCE;
      }

      this.killAura.range = clamp(this.killAura.range, 1.0, 6.0, 3.0);
      this.killAura.cps = clamp(this.killAura.cps, 1.0, 20.0, 8.0);
      if (this.autoTotem == null) {
         this.autoTotem = new ActestConfig.AutoTotem();
      }

      if (this.autoTotem.mode == null) {
         this.autoTotem.mode = ActestConfig.AutoTotem.Mode.ALWAYS;
      }

      if (this.autoTotem.method == null) {
         this.autoTotem.method = ActestConfig.AutoTotem.Method.SWAP;
      }

      this.autoTotem.health = clamp(this.autoTotem.health, 1.0, 20.0, 10.0);
      this.autoTotem.delay = Math.max(0, Math.min(20, this.autoTotem.delay));
      if (this.hud == null) {
         this.hud = new ActestConfig.Hud();
      }
   }

   private static double clamp(double value, double min, double max, double fallback) {
      return Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
   }

   private static int parseColor(String value, int fallback) {
      if (value == null) {
         return fallback;
      } else {
         String hex = value.trim();
         if (hex.startsWith("#")) {
            hex = hex.substring(1);
         }

         try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
         } catch (NumberFormatException e) {
            ActestClient.LOGGER.warn("Неверный цвет '{}', используется #{}", value, String.format(Locale.ROOT, "%06X", fallback));
            return fallback;
         }
      }
   }

   public static final class Fly {
      public ActestConfig.Fly.Mode mode = ActestConfig.Fly.Mode.MOTION;
      public double speed = 0.5;
      public double verticalSpeed = 0.4;
      public double glideSpeed = 0.05;

      public static enum Mode {
         MOTION,
         GLIDE;
      }
   }

   /** Настройки KillAura. */
   public static final class KillAura {
      /** Радиус атаки в блоках: от глаз до ближайшей точки хитбокса цели (ваниль — 3.0). */
      public double range = 3.0;
      /** Как поворачиваться к цели: NONE — никак, PACKET — только в пакете перед ударом, CLIENT — поворачивать камеру. */
      public ActestConfig.KillAura.Rotation rotation = ActestConfig.KillAura.Rotation.PACKET;
      /** Кого бить первым, если целей несколько. */
      public ActestConfig.KillAura.Priority priority = ActestConfig.KillAura.Priority.DISTANCE;
      /** true — бить по готовности ванильного кулдауна атаки, false — с частотой cps. */
      public boolean waitCooldown = true;
      /** Ударов в секунду, если waitCooldown = false (1–20, больше 20 за тик не бывает). */
      public double cps = 8.0;
      public boolean players = true;
      public boolean hostileMobs = false;
      public boolean passiveMobs = false;
      /** Бить без прямой видимости (сквозь блоки). */
      public boolean throughWalls = false;
      /** Отправлять взмах рукой после удара, как ванилла. */
      public boolean swing = true;

      public static enum Rotation {
         NONE,
         PACKET,
         CLIENT;
      }

      public static enum Priority {
         DISTANCE,
         HEALTH,
         ANGLE;
      }
   }

   /** Настройки AutoTotem. */
   public static final class AutoTotem {
      /** ALWAYS — тотем во второй руке всегда, HEALTH — только когда здоровье (с поглощением) <= health. */
      public ActestConfig.AutoTotem.Mode mode = ActestConfig.AutoTotem.Mode.ALWAYS;
      /** Порог здоровья в единицах HP (20 = 10 сердец). */
      public double health = 10.0;
      /** SWAP — один клик SWAP (как F в инвентаре), PICKUP — 2–3 обычных клика мышью. */
      public ActestConfig.AutoTotem.Method method = ActestConfig.AutoTotem.Method.SWAP;
      /** Сколько тиков ждать после того, как тотем пропал из второй руки (0–20). */
      public int delay = 0;

      public static enum Mode {
         ALWAYS,
         HEALTH;
      }

      public static enum Method {
         SWAP,
         PICKUP;
      }
   }

   public static final class Hud {
      public boolean enabled = true;
      public int x = 4;
      public int y = 4;
   }

   public static final class NoFall {
      public ActestConfig.NoFall.Mode mode = ActestConfig.NoFall.Mode.SPOOF;

      public static enum Mode {
         SPOOF,
         PACKET;
      }
   }

   public static final class NoSlow {
      public boolean items = true;
      public boolean blocks = true;
   }

   public static final class Reach {
      public double entityRange = 4.0;
      public boolean blocks = false;
      public double blockRange = 6.0;
   }

   public static final class Speed {
      public ActestConfig.Speed.Mode mode = ActestConfig.Speed.Mode.GROUND;
      public double multiplier = 1.5;

      public static enum Mode {
         GROUND,
         BHOP;
      }
   }

   public static final class Step {
      public double height = 1.0;
   }

   public static final class Wallhack {
      public ActestConfig.Wallhack.Mode mode = ActestConfig.Wallhack.Mode.GLOW;
      public boolean players = true;
      public boolean hostileMobs = false;
      public boolean passiveMobs = false;
      public boolean showNames = true;
      public boolean showDistance = true;
      public String color = "#FF4040";
      public String hostileColor = "#FF9020";
      public String passiveColor = "#40FF40";
      public double maxDistance = 128.0;
      private transient int[] rgb = new int[]{0xFF4040, 0xFF9020, 0x40FF40};

      public boolean shows(ActestConfig.Wallhack.Target target) {
         return switch (target) {
            case PLAYERS -> this.players;
            case HOSTILE -> this.hostileMobs;
            case PASSIVE -> this.passiveMobs;
         };
      }

      public void setShown(ActestConfig.Wallhack.Target target, boolean value) {
         switch (target) {
            case PLAYERS:
               this.players = value;
               break;
            case HOSTILE:
               this.hostileMobs = value;
               break;
            case PASSIVE:
               this.passiveMobs = value;
         }
      }

      public int color(ActestConfig.Wallhack.Target target) {
         return this.rgb[target.ordinal()];
      }

      public void setColor(ActestConfig.Wallhack.Target target, int value) {
         this.rgb[target.ordinal()] = value & 0xFFFFFF;
         String hex = String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
         switch (target) {
            case PLAYERS:
               this.color = hex;
               break;
            case HOSTILE:
               this.hostileColor = hex;
               break;
            case PASSIVE:
               this.passiveColor = hex;
         }
      }

      private void parseColors() {
         this.rgb = new int[]{
            ActestConfig.parseColor(this.color, 0xFF4040),
            ActestConfig.parseColor(this.hostileColor, 0xFF9020),
            ActestConfig.parseColor(this.passiveColor, 0x40FF40)
         };
      }

      public static enum Mode {
         GLOW,
         BOX,
         BOTH;
      }

      public static enum Target {
         PLAYERS,
         HOSTILE,
         PASSIVE;
      }
   }
}

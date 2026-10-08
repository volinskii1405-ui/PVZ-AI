package dev.actest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.actest.ActestClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Конфиг мода: файл config/actest.json в папке игры.
 * Создаётся со значениями по умолчанию при первом запуске и перечитывается
 * автоматически, если файл изменился (ModuleManager проверяет раз в секунду),
 * так что параметры можно менять прямо во время игры.
 */
public final class ActestConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("actest.json");

	private static ActestConfig instance = new ActestConfig();
	private static long lastModified = Long.MIN_VALUE;

	// ---------------- Поля, которые сохраняются в JSON ----------------

	/**
	 * Серверы, на которых модули разрешено включать: "host" или "host:port".
	 * На любом другом сервере модули не включатся, а включённые выключатся сами.
	 */
	public List<String> allowedServers = new ArrayList<>(List.of("localhost", "127.0.0.1"));
	/** Разрешить модули в одиночной игре (удобно для отладки рендера). */
	public boolean allowSingleplayer = true;

	/** Писать в лог клиента смещение и onGround за каждый тик — для сверки с логами античита. */
	public boolean debugLog = false;

	public Speed speed = new Speed();
	public Fly fly = new Fly();
	public NoFall noFall = new NoFall();
	public NoSlow noSlow = new NoSlow();
	public Wallhack wallhack = new Wallhack();
	public Hud hud = new Hud();

	public static final class Speed {
		public enum Mode { GROUND, BHOP }

		/** Режим: GROUND — скорость только по земле, BHOP — автопрыжки с ускорением. */
		public Mode mode = Mode.GROUND;
		/** Множитель относительно ванильной скорости (1.0 = как без мода), диапазон 1.0–5.0. */
		public double multiplier = 1.5;
	}

	public static final class Fly {
		public enum Mode { MOTION, GLIDE }

		/** MOTION — зависание и полёт по WASD/Space/Shift, GLIDE — медленное падение. */
		public Mode mode = Mode.MOTION;
		/** Горизонтальная скорость полёта, блоков за тик (0.1–2.0). */
		public double speed = 0.5;
		/** Скорость подъёма/спуска на Space/Shift, блоков за тик (0.1–2.0). */
		public double verticalSpeed = 0.4;
		/** Скорость падения в режиме GLIDE, блоков за тик (0.01–0.3). */
		public double glideSpeed = 0.05;
	}

	public static final class NoFall {
		public enum Mode { SPOOF, PACKET }

		/** SPOOF — onGround=true в обычных пакетах движения, PACKET — отдельный OnGroundOnly(true). */
		public Mode mode = Mode.SPOOF;
	}

	public static final class NoSlow {
		/** Без замедления при использовании предметов: еда, зелья, лук, арбалет, щит, трезубец… */
		public boolean items = true;
		/** Без замедления на песке душ и блоке мёда. */
		public boolean blocks = true;
	}

	public static final class Wallhack {
		public enum Mode { GLOW, BOX, BOTH }

		/** Группы подсвечиваемых сущностей: у каждой свой переключатель и цвет. */
		public enum Target { PLAYERS, HOSTILE, PASSIVE }

		/** GLOW — ванильный контур (как эффект свечения), BOX — 2D-рамки, BOTH — оба. */
		public Mode mode = Mode.GLOW;
		/** Подсвечивать других игроков. */
		public boolean players = true;
		/** Подсвечивать враждебных мобов (зомби, скелеты, криперы, слаймы, гасты…). */
		public boolean hostileMobs = false;
		/** Подсвечивать остальных мобов (животные, жители, големы, рыбы…). */
		public boolean passiveMobs = false;
		public boolean showNames = true;
		public boolean showDistance = true;
		/** Цвета контура и рамок в формате "#RRGGBB": игроки, враждебные и мирные мобы. */
		public String color = "#FF4040";
		public String hostileColor = "#FF9020";
		public String passiveColor = "#40FF40";
		/** Сущности дальше этой дистанции (в блоках) не подсвечиваются. */
		public double maxDistance = 128.0;

		/** Разобранные цвета 0xRRGGBB по индексу Target; в JSON не пишутся. */
		private transient int[] rgb = {0xFF4040, 0xFF9020, 0x40FF40};

		public boolean shows(Target target) {
			return switch (target) {
				case PLAYERS -> players;
				case HOSTILE -> hostileMobs;
				case PASSIVE -> passiveMobs;
			};
		}

		public void setShown(Target target, boolean value) {
			switch (target) {
				case PLAYERS -> players = value;
				case HOSTILE -> hostileMobs = value;
				case PASSIVE -> passiveMobs = value;
			}
		}

		public int color(Target target) {
			return rgb[target.ordinal()];
		}

		/** Меняет цвет группы (из меню): и строку "#RRGGBB" для JSON, и разобранное значение. */
		public void setColor(Target target, int value) {
			rgb[target.ordinal()] = value & 0xFFFFFF;
			String hex = String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
			switch (target) {
				case PLAYERS -> color = hex;
				case HOSTILE -> hostileColor = hex;
				case PASSIVE -> passiveColor = hex;
			}
		}

		private void parseColors() {
			rgb = new int[] {
					parseColor(color, 0xFF4040),
					parseColor(hostileColor, 0xFF9020),
					parseColor(passiveColor, 0x40FF40)};
		}
	}

	public static final class Hud {
		public boolean enabled = true;
		public int x = 4;
		public int y = 4;
	}

	// ---------------- Загрузка / сохранение ----------------

	public static ActestConfig get() {
		return instance;
	}

	public static Path path() {
		return PATH;
	}

	/** Загрузка при старте: если файла нет — создаём его со значениями по умолчанию. */
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

	/** Перечитать файл, если его изменили с момента последней загрузки. */
	public static void reloadIfChanged() {
		long modified = modifiedTime();
		if (modified == lastModified) {
			return;
		}
		lastModified = modified;
		if (read()) {
			ActestClient.LOGGER.info("Конфиг перечитан: {}", PATH);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			// Свою же запись не считаем внешним изменением файла
			lastModified = modifiedTime();
		} catch (IOException e) {
			ActestClient.LOGGER.error("Не удалось сохранить {}", PATH, e);
		}
	}

	/** Читает файл; при ошибке оставляет предыдущий рабочий конфиг. */
	private static boolean read() {
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			ActestConfig loaded = GSON.fromJson(reader, ActestConfig.class);
			if (loaded == null) {
				loaded = new ActestConfig();
			}
			loaded.sanitize();
			instance = loaded;
			return true;
		} catch (IOException | JsonParseException e) {
			ActestClient.LOGGER.error("Ошибка в {}: {} — оставлены прежние настройки", PATH, e.getMessage());
			return false;
		}
	}

	private static long modifiedTime() {
		try {
			return Files.getLastModifiedTime(PATH).toMillis();
		} catch (IOException e) {
			return Long.MIN_VALUE;
		}
	}

	/** Чиним отсутствующие/некорректные значения после ручного редактирования JSON. */
	private void sanitize() {
		if (allowedServers == null) allowedServers = new ArrayList<>();
		if (speed == null) speed = new Speed();
		if (speed.mode == null) speed.mode = Speed.Mode.GROUND;
		speed.multiplier = clamp(speed.multiplier, 1.0, 5.0, 1.0);
		if (fly == null) fly = new Fly();
		if (fly.mode == null) fly.mode = Fly.Mode.MOTION;
		fly.speed = clamp(fly.speed, 0.1, 2.0, 0.5);
		fly.verticalSpeed = clamp(fly.verticalSpeed, 0.1, 2.0, 0.4);
		fly.glideSpeed = clamp(fly.glideSpeed, 0.01, 0.3, 0.05);
		if (noFall == null) noFall = new NoFall();
		if (noFall.mode == null) noFall.mode = NoFall.Mode.SPOOF;
		if (noSlow == null) noSlow = new NoSlow();
		if (wallhack == null) wallhack = new Wallhack();
		if (wallhack.mode == null) wallhack.mode = Wallhack.Mode.GLOW;
		if (Double.isNaN(wallhack.maxDistance) || wallhack.maxDistance < 1.0) wallhack.maxDistance = 1.0;
		wallhack.parseColors();
		if (hud == null) hud = new Hud();
	}

	private static double clamp(double value, double min, double max, double fallback) {
		return Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
	}

	private static int parseColor(String value, int fallback) {
		if (value == null) return fallback;
		String hex = value.trim();
		if (hex.startsWith("#")) hex = hex.substring(1);
		try {
			return Integer.parseInt(hex, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			ActestClient.LOGGER.warn("Неверный цвет '{}', используется #{}", value,
					String.format(Locale.ROOT, "%06X", fallback));
			return fallback;
		}
	}
}

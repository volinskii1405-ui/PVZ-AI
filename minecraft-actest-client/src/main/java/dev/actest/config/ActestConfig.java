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
	private static final int DEFAULT_COLOR = 0xFF4040;

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

	public Speed speed = new Speed();
	public Wallhack wallhack = new Wallhack();
	public Hud hud = new Hud();

	/** Цвет WH в виде 0xRRGGBB, вычисляется из wallhack.color; в JSON не пишется. */
	private transient int wallhackColorRgb = DEFAULT_COLOR;

	public static final class Speed {
		public enum Mode { GROUND, BHOP }

		/** Режим: GROUND — скорость только по земле, BHOP — автопрыжки с ускорением. */
		public Mode mode = Mode.GROUND;
		/** Множитель относительно ванильной скорости (1.0 = как без мода), диапазон 1.0–5.0. */
		public double multiplier = 1.5;
		/** Писать в лог клиента смещение за каждый тик — для сверки с логами античита. */
		public boolean debugLog = false;
	}

	public static final class Wallhack {
		public enum Mode { GLOW, BOX, BOTH }

		/** GLOW — ванильный контур (как эффект свечения), BOX — 2D-рамки, BOTH — оба. */
		public Mode mode = Mode.GLOW;
		public boolean showNames = true;
		public boolean showDistance = true;
		/** Цвет контура и рамок, "#RRGGBB". */
		public String color = "#FF4040";
		/** Игроки дальше этой дистанции (в блоках) не подсвечиваются. */
		public double maxDistance = 128.0;
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

	public int wallhackColor() {
		return wallhackColorRgb;
	}

	/** Меняет цвет WH (из меню): и строку "#RRGGBB" для JSON, и разобранное значение. */
	public void setWallhackColor(int rgb) {
		wallhackColorRgb = rgb & 0xFFFFFF;
		wallhack.color = String.format(Locale.ROOT, "#%06X", wallhackColorRgb);
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
		speed.multiplier = Double.isNaN(speed.multiplier) ? 1.0 : Math.max(1.0, Math.min(5.0, speed.multiplier));
		if (wallhack == null) wallhack = new Wallhack();
		if (wallhack.mode == null) wallhack.mode = Wallhack.Mode.GLOW;
		if (Double.isNaN(wallhack.maxDistance) || wallhack.maxDistance < 1.0) wallhack.maxDistance = 1.0;
		wallhackColorRgb = parseColor(wallhack.color);
		if (hud == null) hud = new Hud();
	}

	private static int parseColor(String value) {
		if (value == null) return DEFAULT_COLOR;
		String hex = value.trim();
		if (hex.startsWith("#")) hex = hex.substring(1);
		try {
			return Integer.parseInt(hex, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			ActestClient.LOGGER.warn("Неверный цвет '{}', используется #FF4040", value);
			return DEFAULT_COLOR;
		}
	}
}

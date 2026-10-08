package dev.actest.gui;

import dev.actest.config.ActestConfig;
import dev.actest.config.ActestConfig.Wallhack.Target;
import dev.actest.gui.widget.ColorChip;
import dev.actest.gui.widget.FlatButton;
import dev.actest.gui.widget.HueSlider;
import dev.actest.gui.widget.PillToggle;
import dev.actest.gui.widget.Segmented;
import dev.actest.gui.widget.Slider;
import dev.actest.gui.widget.TabButton;
import dev.actest.gui.widget.ToggleSwitch;
import dev.actest.module.Module;
import dev.actest.module.ModuleManager;
import dev.actest.module.SpeedModule;
import dev.actest.module.WallhackModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Меню настроек (по умолчанию правый Shift): тёмная панель со вкладками
 * Speed / WH / Прочее и самописными виджетами из пакета gui.widget.
 * Изменения применяются сразу, при закрытии сохраняются в config/actest.json.
 * Игра не ставится на паузу и фон не размывается — новый цвет подсветки
 * видно на сущностях вживую за меню.
 */
public final class ActestScreen extends Screen {
	private enum Tab {
		SPEED("Speed"), WH("WH"), OTHER("Прочее");

		final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	private static final int PANEL_WIDTH = 360;
	private static final int SIDEBAR_WIDTH = 86;
	private static final int HEADER_HEIGHT = 24;
	private static final int ROW_HEIGHT = 18;
	private static final int MAX_ROWS = 9;
	private static final int PAD = 10;
	private static final int PANEL_HEIGHT = HEADER_HEIGHT + PAD + MAX_ROWS * ROW_HEIGHT + PAD;
	private static final int[] PRESET_COLORS = {
			0xFF4040, 0xFF9020, 0xFFFF40, 0x40FF40, 0x40FFFF, 0x4080FF, 0xC040FF, 0xFFFFFF};

	/** Открытая вкладка и группа, чей цвет редактируется, — запоминаются между открытиями меню. */
	private static Tab tab = Tab.SPEED;
	private static Target colorTarget = Target.PLAYERS;

	/** Строка настройки: подпись слева, виджет справа. */
	private record Row(int y, Supplier<Text> label) {
	}

	private final ModuleManager modules;
	private final List<Row> rows = new ArrayList<>();
	private boolean rebuild;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int contentX;
	private int contentRight;
	private int nextRowY;
	/** Где начинается текст под строками вкладки (описание режима, справка). */
	private int footerY;

	public ActestScreen(ModuleManager modules) {
		super(Text.literal("AC Test Client"));
		this.modules = modules;
	}

	private static ActestConfig cfg() {
		return ActestConfig.get();
	}

	// ======================== Построение виджетов ========================

	@Override
	protected void init() {
		rows.clear();
		panelWidth = Math.min(PANEL_WIDTH, width - 8);
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(4, (height - PANEL_HEIGHT) / 2);
		contentX = panelX + SIDEBAR_WIDTH + PAD;
		contentRight = panelX + panelWidth - PAD;
		nextRowY = panelY + HEADER_HEIGHT + PAD;

		int tabY = panelY + HEADER_HEIGHT + 8;
		for (Tab t : Tab.values()) {
			BooleanSupplier indicator = switch (t) {
				case SPEED -> modules.get(SpeedModule.class)::isEnabled;
				case WH -> modules.get(WallhackModule.class)::isEnabled;
				case OTHER -> null;
			};
			addDrawableChild(new TabButton(panelX, tabY, SIDEBAR_WIDTH, 18, t.title, () -> tab == t, indicator,
					() -> switchTab(t)));
			tabY += 20;
		}
		addDrawableChild(new FlatButton(panelX + panelWidth - 20, panelY + 5, 14, 14, "×", this::close));

		switch (tab) {
			case SPEED -> initSpeed();
			case WH -> initWallhack();
			case OTHER -> initOther();
		}
		footerY = nextRowY + 6;
	}

	private void initSpeed() {
		moduleRow(modules.get(SpeedModule.class));

		int y = row("Режим");
		addDrawableChild(new Segmented<>(contentRight - 130, center(y, 14), 130, 14, Text.literal("Режим"),
				ActestConfig.Speed.Mode.values(),
				mode -> mode == ActestConfig.Speed.Mode.GROUND ? "По земле" : "Bhop",
				() -> cfg().speed.mode, mode -> cfg().speed.mode = mode));

		y = row("Скорость");
		addDrawableChild(new Slider(contentRight - 150, center(y, 14), 150, 14, Text.literal("Скорость"),
				1.0, 5.0, 0.05,
				() -> cfg().speed.multiplier, v -> cfg().speed.multiplier = v,
				v -> String.format(Locale.ROOT, "x%.2f", v)));

		y = row("Debug-лог");
		ToggleSwitch debug = addDrawableChild(new ToggleSwitch(contentRight - ToggleSwitch.WIDTH,
				center(y, ToggleSwitch.HEIGHT), Text.literal("Debug-лог"),
				() -> cfg().speed.debugLog, v -> cfg().speed.debugLog = v));
		debug.setTooltip(Tooltip.of(Text.literal("Смещение за каждый тик в logs/latest.log — для сверки с логами античита")));
	}

	private void initWallhack() {
		moduleRow(modules.get(WallhackModule.class));

		int y = row("Вид");
		addDrawableChild(new Segmented<>(contentRight - 150, center(y, 14), 150, 14, Text.literal("Вид"),
				ActestConfig.Wallhack.Mode.values(),
				mode -> switch (mode) {
					case GLOW -> "Контур";
					case BOX -> "Рамки";
					case BOTH -> "Оба";
				},
				() -> cfg().wallhack.mode, mode -> cfg().wallhack.mode = mode));

		// Группы: цветной квадратик (выбрать, чей цвет править) + переключатель
		for (Target target : Target.values()) {
			y = row(targetName(target));
			int toggleX = contentRight - ToggleSwitch.WIDTH;
			addDrawableChild(new ColorChip(toggleX - 18, center(y, 10), 10, Text.literal("Цвет: " + targetName(target)),
					() -> cfg().wallhack.color(target), () -> colorTarget == target, () -> colorTarget = target))
					.setTooltip(Tooltip.of(Text.literal("Нажмите, чтобы менять цвет этой группы")));
			addDrawableChild(new ToggleSwitch(toggleX, center(y, ToggleSwitch.HEIGHT), Text.literal(targetName(target)),
					() -> cfg().wallhack.shows(target), v -> cfg().wallhack.setShown(target, v)));
		}

		y = row("Подписи");
		PillToggle distance = new PillToggle(0, center(y, 14), 14, "Дистанция",
				() -> cfg().wallhack.showDistance, v -> cfg().wallhack.showDistance = v);
		distance.setX(contentRight - distance.getWidth());
		PillToggle names = new PillToggle(0, center(y, 14), 14, "Имена",
				() -> cfg().wallhack.showNames, v -> cfg().wallhack.showNames = v);
		names.setX(distance.getX() - 4 - names.getWidth());
		addDrawableChild(names);
		addDrawableChild(distance);

		y = row("Дальность");
		addDrawableChild(new Slider(contentRight - 150, center(y, 14), 150, 14, Text.literal("Дальность"),
				16, 512, 8,
				() -> cfg().wallhack.maxDistance, v -> cfg().wallhack.maxDistance = v,
				v -> String.format(Locale.ROOT, "%.0f бл.", v)));

		y = row(() -> Text.literal("Цвет: " + targetGenitive(colorTarget)));
		addDrawableChild(new HueSlider(contentRight - 150, center(y, 14), 150, 14, Text.literal("Оттенок"),
				() -> cfg().wallhack.color(colorTarget), rgb -> cfg().wallhack.setColor(colorTarget, rgb)));

		y = row(() -> Text.literal("Палитра").formatted(Formatting.GRAY));
		int size = 10;
		int gap = 6;
		int x = contentRight - (PRESET_COLORS.length * (size + gap) - gap);
		for (int preset : PRESET_COLORS) {
			addDrawableChild(new ColorChip(x, center(y, size), size, Text.literal(String.format("#%06X", preset)),
					() -> preset, () -> cfg().wallhack.color(colorTarget) == preset,
					() -> cfg().wallhack.setColor(colorTarget, preset)));
			x += size + gap;
		}
	}

	private void initOther() {
		int y = row("Список модулей на экране");
		addDrawableChild(new ToggleSwitch(contentRight - ToggleSwitch.WIDTH, center(y, ToggleSwitch.HEIGHT),
				Text.literal("Список модулей"), () -> cfg().hud.enabled, v -> cfg().hud.enabled = v));

		y = row("Конфиг actest.json");
		addDrawableChild(new FlatButton(contentRight - 86, center(y, 14), 86, 14, "Перечитать", () -> {
			ActestConfig.load();
			rebuild = true;
		})).setTooltip(Tooltip.of(Text.literal("Загрузить config/actest.json заново, если правили его вручную")));
	}

	/** Строка «Включён» с переключателем модуля; неактивна, если сервер не в allowedServers. */
	private void moduleRow(Module module) {
		int y = row("Включён");
		ToggleSwitch toggle = addDrawableChild(new ToggleSwitch(contentRight - ToggleSwitch.WIDTH,
				center(y, ToggleSwitch.HEIGHT), Text.literal(module.getName()), module::isEnabled, module::setEnabled));
		if (!modules.canEnableModules(client) && !module.isEnabled()) {
			toggle.active = false;
			toggle.setTooltip(Tooltip.of(Text.literal("Этот сервер не в allowedServers (config/actest.json)")));
		}
	}

	private int row(String label) {
		Text text = Text.literal(label);
		return row(() -> text);
	}

	private int row(Supplier<Text> label) {
		int y = nextRowY;
		rows.add(new Row(y, label));
		nextRowY += ROW_HEIGHT;
		return y;
	}

	/** Y виджета высотой h, отцентрованного в строке rowY. */
	private static int center(int rowY, int h) {
		return rowY + (ROW_HEIGHT - h) / 2;
	}

	private void switchTab(Tab target) {
		if (tab != target) {
			tab = target;
			// Пересобираем виджеты в начале следующего кадра, а не посреди обработки клика
			rebuild = true;
		}
	}

	private static String targetName(Target target) {
		return switch (target) {
			case PLAYERS -> "Игроки";
			case HOSTILE -> "Враждебные мобы";
			case PASSIVE -> "Мирные мобы";
		};
	}

	private static String targetGenitive(Target target) {
		return switch (target) {
			case PLAYERS -> "игроков";
			case HOSTILE -> "враждебных";
			case PASSIVE -> "мирных";
		};
	}

	// ======================== Отрисовка ========================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (rebuild) {
			rebuild = false;
			clearAndInit();
		}
		// Screen#render вызывает renderBackground (панель, подписи), затем рисует виджеты
		super.render(context, mouseX, mouseY, delta);
	}

	/** Вместо ванильного размытия: лёгкое затемнение, панель, шапка, боковая панель и подписи строк. */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		int x1 = panelX;
		int y1 = panelY;
		int x2 = panelX + panelWidth;
		int y2 = panelY + PANEL_HEIGHT;
		int bodyTop = y1 + HEADER_HEIGHT + 1;

		context.fill(0, 0, width, height, Theme.BACKDROP);
		Theme.roundRect(context, x1 + 2, y1 + 4, x2 + 4, y2 + 6, 4, Theme.SHADOW);
		Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 4, Theme.BORDER);

		// Шапка: логотип-квадрат, название, статус сервера
		Theme.roundRect(context, x1, y1, x2, y1 + HEADER_HEIGHT, 3, Theme.HEADER, true, true, false, false);
		context.fill(x1 + 3, y1, x2 - 3, y1 + 1, 0x14FFFFFF);
		context.fill(x1, y1 + HEADER_HEIGHT, x2, bodyTop, Theme.HEADER_LINE);
		Theme.roundRect(context, x1 + 9, y1 + 8, x1 + 17, y1 + 16, 2, Theme.ACCENT);
		context.drawText(textRenderer, Text.literal("AC Test Client").formatted(Formatting.BOLD), x1 + 22, y1 + 8, Theme.TEXT, false);
		drawServerStatus(context, x2 - 26, y1 + 8);

		// Боковая панель и область настроек
		Theme.roundRect(context, x1, bodyTop, x1 + SIDEBAR_WIDTH, y2, 3, Theme.SIDEBAR, false, false, true, false);
		context.fill(x1 + SIDEBAR_WIDTH, bodyTop, x1 + SIDEBAR_WIDTH + 1, y2, Theme.BORDER);
		Theme.roundRect(context, x1 + SIDEBAR_WIDTH + 1, bodyTop, x2, y2, 3, Theme.PANEL, false, false, false, true);
		context.drawText(textRenderer, "Esc — закрыть", x1 + 12, y2 - 14, Theme.TEXT_DISABLED, false);

		// Строки: подсветка под курсором и подпись слева
		for (Row row : rows) {
			if (mouseX >= contentX - 4 && mouseX < contentRight + 4 && mouseY >= row.y() && mouseY < row.y() + ROW_HEIGHT) {
				Theme.roundRect(context, contentX - 4, row.y(), contentRight + 4, row.y() + ROW_HEIGHT, 2, Theme.ROW_HOVER);
			}
			context.drawText(textRenderer, row.label().get(), contentX, row.y() + (ROW_HEIGHT - 8) / 2, Theme.TEXT, false);
		}

		switch (tab) {
			case SPEED -> drawSpeedHelp(context);
			case OTHER -> drawOtherHelp(context);
			case WH -> {
			}
		}
	}

	/** Цветная точка + адрес: зелёная — модули разрешены, красная — сервер не в allowedServers. */
	private void drawServerStatus(DrawContext context, int right, int y) {
		boolean allowed = modules.canEnableModules(client);
		ServerInfo server = client.getCurrentServerEntry();
		String where = client.isInSingleplayer() ? "одиночная игра" : server != null ? server.address : "нет сервера";
		where = textRenderer.trimToWidth(where, 120);
		int textX = right - textRenderer.getWidth(where);
		Theme.roundRect(context, textX - 9, y + 1, textX - 4, y + 6, 2, allowed ? Theme.GOOD : Theme.BAD);
		context.drawText(textRenderer, where, textX, y, Theme.TEXT_MUTED, false);
	}

	private void drawSpeedHelp(DrawContext context) {
		String text = cfg().speed.mode == ActestConfig.Speed.Mode.GROUND
				? "По земле: меняется только скорость ходьбы по земле, прыжок и падение остаются ванильными."
				: "Bhop: автопрыжок при каждом касании земли с ускорением, в воздухе скорость поворачивает за WASD.";
		int y = drawWrapped(context, text, footerY, Theme.TEXT_MUTED);
		drawWrapped(context, "x1.00 — обычная скорость, x1.40 ≈ легитный спринт под Speed II.", y + 4, Theme.TEXT_DISABLED);
	}

	private void drawOtherHelp(DrawContext context) {
		int y = footerY;
		context.drawText(textRenderer, "Клавиши", contentX, y, Theme.TEXT_MUTED, false);
		y += 13;
		y = drawKeyLine(context, "Speed", modules.get(SpeedModule.class).getKeyBinding(), y);
		y = drawKeyLine(context, "WH", modules.get(WallhackModule.class).getKeyBinding(), y);
		y = drawKeyLine(context, "Это меню", modules.getMenuKey(), y);
		drawWrapped(context, "Разрешённые серверы: " + String.join(", ", cfg().allowedServers), y + 4, Theme.TEXT_DISABLED);
	}

	/** «Название ........ [клавиша]» — клавиша в виде «кнопки клавиатуры». */
	private int drawKeyLine(DrawContext context, String name, KeyBinding key, int y) {
		context.drawText(textRenderer, name, contentX, y, Theme.TEXT, false);
		Text keyName = key.getBoundKeyLocalizedText();
		int w = textRenderer.getWidth(keyName) + 8;
		Theme.roundRect(context, contentRight - w, y - 2, contentRight, y + 10, 2, Theme.CONTROL);
		context.drawText(textRenderer, keyName, contentRight - w + 4, y, Theme.TEXT, false);
		return y + 15;
	}

	/** Текст с переносом по ширине области настроек; возвращает Y под последней строкой. */
	private int drawWrapped(DrawContext context, String text, int y, int color) {
		for (OrderedText line : textRenderer.wrapLines(Text.literal(text), contentRight - contentX)) {
			context.drawText(textRenderer, line, contentX, y, color, false);
			y += 10;
		}
		return y;
	}

	// ======================== Поведение ========================

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Повторное нажатие клавиши меню закрывает его (Esc работает как обычно)
		if (modules.getMenuKey().matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		ActestConfig.save();
	}
}

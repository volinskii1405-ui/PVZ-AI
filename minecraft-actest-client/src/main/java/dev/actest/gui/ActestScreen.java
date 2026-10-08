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
import dev.actest.gui.widget.TopTab;
import dev.actest.gui.widget.ToggleSwitch;
import dev.actest.module.FlyModule;
import dev.actest.module.Module;
import dev.actest.module.ModuleManager;
import dev.actest.module.NoFallModule;
import dev.actest.module.NoSlowModule;
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
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Меню настроек (по умолчанию правый Shift).
 * Шапка — вкладки категорий (Движение / Визуал / Прочее), слева — страницы
 * выбранной категории (модули с индикатором вкл/выкл), справа — настройки страницы.
 * Изменения применяются сразу, при закрытии сохраняются в config/actest.json.
 * Игра не ставится на паузу и фон не размывается — изменения видно вживую за меню.
 */
public final class ActestScreen extends Screen {
	private enum Category {
		MOVEMENT("Движение"), RENDER("Визуал"), OTHER("Прочее");

		final String title;

		Category(String title) {
			this.title = title;
		}
	}

	/** Страница: пункт боковой панели + построение виджетов + текст-подсказка под ними. */
	private record Page(String title, Module module, Runnable build, Consumer<DrawContext> footer) {
	}

	/** Строка настройки: подпись слева, виджет справа. */
	private record Row(int y, Supplier<Text> label) {
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

	/** Открытая категория, страница в каждой категории и редактируемый цвет WH — запоминаются между открытиями. */
	private static Category category = Category.MOVEMENT;
	private static final int[] selectedPage = new int[Category.values().length];
	private static Target colorTarget = Target.PLAYERS;

	private final ModuleManager modules;
	private final List<Row> rows = new ArrayList<>();
	private Page page;
	private boolean rebuild;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int contentX;
	private int contentRight;
	private int nextRowY;
	/** Где начинается текст под строками страницы. */
	private int footerY;

	public ActestScreen(ModuleManager modules) {
		super(Text.literal("AC Test Client"));
		this.modules = modules;
	}

	private static ActestConfig cfg() {
		return ActestConfig.get();
	}

	// ======================== Страницы ========================

	private List<Page> pages(Category c) {
		return switch (c) {
			case MOVEMENT -> List.of(
					modulePage(SpeedModule.class, this::buildSpeed, this::speedHelp),
					modulePage(FlyModule.class, this::buildFly, this::flyHelp),
					modulePage(NoFallModule.class, this::buildNoFall, this::noFallHelp),
					modulePage(NoSlowModule.class, this::buildNoSlow, this::noSlowHelp));
			case RENDER -> List.of(
					modulePage(WallhackModule.class, this::buildWallhack, null));
			case OTHER -> List.of(
					new Page("Интерфейс", null, this::buildInterface, this::interfaceHelp),
					new Page("Клавиши", null, () -> { }, this::keysHelp));
		};
	}

	private Page modulePage(Class<? extends Module> type, Runnable build, Consumer<DrawContext> footer) {
		Module module = modules.get(type);
		return new Page(module.getName(), module, build, footer);
	}

	private void buildSpeed() {
		moduleRow(page.module());
		segmentedRow("Режим", 130, ActestConfig.Speed.Mode.values(),
				mode -> mode == ActestConfig.Speed.Mode.GROUND ? "По земле" : "Bhop",
				() -> cfg().speed.mode, mode -> cfg().speed.mode = mode);
		sliderRow("Скорость", 1.0, 5.0, 0.05, () -> cfg().speed.multiplier, v -> cfg().speed.multiplier = v,
				v -> String.format(Locale.ROOT, "x%.2f", v));
	}

	private void speedHelp(DrawContext context) {
		String text = cfg().speed.mode == ActestConfig.Speed.Mode.GROUND
				? "По земле: меняется только скорость ходьбы по земле, прыжок и падение остаются ванильными."
				: "Bhop: автопрыжок при каждом касании земли с ускорением, в воздухе скорость поворачивает за WASD.";
		int y = drawWrapped(context, text, footerY, Theme.TEXT_MUTED);
		drawWrapped(context, "x1.00 — обычная скорость, x1.40 ≈ легитный спринт под Speed II.", y + 4, Theme.TEXT_DISABLED);
	}

	private void buildFly() {
		moduleRow(page.module());
		segmentedRow("Режим", 150, ActestConfig.Fly.Mode.values(),
				mode -> mode == ActestConfig.Fly.Mode.MOTION ? "Полёт" : "Планирование",
				() -> cfg().fly.mode, mode -> cfg().fly.mode = mode);
		sliderRow("Скорость", 0.1, 2.0, 0.05, () -> cfg().fly.speed, v -> cfg().fly.speed = v,
				v -> String.format(Locale.ROOT, "%.2f б/т", v));
		sliderRow("Вверх / вниз", 0.1, 2.0, 0.05, () -> cfg().fly.verticalSpeed, v -> cfg().fly.verticalSpeed = v,
				v -> String.format(Locale.ROOT, "%.2f б/т", v));
		sliderRow("Падение", 0.01, 0.3, 0.01, () -> cfg().fly.glideSpeed, v -> cfg().fly.glideSpeed = v,
				v -> String.format(Locale.ROOT, "%.2f б/т", v));
	}

	private void flyHelp(DrawContext context) {
		String text = cfg().fly.mode == ActestConfig.Fly.Mode.MOTION
				? "Полёт: зависание в воздухе, WASD — движение, Space — вверх, Shift — вниз."
				: "Планирование: обычное движение, но падение не быстрее заданной скорости.";
		int y = drawWrapped(context, text, footerY, Theme.TEXT_MUTED);
		drawWrapped(context, "Ваниль сама кикает за полёт через 4 с, если allow-flight=false в server.properties.",
				y + 4, Theme.TEXT_DISABLED);
	}

	private void buildNoFall() {
		moduleRow(page.module());
		segmentedRow("Режим", 130, ActestConfig.NoFall.Mode.values(),
				mode -> mode == ActestConfig.NoFall.Mode.SPOOF ? "Spoof" : "Packet",
				() -> cfg().noFall.mode, mode -> cfg().noFall.mode = mode);
	}

	private void noFallHelp(DrawContext context) {
		String text = cfg().noFall.mode == ActestConfig.NoFall.Mode.SPOOF
				? "Spoof: обычные пакеты движения сообщают onGround=true, пока игрок падает."
				: "Packet: пакеты движения честные, но каждый тик падения отправляется ещё OnGroundOnly(true).";
		int y = drawWrapped(context, text, footerY, Theme.TEXT_MUTED);
		drawWrapped(context, "Срабатывает после 2 блоков падения — урон начинается после 3.", y + 4, Theme.TEXT_DISABLED);
	}

	private void buildNoSlow() {
		moduleRow(page.module());
		toggleRow("Предметы", () -> cfg().noSlow.items, v -> cfg().noSlow.items = v)
				.setTooltip(Tooltip.of(Text.literal("Еда, зелья, лук, арбалет, щит, трезубец, подзорная труба")));
		toggleRow("Песок душ и мёд", () -> cfg().noSlow.blocks, v -> cfg().noSlow.blocks = v);
	}

	private void noSlowHelp(DrawContext context) {
		int y = drawWrapped(context, "Скорость ходьбы по земле как без замедления: ваниль при использовании предмета "
				+ "даёт 0.2 от обычной, песок душ и мёд — 0.4.", footerY, Theme.TEXT_MUTED);
		drawWrapped(context, "Клавиши по умолчанию нет — включается здесь или назначьте в «Управлении».",
				y + 4, Theme.TEXT_DISABLED);
	}

	private void buildWallhack() {
		moduleRow(page.module());
		segmentedRow("Вид", 150, ActestConfig.Wallhack.Mode.values(),
				mode -> switch (mode) {
					case GLOW -> "Контур";
					case BOX -> "Рамки";
					case BOTH -> "Оба";
				},
				() -> cfg().wallhack.mode, mode -> cfg().wallhack.mode = mode);

		// Группы: цветной квадратик (выбрать, чей цвет править) + переключатель
		for (Target target : Target.values()) {
			ToggleSwitch toggle = toggleRow(targetName(target),
					() -> cfg().wallhack.shows(target), v -> cfg().wallhack.setShown(target, v));
			addDrawableChild(new ColorChip(toggle.getX() - 18, toggle.getY() + 1, 10,
					Text.literal("Цвет: " + targetName(target)),
					() -> cfg().wallhack.color(target), () -> colorTarget == target, () -> colorTarget = target))
					.setTooltip(Tooltip.of(Text.literal("Нажмите, чтобы менять цвет этой группы")));
		}

		int y = row("Подписи");
		PillToggle distance = new PillToggle(0, center(y, 14), 14, "Дистанция",
				() -> cfg().wallhack.showDistance, v -> cfg().wallhack.showDistance = v);
		distance.setX(contentRight - distance.getWidth());
		PillToggle names = new PillToggle(0, center(y, 14), 14, "Имена",
				() -> cfg().wallhack.showNames, v -> cfg().wallhack.showNames = v);
		names.setX(distance.getX() - 4 - names.getWidth());
		addDrawableChild(names);
		addDrawableChild(distance);

		sliderRow("Дальность", 16, 512, 8, () -> cfg().wallhack.maxDistance, v -> cfg().wallhack.maxDistance = v,
				v -> String.format(Locale.ROOT, "%.0f бл.", v));

		y = row(() -> Text.literal("Цвет " + targetGenitive(colorTarget)));
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

	private void buildInterface() {
		toggleRow("Список модулей на экране", () -> cfg().hud.enabled, v -> cfg().hud.enabled = v);
		toggleRow("Лог движения", () -> cfg().debugLog, v -> cfg().debugLog = v)
				.setTooltip(Tooltip.of(Text.literal("Смещение, onGround и действия NoFall за каждый тик в logs/latest.log")));
		int y = row("Конфиг actest.json");
		addDrawableChild(new FlatButton(contentRight - 86, center(y, 14), 86, 14, "Перечитать", () -> {
			ActestConfig.load();
			rebuild = true;
		})).setTooltip(Tooltip.of(Text.literal("Загрузить config/actest.json заново, если правили его вручную")));
	}

	private void interfaceHelp(DrawContext context) {
		drawWrapped(context, "Лог движения пишет строку [Move] на каждый тик: те же dXZ, dY и onGround, "
				+ "что получил сервер, — удобно сверять с логами античита.", footerY, Theme.TEXT_MUTED);
	}

	private void keysHelp(DrawContext context) {
		int y = nextRowY + 2;
		for (Module module : modules.all()) {
			y = drawKeyLine(context, module.getName(), module.getKeyBinding(), y);
		}
		y = drawKeyLine(context, "Это меню", modules.getMenuKey(), y);
		drawWrapped(context, "Переназначить: Настройки → Управление → AC Test Client.", y + 2, Theme.TEXT_DISABLED);
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

		// Вкладки категорий в шапке
		int tabX = panelX + SIDEBAR_WIDTH + 4;
		for (Category c : Category.values()) {
			TopTab tab = addDrawableChild(new TopTab(tabX, panelY, HEADER_HEIGHT, c.title,
					() -> category == c, () -> switchCategory(c)));
			tabX += tab.getWidth();
		}
		addDrawableChild(new FlatButton(panelX + panelWidth - 20, panelY + 5, 14, 14, "×", this::close));

		// Страницы выбранной категории в боковой панели
		List<Page> pages = pages(category);
		int index = Math.min(selectedPage[category.ordinal()], pages.size() - 1);
		int itemY = panelY + HEADER_HEIGHT + 8;
		for (int i = 0; i < pages.size(); i++) {
			Page p = pages.get(i);
			int pageIndex = i;
			BooleanSupplier indicator = p.module() == null ? null : p.module()::isEnabled;
			addDrawableChild(new TabButton(panelX, itemY, SIDEBAR_WIDTH, 18, p.title(),
					() -> selectedPage[category.ordinal()] == pageIndex, indicator, () -> selectPage(pageIndex)));
			itemY += 20;
		}

		page = pages.get(index);
		page.build().run();
		footerY = nextRowY + 6;
	}

	/** Строка «Имя модуля [клавиша]» с переключателем; неактивна, если сервер не в allowedServers. */
	private void moduleRow(Module module) {
		KeyBinding key = module.getKeyBinding();
		String keyName = key.isUnbound() ? "без клавиши" : key.getBoundKeyLocalizedText().getString();
		Text label = Text.literal(module.getName()).formatted(Formatting.BOLD)
				.append(Text.literal("  " + keyName).formatted(Formatting.GRAY));
		int y = row(() -> label);
		ToggleSwitch toggle = addDrawableChild(new ToggleSwitch(contentRight - ToggleSwitch.WIDTH,
				center(y, ToggleSwitch.HEIGHT), Text.literal(module.getName()), module::isEnabled, module::setEnabled));
		if (!modules.canEnableModules(client) && !module.isEnabled()) {
			toggle.active = false;
			toggle.setTooltip(Tooltip.of(Text.literal("Этот сервер не в allowedServers (config/actest.json)")));
		}
	}

	private ToggleSwitch toggleRow(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		int y = row(label);
		return addDrawableChild(new ToggleSwitch(contentRight - ToggleSwitch.WIDTH, center(y, ToggleSwitch.HEIGHT),
				Text.literal(label), getter, setter));
	}

	private <T> void segmentedRow(String label, int width, T[] values, Function<T, String> names,
			Supplier<T> getter, Consumer<T> setter) {
		int y = row(label);
		addDrawableChild(new Segmented<>(contentRight - width, center(y, 14), width, 14, Text.literal(label),
				values, names, getter, setter));
	}

	private void sliderRow(String label, double min, double max, double step, DoubleSupplier getter,
			DoubleConsumer setter, DoubleFunction<String> format) {
		int y = row(label);
		addDrawableChild(new Slider(contentRight - 150, center(y, 14), 150, 14, Text.literal(label),
				min, max, step, getter, setter, format));
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

	// Пересборка — в начале следующего кадра, а не посреди обработки клика

	private void switchCategory(Category target) {
		if (category != target) {
			category = target;
			rebuild = true;
		}
	}

	private void selectPage(int index) {
		if (selectedPage[category.ordinal()] != index) {
			selectedPage[category.ordinal()] = index;
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

		// Шапка: логотип-квадрат и название над боковой панелью, правее — вкладки (виджеты)
		Theme.roundRect(context, x1, y1, x2, y1 + HEADER_HEIGHT, 3, Theme.HEADER, true, true, false, false);
		context.fill(x1 + 3, y1, x2 - 3, y1 + 1, 0x14FFFFFF);
		context.fill(x1, y1 + HEADER_HEIGHT, x2, bodyTop, Theme.HEADER_LINE);
		Theme.roundRect(context, x1 + 9, y1 + 8, x1 + 17, y1 + 16, 2, Theme.ACCENT);
		context.drawText(textRenderer, Text.literal("AC Test").formatted(Formatting.BOLD), x1 + 22, y1 + 8, Theme.TEXT, false);

		// Боковая панель и область настроек
		Theme.roundRect(context, x1, bodyTop, x1 + SIDEBAR_WIDTH, y2, 3, Theme.SIDEBAR, false, false, true, false);
		context.fill(x1 + SIDEBAR_WIDTH, bodyTop, x1 + SIDEBAR_WIDTH + 1, y2, Theme.BORDER);
		Theme.roundRect(context, x1 + SIDEBAR_WIDTH + 1, bodyTop, x2, y2, 3, Theme.PANEL, false, false, false, true);
		drawServerStatus(context, x1 + 10, y2 - 14, SIDEBAR_WIDTH - 20);

		// Строки: подсветка под курсором и подпись слева
		for (Row row : rows) {
			if (mouseX >= contentX - 4 && mouseX < contentRight + 4 && mouseY >= row.y() && mouseY < row.y() + ROW_HEIGHT) {
				Theme.roundRect(context, contentX - 4, row.y(), contentRight + 4, row.y() + ROW_HEIGHT, 2, Theme.ROW_HOVER);
			}
			context.drawText(textRenderer, row.label().get(), contentX, row.y() + (ROW_HEIGHT - 8) / 2, Theme.TEXT, false);
		}

		if (page != null && page.footer() != null) {
			page.footer().accept(context);
		}
	}

	/** Внизу боковой панели: зелёная точка — модули здесь разрешены, красная — сервер не в allowedServers. */
	private void drawServerStatus(DrawContext context, int x, int y, int maxWidth) {
		boolean allowed = modules.canEnableModules(client);
		ServerInfo server = client.getCurrentServerEntry();
		String where = client.isInSingleplayer() ? "одиночная игра" : server != null ? server.address : "нет сервера";
		Theme.roundRect(context, x, y + 1, x + 5, y + 6, 2, allowed ? Theme.GOOD : Theme.BAD);
		context.drawText(textRenderer, textRenderer.trimToWidth(where, maxWidth - 9), x + 9, y, Theme.TEXT_MUTED, false);
	}

	/** «Название ........ [клавиша]» — клавиша в виде «кнопки клавиатуры». */
	private int drawKeyLine(DrawContext context, String name, KeyBinding key, int y) {
		context.drawText(textRenderer, name, contentX, y, Theme.TEXT, false);
		Text keyName = key.isUnbound() ? Text.literal("—") : key.getBoundKeyLocalizedText();
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

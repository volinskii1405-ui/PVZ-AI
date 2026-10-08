package dev.actest.gui;

import dev.actest.config.ActestConfig;
import dev.actest.module.Module;
import dev.actest.module.ModuleManager;
import dev.actest.module.SpeedModule;
import dev.actest.module.WallhackModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Меню настроек (по умолчанию правый Shift).
 * Все изменения применяются сразу, а при закрытии сохраняются в config/actest.json.
 * Игра не ставится на паузу и фон не размывается, поэтому смену цвета подсветки
 * видно вживую за меню.
 */
public final class ActestScreen extends Screen {
	private static final int COLUMN_WIDTH = 150;
	private static final int ROW_HEIGHT = 22;
	private static final int ROWS = 8;
	/** Быстрый выбор цвета подсветки. */
	private static final int[] PRESET_COLORS = {
			0xFF4040, 0xFF9020, 0xFFFF40, 0x40FF40, 0x40FFFF, 0x4080FF, 0xC040FF, 0xFFFFFF};

	private static final Text ON = Text.literal("ВКЛ").formatted(Formatting.GREEN);
	private static final Text OFF = Text.literal("выкл").formatted(Formatting.GRAY);
	private static final Text YES = Text.literal("да");
	private static final Text NO = Text.literal("нет");

	private final ModuleManager modules;
	private int leftX;
	private int rightX;
	private int top;
	private ValueSlider red;
	private ValueSlider green;
	private ValueSlider blue;

	public ActestScreen(ModuleManager modules) {
		super(Text.literal("AC Test Client"));
		this.modules = modules;
	}

	@Override
	protected void init() {
		leftX = width / 2 - COLUMN_WIDTH - 5;
		rightX = width / 2 + 5;
		top = Math.max(34, (height - ROWS * ROW_HEIGHT) / 2 + 8);
		boolean allowed = modules.canEnableModules(client);
		ActestConfig cfg = ActestConfig.get();

		// ---------------- Левая колонка: Speed ----------------
		addDrawableChild(moduleToggle(modules.get(SpeedModule.class), leftX, row(0), allowed));

		addDrawableChild(CyclingButtonWidget.<ActestConfig.Speed.Mode>builder(mode -> Text.literal(switch (mode) {
					case GROUND -> "По земле";
					case BHOP -> "Bhop";
				}))
				.values(ActestConfig.Speed.Mode.values())
				.initially(cfg.speed.mode)
				.tooltip(mode -> Tooltip.of(Text.literal(switch (mode) {
					case GROUND -> "Прямое изменение скорости на земле, прыжок и падение ванильные";
					case BHOP -> "Автопрыжок на каждом касании земли + разворот скорости в воздухе за WASD";
				})))
				.build(leftX, row(1), COLUMN_WIDTH, 20, Text.literal("Режим"),
						(button, mode) -> ActestConfig.get().speed.mode = mode));

		addDrawableChild(new ValueSlider(leftX, row(2), COLUMN_WIDTH, 1.0, 5.0, 0.05, cfg.speed.multiplier,
				v -> Text.literal(String.format(Locale.ROOT, "Скорость: x%.2f", v)),
				v -> ActestConfig.get().speed.multiplier = v));

		addDrawableChild(CyclingButtonWidget.onOffBuilder(YES, NO)
				.initially(cfg.hud.enabled)
				.build(leftX, row(3), COLUMN_WIDTH, 20, Text.literal("Список модулей"),
						(button, value) -> ActestConfig.get().hud.enabled = value));

		addDrawableChild(CyclingButtonWidget.onOffBuilder(YES, NO)
				.initially(cfg.speed.debugLog)
				.tooltip(value -> Tooltip.of(Text.literal("Смещение за каждый тик в logs/latest.log — для сверки с логами античита")))
				.build(leftX, row(4), COLUMN_WIDTH, 20, Text.literal("Debug-лог Speed"),
						(button, value) -> ActestConfig.get().speed.debugLog = value));

		addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> close())
				.dimensions(leftX, row(ROWS - 1), COLUMN_WIDTH, 20)
				.build());

		// ---------------- Правая колонка: WH ----------------
		addDrawableChild(moduleToggle(modules.get(WallhackModule.class), rightX, row(0), allowed));

		addDrawableChild(CyclingButtonWidget.<ActestConfig.Wallhack.Mode>builder(mode -> Text.literal(switch (mode) {
					case GLOW -> "Контур";
					case BOX -> "Рамки";
					case BOTH -> "Контур + рамки";
				}))
				.values(ActestConfig.Wallhack.Mode.values())
				.initially(cfg.wallhack.mode)
				.build(rightX, row(1), COLUMN_WIDTH, 20, Text.literal("Подсветка"),
						(button, mode) -> ActestConfig.get().wallhack.mode = mode));

		int half = (COLUMN_WIDTH - 2) / 2;
		addDrawableChild(CyclingButtonWidget.onOffBuilder(YES, NO)
				.initially(cfg.wallhack.showNames)
				.build(rightX, row(2), half, 20, Text.literal("Ники"),
						(button, value) -> ActestConfig.get().wallhack.showNames = value));
		addDrawableChild(CyclingButtonWidget.onOffBuilder(YES, NO)
				.initially(cfg.wallhack.showDistance)
				.build(rightX + half + 2, row(2), half, 20, Text.literal("Дист."),
						(button, value) -> ActestConfig.get().wallhack.showDistance = value));

		addDrawableChild(new ValueSlider(rightX, row(3), COLUMN_WIDTH, 16, 512, 8, cfg.wallhack.maxDistance,
				v -> Text.literal(String.format(Locale.ROOT, "Дальность: %.0f бл.", v)),
				v -> ActestConfig.get().wallhack.maxDistance = v));

		// Цвет линий: три ползунка R/G/B
		int color = cfg.wallhackColor();
		red = addDrawableChild(colorSlider(row(4), "Красный", (color >> 16) & 0xFF));
		green = addDrawableChild(colorSlider(row(5), "Зелёный", (color >> 8) & 0xFF));
		blue = addDrawableChild(colorSlider(row(6), "Синий", color & 0xFF));

		// ...и готовые цвета: кнопки с цветным квадратом
		int step = (COLUMN_WIDTH + 2) / PRESET_COLORS.length;
		for (int i = 0; i < PRESET_COLORS.length; i++) {
			int preset = PRESET_COLORS[i];
			addDrawableChild(ButtonWidget.builder(
							Text.literal("■").styled(style -> style.withColor(preset)),
							button -> applyPreset(preset))
					.dimensions(rightX + i * step, row(ROWS - 1), step - 2, 20)
					.build());
		}
	}

	private int row(int index) {
		return top + index * ROW_HEIGHT;
	}

	/** Кнопка «Модуль: ВКЛ/выкл». Если сервер не в allowedServers — неактивна. */
	private CyclingButtonWidget<Boolean> moduleToggle(Module module, int x, int y, boolean allowed) {
		CyclingButtonWidget<Boolean> button = CyclingButtonWidget.onOffBuilder(ON, OFF)
				.initially(module.isEnabled())
				.build(x, y, COLUMN_WIDTH, 20, Text.literal(module.getName()),
						(b, value) -> module.setEnabled(value));
		if (!allowed && !module.isEnabled()) {
			button.active = false;
			button.setTooltip(Tooltip.of(Text.literal("Этот сервер не в allowedServers (config/actest.json)")));
		}
		return button;
	}

	private ValueSlider colorSlider(int y, String name, int initial) {
		return new ValueSlider(rightX, y, COLUMN_WIDTH, 0, 255, 1, initial,
				v -> Text.literal(name + ": " + (int) v),
				v -> applySliderColor());
	}

	private void applySliderColor() {
		int rgb = ((int) red.get() << 16) | ((int) green.get() << 8) | (int) blue.get();
		ActestConfig.get().setWallhackColor(rgb);
	}

	private void applyPreset(int rgb) {
		ActestConfig.get().setWallhackColor(rgb);
		red.set((rgb >> 16) & 0xFF);
		green.set((rgb >> 8) & 0xFF);
		blue.set(rgb & 0xFF);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 26, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal("Speed").formatted(Formatting.AQUA), leftX, top - 12, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal("WH").formatted(Formatting.AQUA), rightX, top - 12, 0xFFFFFFFF);

		// Образец текущего цвета подсветки справа от заголовка WH
		int right = rightX + COLUMN_WIDTH;
		context.fill(right - 41, top - 14, right, top - 3, 0xFFFFFFFF);
		context.fill(right - 40, top - 13, right - 1, top - 4, 0xFF000000 | ActestConfig.get().wallhackColor());
	}

	/** Без размытия и затемнения всего экрана — только тёмная подложка под меню. */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(leftX - 6, top - 32, rightX + COLUMN_WIDTH + 6, row(ROWS) + 4, 0xB0000000);
	}

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

	/** Ползунок с реальным диапазоном [min, max] и шагом step (ванильный SliderWidget хранит 0..1). */
	private static final class ValueSlider extends SliderWidget {
		private final double min;
		private final double max;
		private final double step;
		private final DoubleFunction<Text> label;
		private final DoubleConsumer onChange;

		ValueSlider(int x, int y, int width, double min, double max, double step, double initial,
				DoubleFunction<Text> label, DoubleConsumer onChange) {
			super(x, y, width, 20, Text.empty(), 0.0);
			this.min = min;
			this.max = max;
			this.step = step;
			this.label = label;
			this.onChange = onChange;
			set(initial);
		}

		/** Текущее значение, округлённое до шага. */
		double get() {
			double snapped = min + Math.round(value * (max - min) / step) * step;
			return Math.round(Math.min(max, snapped) * 10000.0) / 10000.0;
		}

		/** Выставить значение без вызова onChange (для пресетов цвета). */
		void set(double real) {
			value = Math.max(0.0, Math.min(1.0, (real - min) / (max - min)));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(get()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(get());
		}
	}
}

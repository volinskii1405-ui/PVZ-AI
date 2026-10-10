package dev.actest.gui;

import dev.actest.config.ActestConfig;
import dev.actest.gui.widget.ColorChip;
import dev.actest.gui.widget.FlatButton;
import dev.actest.gui.widget.HueSlider;
import dev.actest.gui.widget.PillToggle;
import dev.actest.gui.widget.Segmented;
import dev.actest.gui.widget.Slider;
import dev.actest.gui.widget.TabButton;
import dev.actest.gui.widget.ToggleSwitch;
import dev.actest.gui.widget.TopTab;
import dev.actest.module.AutoClickerModule;
import dev.actest.module.AutoTotemModule;
import dev.actest.module.BlinkModule;
import dev.actest.module.ChestStealerModule;
import dev.actest.module.CriticalsModule;
import dev.actest.module.FastBreakModule;
import dev.actest.module.FlyModule;
import dev.actest.module.JesusModule;
import dev.actest.module.KillAuraModule;
import dev.actest.module.Module;
import dev.actest.module.ModuleManager;
import dev.actest.module.NoFallModule;
import dev.actest.module.NoSlowModule;
import dev.actest.module.ReachModule;
import dev.actest.module.ScaffoldModule;
import dev.actest.module.SpeedModule;
import dev.actest.module.StepModule;
import dev.actest.module.VelocityModule;
import dev.actest.module.WallhackModule;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Меню настроек (по умолчанию Right Shift): категории, страницы модулей, переключатели и слайдеры.
 */
public final class ActestScreen extends Screen {
   private static final int PANEL_WIDTH = 360;
   private static final int SIDEBAR_WIDTH = 86;
   private static final int HEADER_HEIGHT = 24;
   private static final int ROW_HEIGHT = 18;
   private static final int MAX_ROWS = 9;
   private static final int PAD = 10;
   private static final int PANEL_HEIGHT = 206;
   private static final int[] PRESET_COLORS = new int[]{0xFF4040, 0xFF9020, 0xFFFF40, 0x40FF40, 0x40FFFF, 0x4080FF, 0xC040FF, 0xFFFFFF};
   private static ActestScreen.Category category = ActestScreen.Category.MOVEMENT;
   private static final int[] selectedPage = new int[ActestScreen.Category.values().length];
   private static ActestConfig.Wallhack.Target colorTarget = ActestConfig.Wallhack.Target.PLAYERS;
   private final ModuleManager modules;
   /** Адрес, для которого уже нажато «Разрешить» и ждём второго нажатия («Подтвердить»). */
   private String pendingAllow;
   private final List<ActestScreen.Row> rows = new ArrayList<>();
   private ActestScreen.Page page;
   private boolean rebuild;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int contentX;
   private int contentRight;
   private int nextRowY;
   private int footerY;

   public ActestScreen(ModuleManager modules) {
      super(Text.literal("AC Test Client"));
      this.modules = modules;
   }

   private static ActestConfig cfg() {
      return ActestConfig.get();
   }

   private List<ActestScreen.Page> pages(ActestScreen.Category c) {
      return switch (c) {
         case MOVEMENT -> List.of(
         this.modulePage(SpeedModule.class, this::buildSpeed, this::speedHelp),
         this.modulePage(FlyModule.class, this::buildFly, this::flyHelp),
         this.modulePage(NoFallModule.class, this::buildNoFall, this::noFallHelp),
         this.modulePage(NoSlowModule.class, this::buildNoSlow, this::noSlowHelp),
         this.modulePage(StepModule.class, this::buildStep, this::stepHelp),
         this.modulePage(JesusModule.class, this::buildJesus, this::jesusHelp),
         this.modulePage(BlinkModule.class, this::buildBlink, this::blinkHelp)
      );
         case WORLD -> List.of(
         this.modulePage(ScaffoldModule.class, this::buildScaffold, this::scaffoldHelp),
         this.modulePage(FastBreakModule.class, this::buildFastBreak, this::fastBreakHelp),
         this.modulePage(ChestStealerModule.class, this::buildChestStealer, this::chestStealerHelp)
      );
         case COMBAT -> List.of(
         this.modulePage(ReachModule.class, this::buildReach, this::reachHelp),
         this.modulePage(KillAuraModule.class, this::buildKillAura, this::killAuraHelp),
         this.modulePage(AutoClickerModule.class, this::buildAutoClicker, this::autoClickerHelp),
         this.modulePage(CriticalsModule.class, this::buildCriticals, this::criticalsHelp),
         this.modulePage(VelocityModule.class, this::buildVelocity, this::velocityHelp),
         this.modulePage(AutoTotemModule.class, this::buildAutoTotem, this::autoTotemHelp)
      );
         case RENDER -> List.of(this.modulePage(WallhackModule.class, this::buildWallhack, null));
         case OTHER -> List.of(
         new ActestScreen.Page("Интерфейс", null, this::buildInterface, this::interfaceHelp), new ActestScreen.Page("Клавиши", null, () -> {
         }, this::keysHelp)
      );
      };
   }

   private ActestScreen.Page modulePage(Class<? extends Module> type, Runnable build, Consumer<DrawContext> footer) {
      Module module = this.modules.get(type);
      return new ActestScreen.Page(module.getName(), module, build, footer);
   }

   private void buildSpeed() {
      this.moduleRow(this.page.module());
      this.segmentedRow(
         "Режим",
         130,
         ActestConfig.Speed.Mode.values(),
         mode -> mode == ActestConfig.Speed.Mode.GROUND ? "По земле" : "Bhop",
         () -> cfg().speed.mode,
         mode -> cfg().speed.mode = mode
      );
      this.sliderRow("Скорость", 1.0, 5.0, 0.05, () -> cfg().speed.multiplier, v -> cfg().speed.multiplier = v, v -> String.format(Locale.ROOT, "x%.2f", v));
   }

   private void speedHelp(DrawContext context) {
      String text = cfg().speed.mode == ActestConfig.Speed.Mode.GROUND
         ? "По земле: меняется только скорость ходьбы по земле, прыжок и падение остаются ванильными."
         : "Bhop: автопрыжок при каждом касании земли с ускорением, в воздухе скорость поворачивает за WASD.";
      int y = this.drawWrapped(context, text, this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "x1.00 — обычная скорость, x1.40 ≈ легитный спринт под Speed II.", y + 4, 0xFF586070);
   }

   private void buildFly() {
      this.moduleRow(this.page.module());
      this.segmentedRow(
         "Режим",
         150,
         ActestConfig.Fly.Mode.values(),
         mode -> mode == ActestConfig.Fly.Mode.MOTION ? "Полёт" : "Планирование",
         () -> cfg().fly.mode,
         mode -> cfg().fly.mode = mode
      );
      this.sliderRow("Скорость", 0.1, 2.0, 0.05, () -> cfg().fly.speed, v -> cfg().fly.speed = v, v -> String.format(Locale.ROOT, "%.2f б/т", v));
      this.sliderRow(
         "Вверх / вниз", 0.1, 2.0, 0.05, () -> cfg().fly.verticalSpeed, v -> cfg().fly.verticalSpeed = v, v -> String.format(Locale.ROOT, "%.2f б/т", v)
      );
      this.sliderRow("Падение", 0.01, 0.3, 0.01, () -> cfg().fly.glideSpeed, v -> cfg().fly.glideSpeed = v, v -> String.format(Locale.ROOT, "%.2f б/т", v));
   }

   private void flyHelp(DrawContext context) {
      String text = cfg().fly.mode == ActestConfig.Fly.Mode.MOTION
         ? "Полёт: зависание в воздухе, WASD — движение, Space — вверх, Shift — вниз."
         : "Планирование: обычное движение, но падение не быстрее заданной скорости.";
      int y = this.drawWrapped(context, text, this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Ваниль сама кикает за полёт через 4 с, если allow-flight=false в server.properties.", y + 4, 0xFF586070);
   }

   private void buildNoFall() {
      this.moduleRow(this.page.module());
      this.segmentedRow(
         "Режим",
         130,
         ActestConfig.NoFall.Mode.values(),
         mode -> mode == ActestConfig.NoFall.Mode.SPOOF ? "Spoof" : "Packet",
         () -> cfg().noFall.mode,
         mode -> cfg().noFall.mode = mode
      );
   }

   private void noFallHelp(DrawContext context) {
      String text = cfg().noFall.mode == ActestConfig.NoFall.Mode.SPOOF
         ? "Spoof: обычные пакеты движения сообщают onGround=true, пока игрок падает."
         : "Packet: пакеты движения честные, но каждый тик падения отправляется ещё OnGroundOnly(true).";
      int y = this.drawWrapped(context, text, this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Срабатывает после 2 блоков падения — урон начинается после 3.", y + 4, 0xFF586070);
   }

   private void buildNoSlow() {
      this.moduleRow(this.page.module());
      this.toggleRow("Предметы", () -> cfg().noSlow.items, v -> cfg().noSlow.items = v)
         .setTooltip(Tooltip.of(Text.literal("Еда, зелья, лук, арбалет, щит, трезубец, подзорная труба")));
      this.toggleRow("Песок душ и мёд", () -> cfg().noSlow.blocks, v -> cfg().noSlow.blocks = v);
   }

   private void noSlowHelp(DrawContext context) {
      int y = this.drawWrapped(
         context,
         "Скорость ходьбы по земле как без замедления: ваниль при использовании предмета даёт 0.2 от обычной, песок душ и мёд — 0.4.",
         this.footerY,
         0xFF8B93A3
      );
      this.drawWrapped(context, "Клавиши по умолчанию нет — включается здесь или назначьте в «Управлении».", y + 4, 0xFF586070);
   }

   private void buildStep() {
      this.moduleRow(this.page.module());
      this.sliderRow("Высота шага", 0.6, 3.0, 0.1, () -> cfg().step.height, v -> cfg().step.height = v, v -> String.format(Locale.ROOT, "%.1f бл.", v));
   }

   private void stepHelp(DrawContext context) {
      int y = this.drawWrapped(context, "Подъём на блоки без прыжка: 1.0 — полный блок, 2.0 — два блока за один тик.", this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Ванильная высота шага 0.6 — ступеньки и полублоки.", y + 4, 0xFF586070);
   }

   private void buildReach() {
      this.moduleRow(this.page.module());
      this.sliderRow("Атака", 3.0, 8.0, 0.1, () -> cfg().reach.entityRange, v -> cfg().reach.entityRange = v, v -> String.format(Locale.ROOT, "%.1f бл.", v));
      this.toggleRow("Блоки тоже", () -> cfg().reach.blocks, v -> cfg().reach.blocks = v);
      this.sliderRow("Блоки", 4.5, 8.0, 0.1, () -> cfg().reach.blockRange, v -> cfg().reach.blockRange = v, v -> String.format(Locale.ROOT, "%.1f бл.", v));
   }

   private void reachHelp(DrawContext context) {
      int y = this.drawWrapped(context, "Дальность атаки и взаимодействия с блоками. Ваниль: атака 3.0, блоки 4.5.", this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Атака уходит обычным пакетом PlayerInteractEntityC2SPacket — дистанцию проверяет сервер.", y + 4, 0xFF586070);
   }

   private void buildKillAura() {
      this.moduleRow(this.page.module());
      this.segmentedRow("Поворот", 150, ActestConfig.KillAura.Rotation.values(), rotation -> {
         return switch (rotation) {
            case NONE -> "Нет";
            case PACKET -> "Пакет";
            case CLIENT -> "Камера";
         };
      }, () -> cfg().killAura.rotation, rotation -> cfg().killAura.rotation = rotation);
      this.segmentedRow("Приоритет", 168, ActestConfig.KillAura.Priority.values(), priority -> {
         return switch (priority) {
            case DISTANCE -> "Ближний";
            case HEALTH -> "Слабый";
            case ANGLE -> "Прицел";
         };
      }, () -> cfg().killAura.priority, priority -> cfg().killAura.priority = priority);
      this.sliderRow("Радиус", 1.0, 6.0, 0.1, () -> cfg().killAura.range, v -> cfg().killAura.range = v, v -> String.format(Locale.ROOT, "%.1f бл.", v));
      this.toggleRow("Ждать кулдаун", () -> cfg().killAura.waitCooldown, v -> cfg().killAura.waitCooldown = v)
         .setTooltip(Tooltip.of(Text.literal("Бить, когда шкала атаки заполнена (как в 1.9+). Выкл — с частотой CPS")));
      this.sliderRow("CPS", 1.0, 20.0, 1.0, () -> cfg().killAura.cps, v -> cfg().killAura.cps = v, v -> String.format(Locale.ROOT, "%.0f уд/с", v));
      int y = this.pillRowY("Цели");
      this.placePills(
         new PillToggle(0, y, 14, "Игроки", () -> cfg().killAura.players, v -> cfg().killAura.players = v),
         new PillToggle(0, y, 14, "Монстры", () -> cfg().killAura.hostileMobs, v -> cfg().killAura.hostileMobs = v),
         new PillToggle(0, y, 14, "Мирные", () -> cfg().killAura.passiveMobs, v -> cfg().killAura.passiveMobs = v)
      );
      y = this.pillRowY("Ещё");
      this.placePills(
         new PillToggle(0, y, 14, "Сквозь стены", () -> cfg().killAura.throughWalls, v -> cfg().killAura.throughWalls = v),
         new PillToggle(0, y, 14, "Взмах рукой", () -> cfg().killAura.swing, v -> cfg().killAura.swing = v)
      );
   }

   private void killAuraHelp(DrawContext context) {
      String text = switch (cfg().killAura.rotation) {
         case NONE -> "Без поворота: удар уходит, куда бы игрок ни смотрел.";
         case PACKET -> "Пакет: перед ударом серверу уходит взгляд на цель, потом возвращается настоящий.";
         case CLIENT -> "Камера наводится на цель, удар — тиком позже, когда поворот уже ушёл на сервер.";
      };
      this.drawWrapped(context, text, this.footerY, 0xFF8B93A3);
   }

   private void buildAutoClicker() {
      this.moduleRow(this.page.module());
      int y = this.pillRowY("Кнопки");
      this.placePills(
         new PillToggle(0, y, 14, "ЛКМ", () -> cfg().autoClicker.left, v -> cfg().autoClicker.left = v),
         new PillToggle(0, y, 14, "ПКМ", () -> cfg().autoClicker.right, v -> cfg().autoClicker.right = v)
      );
      this.sliderRow("Мин. CPS", 1.0, 30.0, 1.0, () -> cfg().autoClicker.minCps, v -> cfg().autoClicker.minCps = v, v -> String.format(Locale.ROOT, "%.0f", v));
      this.sliderRow("Макс. CPS", 1.0, 30.0, 1.0, () -> cfg().autoClicker.maxCps, v -> cfg().autoClicker.maxCps = v, v -> String.format(Locale.ROOT, "%.0f", v));
      this.toggleRow("Только пока зажата", () -> cfg().autoClicker.holdOnly, v -> cfg().autoClicker.holdOnly = v)
         .setTooltip(Tooltip.of(Text.literal("Выкл — кликает всё время, пока модуль включён")));
      this.toggleRow("Не мешать копанию", () -> cfg().autoClicker.ignoreBlocks, v -> cfg().autoClicker.ignoreBlocks = v)
         .setTooltip(Tooltip.of(Text.literal("Не кликать ЛКМ, когда прицел на блоке")));
   }

   private void autoClickerHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Интервал между кликами случайный: от 1/макс. до 1/мин. CPS. Клик обрабатывает сама игра, как настоящий.", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "ПКМ при зажатой кнопке ванилла и так повторяет ~5 раз/с — клики добавляются сверху.", y + 4, 0xFF586070);
   }

   private void buildCriticals() {
      this.moduleRow(this.page.module());
      this.sliderRow(
         "Высота", 0.0125, 0.5, 0.0125, () -> cfg().criticals.height, v -> cfg().criticals.height = v, v -> String.format(Locale.ROOT, "%.4f", v)
      );
      this.toggleRow("Только заряженный удар", () -> cfg().criticals.onlyWhenCharged, v -> cfg().criticals.onlyWhenCharged = v)
         .setTooltip(Tooltip.of(Text.literal("Ванилла критует только удар, заряженный больше чем на 90%")));
   }

   private void criticalsHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Перед ударом уходят 2 пакета позиции: y + высота и снова y, оба с onGround=false — сервер видит «падение».", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "Не срабатывает в спринте, в воде и лаве, на лестнице и верхом — там ванилла крит не даёт.", y + 4, 0xFF586070);
   }

   private void buildVelocity() {
      this.moduleRow(this.page.module());
      this.sliderRow(
         "По горизонтали", 0.0, 100.0, 5.0, () -> cfg().velocity.horizontal, v -> cfg().velocity.horizontal = v, v -> String.format(Locale.ROOT, "%.0f%%", v)
      );
      this.sliderRow(
         "По вертикали", 0.0, 100.0, 5.0, () -> cfg().velocity.vertical, v -> cfg().velocity.vertical = v, v -> String.format(Locale.ROOT, "%.0f%%", v)
      );
      this.toggleRow("Взрывы и ветер", () -> cfg().velocity.explosions, v -> cfg().velocity.explosions = v);
   }

   private void velocityHelp(DrawContext context) {
      int y = this.drawWrapped(context, "Сколько отбрасывания оставить: 0% — игрок не сдвигается, 100% — как в ванилле.", this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Сервер шлёт обычный EntityVelocityUpdate / Explosion — меняется только реакция клиента.", y + 4, 0xFF586070);
   }

   private void buildScaffold() {
      this.moduleRow(this.page.module());
      this.segmentedRow(
         "Поворот",
         120,
         ActestConfig.Scaffold.Rotation.values(),
         rotation -> rotation == ActestConfig.Scaffold.Rotation.NONE ? "Нет" : "Пакет",
         () -> cfg().scaffold.rotation,
         rotation -> cfg().scaffold.rotation = rotation
      );
      this.sliderRow(
         "Задержка",
         0.0,
         10.0,
         1.0,
         () -> (double)cfg().scaffold.delay,
         v -> cfg().scaffold.delay = (int)Math.round(v),
         v -> String.format(Locale.ROOT, "%.0f тик.", v)
      );
      this.toggleRow("Вернуть слот", () -> cfg().scaffold.switchBack, v -> cfg().scaffold.switchBack = v)
         .setTooltip(Tooltip.of(Text.literal("После установки вернуть слот хотбара, который был выбран")));
      this.toggleRow("Взмах рукой", () -> cfg().scaffold.swing, v -> cfg().scaffold.swing = v);
   }

   private void scaffoldHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Ставит блок из хотбара под ноги кликом по грани соседнего блока (снизу или сбоку).", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "В прыжке строит вверх. Нужен полный блок в хотбаре.", y + 4, 0xFF586070);
   }

   private void buildJesus() {
      this.moduleRow(this.page.module());
      this.toggleRow("Лава тоже", () -> cfg().jesus.lava, v -> cfg().jesus.lava = v);
      this.toggleRow("Всплывать", () -> cfg().jesus.swimUp, v -> cfg().jesus.swimUp = v)
         .setTooltip(Tooltip.of(Text.literal("Если игрок уже в жидкости — выталкивать его к поверхности")));
   }

   private void jesusHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Вода и лава твёрдые сверху: ходьба по поверхности обычной наземной физикой, в пакетах onGround=true.", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "Присесть (Shift) — нырнуть. Блоки с водой внутри (waterlogged) не затрагиваются.", y + 4, 0xFF586070);
   }

   private void buildBlink() {
      this.moduleRow(this.page.module());
      this.sliderRow(
         "Пульс",
         0.0,
         100.0,
         5.0,
         () -> (double)cfg().blink.releaseTicks,
         v -> cfg().blink.releaseTicks = (int)Math.round(v),
         v -> v < 1.0 ? "выкл" : String.format(Locale.ROOT, "%.0f тик.", v)
      );
      this.sliderRow(
         "Максимум",
         20.0,
         1200.0,
         20.0,
         () -> (double)cfg().blink.maxTicks,
         v -> cfg().blink.maxTicks = (int)Math.round(v),
         v -> String.format(Locale.ROOT, "%.0f с", v / 20.0)
      );
   }

   private void blinkHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Пакеты движения копятся и уходят пачкой при выключении (или каждые «Пульс» тиков). Для сервера игрок стоит на месте.", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "«Максимум» — через сколько модуль выключится сам. Телепорт от сервера во время Blink даст откат.", y + 4, 0xFF586070);
   }

   private void buildFastBreak() {
      this.moduleRow(this.page.module());
      this.sliderRow(
         "Ломать при", 0.0, 1.0, 0.05, () -> cfg().fastBreak.breakAt, v -> cfg().fastBreak.breakAt = v, v -> String.format(Locale.ROOT, "%.0f%%", v * 100.0)
      );
      this.toggleRow("Без паузы между блоками", () -> cfg().fastBreak.noDelay, v -> cfg().fastBreak.noDelay = v)
         .setTooltip(Tooltip.of(Text.literal("Ванилла ждёт 5 тиков, прежде чем начать ломать следующий блок")));
   }

   private void fastBreakHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Блок «доламывается», как только ванильный прогресс дошёл до заданной доли. 100% — как в ванилле.", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "Ванильный сервер принимает поломку с 70%; раньше — сам доломает блок по полному времени.", y + 4, 0xFF586070);
   }

   private void buildChestStealer() {
      this.moduleRow(this.page.module());
      this.sliderRow(
         "Задержка",
         0.0,
         10.0,
         1.0,
         () -> (double)cfg().chestStealer.delay,
         v -> cfg().chestStealer.delay = (int)Math.round(v),
         v -> String.format(Locale.ROOT, "%.0f тик.", v)
      );
      this.sliderRow(
         "Старт",
         0.0,
         20.0,
         1.0,
         () -> (double)cfg().chestStealer.startDelay,
         v -> cfg().chestStealer.startDelay = (int)Math.round(v),
         v -> String.format(Locale.ROOT, "%.0f тик.", v)
      );
      this.toggleRow("Закрыть после", () -> cfg().chestStealer.autoClose, v -> cfg().chestStealer.autoClose = v);
   }

   private void chestStealerHelp(DrawContext context) {
      int y = this.drawWrapped(
         context, "Shift-клик по каждому слоту сундука, бочки, эндер-сундука или шалкера. Задержка 0 — всё за один тик.", this.footerY, 0xFF8B93A3
      );
      this.drawWrapped(context, "«Старт» — пауза после открытия: заодно успевает прийти содержимое контейнера.", y + 4, 0xFF586070);
   }

   private void buildAutoTotem() {
      this.moduleRow(this.page.module());
      this.segmentedRow(
         "Когда",
         150,
         ActestConfig.AutoTotem.Mode.values(),
         mode -> mode == ActestConfig.AutoTotem.Mode.ALWAYS ? "Всегда" : "По здоровью",
         () -> cfg().autoTotem.mode,
         mode -> cfg().autoTotem.mode = mode
      );
      this.sliderRow(
         "Порог здоровья", 1.0, 20.0, 1.0, () -> cfg().autoTotem.health, v -> cfg().autoTotem.health = v, v -> String.format(Locale.ROOT, "%.0f HP", v)
      );
      this.segmentedRow(
         "Способ",
         150,
         ActestConfig.AutoTotem.Method.values(),
         method -> method == ActestConfig.AutoTotem.Method.SWAP ? "Swap (F)" : "Клики",
         () -> cfg().autoTotem.method,
         method -> cfg().autoTotem.method = method
      );
      this.sliderRow(
         "Задержка",
         0.0,
         20.0,
         1.0,
         () -> (double)cfg().autoTotem.delay,
         v -> cfg().autoTotem.delay = (int)Math.round(v),
         v -> String.format(Locale.ROOT, "%.0f тик.", v)
      );
   }

   private void autoTotemHelp(DrawContext context) {
      String text = cfg().autoTotem.method == ActestConfig.AutoTotem.Method.SWAP
         ? "Swap: один ClickSlotC2SPacket (SWAP, кнопка 40) — как F в открытом инвентаре."
         : "Клики: 2–3 ClickSlotC2SPacket PICKUP — взять тотем, положить во вторую руку, вернуть предмет.";
      int y = this.drawWrapped(context, text, this.footerY, 0xFF8B93A3);
      this.drawWrapped(context, "Экран инвентаря не открывается. Задержка считается с момента, когда тотем пропал из руки.", y + 4, 0xFF586070);
   }

   private void buildWallhack() {
      this.moduleRow(this.page.module());
      this.segmentedRow("Вид", 150, ActestConfig.Wallhack.Mode.values(), mode -> {
         return switch (mode) {
            case GLOW -> "Контур";
            case BOX -> "Рамки";
            case BOTH -> "Оба";
         };
      }, () -> cfg().wallhack.mode, mode -> cfg().wallhack.mode = mode);

      for (ActestConfig.Wallhack.Target target : ActestConfig.Wallhack.Target.values()) {
         ToggleSwitch toggle = this.toggleRow(targetName(target), () -> cfg().wallhack.shows(target), v -> cfg().wallhack.setShown(target, v));
         ((ColorChip)this.addDrawableChild(
               new ColorChip(
                  toggle.getX() - 18,
                  toggle.getY() + 1,
                  10,
                  Text.literal("Цвет: " + targetName(target)),
                  () -> cfg().wallhack.color(target),
                  () -> colorTarget == target,
                  () -> colorTarget = target
               )
            ))
            .setTooltip(Tooltip.of(Text.literal("Нажмите, чтобы менять цвет этой группы")));
      }

      int y = this.row("Подписи");
      PillToggle distance = new PillToggle(0, center(y, 14), 14, "Дистанция", () -> cfg().wallhack.showDistance, v -> cfg().wallhack.showDistance = v);
      distance.setX(this.contentRight - distance.getWidth());
      PillToggle names = new PillToggle(0, center(y, 14), 14, "Имена", () -> cfg().wallhack.showNames, v -> cfg().wallhack.showNames = v);
      names.setX(distance.getX() - 4 - names.getWidth());
      this.addDrawableChild(names);
      this.addDrawableChild(distance);
      this.sliderRow(
         "Дальность", 16.0, 512.0, 8.0, () -> cfg().wallhack.maxDistance, v -> cfg().wallhack.maxDistance = v, v -> String.format(Locale.ROOT, "%.0f бл.", v)
      );
      y = this.row(() -> Text.literal("Цвет " + targetGenitive(colorTarget)));
      this.addDrawableChild(
         new HueSlider(
            this.contentRight - 150,
            center(y, 14),
            150,
            14,
            Text.literal("Оттенок"),
            () -> cfg().wallhack.color(colorTarget),
            rgb -> cfg().wallhack.setColor(colorTarget, rgb)
         )
      );
      y = this.row(() -> Text.literal("Палитра").formatted(Formatting.GRAY));
      int size = 10;
      int gap = 6;
      int x = this.contentRight - (PRESET_COLORS.length * (size + gap) - gap);

      for (int preset : PRESET_COLORS) {
         this.addDrawableChild(
            new ColorChip(
               x,
               center(y, size),
               size,
               Text.literal(String.format("#%06X", preset)),
               () -> preset,
               () -> cfg().wallhack.color(colorTarget) == preset,
               () -> cfg().wallhack.setColor(colorTarget, preset)
            )
         );
         x += size + gap;
      }
   }

   private void buildInterface() {
      this.toggleRow("Список модулей на экране", () -> cfg().hud.enabled, v -> cfg().hud.enabled = v);
      this.toggleRow("Лог движения", () -> cfg().debugLog, v -> cfg().debugLog = v)
         .setTooltip(Tooltip.of(Text.literal("В logs/latest.log: смещение и onGround за каждый тик ([Move]) и каждое действие модулей ([KillAura], [Blink] и т.д.)")));
      int y = this.row("Конфиг actest.json");
      ((FlatButton)this.addDrawableChild(new FlatButton(this.contentRight - 86, center(y, 14), 86, 14, "Перечитать", () -> {
         ActestConfig.load();
         this.rebuild = true;
      }))).setTooltip(Tooltip.of(Text.literal("Загрузить config/actest.json заново, если правили его вручную")));
      this.serverRow();
   }

   /** Строка «Сервер: адрес» с кнопкой: «Разрешить» → «Подтвердить» (добавить в allowedServers) или «Убрать». */
   private void serverRow() {
      ServerInfo server = this.client.getCurrentServerEntry();
      if (this.client.isInSingleplayer() || server == null || server.address == null) {
         String where = this.client.isInSingleplayer() ? "Сервер: одиночная игра" : "Сервер: не подключён";
         this.row(() -> Text.literal(where).formatted(Formatting.GRAY));
         return;
      }

      String address = server.address.trim();
      boolean allowed = this.modules.canEnableModules(this.client);
      int y = this.row(this.textRenderer.trimToWidth("Сервер: " + address, this.contentRight - this.contentX - 86 - 8));
      String text = allowed ? "Убрать" : (address.equals(this.pendingAllow) ? "Подтвердить" : "Разрешить");
      FlatButton button = (FlatButton)this.addDrawableChild(new FlatButton(this.contentRight - 86, center(y, 14), 86, 14, text, () -> {
         if (allowed) {
            ActestConfig.disallowServer(address);
            this.pendingAllow = null;
         } else if (address.equals(this.pendingAllow)) {
            ActestConfig.allowServer(address);
            this.pendingAllow = null;
         } else {
            this.pendingAllow = address;
         }

         this.rebuild = true;
      }));
      button.setTooltip(
         Tooltip.of(
            Text.literal(
               allowed
                  ? "Убрать этот адрес из allowedServers — модули здесь сразу выключатся"
                  : "Добавить этот адрес в allowedServers (config/actest.json). Нажмите ещё раз для подтверждения"
            )
         )
      );
   }

   private void interfaceHelp(DrawContext context) {
      int y = this.drawWrapped(
         context,
         "Лог пишет [Move] на каждый тик (dXZ, dY, onGround — как их получил сервер) и строку на каждое действие модулей.",
         this.footerY,
         0xFF8B93A3
      );
      this.drawWrapped(
         context,
         "«Разрешить» (два нажатия) добавляет текущий сервер в allowedServers, «Убрать» — удаляет. Одиночная игра разрешена, пока allowSingleplayer = true.",
         y + 4,
         0xFF586070
      );
   }

   private void keysHelp(DrawContext context) {
      // Две колонки: модулей больше, чем помещается строк на странице.
      List<Module> all = this.modules.all();
      int total = all.size() + 1;
      int perColumn = (total + 1) / 2;
      int middle = (this.contentX + this.contentRight) / 2;
      int top = this.nextRowY + 2;
      int bottom = top;

      for (int i = 0; i < total; i++) {
         boolean leftColumn = i < perColumn;
         int x1 = leftColumn ? this.contentX : middle + 6;
         int x2 = leftColumn ? middle - 6 : this.contentRight;
         int y = top + (leftColumn ? i : i - perColumn) * 14;
         if (i < all.size()) {
            this.drawKeyLine(context, all.get(i).getName(), all.get(i).getKeyBinding(), x1, x2, y);
         } else {
            this.drawKeyLine(context, "Меню", this.modules.getMenuKey(), x1, x2, y);
         }

         bottom = Math.max(bottom, y + 14);
      }

      this.drawWrapped(context, "Переназначить: Настройки → Управление → AC Test Client.", bottom + 2, 0xFF586070);
   }

   protected void init() {
      this.rows.clear();
      this.panelWidth = Math.min(360, this.width - 8);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = Math.max(4, (this.height - 206) / 2);
      this.contentX = this.panelX + 86 + 10;
      this.contentRight = this.panelX + this.panelWidth - 10;
      this.nextRowY = this.panelY + 24 + 10;
      int tabX = this.panelX + 86 + 4;

      for (ActestScreen.Category c : ActestScreen.Category.values()) {
         TopTab tab = (TopTab)this.addDrawableChild(new TopTab(tabX, this.panelY, 24, c.title, () -> category == c, () -> this.switchCategory(c)));
         tabX += tab.getWidth();
      }

      this.addDrawableChild(new FlatButton(this.panelX + this.panelWidth - 20, this.panelY + 5, 14, 14, "×", this::close));
      List<ActestScreen.Page> pages = this.pages(category);
      int index = Math.min(selectedPage[category.ordinal()], pages.size() - 1);
      int itemY = this.panelY + 24 + 8;

      for (int i = 0; i < pages.size(); i++) {
         ActestScreen.Page p = pages.get(i);
         int pageIndex = i;
         BooleanSupplier indicator = p.module() == null ? null : p.module()::isEnabled;
         this.addDrawableChild(
            new TabButton(
               this.panelX, itemY, 86, 18, p.title(), () -> selectedPage[category.ordinal()] == pageIndex, indicator, () -> this.selectPage(pageIndex)
            )
         );
         itemY += 20;
      }

      this.page = pages.get(index);
      this.page.build().run();
      this.footerY = this.nextRowY + 6;
   }

   private void moduleRow(Module module) {
      KeyBinding key = module.getKeyBinding();
      String keyName = key.isUnbound() ? "без клавиши" : key.getBoundKeyLocalizedText().getString();
      Text label = Text.literal(module.getName())
         .formatted(Formatting.BOLD)
         .append(Text.literal("  " + keyName).formatted(Formatting.GRAY));
      int y = this.row(() -> label);
      ToggleSwitch toggle = (ToggleSwitch)this.addDrawableChild(
         new ToggleSwitch(this.contentRight - 22, center(y, 12), Text.literal(module.getName()), module::isEnabled, module::setEnabled)
      );
      if (!this.modules.canEnableModules(this.client) && !module.isEnabled()) {
         toggle.active = false;
         toggle.setTooltip(Tooltip.of(Text.literal("Сервер не разрешён: Прочее → Интерфейс → «Разрешить»")));
      }
   }

   private ToggleSwitch toggleRow(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
      int y = this.row(label);
      return (ToggleSwitch)this.addDrawableChild(new ToggleSwitch(this.contentRight - 22, center(y, 12), Text.literal(label), getter, setter));
   }

   private <T> void segmentedRow(String label, int width, T[] values, Function<T, String> names, Supplier<T> getter, Consumer<T> setter) {
      int y = this.row(label);
      this.addDrawableChild(new Segmented<T>(this.contentRight - width, center(y, 14), width, 14, Text.literal(label), values, names, getter, setter));
   }

   private void sliderRow(String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format) {
      int y = this.row(label);
      this.addDrawableChild(new Slider(this.contentRight - 150, center(y, 14), 150, 14, Text.literal(label), min, max, step, getter, setter, format));
   }

   private int pillRowY(String label) {
      return center(this.row(label), 14);
   }

   /** Выравнивает «пилюли» по правому краю строки и добавляет их на экран. */
   private void placePills(PillToggle... pills) {
      int x = this.contentRight;

      for (int i = pills.length - 1; i >= 0; i--) {
         x -= pills[i].getWidth();
         pills[i].setX(x);
         x -= 4;
      }

      for (PillToggle pill : pills) {
         this.addDrawableChild(pill);
      }
   }

   private int row(String label) {
      Text text = Text.literal(label);
      return this.row(() -> text);
   }

   private int row(Supplier<Text> label) {
      int y = this.nextRowY;
      this.rows.add(new ActestScreen.Row(y, label));
      this.nextRowY += 18;
      return y;
   }

   private static int center(int rowY, int h) {
      return rowY + (18 - h) / 2;
   }

   private void switchCategory(ActestScreen.Category target) {
      if (category != target) {
         category = target;
         this.rebuild = true;
      }
   }

   private void selectPage(int index) {
      if (selectedPage[category.ordinal()] != index) {
         selectedPage[category.ordinal()] = index;
         this.rebuild = true;
      }
   }

   private static String targetName(ActestConfig.Wallhack.Target target) {
      return switch (target) {
         case PLAYERS -> "Игроки";
         case HOSTILE -> "Враждебные мобы";
         case PASSIVE -> "Мирные мобы";
      };
   }

   private static String targetGenitive(ActestConfig.Wallhack.Target target) {
      return switch (target) {
         case PLAYERS -> "игроков";
         case HOSTILE -> "враждебных";
         case PASSIVE -> "мирных";
      };
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      if (this.rebuild) {
         this.rebuild = false;
         this.clearAndInit();
      }

      super.render(context, mouseX, mouseY, delta);
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
      int x1 = this.panelX;
      int y1 = this.panelY;
      int x2 = this.panelX + this.panelWidth;
      int y2 = this.panelY + 206;
      int bodyTop = y1 + 24 + 1;
      context.fill(0, 0, this.width, this.height, 0x50000000);
      Theme.roundRect(context, x1 + 2, y1 + 4, x2 + 4, y2 + 6, 4, 0x70000000);
      Theme.roundRect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 4, 0xFF2C3442);
      Theme.roundRect(context, x1, y1, x2, y1 + 24, 3, 0xFF1C2230, true, true, false, false);
      context.fill(x1 + 3, y1, x2 - 3, y1 + 1, 0x14FFFFFF);
      context.fill(x1, y1 + 24, x2, bodyTop, 0xA05B8CFF);
      Theme.roundRect(context, x1 + 9, y1 + 8, x1 + 17, y1 + 16, 2, 0xFF5B8CFF);
      context.drawText(this.textRenderer, Text.literal("AC Test").formatted(Formatting.BOLD), x1 + 22, y1 + 8, 0xFFE6E9EF, false);
      Theme.roundRect(context, x1, bodyTop, x1 + 86, y2, 3, 0xFF111419, false, false, true, false);
      context.fill(x1 + 86, bodyTop, x1 + 86 + 1, y2, 0xFF2C3442);
      Theme.roundRect(context, x1 + 86 + 1, bodyTop, x2, y2, 3, 0xFF161A22, false, false, false, true);
      this.drawServerStatus(context, x1 + 10, y2 - 14, 66);

      for (ActestScreen.Row row : this.rows) {
         if (mouseX >= this.contentX - 4 && mouseX < this.contentRight + 4 && mouseY >= row.y() && mouseY < row.y() + 18) {
            Theme.roundRect(context, this.contentX - 4, row.y(), this.contentRight + 4, row.y() + 18, 2, 0x10FFFFFF);
         }

         context.drawText(this.textRenderer, row.label().get(), this.contentX, row.y() + 5, 0xFFE6E9EF, false);
      }

      if (this.page != null && this.page.footer() != null) {
         this.page.footer().accept(context);
      }
   }

   private void drawServerStatus(DrawContext context, int x, int y, int maxWidth) {
      boolean allowed = this.modules.canEnableModules(this.client);
      ServerInfo server = this.client.getCurrentServerEntry();
      String where = this.client.isInSingleplayer() ? "одиночная игра" : (server != null ? server.address : "нет сервера");
      Theme.roundRect(context, x, y + 1, x + 5, y + 6, 2, allowed ? 0xFF4ADE80 : 0xFFF87171);
      context.drawText(this.textRenderer, this.textRenderer.trimToWidth(where, maxWidth - 9), x + 9, y, 0xFF8B93A3, false);
   }

   private int drawKeyLine(DrawContext context, String name, KeyBinding key, int x1, int x2, int y) {
      context.drawText(this.textRenderer, name, x1, y, 0xFFE6E9EF, false);
      Text keyName = (Text)(key.isUnbound() ? Text.literal("—") : key.getBoundKeyLocalizedText());
      int w = this.textRenderer.getWidth(keyName) + 8;
      Theme.roundRect(context, x2 - w, y - 2, x2, y + 10, 2, 0xFF272E3B);
      context.drawText(this.textRenderer, keyName, x2 - w + 4, y, 0xFFE6E9EF, false);
      return y + 14;
   }

   private int drawWrapped(DrawContext context, String text, int y, int color) {
      for (OrderedText line : this.textRenderer.wrapLines(Text.literal(text), this.contentRight - this.contentX)) {
         context.drawText(this.textRenderer, line, this.contentX, y, color, false);
         y += 10;
      }

      return y;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.modules.getMenuKey().matchesKey(keyCode, scanCode)) {
         this.close();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void removed() {
      ActestConfig.save();
   }

   private static enum Category {
      MOVEMENT("Движение"),
      COMBAT("Бой"),
      WORLD("Мир"),
      RENDER("Визуал"),
      OTHER("Прочее");

      final String title;

      private Category(String title) {
         this.title = title;
      }
   }

   private static record Page(String title, Module module, Runnable build, Consumer<DrawContext> footer) {
   }

   private static record Row(int y, Supplier<Text> label) {
   }
}

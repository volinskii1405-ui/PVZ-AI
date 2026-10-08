package dev.actest;

import dev.actest.config.ActestConfig;
import dev.actest.module.ModuleManager;
import dev.actest.render.Projection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа клиентского мода.
 * Загружает конфиг, создаёт модули и подписывается на события Fabric API.
 */
public final class ActestClient implements ClientModInitializer {
	public static final String MOD_ID = "actest";
	/** Категория наших клавиш в меню «Настройки → Управление». */
	public static final String KEY_CATEGORY = "category.actest";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModuleManager modules;

	@Override
	public void onInitializeClient() {
		ActestConfig.load();
		modules = new ModuleManager();

		// Клавиши и логика модулей: 20 раз в секунду, ПОСЛЕ тика игрока
		// (скорость, выставленная здесь, применится к движению на следующем тике)
		ClientTickEvents.END_CLIENT_TICK.register(modules::onTick);

		// В конце отрисовки мира запоминаем матрицы камеры этого кадра,
		// чтобы в HUD переводить мировые координаты в экранные
		WorldRenderEvents.LAST.register(Projection::capture);

		// Отрисовка поверх экрана: ESP-рамки/ники и список включённых модулей
		HudRenderCallback.EVENT.register((context, tickCounter) ->
				modules.onHudRender(context, tickCounter.getTickDelta(true)));

		LOGGER.info("AC Test Client загружен, конфиг: {}", ActestConfig.path());
	}

	/** Менеджер модулей (используется и из mixin'ов). До инициализации мода — null. */
	public static ModuleManager modules() {
		return modules;
	}
}

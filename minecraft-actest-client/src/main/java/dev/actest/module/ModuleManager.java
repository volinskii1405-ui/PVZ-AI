package dev.actest.module;

import dev.actest.config.ActestConfig;
import dev.actest.render.ModuleListHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Хранит модули, обрабатывает клавиши и раздаёт им тики и кадры. */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
	private int configCheckTimer;

	public ModuleManager() {
		// Новые модули регистрируются здесь — порядок = порядок в HUD-списке
		register(new SpeedModule());
		register(new WallhackModule());
	}

	private void register(Module module) {
		modules.add(module);
		byType.put(module.getClass(), module);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	/** Модуль по классу или null, например get(WallhackModule.class). */
	public <T extends Module> T get(Class<T> type) {
		return type.cast(byType.get(type));
	}

	/** ClientTickEvents.END_CLIENT_TICK */
	public void onTick(MinecraftClient client) {
		// Горячая перезагрузка конфига: проверяем дату изменения файла раз в секунду
		if (++configCheckTimer >= 20) {
			configCheckTimer = 0;
			ActestConfig.reloadIfChanged();
		}

		boolean allowed = ServerGuard.isAllowed(client);

		for (Module module : modules) {
			// wasPressed() возвращает по одному нажатию за вызов, поэтому while
			while (module.getKeyBinding().wasPressed()) {
				if (!module.isEnabled() && !allowed) {
					notify(client, Text.literal(module.getName() + ": сервер не в allowedServers (config/actest.json)")
							.formatted(Formatting.RED));
					continue;
				}
				module.toggle();
				notify(client, Text.literal(module.getName() + (module.isEnabled() ? ": ВКЛ" : ": выкл"))
						.formatted(module.isEnabled() ? Formatting.GREEN : Formatting.GRAY));
			}

			if (!module.isEnabled()) {
				continue;
			}
			if (allowed) {
				module.onTick(client);
			} else {
				// Вышли из мира или зашли на сервер не из списка — выключаем
				module.setEnabled(false);
			}
		}
	}

	/** HudRenderCallback: сначала оверлеи модулей (ESP), поверх — список модулей. */
	public void onHudRender(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}
		for (Module module : modules) {
			if (module.isEnabled()) {
				module.onRender(context, tickDelta);
			}
		}
		ModuleListHud.render(context, client.textRenderer, modules);
	}

	/** Короткое сообщение над хотбаром (action bar). */
	private static void notify(MinecraftClient client, Text text) {
		if (client.player != null) {
			client.player.sendMessage(text, true);
		}
	}
}

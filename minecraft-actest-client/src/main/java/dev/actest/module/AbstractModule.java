package dev.actest.module;

import dev.actest.ActestClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Базовая реализация: хранит имя, состояние и регистрирует клавишу. */
public abstract class AbstractModule implements Module {
	private final String name;
	private final KeyBinding keyBinding;
	private boolean enabled;

	/**
	 * @param keyTranslation ключ перевода клавиши (см. assets/actest/lang)
	 * @param defaultKey     клавиша по умолчанию, константа GLFW.GLFW_KEY_*
	 */
	protected AbstractModule(String name, String keyTranslation, int defaultKey) {
		this.name = name;
		this.keyBinding = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(keyTranslation, InputUtil.Type.KEYSYM, defaultKey, ActestClient.KEY_CATEGORY));
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public KeyBinding getKeyBinding() {
		return keyBinding;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/** Переопределить, если модулю нужно что-то сделать при включении. */
	protected void onEnable() {
	}

	/** Переопределить, если модулю нужно что-то вернуть при выключении. */
	protected void onDisable() {
	}
}

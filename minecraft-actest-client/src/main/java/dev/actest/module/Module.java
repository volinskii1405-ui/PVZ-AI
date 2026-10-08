package dev.actest.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/**
 * Общий интерфейс модуля.
 * Чтобы добавить новый модуль: унаследоваться от {@link AbstractModule}
 * и добавить одну строку register(...) в конструкторе {@link ModuleManager}.
 */
public interface Module {
	/** Имя для HUD-списка и сообщений. */
	String getName();

	/** Клавиша включения/выключения (переназначается в меню «Управление»). */
	KeyBinding getKeyBinding();

	boolean isEnabled();

	void setEnabled(boolean enabled);

	default void toggle() {
		setEnabled(!isEnabled());
	}

	/**
	 * Вызывается каждый клиентский тик (20 раз/с) после тика игрока,
	 * только когда модуль включён и игрок находится в мире.
	 */
	default void onTick(MinecraftClient client) {
	}

	/**
	 * Вызывается каждый кадр при отрисовке HUD, только когда модуль включён.
	 *
	 * @param tickDelta доля текущего тика (0..1) для интерполяции позиций
	 */
	default void onRender(DrawContext context, float tickDelta) {
	}

	/** Короткая подпись рядом с именем в HUD-списке, например "BHOP x1.50". */
	default String getHudInfo() {
		return "";
	}
}

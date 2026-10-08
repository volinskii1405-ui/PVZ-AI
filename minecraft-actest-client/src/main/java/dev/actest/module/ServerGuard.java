package dev.actest.module;

import dev.actest.config.ActestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/**
 * Разрешает модули только на серверах из allowedServers (и в одиночной игре,
 * если allowSingleplayer = true), чтобы тестовый клиент случайно
 * не оказался включён на чужом сервере.
 */
final class ServerGuard {
	private ServerGuard() {
	}

	static boolean isAllowed(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return false;
		}
		ActestConfig config = ActestConfig.get();
		if (client.isInSingleplayer()) {
			return config.allowSingleplayer;
		}

		ServerInfo server = client.getCurrentServerEntry();
		if (server == null || server.address == null) {
			return false;
		}
		String address = server.address.trim().toLowerCase(Locale.ROOT);
		String host = stripPort(address);
		for (String entry : config.allowedServers) {
			if (entry == null) continue;
			String allowed = entry.trim().toLowerCase(Locale.ROOT);
			if (allowed.equals(address) || allowed.equals(host)) {
				return true;
			}
		}
		return false;
	}

	/** "host:25565" → "host"; "[::1]:25565" → "[::1]"; адрес без порта не меняется. */
	private static String stripPort(String address) {
		if (address.startsWith("[")) {
			int end = address.indexOf(']');
			return end > 0 ? address.substring(0, end + 1) : address;
		}
		int colon = address.indexOf(':');
		// Ровно одно двоеточие — это host:port; больше — голый IPv6 без порта
		if (colon > 0 && colon == address.lastIndexOf(':')) {
			return address.substring(0, colon);
		}
		return address;
	}
}

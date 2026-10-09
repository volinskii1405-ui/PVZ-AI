package dev.actest.module;

import dev.actest.config.ActestConfig;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Разрешает модули только в одиночной игре (allowSingleplayer) и на серверах из allowedServers.
 */
final class ServerGuard {
   private ServerGuard() {
   }

   static boolean isAllowed(MinecraftClient client) {
      if (client.world != null && client.player != null) {
         ActestConfig config = ActestConfig.get();
         if (client.isInSingleplayer()) {
            return config.allowSingleplayer;
         } else {
            ServerInfo server = client.getCurrentServerEntry();
            if (server != null && server.address != null) {
               String address = server.address.trim().toLowerCase(Locale.ROOT);
               String host = stripPort(address);

               for (String entry : config.allowedServers) {
                  if (entry != null) {
                     String allowed = entry.trim().toLowerCase(Locale.ROOT);
                     if (allowed.equals(address) || allowed.equals(host)) {
                        return true;
                     }
                  }
               }

               return false;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static String stripPort(String address) {
      if (address.startsWith("[")) {
         int end = address.indexOf(']');
         return end > 0 ? address.substring(0, end + 1) : address;
      } else {
         int colon = address.indexOf(':');
         return colon > 0 && colon == address.lastIndexOf(':') ? address.substring(0, colon) : address;
      }
   }
}

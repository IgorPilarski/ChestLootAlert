package pl.igorpilarski.chestlootalert;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends plain-text alerts to a Discord channel via an incoming webhook.
 * Delivery always happens off the main thread so a slow/unreachable webhook
 * can never stall the server.
 */
public final class DiscordWebhook {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final JavaPlugin plugin;
    private final String webhookUrl;

    public DiscordWebhook(JavaPlugin plugin, String webhookUrl) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
    }

    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    public void send(String message) {
        if (!isEnabled()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> deliver(message));
    }

    private void deliver(String message) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setConnectTimeout((int) TIMEOUT.toMillis());
            connection.setReadTimeout((int) TIMEOUT.toMillis());
            connection.setDoOutput(true);

            String payload = "{\"content\":\"" + escapeJson(message) + "\"}";
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            connection.disconnect();

            if (status >= 300) {
                plugin.getLogger().warning("Discord webhook responded with HTTP " + status + ".");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to deliver Discord webhook: " + ex.getMessage());
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}

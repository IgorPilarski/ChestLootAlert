package pl.igorpilarski.chestlootalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstraps the plugin: loads configuration, owns the shared
 * {@link OwnerRepository}/{@link DiscordWebhook} instances used by
 * {@link ChestListener}, and takes care of periodic + shutdown autosave.
 */
public final class ChestLootAlertPlugin extends JavaPlugin {

    public static final String RECEIVE_PERMISSION = "chestlootalert.receive";

    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "item-taken", "&c[ALERT] &f{player} wyjął {amount}x {item} ze skrzyni gracza {owner}",
            "item-added", "&a[ALERT] &f{player} włożył {amount}x {item} do skrzyni gracza {owner}",
            "chest-broken", "&c[ALERT] &f{player} zniszczył skrzynię gracza {owner} na kordach: {x} {y} {z}"
    );

    private final OwnerRepository ownerRepository = new OwnerRepository(getLogger());
    private final Map<String, String> messages = new LinkedHashMap<>(DEFAULT_MESSAGES);

    private DiscordWebhook discordWebhook;
    private boolean chestOpenAlertsEnabled;
    private boolean chestBreakAlertsEnabled;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        startAutosaveTask();

        getLogger().info("ChestLootAlert enabled: tracking " + ownerRepository.size() + " container(s).");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        saveOwners();
        getLogger().info("ChestLootAlert disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chestlootalert")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            sender.sendMessage(Component.text("ChestLootAlert configuration reloaded."));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /chestlootalert reload"));
        return true;
    }

    private void loadSettings() {
        reloadConfig();

        chestOpenAlertsEnabled = getConfig().getBoolean("alerts.chest-open", true);
        chestBreakAlertsEnabled = getConfig().getBoolean("alerts.chest-break", true);
        discordWebhook = new DiscordWebhook(this, getConfig().getString("webhook-url", ""));

        loadMessages();
        ownerRepository.load(getConfig());
    }

    private void loadMessages() {
        messages.clear();
        messages.putAll(DEFAULT_MESSAGES);

        ConfigurationSection section = getConfig().getConfigurationSection("messages");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                messages.put(key, value);
            }
        }
    }

    private void startAutosaveTask() {
        int minutes = Math.max(1, getConfig().getInt("autosave-interval-minutes", 5));
        long intervalTicks = 20L * 60L * minutes;

        autosaveTask = getServer().getScheduler().runTaskTimer(this, this::saveOwners, intervalTicks, intervalTicks);
        getLogger().info("Container ownership autosave every " + minutes + " minute(s).");
    }

    /** Persists ownership only; webhook-url and alerts are left untouched. */
    public void saveOwners() {
        ownerRepository.save(getConfig());
        saveConfig();
    }

    public OwnerRepository getOwnerRepository() {
        return ownerRepository;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public boolean isChestOpenAlertsEnabled() {
        return chestOpenAlertsEnabled;
    }

    public boolean isChestBreakAlertsEnabled() {
        return chestBreakAlertsEnabled;
    }

    /**
     * Renders a configured message template (substituting {@code {placeholder}}
     * tokens), broadcasts it to everyone holding {@link #RECEIVE_PERMISSION},
     * and mirrors the plain-text version to Discord.
     */
    public void alert(String messageKey, Map<String, String> placeholders) {
        String template = messages.getOrDefault(messageKey, DEFAULT_MESSAGES.getOrDefault(messageKey, ""));
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            template = template.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }

        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(template);
        getServer().broadcast(message, RECEIVE_PERMISSION);
        discordWebhook.send(PlainTextComponentSerializer.plainText().serialize(message));
    }
}

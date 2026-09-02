package pl.igorpilarski.chestlootalert;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Keeps track of which player owns which tracked container and persists that
 * mapping under the {@code owners} section of config.yml, keyed by
 * {@code "world:x:y:z" -> uuid}. This key format matches the legacy
 * ChestAlertPlugin config so existing production data loads unchanged.
 */
public final class OwnerRepository {

    private static final String SECTION = "owners";

    private final Map<String, UUID> owners = new ConcurrentHashMap<>();
    private final Logger logger;

    public OwnerRepository(Logger logger) {
        this.logger = logger;
    }

    private static String keyFor(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }

    public void load(FileConfiguration config) {
        owners.clear();

        ConfigurationSection section = config.getConfigurationSection(SECTION);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String rawUuid = section.getString(key);
            if (rawUuid == null || rawUuid.isBlank()) {
                continue;
            }
            try {
                owners.put(key, UUID.fromString(rawUuid));
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping owner entry '" + key + "': invalid UUID '" + rawUuid + "'.");
            }
        }

        logger.info("Loaded " + owners.size() + " tracked container owner(s).");
    }

    public void save(FileConfiguration config) {
        config.set(SECTION, null);
        for (Map.Entry<String, UUID> entry : owners.entrySet()) {
            config.set(SECTION + "." + entry.getKey(), entry.getValue().toString());
        }
    }

    public void setOwner(Location location, UUID owner) {
        owners.put(keyFor(location), owner);
    }

    public UUID getOwner(Location location) {
        return owners.get(keyFor(location));
    }

    public void removeOwner(Location location) {
        owners.remove(keyFor(location));
    }

    public int size() {
        return owners.size();
    }
}

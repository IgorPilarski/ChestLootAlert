package pl.igorpilarski.chestlootalert;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles container placement/break for ownership tracking, and container
 * open/close for loot-diff alerts.
 */
public final class ChestListener implements Listener {

    /**
     * Snapshot of a container a player currently has open. Kept in this
     * listener's own memory instead of player metadata, so it can never
     * "leak" across a disconnect or world change — it only ever lives for
     * the duration of one open inventory and is cleared explicitly on close.
     */
    private record Session(Map<Integer, ItemStack> snapshot) {
    }

    private final ChestLootAlertPlugin plugin;
    private final Map<UUID, Session> openSessions = new ConcurrentHashMap<>();

    public ChestListener(ChestLootAlertPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!ContainerType.isTracked(block.getType())) {
            return;
        }
        UUID ownerId = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        plugin.getOwnerRepository().setOwner(block.getLocation(), ownerId, playerName);

        // Double chests are two blocks — the linked half may not fire its own place event.
        plugin.getServer().getScheduler().runTask(plugin, () ->
                registerDoubleChestHalves(block, ownerId, playerName));
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!ContainerType.isTracked(block.getType())) {
            return;
        }

        OwnerRepository owners = plugin.getOwnerRepository();
        Location location = block.getLocation();
        UUID ownerId = resolveOwner(block, owners);
        if (ownerId == null) {
            plugin.getLogger().info("Broken tracked container at " + formatLocation(location)
                    + " has no registered owner — break alert skipped.");
            return;
        }

        Player breaker = event.getPlayer();
        clearOwnership(block, breaker.getName(), owners);

        if (ownerId.equals(breaker.getUniqueId())) {
            plugin.getLogger().info("Broken tracked container at " + formatLocation(location)
                    + " was destroyed by its owner — break alert skipped.");
            return;
        }
        if (!plugin.isChestBreakAlertsEnabled()) {
            return;
        }

        plugin.alert("chest-broken", Map.of(
                "player", breaker.getName(),
                "owner", ownerName(ownerId),
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ())
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        OwnerRepository owners = plugin.getOwnerRepository();
        for (Block block : event.blockList()) {
            if (ContainerType.isTracked(block.getType())) {
                clearOwnership(block, "explosion", owners);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (!ContainerType.isTracked(block.getType())) {
            return;
        }
        clearOwnership(block, "fire", plugin.getOwnerRepository());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isTrackedContainerInventory(topInventory)) {
            return;
        }

        UUID playerId = event.getWhoClicked().getUniqueId();
        openSessions.computeIfAbsent(playerId, id -> new Session(InventoryDiff.snapshot(topInventory)));
    }

    @EventHandler
    public void onContainerClose(InventoryCloseEvent event) {
        HumanEntity player = event.getPlayer();
        Session session = openSessions.remove(player.getUniqueId());
        if (session == null || !plugin.isChestOpenAlertsEnabled()) {
            return;
        }

        Inventory closedInventory = event.getView().getTopInventory();
        Location location = closedInventory.getLocation();
        if (location == null) {
            return;
        }

        UUID ownerId = resolveOwner(closedInventory.getLocation().getBlock(), plugin.getOwnerRepository());
        if (ownerId == null || ownerId.equals(player.getUniqueId())) {
            return;
        }

        InventoryDiff.Result diff = InventoryDiff.compare(session.snapshot(), closedInventory);
        if (diff.isEmpty()) {
            return;
        }

        String ownerDisplayName = ownerName(ownerId);

        diff.taken().forEach((material, amount) -> plugin.alert("item-taken", Map.of(
                "player", player.getName(),
                "owner", ownerDisplayName,
                "amount", String.valueOf(amount),
                "item", prettify(material)
        )));

        diff.added().forEach((material, amount) -> plugin.alert("item-added", Map.of(
                "player", player.getName(),
                "owner", ownerDisplayName,
                "amount", String.valueOf(amount),
                "item", prettify(material)
        )));
    }

    private boolean isTrackedContainerInventory(Inventory inventory) {
        Location location = inventory.getLocation();
        return location != null && ContainerType.isTracked(location.getBlock().getType());
    }

    private String ownerName(UUID ownerId) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        String name = owner.getName();
        return name != null ? name : ownerId.toString();
    }

    private String prettify(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private UUID resolveOwner(Block block, OwnerRepository owners) {
        UUID owner = owners.getOwner(block.getLocation());
        if (owner != null) {
            return owner;
        }

        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) {
            return null;
        }

        InventoryHolder holder = chest.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) {
            return null;
        }

        owner = ownerFromHalf(doubleChest.getLeftSide(), owners);
        if (owner != null) {
            return owner;
        }
        return ownerFromHalf(doubleChest.getRightSide(), owners);
    }

    private UUID ownerFromHalf(InventoryHolder half, OwnerRepository owners) {
        if (half instanceof Chest chest) {
            return owners.getOwner(chest.getLocation());
        }
        return null;
    }

    private void registerDoubleChestHalves(Block block, UUID ownerId, String placedBy) {
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) {
            return;
        }

        InventoryHolder holder = chest.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) {
            return;
        }

        setOwnerOnHalf(doubleChest.getLeftSide(), ownerId, placedBy);
        setOwnerOnHalf(doubleChest.getRightSide(), ownerId, placedBy);
    }

    private void setOwnerOnHalf(InventoryHolder half, UUID ownerId, String placedBy) {
        if (half instanceof Chest chest) {
            plugin.getOwnerRepository().setOwner(chest.getLocation(), ownerId, placedBy);
        }
    }

    private void clearOwnership(Block block, String brokenBy, OwnerRepository owners) {
        owners.removeOwner(block.getLocation(), brokenBy);

        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) {
            return;
        }

        InventoryHolder holder = chest.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) {
            return;
        }

        removeOwnerOnHalf(doubleChest.getLeftSide(), brokenBy, owners);
        removeOwnerOnHalf(doubleChest.getRightSide(), brokenBy, owners);
    }

    private void removeOwnerOnHalf(InventoryHolder half, String brokenBy, OwnerRepository owners) {
        if (half instanceof Chest chest) {
            owners.removeOwner(chest.getLocation(), brokenBy);
        }
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }
}

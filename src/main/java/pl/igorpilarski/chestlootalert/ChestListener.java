package pl.igorpilarski.chestlootalert;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
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

    @EventHandler
    public void onContainerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!ContainerType.isTracked(block.getType())) {
            return;
        }
        plugin.getOwnerRepository().setOwner(block.getLocation(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!ContainerType.isTracked(block.getType())) {
            return;
        }

        OwnerRepository owners = plugin.getOwnerRepository();
        Location location = block.getLocation();
        UUID ownerId = owners.getOwner(location);
        if (ownerId == null) {
            return;
        }

        Player breaker = event.getPlayer();
        owners.removeOwner(location);

        if (ownerId.equals(breaker.getUniqueId()) || !plugin.isChestBreakAlertsEnabled()) {
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

    @EventHandler
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

        UUID ownerId = plugin.getOwnerRepository().getOwner(location);
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
}

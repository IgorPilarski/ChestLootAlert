package pl.igorpilarski.chestlootalert;

import org.bukkit.Material;

/**
 * Decides which block types are tracked for ownership/loot alerts.
 */
public final class ContainerType {

    private ContainerType() {
    }

    /**
     * Chests, trapped chests, barrels, every shulker box color, and every
     * copper chest variant (Paper 1.21+, matched by name suffix so this keeps
     * working if new oxidation/wax variants are added). Ender chests are
     * explicitly excluded — they are personal per-player storage, not a
     * container someone else can "own".
     */
    public static boolean isTracked(Material material) {
        if (material == Material.ENDER_CHEST) {
            return false;
        }

        if (material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL) {
            return true;
        }

        String name = material.name();
        return name.endsWith("SHULKER_BOX") || name.endsWith("COPPER_CHEST");
    }
}

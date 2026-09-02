package pl.igorpilarski.chestlootalert;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Snapshots a container's contents and compares totals per material to find
 * what was taken out and what was added. Using inventory-wide totals avoids
 * false alerts when items are merely rearranged between slots.
 */
public final class InventoryDiff {

    private InventoryDiff() {
    }

    /**
     * A per-material summary of what changed between two snapshots.
     */
    public record Result(Map<Material, Integer> taken, Map<Material, Integer> added) {

        public boolean isEmpty() {
            return taken.isEmpty() && added.isEmpty();
        }
    }

    /** Clones the current contents of an inventory into a slot -> item map. */
    public static Map<Integer, ItemStack> snapshot(Inventory inventory) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            snapshot.put(slot, item == null ? null : item.clone());
        }
        return snapshot;
    }

    /** Compares a "before" snapshot against the current state of {@code after}. */
    public static Result compare(Map<Integer, ItemStack> before, Inventory after) {
        Map<Material, Integer> beforeTotals = materialTotals(before);
        Map<Material, Integer> afterTotals = materialTotals(after);

        Map<Material, Integer> taken = new LinkedHashMap<>();
        Map<Material, Integer> added = new LinkedHashMap<>();

        Set<Material> materials = new HashSet<>();
        materials.addAll(beforeTotals.keySet());
        materials.addAll(afterTotals.keySet());

        for (Material material : materials) {
            int beforeAmount = beforeTotals.getOrDefault(material, 0);
            int afterAmount = afterTotals.getOrDefault(material, 0);
            if (afterAmount > beforeAmount) {
                added.put(material, afterAmount - beforeAmount);
            } else if (beforeAmount > afterAmount) {
                taken.put(material, beforeAmount - afterAmount);
            }
        }

        return new Result(taken, added);
    }

    private static Map<Material, Integer> materialTotals(Map<Integer, ItemStack> slots) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack item : slots.values()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            totals.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return totals;
    }

    private static Map<Material, Integer> materialTotals(Inventory inventory) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            totals.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return totals;
    }
}

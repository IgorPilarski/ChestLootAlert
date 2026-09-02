package pl.igorpilarski.chestlootalert;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshots a container's contents and compares two snapshots slot-by-slot to
 * find which items were taken out and which were added.
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
        Map<Material, Integer> taken = new LinkedHashMap<>();
        Map<Material, Integer> added = new LinkedHashMap<>();

        for (int slot = 0; slot < after.getSize(); slot++) {
            diffSlot(before.get(slot), after.getItem(slot), taken, added);
        }

        return new Result(taken, added);
    }

    private static void diffSlot(ItemStack beforeItem, ItemStack afterItem,
                                  Map<Material, Integer> taken, Map<Material, Integer> added) {
        int beforeAmount = beforeItem == null ? 0 : beforeItem.getAmount();
        int afterAmount = afterItem == null ? 0 : afterItem.getAmount();
        Material beforeType = beforeItem == null ? null : beforeItem.getType();
        Material afterType = afterItem == null ? null : afterItem.getType();

        if (beforeType == afterType) {
            if (afterAmount > beforeAmount) {
                added.merge(afterType, afterAmount - beforeAmount, Integer::sum);
            } else if (beforeAmount > afterAmount) {
                taken.merge(beforeType, beforeAmount - afterAmount, Integer::sum);
            }
            return;
        }

        // The slot's item type itself changed (e.g. swapped for a different item) —
        // count the old stack as fully taken and the new one as fully added.
        if (beforeType != null) {
            taken.merge(beforeType, beforeAmount, Integer::sum);
        }
        if (afterType != null) {
            added.merge(afterType, afterAmount, Integer::sum);
        }
    }
}

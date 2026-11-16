package rustcraft.world.menu;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIntArray;
import net.minecraft.util.IntArray;
import rustcraft.world.WorldRegistry;
import rustcraft.world.tile.RefineryTile;

public class RefineryContainer extends Container {
    private final IInventory inv;
    private final IIntArray data;

    /** Клиентский конструктор. */
    public RefineryContainer(int id, PlayerInventory playerInv) {
        this(id, playerInv, new Inventory(3), new IntArray(4));
    }

    public RefineryContainer(int id, PlayerInventory playerInv, IInventory inv, IIntArray data) {
        super(WorldRegistry.REFINERY_CONTAINER.get(), id);
        this.inv = inv;
        this.data = data;
        addSlot(new Slot(inv, 0, 56, 17) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return RefineryTile.isOil(stack);
            }
        });
        addSlot(new Slot(inv, 1, 56, 53) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return RefineryTile.isFuel(stack);
            }
        });
        addSlot(new Slot(inv, 2, 116, 35) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        });
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; ++c) {
            addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
        }
        trackIntArray(data);
    }

    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return inv.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index < 3) {
                if (!mergeItemStack(stack, 3, 39, true)) return ItemStack.EMPTY;
                slot.onSlotChange(stack, result);
            } else if (RefineryTile.isOil(stack)) {
                if (!mergeItemStack(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else if (RefineryTile.isFuel(stack)) {
                if (!mergeItemStack(stack, 1, 2, false)) return ItemStack.EMPTY;
            } else if (index < 30) {
                if (!mergeItemStack(stack, 30, 39, false)) return ItemStack.EMPTY;
            } else if (!mergeItemStack(stack, 3, 30, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
            if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return result;
    }

    /** 0..1 — доля оставшегося горения. */
    public float burnFraction() {
        int total = data.get(1);
        return total <= 0 ? 0F : (float) data.get(0) / (float) total;
    }

    /** 0..1 — прогресс перегонки. */
    public float cookFraction() {
        int total = data.get(3);
        return total <= 0 ? 0F : (float) data.get(2) / (float) total;
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }
}

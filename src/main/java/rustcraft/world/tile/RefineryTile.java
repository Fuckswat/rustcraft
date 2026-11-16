package rustcraft.world.tile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIntArray;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeHooks;
import rustcraft.world.WorldRegistry;
import rustcraft.world.block.RefineryBlock;
import rustcraft.world.menu.RefineryContainer;

/**
 * Слоты: 0 — сырая нефть, 1 — топливо для горения, 2 — выход (низкосортное топливо).
 * 1 сырая нефть -> 3 низкосортного топлива за {@link #COOK_TIME} тиков.
 */
public class RefineryTile extends TileEntity implements ITickableTileEntity, IInventory, INamedContainerProvider {
    public static final int COOK_TIME = 160;
    public static final int OUTPUT_PER_OIL = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int burnTime;
    private int burnTotal;
    private int cookTime;

    public final IIntArray data = new IIntArray() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return burnTime;
                case 1: return burnTotal;
                case 2: return cookTime;
                default: return COOK_TIME;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: burnTime = value; break;
                case 1: burnTotal = value; break;
                case 2: cookTime = value; break;
                default: break;
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public RefineryTile() {
        super(WorldRegistry.REFINERY_TILE.get());
    }

    public static boolean isOil(ItemStack s) {
        return s.getItem() == WorldRegistry.CRUDE_OIL.get();
    }

    public static boolean isFuel(ItemStack s) {
        return !isOil(s) && ForgeHooks.getBurnTime(s) > 0;
    }

    private boolean canRefine() {
        if (!isOil(items.get(0))) return false;
        ItemStack out = items.get(2);
        if (out.isEmpty()) return true;
        return out.getItem() == WorldRegistry.LOW_GRADE_FUEL.get() && out.getCount() + OUTPUT_PER_OIL <= out.getMaxStackSize();
    }

    @Override
    public void tick() {
        if (world == null || world.isRemote) return;
        boolean wasBurning = burnTime > 0;
        boolean dirty = false;
        if (burnTime > 0) burnTime--;

        if (canRefine()) {
            if (burnTime <= 0) {
                ItemStack fuel = items.get(1);
                int bt = isFuel(fuel) ? ForgeHooks.getBurnTime(fuel) : 0;
                if (bt > 0) {
                    burnTime = burnTotal = bt;
                    if (fuel.hasContainerItem()) {
                        items.set(1, fuel.getContainerItem());
                    } else {
                        fuel.shrink(1);
                    }
                    dirty = true;
                }
            }
            if (burnTime > 0) {
                cookTime++;
                if (cookTime >= COOK_TIME) {
                    cookTime = 0;
                    items.get(0).shrink(1);
                    ItemStack out = items.get(2);
                    if (out.isEmpty()) {
                        items.set(2, new ItemStack(WorldRegistry.LOW_GRADE_FUEL.get(), OUTPUT_PER_OIL));
                    } else {
                        out.grow(OUTPUT_PER_OIL);
                    }
                    dirty = true;
                }
            } else if (cookTime > 0) {
                cookTime = Math.max(0, cookTime - 2);
            }
        } else if (cookTime > 0) {
            cookTime = 0;
        }

        boolean burning = burnTime > 0;
        if (wasBurning != burning) {
            BlockState st = getBlockState();
            if (st.hasProperty(RefineryBlock.LIT)) {
                world.setBlockState(pos, st.with(RefineryBlock.LIT, burning), 3);
            }
            dirty = true;
        }
        if (dirty) markDirty();
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        items.clear();
        ItemStackHelper.loadAllItems(nbt, items);
        burnTime = nbt.getInt("BurnTime");
        burnTotal = nbt.getInt("BurnTotal");
        cookTime = nbt.getInt("CookTime");
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        ItemStackHelper.saveAllItems(nbt, items);
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("BurnTotal", burnTotal);
        nbt.putInt("CookTime", cookTime);
        return nbt;
    }

    @Override
    public int getSizeInventory() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(items, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(items, index);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        items.set(index, stack);
        if (stack.getCount() > getInventoryStackLimit()) stack.setCount(getInventoryStackLimit());
        markDirty();
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index == 0) return isOil(stack);
        if (index == 1) return isFuel(stack);
        return false;
    }

    @Override
    public boolean isUsableByPlayer(PlayerEntity player) {
        if (world == null || world.getTileEntity(pos) != this) return false;
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TranslationTextComponent("container.rustcraft.refinery");
    }

    @Override
    public Container createMenu(int id, PlayerInventory inv, PlayerEntity player) {
        return new RefineryContainer(id, inv, this, data);
    }
}

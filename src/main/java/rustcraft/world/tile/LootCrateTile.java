package rustcraft.world.tile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import rustcraft.world.WorldConfig;
import rustcraft.world.WorldRegistry;

/** Инвентарь 9x3 с перезаполнением по таймеру. */
public class LootCrateTile extends LockableLootTileEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private long nextRefill;

    public LootCrateTile() {
        super(WorldRegistry.CRATE_TILE.get());
    }

    /** Если таймер истёк — очистить и заново назначить таблицу лута (заполнится при открытии). */
    public void refillIfDue(ResourceLocation table) {
        if (world == null) return;
        long now = world.getGameTime();
        if (now >= nextRefill) {
            for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
            setLootTable(table, world.rand.nextLong());
            nextRefill = now + WorldConfig.CRATE_REFILL_MINUTES.get() * 1200L;
            markDirty();
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        items = list;
    }

    @Override
    protected ITextComponent getDefaultName() {
        Block b = getBlockState().getBlock();
        return new TranslationTextComponent(b.getTranslationKey());
    }

    @Override
    protected Container createMenu(int id, PlayerInventory inv) {
        return ChestContainer.createGeneric9X3(id, inv, this);
    }

    @Override
    public int getSizeInventory() {
        return 27;
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        items = NonNullList.withSize(getSizeInventory(), ItemStack.EMPTY);
        if (!checkLootAndRead(nbt)) {
            net.minecraft.inventory.ItemStackHelper.loadAllItems(nbt, items);
        }
        nextRefill = nbt.getLong("NextRefill");
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        if (!checkLootAndWrite(nbt)) {
            net.minecraft.inventory.ItemStackHelper.saveAllItems(nbt, items);
        }
        nbt.putLong("NextRefill", nextRefill);
        return nbt;
    }
}

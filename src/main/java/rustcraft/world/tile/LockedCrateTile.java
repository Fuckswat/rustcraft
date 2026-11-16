package rustcraft.world.tile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.server.ServerBossInfo;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;
import rustcraft.world.NpcUtil;
import rustcraft.world.WorldConfig;
import rustcraft.world.WorldRegistry;
import rustcraft.world.block.LockedCrateBlock;

import java.util.ArrayList;
import java.util.List;

public class LockedCrateTile extends LockableLootTileEntity implements ITickableTileEntity {
    private static final double BAR_RANGE_SQ = 48.0D * 48.0D;

    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private int hackLeft = -1;
    private int hackTotal = 2400;
    private int wavesSent;
    private long relockAt;
    private ServerBossInfo bar;

    public LockedCrateTile() {
        super(WorldRegistry.LOCKED_CRATE_TILE.get());
    }

    private boolean isOpen() {
        BlockState st = getBlockState();
        return st.hasProperty(LockedCrateBlock.OPEN) && st.get(LockedCrateBlock.OPEN);
    }

    private void setOpen(boolean open) {
        BlockState st = getBlockState();
        if (world != null && st.hasProperty(LockedCrateBlock.OPEN)) {
            world.setBlockState(pos, st.with(LockedCrateBlock.OPEN, open), 3);
        }
    }

    public void interact(ServerPlayerEntity player) {
        if (isOpen()) {
            NetworkHooks.openGui(player, this, pos);
            return;
        }
        if (hackLeft > 0) {
            player.sendStatusMessage(new TranslationTextComponent("rustcraft.world.locked_crate.hacking", (hackLeft + 19) / 20)
                    .mergeStyle(TextFormatting.YELLOW), true);
            return;
        }
        hackTotal = WorldConfig.HACK_SECONDS.get() * 20;
        hackLeft = hackTotal;
        wavesSent = 0;
        markDirty();
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.6F);
        for (ServerPlayerEntity p : nearbyPlayers()) {
            p.sendStatusMessage(new TranslationTextComponent("rustcraft.world.locked_crate.started", player.getDisplayName(), hackTotal / 20)
                    .mergeStyle(TextFormatting.RED), false);
        }
        sendWave();
    }

    private List<ServerPlayerEntity> nearbyPlayers() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        if (!(world instanceof ServerWorld)) return out;
        for (ServerPlayerEntity p : ((ServerWorld) world).getPlayers()) {
            if (p.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= BAR_RANGE_SQ) out.add(p);
        }
        return out;
    }

    private void sendWave() {
        if (!(world instanceof ServerWorld)) return;
        wavesSent++;
        ServerWorld sw = (ServerWorld) world;
        int n = 2 + wavesSent / 2;
        int spawned = NpcUtil.spawnAround(sw, pos, "rustcraft:scientist", n, 5, 14, null);
        if (wavesSent >= 3) {
            spawned += NpcUtil.spawnAround(sw, pos, "rustcraft:heavy_scientist", 1, 5, 12, null);
        }
        if (spawned > 0) {
            world.playSound(null, pos, SoundEvents.EVENT_RAID_HORN, SoundCategory.HOSTILE, 2.0F, 1.2F);
        }
    }

    @Override
    public void tick() {
        if (world == null || world.isRemote) return;
        if (hackLeft > 0) {
            hackLeft--;
            float frac = 1.0F - (float) hackLeft / (float) Math.max(1, hackTotal);
            if (wavesSent < 2 && frac >= 0.4F) sendWave();
            else if (wavesSent < 3 && frac >= 0.75F) sendWave();
            if (hackLeft % 20 == 0) {
                updateBar(frac);
                if (hackLeft % 100 == 0) {
                    world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT, SoundCategory.BLOCKS, 0.8F, 1.8F);
                }
            }
            if (hackLeft == 0) {
                hackLeft = -1;
                clearBossBar();
                setLootTable(WorldRegistry.loc("world/locked_crate"), world.rand.nextLong());
                relockAt = world.getGameTime() + WorldConfig.LOCKED_CRATE_RESET_MINUTES.get() * 1200L;
                setOpen(true);
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.6F);
                world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1.0F, 0.8F);
                for (ServerPlayerEntity p : nearbyPlayers()) {
                    p.sendStatusMessage(new TranslationTextComponent("rustcraft.world.locked_crate.done").mergeStyle(TextFormatting.GREEN), true);
                }
            }
            markDirty();
        } else if (relockAt > 0 && world.getGameTime() >= relockAt && isOpen()) {
            relockAt = 0;
            for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
            lootTable = null;
            setOpen(false);
            markDirty();
        }
    }

    private void updateBar(float frac) {
        if (bar == null) {
            bar = new ServerBossInfo(new TranslationTextComponent("rustcraft.world.locked_crate.bar", 0), BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10);
        }
        bar.setName(new TranslationTextComponent("rustcraft.world.locked_crate.bar", (hackLeft + 19) / 20));
        bar.setPercent(Math.max(0F, Math.min(1F, frac)));
        List<ServerPlayerEntity> near = nearbyPlayers();
        for (ServerPlayerEntity p : new ArrayList<>(bar.getPlayers())) {
            if (!near.contains(p)) bar.removePlayer(p);
        }
        for (ServerPlayerEntity p : near) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    public void clearBossBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    @Override
    public void remove() {
        clearBossBar();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        clearBossBar();
        super.onChunkUnloaded();
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
        return new TranslationTextComponent("block.rustcraft.locked_crate");
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
        if (!checkLootAndRead(nbt)) ItemStackHelper.loadAllItems(nbt, items);
        hackLeft = nbt.contains("HackLeft") ? nbt.getInt("HackLeft") : -1;
        hackTotal = nbt.contains("HackTotal") ? nbt.getInt("HackTotal") : 2400;
        wavesSent = nbt.getInt("Waves");
        relockAt = nbt.getLong("RelockAt");
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        if (!checkLootAndWrite(nbt)) ItemStackHelper.saveAllItems(nbt, items);
        nbt.putInt("HackLeft", hackLeft);
        nbt.putInt("HackTotal", hackTotal);
        nbt.putInt("Waves", wavesSent);
        nbt.putLong("RelockAt", relockAt);
        return nbt;
    }
}

package rustcraft.survival;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Состояние узла сохраняется с чанком. Выход считается накопительно: округления не создают ресурсы. */
public class ResourceNodeTile extends TileEntity implements ITickableTileEntity {
    private int hp = -1, awarded, totalYield;
    private long respawnAt;
    private boolean canopyReady;
    public ResourceNodeTile() { super(SurvivalModule.NODE_TILE.get()); }
    public ResourceNodeBlock.Kind kind() { return ((ResourceNodeBlock) getBlockState().getBlock()).kind; }
    public int maxHp() { return kind() == ResourceNodeBlock.Kind.TREE ? 300 : kind() == ResourceNodeBlock.Kind.HEMP ? 1 : 400; }
    public int hp() { return Math.max(0, hp < 0 ? maxHp() : hp); }
    public long respawnAt() { return respawnAt; }
    private Item resource() {
        switch (kind()) {
            case TREE: return SurvivalModule.WOOD.get();
            case STONE: return SurvivalModule.STONES.get();
            case METAL: return SurvivalModule.METAL_ORE.get();
            case SULFUR: return ForgeRegistries.ITEMS.getValue(new ResourceLocation("rustcraft", "sulfur"));
            default: return SurvivalModule.CLOTH.get();
        }
    }
    private int baseYield() {
        switch (kind()) { case TREE: return 200; case STONE: return 150; case METAL: return 40; case SULFUR: return 80; default: return 10; }
    }
    private void reset() {
        hp = maxHp(); awarded = 0; totalYield = Math.max(1, (int)Math.round(baseYield() * SurvivalModule.RATE.get()));
        respawnAt = 0;
        world.setBlockState(pos, getBlockState().with(ResourceNodeBlock.SPENT, false), 3);
        if (kind() == ResourceNodeBlock.Kind.TREE) ResourceNodes.createCanopy(world, pos);
        canopyReady = true;
        sync();
    }
    @Override public void tick() {
        if (world == null || world.isRemote) return;
        if (hp < 0) reset();
        if (!canopyReady && hp > 0 && kind() == ResourceNodeBlock.Kind.TREE) {
            ResourceNodes.createCanopy(world, pos); canopyReady = true;
        }
        if (hp == 0 && respawnAt > 0 && world.getGameTime() >= respawnAt) reset();
    }
    public void hit(ServerPlayerEntity player) {
        if (world == null || world.isRemote || player.isCreative() || player.isSpectator()) return;
        if (hp < 0) reset();
        if (hp <= 0) return;
        ItemStack held = player.getHeldItemMainhand();
        HarvestToolItem tool = held.getItem() instanceof HarvestToolItem ? (HarvestToolItem)held.getItem() : null;
        if (kind() != ResourceNodeBlock.Kind.HEMP && tool == null) {
            player.sendStatusMessage(new TranslationTextComponent("msg.rustcraft.harvest.tool_required"), true); return;
        }
        int damage = 1;
        if (tool != null) damage = tool.tier == 0 || (kind() == ResourceNodeBlock.Kind.TREE) == tool.axe ? tool.damage : 12;
        hp = Math.max(0, hp - damage);
        // Only the cumulative damage fraction is rewarded, including a smaller final hit.
        int shouldAward = (int)((long)totalYield * (maxHp() - hp) / maxHp());
        int amount = Math.max(0, shouldAward - awarded);
        awarded = shouldAward;
        give(player, resource(), amount);
        if (tool != null) held.damageItem(1, player, p -> p.sendBreakAnimation(Hand.MAIN_HAND));
        player.addExhaustion(0.025F);
        world.playSound(null, pos, kind() == ResourceNodeBlock.Kind.TREE ? SoundEvents.BLOCK_WOOD_HIT :
                kind() == ResourceNodeBlock.Kind.HEMP ? SoundEvents.BLOCK_GRASS_BREAK : SoundEvents.BLOCK_STONE_HIT,
                SoundCategory.BLOCKS, 0.8F, 0.85F + world.rand.nextFloat() * 0.3F);
        ((ServerWorld)world).spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, getBlockState()), pos.getX()+0.5, pos.getY()+0.8, pos.getZ()+0.5, 9, 0.3, 0.3, 0.3, 0.02);
        player.swingArm(Hand.MAIN_HAND);
        if (amount > 0) player.sendStatusMessage(new TranslationTextComponent("msg.rustcraft.harvest.gain", amount, new TranslationTextComponent(resource().getTranslationKey()), hp, maxHp()), true);
        if (hp == 0) {
            if (kind() == ResourceNodeBlock.Kind.METAL) give(player, ForgeRegistries.ITEMS.getValue(new ResourceLocation("rustcraft", "hqm")), 2);
            respawnAt = world.getGameTime() + SurvivalModule.RESPAWN.get() * 1200L;
            if (kind() == ResourceNodeBlock.Kind.TREE) ResourceNodes.removeCanopy(world, pos);
            world.setBlockState(pos, getBlockState().with(ResourceNodeBlock.SPENT, true), 3);
            world.playSound(null, pos, kind() == ResourceNodeBlock.Kind.TREE ? SoundEvents.BLOCK_WOOD_BREAK : SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.2F, 0.7F);
        }
        sync();
    }
    public static void give(ServerPlayerEntity player, Item item, int amount) {
        while (amount > 0) {
            int n = Math.min(new ItemStack(item).getMaxStackSize(), amount);
            ItemStack stack = new ItemStack(item, n);
            player.inventory.addItemStackToInventory(stack);
            if (!stack.isEmpty()) player.dropItem(stack, false);
            amount -= n;
        }
        player.inventory.markDirty();
    }
    private void sync() { markDirty(); if (world != null) world.notifyBlockUpdate(pos, getBlockState(), getBlockState(), 3); }
    @Override public void read(BlockState state, CompoundNBT n) {
        super.read(state, n);
        hp = n.contains("HP") ? n.getInt("HP") : -1;
        awarded = n.getInt("Awarded"); totalYield = n.getInt("Yield"); respawnAt = n.getLong("RespawnAt");
        canopyReady = n.getBoolean("CanopyReady");
    }
    @Override public CompoundNBT write(CompoundNBT n) {
        super.write(n); n.putInt("HP", hp); n.putInt("Awarded", awarded); n.putInt("Yield", totalYield);
        n.putLong("RespawnAt", respawnAt); n.putBoolean("CanopyReady", canopyReady); return n;
    }
    @Override public CompoundNBT getUpdateTag() { return write(new CompoundNBT()); }
    @Nullable @Override public SUpdateTileEntityPacket getUpdatePacket() { return new SUpdateTileEntityPacket(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) { read(getBlockState(), packet.getNbtCompound()); }
}

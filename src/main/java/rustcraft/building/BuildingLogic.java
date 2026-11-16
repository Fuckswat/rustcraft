package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/** Серверная логика построек: урон, разрушение, улучшение, починка, разбор. */
public final class BuildingLogic {
    private BuildingLogic() {}

    /** Нельзя чинить столько тиков после получения урона (как в Rust — 30 с). */
    public static final int REPAIR_COOLDOWN = 30 * 20;

    public static StructureTileEntity getStructure(World world, BlockPos mainPos) {
        TileEntity te = world.getTileEntity(mainPos);
        return te instanceof StructureTileEntity ? (StructureTileEntity) te : null;
    }

    public static Item itemFor(StructureKind kind) {
        switch (kind) {
            case WOOD_WALL: return BRegistry.WOOD_WALL_ITEM.get();
            case STONE_WALL: return BRegistry.STONE_WALL_ITEM.get();
            case METAL_WALL: return BRegistry.METAL_WALL_ITEM.get();
            case ARMORED_WALL: return BRegistry.ARMORED_WALL_ITEM.get();
            case WOOD_DOOR: return BRegistry.WOOD_DOOR_ITEM.get();
            case METAL_DOOR: return BRegistry.METAL_DOOR_ITEM.get();
            case GARAGE_DOOR: return BRegistry.GARAGE_DOOR_ITEM.get();
            default: return BRegistry.ARMORED_DOOR_ITEM.get();
        }
    }

    public static Block blockFor(StructureKind kind) {
        switch (kind) {
            case WOOD_WALL: return BRegistry.WOOD_WALL.get();
            case STONE_WALL: return BRegistry.STONE_WALL.get();
            case METAL_WALL: return BRegistry.METAL_WALL.get();
            default: return BRegistry.ARMORED_WALL.get();
        }
    }

    private static final String MSG_TIME_KEY = "rustcraft_building_msg";

    /** Сообщение в action-bar; HUD молотка не перебивает его ~1.5 с. */
    public static void notify(PlayerEntity player, net.minecraft.util.text.ITextComponent text) {
        player.sendStatusMessage(text, true);
        player.getPersistentData().putLong(MSG_TIME_KEY, player.world.getGameTime());
    }

    public static boolean recentlyNotified(PlayerEntity player) {
        return player.world.getGameTime() - player.getPersistentData().getLong(MSG_TIME_KEY) < 30;
    }

    private static boolean holdsHammer(PlayerEntity player) {
        return player.getHeldItemMainhand().getItem() instanceof HammerItem;
    }

    // урон

    /** Наносит урон постройке; при HP ≤ 0 — разрушает её с частицами. */
    public static void damage(World world, BlockPos mainPos, float amount) {
        if (amount <= 0) return;
        BlockState state = world.getBlockState(mainPos);
        if (!(state.getBlock() instanceof IRustStructure)) return;
        StructureTileEntity te = getStructure(world, mainPos);
        if (te == null) return;
        float hp = te.getHp() - amount;
        te.setLastDamaged(world.getGameTime());
        if (hp <= 0.5F) {
            destroy(world, mainPos, state);
        } else {
            te.setHp(hp);
            updateDamageState(world, mainPos, te);
            world.playSound(null, mainPos, hitSound(((IRustStructure) state.getBlock()).getKind()), SoundCategory.BLOCKS, 1.0F, 0.6F + world.rand.nextFloat() * 0.3F);
        }
    }

    public static void destroy(World world, BlockPos mainPos, BlockState state) {
        StructureKind kind = ((IRustStructure) state.getBlock()).getKind();
        BlockPos up = mainPos.up();
        BlockState upState = world.getBlockState(up);
        world.destroyBlock(mainPos, false);
        if (kind.door) {
            if (upState.getBlock() == state.getBlock()) {
                world.playEvent(2001, up, Block.getStateId(upState));
                if (world.getBlockState(up).getBlock() == state.getBlock()) {
                    world.removeBlock(up, false);
                }
            }
        }
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).spawnParticle(ParticleTypes.LARGE_SMOKE, mainPos.getX() + 0.5, mainPos.getY() + 0.5, mainPos.getZ() + 0.5,
                    12, 0.4, kind.door ? 0.9 : 0.4, 0.4, 0.02);
        }
        world.playSound(null, mainPos, breakSound(kind), SoundCategory.BLOCKS, 1.2F, 0.7F);
    }

    public static void updateDamageState(World world, BlockPos pos, StructureTileEntity te) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof RustWallBlock) {
            int stage = RustWallBlock.stageFor(te.getHp(), te.getMaxHp());
            if (state.get(RustWallBlock.DAMAGE) != stage) {
                world.setBlockState(pos, state.with(RustWallBlock.DAMAGE, stage), 3);
            }
        }
    }

    static SoundEvent hitSound(StructureKind kind) {
        switch (kind.mat) {
            case WOOD: return SoundEvents.BLOCK_WOOD_BREAK;
            case STONE: return SoundEvents.BLOCK_STONE_BREAK;
            default: return SoundEvents.BLOCK_ANVIL_LAND;
        }
    }

    static SoundEvent breakSound(StructureKind kind) {
        switch (kind.mat) {
            case WOOD: return SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR;
            case STONE: return SoundEvents.BLOCK_STONE_BREAK;
            default: return SoundEvents.BLOCK_ANVIL_DESTROY;
        }
    }

    // молоток

    /** Shift + ЛКМ молотком: разобрать и вернуть предмет (и замок) владельцу/авторизованному. */
    public static void tryDemolish(World world, BlockPos pos, PlayerEntity player) {
        if (!player.isSneaking() || !holdsHammer(player)) return;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IRustStructure)) return;
        IRustStructure s = (IRustStructure) state.getBlock();
        BlockPos main = s.getMainPos(state, pos);
        StructureTileEntity te = getStructure(world, main);
        if (te == null) return;
        if (!BuildPrivilege.canModify(player, world, main, te.getOwner())) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.no_permission").mergeStyle(TextFormatting.RED));
            return;
        }
        StructureKind kind = s.getKind();
        if (te.getHp() < te.getMaxHp() * 0.99F) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.demolish_damaged").mergeStyle(TextFormatting.RED));
            return;
        }
        if (!player.isCreative()) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(state.getBlock().asItem()));
            if (te.getLockType() == StructureTileEntity.LOCK_KEY) {
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(BRegistry.KEY_LOCK.get()));
            } else if (te.getLockType() == StructureTileEntity.LOCK_CODE) {
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(BRegistry.CODE_LOCK.get()));
            }
        }
        BlockState mainState = world.getBlockState(main);
        world.removeBlock(main, false);
        if (kind.door && world.getBlockState(main.up()).getBlock() == state.getBlock()) {
            world.removeBlock(main.up(), false);
        }
        world.playEvent(2001, main, Block.getStateId(mainState));
        BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.demolished").mergeStyle(TextFormatting.YELLOW));
    }

    /** ПКМ молотком: улучшить стену на уровень выше. */
    public static void tryUpgrade(World world, BlockPos pos, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IRustStructure)) return;
        IRustStructure s = (IRustStructure) state.getBlock();
        StructureKind kind = s.getKind();
        BlockPos main = s.getMainPos(state, pos);
        StructureTileEntity te = getStructure(world, main);
        if (te == null) return;
        if (kind.door) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.door_no_upgrade").mergeStyle(TextFormatting.GRAY));
            return;
        }
        StructureKind next = kind.upgrade();
        if (next == null) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.max_tier").mergeStyle(TextFormatting.GRAY));
            return;
        }
        if (!BuildPrivilege.canModify(player, world, main, te.getOwner())) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.no_permission").mergeStyle(TextFormatting.RED));
            return;
        }
        if (world.getGameTime() - te.getLastDamaged() < REPAIR_COOLDOWN) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.recently_damaged").mergeStyle(TextFormatting.RED));
            return;
        }
        List<Cost.Option> cost = Cost.upgradeTo(next.mat);
        IFormattableTextComponent missing = Cost.tryPay(player, cost, 1.0F);
        if (missing != null) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.need", missing).mergeStyle(TextFormatting.RED));
            return;
        }
        java.util.UUID owner = te.getOwner();
        String ownerName = te.getOwnerName();
        BlockState newState = state.getBlock() instanceof BuildingPieceBlock
                ? ((BuildingPieceBlock)state.getBlock()).upgraded(next,state) : blockFor(next).getDefaultState();
        world.setBlockState(main, newState, 3);
        StructureTileEntity nte = getStructure(world, main);
        if (nte != null) {
            nte.setOwner(owner, ownerName);
            nte.setHp(next.maxHp);
        }
        world.playEvent(2001, main, Block.getStateId(newState));
        world.playSound(null, main, upgradeSound(next), SoundCategory.BLOCKS, 0.8F, 1.0F);
        BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.upgraded",
                new TranslationTextComponent(next.translationKey())).mergeStyle(TextFormatting.GREEN));
    }

    static SoundEvent upgradeSound(StructureKind kind) {
        switch (kind.mat) {
            case STONE: return SoundEvents.BLOCK_STONE_PLACE;
            case METAL: return SoundEvents.BLOCK_ANVIL_PLACE;
            case ARMORED: return SoundEvents.BLOCK_ANVIL_USE;
            default: return SoundEvents.BLOCK_WOOD_PLACE;
        }
    }

    /** Shift + ПКМ молотком: починить до полного HP за ресурсы, пропорциональные урону. */
    public static void tryRepair(World world, BlockPos pos, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IRustStructure)) return;
        IRustStructure s = (IRustStructure) state.getBlock();
        BlockPos main = s.getMainPos(state, pos);
        StructureTileEntity te = getStructure(world, main);
        if (te == null) return;
        float missingHp = te.getMaxHp() - te.getHp();
        if (missingHp < 0.5F) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.full_hp").mergeStyle(TextFormatting.GRAY));
            return;
        }
        if (!BuildPrivilege.hasPrivilege(player, world, main)) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.building_blocked").mergeStyle(TextFormatting.RED));
            return;
        }
        long since = world.getGameTime() - te.getLastDamaged();
        if (since < REPAIR_COOLDOWN) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.repair_cooldown",
                    (REPAIR_COOLDOWN - since) / 20 + 1).mergeStyle(TextFormatting.RED));
            return;
        }
        IFormattableTextComponent missing = Cost.tryPay(player, Cost.repairFull(s.getKind().mat), missingHp / te.getMaxHp());
        if (missing != null) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.need", missing).mergeStyle(TextFormatting.RED));
            return;
        }
        te.setHp(te.getMaxHp());
        updateDamageState(world, main, te);
        world.playSound(null, main, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 0.8F, 1.1F);
        BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.repaired").mergeStyle(TextFormatting.GREEN));
    }

    /** Строка для action-bar при взгляде с молотком. */
    public static IFormattableTextComponent describe(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IRustStructure)) return null;
        IRustStructure s = (IRustStructure) state.getBlock();
        StructureTileEntity te = getStructure(world, s.getMainPos(state, pos));
        if (te == null) return null;
        int hp = (int) Math.ceil(te.getHp());
        int max = te.getMaxHp();
        float f = hp / (float) max;
        TextFormatting col = f > 0.66F ? TextFormatting.GREEN : f > 0.33F ? TextFormatting.YELLOW : TextFormatting.RED;
        IFormattableTextComponent text = new TranslationTextComponent(s.getKind().translationKey()).mergeStyle(TextFormatting.WHITE);
        text.append(new StringTextComponent("  ").append(new StringTextComponent(hp + "/" + max + " HP").mergeStyle(col)));
        if (!te.getOwnerName().isEmpty()) {
            text.append(new StringTextComponent("  ").append(new TranslationTextComponent("msg.rustcraft.building.owner", te.getOwnerName()).mergeStyle(TextFormatting.GRAY)));
        }
        if (s.getKind().door) {
            String lockKey = te.getLockType() == StructureTileEntity.LOCK_KEY ? "msg.rustcraft.building.lock_key"
                    : te.getLockType() == StructureTileEntity.LOCK_CODE ? "msg.rustcraft.building.lock_code"
                    : "msg.rustcraft.building.lock_none";
            text.append(new StringTextComponent("  ").append(new TranslationTextComponent(lockKey).mergeStyle(TextFormatting.AQUA)));
        }
        return text;
    }
}

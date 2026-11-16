package rustcraft.building;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.items.ItemHandlerHelper;
import rustcraft.building.network.BuildingNetwork;
import rustcraft.building.network.OpenCodeLockPacket;

/** Открытие дверей, замки, ввод кода. Всё на сервере. */
public final class DoorLogic {
    private DoorLogic() {}

    /** Удар током при неверном коде (death.attack.rustcraft_shock). */
    public static final DamageSource SHOCK = new DamageSource("rustcraft_shock").setDamageBypassesArmor();

    public static void interact(World world, BlockPos lowerPos, PlayerEntity player) {
        BlockState state = world.getBlockState(lowerPos);
        if (!(state.getBlock() instanceof RustDoorBlock)) return;
        RustDoorBlock door = (RustDoorBlock) state.getBlock();
        StructureTileEntity te = BuildingLogic.getStructure(world, lowerPos);
        if (te == null) {
            door.toggle(world, state, lowerPos, player);
            return;
        }
        switch (te.getLockType()) {
            case StructureTileEntity.LOCK_KEY:
                if (player.isCreative() || hasKey(player, te.getLockId())) {
                    door.toggle(world, state, lowerPos, player);
                } else {
                    locked(world, lowerPos, player, "msg.rustcraft.building.locked_key");
                }
                return;
            case StructureTileEntity.LOCK_CODE:
                boolean codeSet = !te.getCode().isEmpty();
                if (player.isSneaking() && player.getHeldItemMainhand().isEmpty()
                        && (te.isLockOwner(player) || (!codeSet && te.isCodeAuthorized(player)))) {
                    openScreen(player, lowerPos, true);
                    return;
                }
                if (!codeSet || te.isCodeAuthorized(player) || player.isCreative()) {
                    door.toggle(world, state, lowerPos, player);
                } else {
                    openScreen(player, lowerPos, false);
                }
                return;
            default:
                door.toggle(world, state, lowerPos, player);
        }
    }

    private static void locked(World world, BlockPos pos, PlayerEntity player, String key) {
        BuildingLogic.notify(player, new TranslationTextComponent(key).mergeStyle(TextFormatting.RED));
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.6F, 1.6F);
    }

    public static boolean hasKey(PlayerEntity player, int lockId) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s.getItem() instanceof DoorKeyItem && DoorKeyItem.getLockId(s) == lockId) return true;
        }
        return false;
    }

    public static void openScreen(PlayerEntity player, BlockPos pos, boolean setMode) {
        if (player instanceof ServerPlayerEntity) {
            BuildingNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                    new OpenCodeLockPacket(pos, setMode));
        }
    }

    public static void playDoorSound(World world, BlockPos pos, StructureKind kind, boolean open) {
        if (kind.mat == StructureKind.Mat.WOOD) {
            world.playSound(null, pos, open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, 0.9F + world.rand.nextFloat() * 0.1F);
        } else if (kind == StructureKind.GARAGE_DOOR) {
            world.playSound(null, pos, open ? SoundEvents.BLOCK_PISTON_EXTEND : SoundEvents.BLOCK_PISTON_CONTRACT,
                    SoundCategory.BLOCKS, 0.8F, 0.6F);
        } else {
            world.playSound(null, pos, open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, kind.mat == StructureKind.Mat.ARMORED ? 0.6F : 0.85F);
        }
    }

    /** Установка замка предметом key_lock / code_lock. */
    public static void installLock(World world, BlockPos clicked, PlayerEntity player, ItemStack stack, boolean code) {
        BlockState state = world.getBlockState(clicked);
        if (!(state.getBlock() instanceof RustDoorBlock)) return;
        BlockPos lower = ((RustDoorBlock) state.getBlock()).getMainPos(state, clicked);
        StructureTileEntity te = BuildingLogic.getStructure(world, lower);
        if (te == null) return;
        if (te.getLockType() != StructureTileEntity.LOCK_NONE) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.already_locked").mergeStyle(TextFormatting.RED));
            return;
        }
        if (!BuildPrivilege.canModify(player, world, lower, te.getOwner())) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.no_permission").mergeStyle(TextFormatting.RED));
            return;
        }
        if (!player.isCreative()) stack.shrink(1);
        if (code) {
            te.installCodeLock(player);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.code_lock_installed").mergeStyle(TextFormatting.GREEN));
            openScreen(player, lower, true);
        } else {
            int id = 1 + world.rand.nextInt(999999);
            te.installKeyLock(id, player);
            ItemHandlerHelper.giveItemToPlayer(player, DoorKeyItem.bound(id));
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.key_lock_installed", id).mergeStyle(TextFormatting.GREEN));
        }
        world.playSound(null, lower, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.0F, 1.2F);
    }

    /** Привязка пустого ключа к ключевому замку (только владелец замка). */
    public static void bindKey(World world, BlockPos clicked, PlayerEntity player, ItemStack stack) {
        BlockState state = world.getBlockState(clicked);
        if (!(state.getBlock() instanceof RustDoorBlock)) return;
        BlockPos lower = ((RustDoorBlock) state.getBlock()).getMainPos(state, clicked);
        StructureTileEntity te = BuildingLogic.getStructure(world, lower);
        if (te == null || te.getLockType() != StructureTileEntity.LOCK_KEY) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.no_key_lock").mergeStyle(TextFormatting.GRAY));
            return;
        }
        if (!te.isLockOwner(player) && !hasKey(player, te.getLockId()) && !player.isCreative()) {
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.no_permission").mergeStyle(TextFormatting.RED));
            return;
        }
        DoorKeyItem.setLockId(stack, te.getLockId());
        BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.key_bound", te.getLockId()).mergeStyle(TextFormatting.GREEN));
        world.playSound(null, lower, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 1.0F, 1.4F);
    }

    /** Код от клиента. setMode — смена кода владельцем, иначе попытка ввода. */
    public static void handleCode(ServerPlayerEntity player, BlockPos lower, String code, boolean setMode) {
        World world = player.world;
        if (!world.isBlockLoaded(lower)) return;
        if (player.getDistanceSq(lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5) > 64.0) return;
        if (code == null || !code.matches("\\d{4}")) return;
        BlockState state = world.getBlockState(lower);
        if (!(state.getBlock() instanceof RustDoorBlock)) return;
        StructureTileEntity te = BuildingLogic.getStructure(world, lower);
        if (te == null || te.getLockType() != StructureTileEntity.LOCK_CODE) return;
        if (setMode) {
            boolean allowed = te.isLockOwner(player) || (te.getCode().isEmpty() && te.isCodeAuthorized(player)) || player.isCreative();
            if (!allowed) return;
            te.setCode(code, player);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.code_set").mergeStyle(TextFormatting.GREEN));
            world.playSound(null, lower, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.5F);
            return;
        }
        if (code.equals(te.getCode())) {
            te.authorizeCode(player);
            world.playSound(null, lower, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 2.0F);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.code_ok").mergeStyle(TextFormatting.GREEN));
            if (!state.get(RustDoorBlock.OPEN)) {
                ((RustDoorBlock) state.getBlock()).toggle(world, state, lower, player);
            }
        } else {
            world.playSound(null, lower, SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 1.0F, 0.5F);
            world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), SoundEvents.ENTITY_PLAYER_HURT_ON_FIRE, SoundCategory.PLAYERS, 0.8F, 1.6F);
            player.attackEntityFrom(SHOCK, 3.0F);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.code_wrong").mergeStyle(TextFormatting.RED));
        }
    }
}

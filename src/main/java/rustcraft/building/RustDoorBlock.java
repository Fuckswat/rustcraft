package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * Дверь Rust-типа: открывается только рукой (без редстоуна), поддерживает замок
 * (ключевой/кодовый), имеет HP в нижней половине и ломается только взрывчаткой.
 */
public class RustDoorBlock extends DoorBlock implements IRustStructure {
    private final StructureKind kind;

    public RustDoorBlock(StructureKind kind) {
        super(Properties.create(Material.IRON)
                .hardnessAndResistance(-1.0F, 3600000.0F)
                .sound(RustWallBlock.soundFor(kind))
                .notSolid()
                .noDrops());
        this.kind = kind;
    }

    @Override
    public StructureKind getKind() {
        return kind;
    }

    @Override
    public BlockPos getMainPos(BlockState state, BlockPos pos) {
        if (state.getBlock() == this && state.get(HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        return pos;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new StructureTileEntity() : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state.with(OPEN, false).with(POWERED, false);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof PlayerEntity) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof StructureTileEntity) {
                ((StructureTileEntity) te).setOwner(placer.getUniqueID(), placer.getName().getString());
            }
        }
    }

    /** Редстоун двери Rust не открывает — иначе замок обходится рычагом. */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        Item held = player.getHeldItem(hand).getItem();
        // Молоток, замки и пустой ключ обрабатываются самими предметами.
        if (held instanceof HammerItem || held instanceof LockItem
                || (held instanceof DoorKeyItem && !DoorKeyItem.isBound(player.getHeldItem(hand)))) {
            return ActionResultType.PASS;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }
        if (!world.isRemote) {
            DoorLogic.interact(world, getMainPos(state, pos), player);
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockClicked(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isRemote) {
            BuildingLogic.tryDemolish(world, pos, player);
        }
        super.onBlockClicked(state, world, pos, player);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, IBlockReader world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public PushReaction getPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    /** Открыть/закрыть без проверок (проверки — в DoorLogic). */
    public void toggle(World world, BlockState state, BlockPos lowerPos, PlayerEntity player) {
        boolean open = !state.get(OPEN);
        world.setBlockState(lowerPos, state.with(OPEN, open), 10);
        BlockPos up = lowerPos.up();
        BlockState upState = world.getBlockState(up);
        if (upState.getBlock() == this) {
            world.setBlockState(up, upState.with(OPEN, open), 10);
        }
        DoorLogic.playDoorSound(world, lowerPos, kind, open);
    }
}

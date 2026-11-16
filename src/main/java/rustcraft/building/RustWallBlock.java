package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * Стена Rust-типа. Нельзя добыть руками (прочность -1), только взорвать рейдом
 * или разобрать молотком владельцу. Свойство damage (0-3) показывает трещины.
 */
public class RustWallBlock extends Block implements IRustStructure {
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 3);

    private final StructureKind kind;

    public RustWallBlock(StructureKind kind) {
        super(Properties.create(materialFor(kind))
                .hardnessAndResistance(-1.0F, 3600000.0F)
                .sound(soundFor(kind))
                .noDrops());
        this.kind = kind;
        setDefaultState(getStateContainer().getBaseState().with(DAMAGE, 0));
    }

    static Material materialFor(StructureKind kind) {
        switch (kind.mat) {
            case WOOD: return Material.WOOD;
            case STONE: return Material.ROCK;
            default: return Material.IRON;
        }
    }

    static SoundType soundFor(StructureKind kind) {
        switch (kind.mat) {
            case WOOD: return SoundType.WOOD;
            case STONE: return SoundType.STONE;
            case METAL: return SoundType.METAL;
            default: return SoundType.NETHERITE;
        }
    }

    @Override
    public StructureKind getKind() {
        return kind;
    }

    @Override
    public BlockPos getMainPos(BlockState state, BlockPos pos) {
        return pos;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(DAMAGE);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new StructureTileEntity();
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

    /** Shift + ЛКМ молотком — разобрать (владелец / авторизованный в шкафу). */
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

    public static int stageFor(float hp, int max) {
        float f = hp / (float) max;
        if (f > 0.75F) return 0;
        if (f > 0.5F) return 1;
        if (f > 0.25F) return 2;
        return 3;
    }
}

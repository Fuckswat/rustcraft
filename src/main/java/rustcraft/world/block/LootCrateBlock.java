package rustcraft.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import rustcraft.world.tile.LootCrateTile;

/** Ящик с лутом: при открытии наполняется из своей таблицы, перезаполняется через N минут. */
public class LootCrateBlock extends Block {
    private final ResourceLocation lootTable;

    public LootCrateBlock(ResourceLocation lootTable, Properties props) {
        super(props);
        this.lootTable = lootTable;
    }

    public ResourceLocation getCrateLootTable() {
        return lootTable;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new LootCrateTile();
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof LootCrateTile && player instanceof ServerPlayerEntity) {
                LootCrateTile crate = (LootCrateTile) te;
                crate.refillIfDue(lootTable);
                world.playSound(null, pos, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.7F, 0.8F + world.rand.nextFloat() * 0.3F);
                NetworkHooks.openGui((ServerPlayerEntity) player, crate, pos);
            }
        }
        return ActionResultType.func_233537_a_(world.isRemote);
    }

    @Override
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        // Содержимое ящика при разрушении пропадает (как в Rust) — ничего не выбрасываем.
        super.onReplaced(state, world, pos, newState, isMoving);
    }
}


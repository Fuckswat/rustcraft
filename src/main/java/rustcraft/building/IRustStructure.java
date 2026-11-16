package rustcraft.building;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Блок с прочностью (стена/дверь). HP хранится в StructureTileEntity по "главной" позиции. */
public interface IRustStructure {
    StructureKind getKind();

    /** Позиция, где лежит тайл-энтити (для двери — нижняя половина). */
    BlockPos getMainPos(BlockState state, BlockPos pos);
}

package rustcraft.building;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Список авторизованных игроков шкафа (UUID → последнее известное имя). */
public class ToolCupboardTileEntity extends TileEntity {
    private final Map<UUID, String> authorized = new LinkedHashMap<UUID, String>();

    public ToolCupboardTileEntity() {
        super(BRegistry.TOOL_CUPBOARD_TE.get());
    }

    public boolean isAuthorized(PlayerEntity player) {
        return authorized.containsKey(player.getUniqueID());
    }

    public void authorize(PlayerEntity player) {
        authorized.put(player.getUniqueID(), player.getName().getString());
        markDirty();
    }

    public void deauthorize(PlayerEntity player) {
        authorized.remove(player.getUniqueID());
        markDirty();
    }

    public void clear() {
        authorized.clear();
        markDirty();
    }

    public Collection<String> names() {
        return authorized.values();
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        authorized.clear();
        ListNBT list = nbt.getList("Authorized", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT e = list.getCompound(i);
            if (e.hasUniqueId("Id")) {
                authorized.put(e.getUniqueId("Id"), e.getString("Name"));
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        ListNBT list = new ListNBT();
        for (Map.Entry<UUID, String> en : authorized.entrySet()) {
            CompoundNBT e = new CompoundNBT();
            e.putUniqueId("Id", en.getKey());
            e.putString("Name", en.getValue());
            list.add(e);
        }
        nbt.put("Authorized", list);
        return nbt;
    }
}

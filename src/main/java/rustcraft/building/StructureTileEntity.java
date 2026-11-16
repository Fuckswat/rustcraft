package rustcraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.INBT;
import net.minecraft.tileentity.TileEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Состояние стены/двери: владелец, HP, время последнего урона, замок (только у нижней половины двери).
 * Всё хранится в NBT и переживает выгрузку чанков и перезапуск мира.
 */
public class StructureTileEntity extends TileEntity {
    public static final int LOCK_NONE = 0;
    public static final int LOCK_KEY = 1;
    public static final int LOCK_CODE = 2;

    private UUID owner;
    private String ownerName = "";
    private float hp = -1F;
    private long lastDamaged = -100000L;

    private int lockType = LOCK_NONE;
    private int lockId;
    private String code = "";
    private UUID lockOwner;
    private final Set<UUID> codeAuth = new HashSet<UUID>();

    public StructureTileEntity() {
        super(BRegistry.STRUCTURE_TE.get());
    }

    public StructureKind getKind() {
        Block b = getBlockState().getBlock();
        return b instanceof IRustStructure ? ((IRustStructure) b).getKind() : StructureKind.WOOD_WALL;
    }

    public int getMaxHp() {
        return getKind().maxHp;
    }

    public float getHp() {
        return hp < 0 ? getMaxHp() : hp;
    }

    public void setHp(float value) {
        this.hp = Math.max(0F, Math.min(getMaxHp(), value));
        markDirty();
    }

    public long getLastDamaged() {
        return lastDamaged;
    }

    public void setLastDamaged(long time) {
        this.lastDamaged = time;
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name == null ? "" : name;
        markDirty();
    }

    public boolean isOwner(PlayerEntity player) {
        return owner != null && owner.equals(player.getUniqueID());
    }

    // замок

    public int getLockType() {
        return lockType;
    }

    public int getLockId() {
        return lockId;
    }

    public String getCode() {
        return code;
    }

    public UUID getLockOwner() {
        return lockOwner;
    }

    public void installKeyLock(int id, PlayerEntity installer) {
        lockType = LOCK_KEY;
        lockId = id;
        lockOwner = installer.getUniqueID();
        code = "";
        codeAuth.clear();
        markDirty();
    }

    public void installCodeLock(PlayerEntity installer) {
        lockType = LOCK_CODE;
        lockId = 0;
        lockOwner = installer.getUniqueID();
        code = "";
        codeAuth.clear();
        codeAuth.add(installer.getUniqueID());
        markDirty();
    }

    public void removeLock() {
        lockType = LOCK_NONE;
        lockId = 0;
        lockOwner = null;
        code = "";
        codeAuth.clear();
        markDirty();
    }

    /** Смена кода сбрасывает всех авторизованных, кроме сменившего. */
    public void setCode(String newCode, PlayerEntity setter) {
        code = newCode;
        codeAuth.clear();
        codeAuth.add(setter.getUniqueID());
        markDirty();
    }

    public boolean isCodeAuthorized(PlayerEntity player) {
        return codeAuth.contains(player.getUniqueID());
    }

    public void authorizeCode(PlayerEntity player) {
        codeAuth.add(player.getUniqueID());
        markDirty();
    }

    public boolean isLockOwner(PlayerEntity player) {
        return lockOwner != null && lockOwner.equals(player.getUniqueID());
    }


    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        owner = nbt.hasUniqueId("Owner") ? nbt.getUniqueId("Owner") : null;
        ownerName = nbt.getString("OwnerName");
        hp = nbt.contains("Hp") ? nbt.getFloat("Hp") : -1F;
        lastDamaged = nbt.getLong("LastDamaged");
        lockType = nbt.getInt("LockType");
        lockId = nbt.getInt("LockId");
        code = nbt.getString("Code");
        lockOwner = nbt.hasUniqueId("LockOwner") ? nbt.getUniqueId("LockOwner") : null;
        codeAuth.clear();
        ListNBT list = nbt.getList("CodeAuth", 11);
        for (INBT tag : list) {
            codeAuth.add(NBTUtil.readUniqueId(tag));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        super.write(nbt);
        if (owner != null) nbt.putUniqueId("Owner", owner);
        nbt.putString("OwnerName", ownerName);
        if (hp >= 0) nbt.putFloat("Hp", hp);
        nbt.putLong("LastDamaged", lastDamaged);
        nbt.putInt("LockType", lockType);
        nbt.putInt("LockId", lockId);
        nbt.putString("Code", code);
        if (lockOwner != null) nbt.putUniqueId("LockOwner", lockOwner);
        ListNBT list = new ListNBT();
        for (UUID id : codeAuth) {
            list.add(NBTUtil.func_240626_a_(id));
        }
        nbt.put("CodeAuth", list);
        return nbt;
    }
}

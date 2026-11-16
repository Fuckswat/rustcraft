package rustcraft.progression;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.entity.player.ServerPlayerEntity;

/** World-owned UUID records survive player death, logout and dedicated-server restart. */
public class ProgressionData extends WorldSavedData {
    public static final class Record {
        public final Set<String> learned=new HashSet<>();
        public long kitReady;
    }
    private final Map<UUID,Record> records=new HashMap<>();
    public ProgressionData() {super("rustcraft_progression");}
    public static ProgressionData get(ServerPlayerEntity p) { return p.getServer().func_241755_D_().getSavedData().getOrCreate(ProgressionData::new,"rustcraft_progression"); }
    public Record record(UUID id) { return records.computeIfAbsent(id,k->new Record()); }
    @Override public void read(CompoundNBT nbt) {
        records.clear();ListNBT rows=nbt.getList("Players",10);
        for(int i=0;i<rows.size();i++) {CompoundNBT row=rows.getCompound(i);try {Record r=record(UUID.fromString(row.getString("UUID")));r.kitReady=row.getLong("KitReady");ListNBT learned=row.getList("Learned",8);for(int j=0;j<learned.size();j++)r.learned.add(learned.getString(j));}catch(IllegalArgumentException ignored) {}}
    }
    @Override public CompoundNBT write(CompoundNBT nbt) {
        ListNBT rows=new ListNBT();records.forEach((uuid,r)-> {CompoundNBT row=new CompoundNBT();row.putString("UUID",uuid.toString());row.putLong("KitReady",r.kitReady);ListNBT learned=new ListNBT();r.learned.stream().sorted().forEach(s->learned.add(StringNBT.valueOf(s)));row.put("Learned",learned);rows.add(row);});nbt.put("Players",rows);return nbt;
    }
}

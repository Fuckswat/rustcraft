package rustcraft.progression;

import java.util.*;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit server-owned costs. Higher tiers never use vanilla crafting recipes. */
public final class Catalog {
    private Catalog() {}
    public static final class Recipe {
        public final String id;
        public final int count, tier, research;
        public final LinkedHashMap<String,Integer> costs = new LinkedHashMap<>();
        Recipe(String id,int count,int tier,int research,Object... costs) {
            this.id=qualify(id); this.count=count; this.tier=tier; this.research=research;
            for(int i=0;i<costs.length;i+=2) this.costs.put(qualify((String)costs[i]),(Integer)costs[i+1]);
        }
        public ItemStack output() { return new ItemStack(item(id),count); }
    }
    public static final List<Recipe> RECIPES=new ArrayList<>();
    public static final Map<String,Recipe> BY_ID=new LinkedHashMap<>();
    private static void add(String id,int count,int tier,int research,Object... costs) {
        Recipe r=new Recipe(id,count,tier,research,costs); RECIPES.add(r); BY_ID.put(r.id,r);
    }
    public static String qualify(String id) { return id.contains(":")?id:"rustcraft:"+id; }
    public static Item item(String id) { Item i=ForgeRegistries.ITEMS.getValue(new ResourceLocation(qualify(id))); return i==null?Items.AIR:i; }
    static {
        add("minecraft:oak_planks",4,0,0,"wood",1);
        add("minecraft:stick",4,0,0,"wood",1);
        add("minecraft:crafting_table",1,0,0,"wood",4);
        add("minecraft:furnace",1,0,0,"stones",200);
        add("minecraft:chest",1,0,0,"wood",8);
        add("minecraft:white_wool",1,0,0,"cloth",4);
        add("minecraft:leather",1,0,0,"cloth",8,"animal_fat",2);
        add("minecraft:torch",4,0,0,"wood",1,"minecraft:charcoal",1);
        add("minecraft:campfire",1,0,0,"wood",10,"stones",5);
        add("minecraft:gunpowder",10,0,0,"sulfur",20,"minecraft:charcoal",30);
        add("wood_foundation",1,0,0,"wood",25); add("wood_floor",1,0,0,"wood",15);
        add("wood_roof",1,0,0,"wood",15); add("wood_stairs",1,0,0,"wood",20); add("wood_window",1,0,0,"wood",15);
        add("stone_foundation",1,0,0,"stones",50); add("stone_floor",1,0,0,"stones",30); add("stone_roof",1,0,0,"stones",30);
        add("rock",1,0,0,"stones",10);
        add("stone_hatchet",1,0,0,"wood",100,"stones",100);
        add("stone_pickaxe",1,0,0,"wood",100,"stones",100);
        add("minecraft:bow",1,0,0,"wood",50,"cloth",15);
        add("minecraft:arrow",8,0,0,"wood",20,"stones",10);
        add("bandage",2,0,0,"cloth",10);
        add("low_grade_fuel",4,0,0,"animal_fat",3,"cloth",1);
        add("cloth_hood",1,0,0,"cloth",8); add("cloth_shirt",1,0,0,"cloth",15);
        add("cloth_pants",1,0,0,"cloth",12); add("cloth_boots",1,0,0,"cloth",8);
        add("wood_helmet",1,0,0,"wood",40,"cloth",5);
        add("wood_chestplate",1,0,0,"wood",80,"cloth",10);
        add("wood_leggings",1,0,0,"wood",60,"cloth",8);
        add("hammer",1,0,0,"wood",40);
        add("wood_wall",4,0,0,"wood",100); add("stone_wall",4,0,0,"stones",200);
        add("wood_door",1,0,0,"wood",80); add("key_lock",1,0,0,"wood",30,"metal_fragments",10); add("door_key",1,0,0,"metal_fragments",3);
        add("tool_cupboard",1,0,0,"wood",300);
        add("research_table",1,0,0,"wood",100,"metal_fragments",100,"scrap",25);
        add("workbench_tier1",1,0,0,"wood",200,"metal_fragments",50,"scrap",50);
        add("workbench_tier2",1,1,0,"wood",300,"metal_fragments",150,"hqm",10,"scrap",250);
        add("workbench_tier3",1,2,0,"wood",400,"metal_fragments",250,"hqm",25,"scrap",500);
        add("refinery",1,1,0,"stones",250,"wood",150,"metal_fragments",100,"scrap",25);
        add("salvaged_axe",1,1,40,"wood",100,"metal_fragments",50);
        add("salvaged_pickaxe",1,1,40,"wood",100,"metal_fragments",50);
        add("metal_wall",4,1,0,"metal_fragments",100);
        add("metal_door",1,1,0,"metal_fragments",100);
        add("code_lock",1,1,40,"metal_fragments",50,"scrap",10);
        add("revolver",1,1,60,"metal_fragments",100,"cloth",15,"scrap",30);
        add("ammo_pistol",8,1,30,"metal_fragments",10,"minecraft:gunpowder",5);
        add("minecraft:crossbow",1,1,30,"wood",80,"cloth",20,"metal_fragments",20);
        add("ammo_shotgun",4,1,30,"metal_fragments",10,"minecraft:gunpowder",4);
        add("beancan_grenade",1,1,60,"metal_fragments",30,"minecraft:gunpowder",30);
        add("syringe",2,2,60,"metal_fragments",10,"cloth",8,"low_grade_fuel",5);
        add("medkit",1,2,100,"syringe",2,"bandage",4,"cloth",15);
        add("roadsign_helmet",1,2,60,"metal_fragments",75,"cloth",10);
        add("roadsign_jacket",1,2,60,"metal_fragments",125,"cloth",20);
        add("roadsign_kilt",1,2,60,"metal_fragments",100,"cloth",15);
        add("semi_pistol",1,2,100,"metal_fragments",150,"hqm",5,"scrap",40);
        add("thompson",1,2,125,"metal_fragments",200,"hqm",8,"wood",50,"scrap",50);
        add("pump_shotgun",1,2,100,"metal_fragments",200,"hqm",5,"wood",75);
        add("ammo_rifle",8,2,60,"metal_fragments",15,"minecraft:gunpowder",8);
        add("garage_door",1,2,60,"metal_fragments",200,"scrap",20);
        add("satchel_charge",1,2,125,"beancan_grenade",4,"cloth",15,"low_grade_fuel",10);
        add("rhib_item",1,2,125,"metal_fragments",500,"hqm",15,"wood",200,"scrap",150);
        add("mp5",1,3,250,"metal_fragments",250,"hqm",15,"scrap",75);
        add("ak47",1,3,250,"metal_fragments",300,"hqm",20,"wood",100,"scrap",100);
        add("bolt_rifle",1,3,250,"metal_fragments",250,"hqm",20,"wood",100,"scrap",100);
        add("metal_facemask",1,3,125,"hqm",15,"cloth",20);
        add("metal_chestplate",1,3,125,"hqm",25,"cloth",30);
        add("armored_wall",4,3,0,"hqm",20);
        add("armored_door",1,3,125,"hqm",15,"metal_fragments",150);
        add("rocket_launcher",1,3,250,"metal_fragments",300,"hqm",25,"scrap",125);
        add("ammo_rocket",1,3,250,"metal_fragments",100,"minecraft:gunpowder",150,"low_grade_fuel",15);
        add("timed_explosive",1,3,250,"metal_fragments",100,"minecraft:gunpowder",250,"low_grade_fuel",30,"cloth",10);
        add("minicopter_item",1,3,250,"metal_fragments",600,"hqm",40,"scrap",300);
        add("scrap_heli_item",1,3,500,"metal_fragments",900,"hqm",70,"scrap",500);
    }
    public static final String[] KIT_IDS={"hunter","builder","medic"};
    public static List<ItemStack> kit(String id) {
        List<ItemStack> out=new ArrayList<>();
        if("hunter".equals(id)) { give(out,"minecraft:bow",1);give(out,"minecraft:arrow",32);give(out,"cloth_hood",1);give(out,"cloth_shirt",1);give(out,"cloth_pants",1);give(out,"cloth_boots",1);give(out,"minecraft:cooked_beef",4);give(out,"bandage",3); }
        if("builder".equals(id)) { give(out,"stone_hatchet",1);give(out,"stone_pickaxe",1);give(out,"hammer",1);give(out,"wood",160);give(out,"stones",120);give(out,"minecraft:cooked_beef",3); }
        if("medic".equals(id)) { give(out,"bandage",8);give(out,"minecraft:cooked_beef",6);give(out,"cloth",15); }
        return out;
    }
    private static void give(List<ItemStack> out,String id,int n) { Item i=item(id); while(n>0) {int q=Math.min(n,i.getItemStackLimit(new ItemStack(i)));out.add(new ItemStack(i,q));n-=q;} }
}

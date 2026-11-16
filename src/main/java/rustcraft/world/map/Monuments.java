package rustcraft.world.map;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import rustcraft.world.WorldRegistry;
/** Original industrial layout: each structure is reachable on foot via ladders. */
public final class Monuments {
 private Monuments(){}
 public static Blueprint oilrig(BlockPos origin){
  Blueprint b=new Blueprint(origin);
  b.fill(-25,-17,-25,25,-10,25,Blocks.WATER);b.air(-25,-9,-25,25,27,25);
  b.fill(-25,-18,-25,25,-18,25,Blocks.SAND);
  for(int x:new int[]{-15,15})for(int z:new int[]{-15,15}){
   b.fill(x-1,-17,z-1,x+1,21,z+1,Blocks.IRON_BLOCK);b.pillarDown(x,-18,z,Blocks.STONE_BRICKS);
  }
  for(int y:new int[]{0,7,14}){
   b.fill(-17,y,-17,17,y,17,Blocks.SMOOTH_STONE);
   for(int x=-17;x<=17;x++){b.set(x,y,-17,x%4<2?Blocks.YELLOW_CONCRETE:Blocks.BLACK_CONCRETE);b.set(x,y,17,x%4<2?Blocks.YELLOW_CONCRETE:Blocks.BLACK_CONCRETE);}
   b.walls(-17,y+1,-17,17,y+1,17,Blocks.IRON_BARS);
   b.crateMilitary(12,y+1,12);b.barrel(-10,y+1,11);b.oilBarrel(-10,y+1,13);
   b.scientists(7,y,7,2,7);b.set(0,y+1,0,Blueprint.lantern(false));
  }
  b.room(3,14,-12,13,19,-4,Blocks.IRON_BLOCK.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.keycardDoor(7,15,-4,Direction.SOUTH,3,8,16,-4,Direction.SOUTH);b.crateElite(10,15,-10);
  b.lockedCrate(-6,15,-9,Direction.SOUTH);b.heavies(-7,14,5,2,6);
  b.fill(-8,21,-8,8,21,8,Blocks.GRAY_CONCRETE);
  b.fill(-4,21,-4,-3,21,4,Blocks.WHITE_CONCRETE);b.fill(3,21,-4,4,21,4,Blocks.WHITE_CONCRETE);b.fill(-2,21,0,2,21,0,Blocks.WHITE_CONCRETE);
  b.fill(-14,1,-2,-14,15,-2,Blocks.IRON_BLOCK);
  b.ladderColumn(-13,-2,1,15,Direction.EAST);
  b.fill(-10,14,0,-10,22,0,Blocks.IRON_BLOCK);b.ladderColumn(-9,0,15,22,Direction.EAST);
  // Crane with counterweight, boom and hanging chain.
  b.fill(12,15,-14,13,26,-13,Blocks.YELLOW_CONCRETE);b.fill(4,26,-14,18,26,-13,Blocks.YELLOW_CONCRETE);
  b.fill(18,23,-14,18,25,-14,Blocks.CHAIN);b.fill(3,25,-14,5,26,-13,Blocks.BLACK_CONCRETE);
  b.fill(-6,1,4,-1,4,7,Blocks.BLUE_CONCRETE);b.air(-5,2,5,-2,3,6);b.air(-1,2,5,-1,3,6);
  oilrigAccess(b);
  return b;
 }
 /** Shared by new construction and the small, explicit repair command for existing worlds. */
 public static void oilrigAccess(Blueprint b){
  b.air(-5,-9,-22,5,-9,-18);
  b.fill(-5,-10,-22,5,-10,-18,Blocks.OAK_PLANKS);
  b.fill(0,-10,-17,0,0,-17,Blocks.IRON_BLOCK);
  b.ladderColumn(0,-18,-9,0,Direction.NORTH);
  b.air(-1,1,-18,1,2,-17); // free headroom and a three-wide opening through the railing
  b.fill(-1,0,-17,1,0,-16,Blocks.SMOOTH_STONE);
 }
 public static Blueprint silo(BlockPos origin){
  Blueprint b=new Blueprint(origin);
  b.fill(-18,-35,-18,18,0,18,Blocks.STONE_BRICKS);b.air(-16,-33,-16,16,-1,16);
  b.fill(-16,-33,-16,16,-33,16,Blocks.SMOOTH_STONE);
  b.fill(-18,0,-18,18,0,18,Blocks.SMOOTH_STONE);
  b.walls(-17,1,-17,17,2,17,Blocks.IRON_BARS);b.air(-1,1,17,1,2,17);
  b.room(-6,0,9,6,5,16,Blocks.STONE_BRICKS.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.door(0,1,16,Blocks.OAK_DOOR,Direction.SOUTH,false);
  // Supported ladder from surface entrance to control floor.
  b.fill(-5,-32,10,-5,4,10,Blocks.IRON_BLOCK);
  b.fill(-16,-24,-16,16,-24,16,Blocks.SMOOTH_STONE);b.air(-4,-24,10,-4,-24,10);
  b.fill(-16,-14,-16,16,-14,16,Blocks.SMOOTH_STONE);b.air(-4,-14,10,-4,-14,10);
  // Open central rocket shaft through both bunker floors.
  b.air(-5,-33,-7,5,-1,3);b.walls(-6,-33,-8,6,-1,4,Blocks.IRON_BARS);
  b.fill(-1,-32,-3,1,-8,-1,Blocks.WHITE_CONCRETE);b.fill(-1,-7,-3,1,-6,-1,Blocks.RED_CONCRETE);b.set(0,-5,-2,Blocks.RED_CONCRETE);
  b.fill(-3,-32,-2,3,-28,-2,Blocks.WHITE_CONCRETE);b.fill(0,-32,-5,0,-28,1,Blocks.WHITE_CONCRETE);
  for(int y:new int[]{-33,-24,-14}){
   b.scientists(10,y,0,2,5);b.crateMilitary(-11,y+1,-12);b.set(12,y+1,12,Blueprint.lantern(false));
  }
  b.room(7,-33,-15,15,-28,-6,Blocks.IRON_BLOCK.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.keycardDoor(10,-32,-6,Direction.SOUTH,2,11,-31,-6,Direction.SOUTH);b.crateElite(12,-32,-12);
  b.room(-15,-24,-15,-8,-19,-7,Blocks.IRON_BLOCK.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.keycardDoor(-11,-23,-7,Direction.SOUTH,3,-10,-22,-7,Direction.SOUTH);b.lockedCrate(-12,-23,-12,Direction.SOUTH);
  b.heavies(10,-24,8,2,5);b.scientists(10,0,8,2,6);
  for(int x=-3;x<=3;x+=3){b.set(x,-32,13,Blocks.REDSTONE_LAMP);b.set(x,-32,12,Blocks.LEVER);}
  b.fill(12,0,12,16,8,16,Blocks.STONE_BRICKS);b.air(13,1,13,15,7,15);b.fill(11,8,11,17,8,17,Blocks.SMOOTH_STONE);
  b.fill(12,1,11,12,9,11,Blocks.IRON_BLOCK);b.ladderColumn(11,11,1,9,Direction.WEST);
  // Place the continuous shaft LAST: floor carving must never erase ladder segments.
  b.ladderColumn(-4,10,-32,1,Direction.EAST);
  return b;
 }
 public static Blueprint small(BlockPos origin,boolean gas){
  Blueprint b=new Blueprint(origin);b.fill(-10,-1,-9,10,0,9,Blocks.SMOOTH_STONE);b.air(-10,1,-9,10,9,9);
  b.room(-8,0,-7,8,5,3,(gas?Blocks.WHITE_CONCRETE:Blocks.BRICKS).getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.RED_CONCRETE.getDefaultState());
  b.door(0,1,3,Blocks.OAK_DOOR,Direction.SOUTH,false);b.fill(-6,2,3,-3,3,3,Blocks.GLASS_PANE);b.fill(3,2,3,6,3,3,Blocks.GLASS_PANE);
  b.crateBasic(-5,1,-4);b.crateMilitary(5,1,-4);b.barrel(-8,1,6);b.oilBarrel(8,1,6);b.set(0,4,0,Blocks.GLOWSTONE);
  if(gas){b.fill(-8,5,4,8,5,9,Blocks.WHITE_CONCRETE);b.fill(-7,1,8,-7,4,8,Blocks.IRON_BLOCK);b.fill(7,1,8,7,4,8,Blocks.IRON_BLOCK);b.set(-3,1,7,Blocks.RED_CONCRETE);b.set(3,1,7,Blocks.RED_CONCRETE);}
  else{for(int x:new int[]{-5,5})b.fill(x,1,-1,x,2,1,Blocks.BOOKSHELF);}
  return b;
 }
 public static Blueprint tower(BlockPos origin){
  Blueprint b=new Blueprint(origin);b.fill(-6,-1,-6,6,0,6,Blocks.SMOOTH_STONE);b.air(-6,1,-6,6,30,6);
  for(int x:new int[]{-3,3})for(int z:new int[]{-3,3})b.fill(x,1,z,x,23,z,Blocks.IRON_BARS);
  b.fill(-4,12,-4,4,12,4,Blocks.IRON_BLOCK);b.fill(-4,24,-4,4,24,4,Blocks.IRON_BLOCK);
  b.fill(0,1,0,0,29,0,Blocks.IRON_BLOCK);b.ladderColumn(0,-1,1,25,Direction.NORTH);
  b.set(0,30,0,Blocks.REDSTONE_BLOCK);b.crateMilitary(3,13,3);b.crateElite(3,25,3);b.barrel(5,1,4);return b;
 }
 public static Blueprint harbor(BlockPos origin){
  Blueprint b=new Blueprint(origin);
  b.fill(-15,-1,-17,17,0,17,Blocks.SMOOTH_STONE);b.air(-15,1,-17,25,14,17);
  b.fill(17,-1,-5,31,-1,5,Blocks.OAK_PLANKS);
  for(int x:new int[]{17,24,31})for(int z:new int[]{-5,5})b.pillarDown(x,-2,z,Blocks.OAK_LOG);
  b.room(-13,0,-15,0,6,-5,Blocks.BRICKS.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.door(-6,1,-5,Blocks.OAK_DOOR,Direction.SOUTH,false);b.crateBasic(-10,1,-11);b.crateMilitary(-3,1,-11);
  b.fill(8,1,-13,9,12,-12,Blocks.YELLOW_CONCRETE);b.fill(3,12,-13,23,12,-12,Blocks.YELLOW_CONCRETE);
  b.fill(23,7,-13,23,11,-13,Blocks.CHAIN);b.barrel(4,1,12);b.oilBarrel(6,1,12);
  // Dock level y63, one block above sea level, with a clear boat approach from the east.
  b.air(18,0,-5,31,2,5);
  return b;
 }
 public static Blueprint refinery(BlockPos origin){
  Blueprint b=new Blueprint(origin);
  b.fill(-13,-1,-12,13,0,12,Blocks.SMOOTH_STONE);b.air(-13,1,-12,13,17,12);
  b.room(-12,0,-11,-2,6,-3,Blocks.BRICKS.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState());
  b.door(-7,1,-3,Blocks.OAK_DOOR,Direction.SOUTH,false);b.crateBasic(-9,1,-8);
  for(int x:new int[]{4,10}){
   b.fill(x-1,1,-8,x+1,8,-6,Blocks.LIGHT_GRAY_CONCRETE);
   b.fill(x-1,9,-8,x+1,9,-6,Blocks.IRON_BLOCK);
   b.fill(x,1,-5,x,1,5,Blocks.IRON_BLOCK);
  }
  b.set(-5,1,5,WorldRegistry.REFINERY.get());b.set(0,1,5,WorldRegistry.REFINERY.get());
  b.oilBarrel(7,1,8);b.oilBarrel(10,1,8);b.barrel(-10,1,8);
  b.fill(11,1,0,12,15,1,Blocks.BRICKS);b.set(11,16,0,Blocks.CAMPFIRE);
  return b;
 }
 public static Blueprint roadLoot(BlockPos origin){
  Blueprint b=new Blueprint(origin);
  for(int x:new int[]{-32,-12,18,76})b.barrel(x,MapLayout.surfaceHeight(x,4)+1-origin.getY(),4);
  for(int z:new int[]{-24,22})b.barrel(54,MapLayout.surfaceHeight(54,z)+1-origin.getY(),z);
  return b;
 }
}

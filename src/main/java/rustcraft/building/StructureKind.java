package rustcraft.building;

/** Все разрушаемые рейдом постройки модуля: стены и двери с их прочностью. */
public enum StructureKind {
    WOOD_WALL("wood_wall", 250, Mat.WOOD, false),
    STONE_WALL("stone_wall", 500, Mat.STONE, false),
    METAL_WALL("metal_wall", 1000, Mat.METAL, false),
    ARMORED_WALL("armored_wall", 2000, Mat.ARMORED, false),
    WOOD_DOOR("wood_door", 200, Mat.WOOD, true),
    METAL_DOOR("metal_door", 250, Mat.METAL, true),
    GARAGE_DOOR("garage_door", 600, Mat.METAL, true),
    ARMORED_DOOR("armored_door", 1000, Mat.ARMORED, true);

    public enum Mat { WOOD, STONE, METAL, ARMORED }

    public final String id;
    public final int maxHp;
    public final Mat mat;
    public final boolean door;

    StructureKind(String id, int maxHp, Mat mat, boolean door) {
        this.id = id;
        this.maxHp = maxHp;
        this.mat = mat;
        this.door = door;
    }

    /** Следующий уровень стены при улучшении молотком, или null. */
    public StructureKind upgrade() {
        switch (this) {
            case WOOD_WALL: return STONE_WALL;
            case STONE_WALL: return METAL_WALL;
            case METAL_WALL: return ARMORED_WALL;
            default: return null;
        }
    }

    public String translationKey() {
        return "block.rustcraft." + id;
    }
}

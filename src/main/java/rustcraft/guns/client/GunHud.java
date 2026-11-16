package rustcraft.guns.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rustcraft.guns.GunItem;
import rustcraft.guns.GunStats;
import rustcraft.guns.GunsItems;
import rustcraft.guns.GunsModule;

/** HUD: здоровье в шкале Rust 0–100, кровотечение, магазин/запас патронов, перезарядка, хитмаркер. */
public final class GunHud {
    private GunHud() {}

    static int hitMarker;
    static boolean hitHead;
    private static int reserveCache;
    private static int reserveTimer;
    private static Item reserveItem;

    static void tick() {
        if (hitMarker > 0) hitMarker--;
        if (reserveTimer > 0) reserveTimer--;
    }

    public static void onHit(boolean head) {
        hitMarker = 7;
        hitHead = head;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.playerController == null) return;
        MatrixStack ms = event.getMatrixStack();
        FontRenderer font = mc.fontRenderer;
        int w = event.getWindow().getScaledWidth();
        int h = event.getWindow().getScaledHeight();

        // ---- здоровье 0..100 ----
        int panelX = w - 94;
        int y = h - 18;
        if (mc.playerController.shouldDrawHUD()) {
            float health = p.getHealth();
            int hp = Math.max(0, Math.round(health * 5.0F));
            float frac = Math.max(0.0F, Math.min(1.0F, health / Math.max(1.0F, p.getMaxHealth())));
            boolean bleeding = p.isPotionActive(GunsModule.BLEEDING.get());
            AbstractGui.fill(ms, panelX - 1, y - 1, panelX + 91, y + 13, 0xB0101010);
            int barColor = bleeding ? 0xFFB3261E : (frac > 0.35F ? 0xFF6E9E3A : 0xFFC9822B);
            AbstractGui.fill(ms, panelX + 1, y + 1, panelX + 1 + Math.round(88 * frac), y + 11, barColor);
            AbstractGui.fill(ms, panelX + 1, y + 1, panelX + 1 + Math.round(88 * frac), y + 2, 0x40FFFFFF);
            String label = I18n.format("hud.rustcraft.guns.health");
            font.drawStringWithShadow(ms, label, panelX + 4, y + 2, 0xFFFFFF);
            String num = String.valueOf(hp);
            font.drawStringWithShadow(ms, num, panelX + 88 - font.getStringWidth(num), y + 2, 0xFFFFFF);
            if (bleeding) {
                String b = I18n.format("hud.rustcraft.guns.bleeding");
                boolean blink = (p.ticksExisted / 10) % 2 == 0;
                font.drawStringWithShadow(ms, b, panelX + 90 - font.getStringWidth(b), y - 11, blink ? 0xFF5050 : 0xC02020);
            }
            y -= 14;
        }

        // ---- оружие ----
        ItemStack stack = p.getHeldItemMainhand();
        if (stack.getItem() instanceof GunItem) {
            GunStats st = ((GunItem) stack.getItem()).getStats();
            int ammo = GunItem.getAmmo(stack);
            int reserve = reserve(p, GunsItems.ammoItemFor(st));
            int top = y - 24;
            AbstractGui.fill(ms, panelX - 1, top, panelX + 91, top + 22, 0xB0101010);
            String name = stack.getDisplayName().getString();
            font.drawStringWithShadow(ms, name, panelX + 3, top + 2, 0xC8C8C8);
            int col = ammo == 0 ? 0xFF4040 : (ammo <= st.magSize / 4 ? 0xFFB040 : 0xFFFFFF);
            font.drawStringWithShadow(ms, ammo + " / " + st.magSize, panelX + 3, top + 11, col);
            String r = (p.abilities.isCreativeMode ? "∞" : String.valueOf(reserve));
            font.drawStringWithShadow(ms, r, panelX + 88 - font.getStringWidth(r), top + 11, 0x9A9A9A);

            // перезарядка — полоска под прицелом
            long end = GunItem.getReloadEnd(stack);
            if (end > 0 && mc.world != null) {
                long start = GunItem.getReloadStart(stack);
                float now = mc.world.getGameTime() + event.getPartialTicks();
                float prog = end > start ? Math.max(0.0F, Math.min(1.0F, (now - start) / (float) (end - start))) : 1.0F;
                int cx = w / 2, cy = h / 2;
                AbstractGui.fill(ms, cx - 21, cy + 11, cx + 21, cy + 16, 0xB0101010);
                AbstractGui.fill(ms, cx - 20, cy + 12, cx - 20 + Math.round(40 * prog), cy + 15, 0xFFE0E0E0);
                String rl = I18n.format("hud.rustcraft.guns.reloading");
                font.drawStringWithShadow(ms, rl, cx - font.getStringWidth(rl) / 2.0F, cy + 18, 0xE0E0E0);
            }
        }

        // ---- хитмаркер ----
        if (hitMarker > 0) {
            int cx = w / 2, cy = h / 2;
            int c = hitHead ? 0xFFFF3030 : 0xFFFFFFFF;
            for (int i = 3; i <= 6; i++) {
                AbstractGui.fill(ms, cx - i - 1, cy - i - 1, cx - i + 1, cy - i + 1, c);
                AbstractGui.fill(ms, cx + i - 1, cy - i - 1, cx + i + 1, cy - i + 1, c);
                AbstractGui.fill(ms, cx - i - 1, cy + i - 1, cx - i + 1, cy + i + 1, c);
                AbstractGui.fill(ms, cx + i - 1, cy + i - 1, cx + i + 1, cy + i + 1, c);
            }
        }
    }

    private static int reserve(ClientPlayerEntity p, Item ammo) {
        if (reserveTimer > 0 && reserveItem == ammo) return reserveCache;
        int c = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == ammo) c += s.getCount();
        }
        reserveCache = c;
        reserveItem = ammo;
        reserveTimer = 5;
        return c;
    }
}

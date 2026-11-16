package rustcraft.building.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;
import rustcraft.building.network.BuildingNetwork;
import rustcraft.building.network.SubmitCodePacket;

/** Клавиатура кодового замка: 0-9, сброс, ввод. Работает и с клавиатуры. */
public class CodeLockScreen extends Screen {
    private static final int PANEL_W = 120;
    private static final int PANEL_H = 170;

    private final BlockPos pos;
    private final boolean setMode;
    private String entered = "";

    public CodeLockScreen(BlockPos pos, boolean setMode) {
        super(new TranslationTextComponent(setMode ? "gui.rustcraft.code_lock.set" : "gui.rustcraft.code_lock.enter"));
        this.pos = pos;
        this.setMode = setMode;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int bw = 30, bh = 20, gap = 6;
        int gx = left + (PANEL_W - (bw * 3 + gap * 2)) / 2;
        int gy = top + 48;
        for (int i = 1; i <= 9; i++) {
            final int digit = i;
            int col = (i - 1) % 3;
            int row = (i - 1) / 3;
            addButton(new Button(gx + col * (bw + gap), gy + row * (bh + gap), bw, bh,
                    new StringTextComponent(String.valueOf(i)), b -> press(digit)));
        }
        int row4 = gy + 3 * (bh + gap);
        addButton(new Button(gx, row4, bw, bh, new StringTextComponent("C"), b -> entered = ""));
        addButton(new Button(gx + bw + gap, row4, bw, bh, new StringTextComponent("0"), b -> press(0)));
        addButton(new Button(gx + 2 * (bw + gap), row4, bw, bh, new StringTextComponent("OK"), b -> submit()));
    }

    private void press(int digit) {
        if (entered.length() < 4) {
            entered += digit;
        }
        if (entered.length() == 4 && !setMode) {
            submit();
        }
    }

    private void submit() {
        if (entered.length() != 4) return;
        BuildingNetwork.CHANNEL.sendToServer(new SubmitCodePacket(pos, entered, setMode));
        closeScreen();
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c >= '0' && c <= '9') {
            press(c - '0');
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && !entered.isEmpty()) {
            entered = entered.substring(0, entered.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partial) {
        renderBackground(ms);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        // корпус
        fill(ms, left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, 0xFF1A1A1A);
        fill(ms, left, top, left + PANEL_W, top + PANEL_H, 0xFF3A3D40);
        // дисплей
        fill(ms, left + 10, top + 20, left + PANEL_W - 10, top + 40, 0xFF0B1F0B);
        drawCenteredString(ms, font, title, width / 2, top + 7, 0xFFE0E0E0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < entered.length()) {
                sb.append(setMode ? entered.charAt(i) : '*');
            } else {
                sb.append('_');
            }
            if (i < 3) sb.append(' ');
        }
        drawCenteredString(ms, font, sb.toString(), width / 2, top + 26, setMode ? 0xFFFFD040 : 0xFF40FF40);
        super.render(ms, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

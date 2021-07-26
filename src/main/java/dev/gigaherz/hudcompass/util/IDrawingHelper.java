package dev.gigaherz.hudcompass.util;

import net.minecraft.client.gui.Font;

public interface IDrawingHelper
{
    Font getFont();
    void drawString(String s, int x, int y, int color);
    void drawString(String s, int x, int y, int color, TextAlignment horizontalAlign);
    void drawString(String s, int x, int y, int color, TextAlignment horizontalAlign, TextAlignment verticalAlign);

    enum TextAlignment
    {
        START,  // left/top
        MIDDLE, // center
        END     // right/bottom
    }
}

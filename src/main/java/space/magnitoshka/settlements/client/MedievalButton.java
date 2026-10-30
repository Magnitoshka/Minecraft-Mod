package space.magnitoshka.settlements.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Timber-and-brass button drawn with native GUI primitives, without external textures. */
final class MedievalButton extends Button {
    private final BooleanSupplier selected;

    MedievalButton(int x, int y, int width, int height, Component label, Runnable action) {
        this(x, y, width, height, label, action, () -> false);
    }

    MedievalButton(int x, int y, int width, int height, Component label, Runnable action, BooleanSupplier selected) {
        super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x=getX(), y=getY(), w=getWidth(), h=getHeight();
        boolean highlighted=active && (isHoveredOrFocused() || selected.getAsBoolean());
        int edge=highlighted?0xFFE4C176:0xFF9B7846;
        int timber=active?(highlighted?0xFF765132:0xFF513825):0xFF413A30;
        graphics.fill(x+1,y+1,x+w+1,y+h+1,0x66000000);
        graphics.fill(x,y,x+w,y+h,0xFF211A14);
        graphics.fill(x+1,y+1,x+w-1,y+h-1,edge);
        graphics.fill(x+2,y+2,x+w-2,y+h-2,timber);
        graphics.fill(x+3,y+3,x+w-3,y+4,highlighted?0xFF9C7348:0xFF735035);
        graphics.fill(x+3,y+h-4,x+w-3,y+h-3,0xFF382719);
        if(w>40) {
            graphics.fill(x+4,y+h/2,x+5,y+h/2+1,edge);
            graphics.fill(x+w-5,y+h/2,x+w-4,y+h/2+1,edge);
        }
        var font=Minecraft.getInstance().font;
        String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,w-(w>40?14:4)));
        graphics.text(font,label,x+(w-font.width(label))/2,y+(h-8)/2,active?0xFFF6E6BB:0xFFB2A287,false);
    }
}

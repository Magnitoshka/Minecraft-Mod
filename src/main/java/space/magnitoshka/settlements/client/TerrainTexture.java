package space.magnitoshka.settlements.client;

import java.util.Arrays;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import space.magnitoshka.settlements.TerrainMap;

/** A survey is uploaded once. Panning/zooming submit one textured quad, never cell geometry. */
final class TerrainTexture implements AutoCloseable {
    private static final Identifier ID=Identifier.fromNamespaceAndPath("smartsettlements","terrain_map");
    private TerrainMap.Survey source;
    private DynamicTexture texture;
    void draw(GuiGraphicsExtractor graphics,TerrainMap.Survey survey,int x0,int y0,int x1,int y1) {
        update(survey);
        graphics.blit(ID,x0,y0,x1,y1,0f,1f,0f,1f);
    }
    private void update(TerrainMap.Survey survey) {
        if(source==survey)return;
        if(source!=null&&source.size()==survey.size()&&Arrays.equals(source.colors(),survey.colors())&&Arrays.equals(source.flags(),survey.flags())) {
            source=survey;return;
        }
        if(texture==null||source.size()!=survey.size()) {
            close();
            var image=new NativeImage(survey.size(),survey.size(),false);
            paint(image,survey);
            texture=new DynamicTexture(()->"Settlement terrain map",image);
            Minecraft.getInstance().getTextureManager().register(ID,texture);
        } else {
            paint(texture.getPixels(),survey);texture.upload();
        }
        source=survey;
    }
    private static void paint(NativeImage image,TerrainMap.Survey survey) {
        for(int z=0;z<survey.size();z++)for(int x=0;x<survey.size();x++) {
            int index=z*survey.size()+x;
            int color=survey.kind(index)==TerrainMap.UNKNOWN?(((x+z)&3)==0?0xFF756F64:0xFF8A8273):survey.colors()[index];
            image.setPixel(x,z,color);
        }
    }
    @Override public void close() {
        if(texture!=null)Minecraft.getInstance().getTextureManager().release(ID);
        texture=null;source=null;
    }
}

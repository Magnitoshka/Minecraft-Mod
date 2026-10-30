package space.magnitoshka.settlements.client;

import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Personal offline skins: vanilla faces, shaded linen tunics, trousers and leather belts. */
public final class CitizenSkins {
    private static final String[] FACES={"steve","alex","ari","efe","kai","makena","noor","sunny","zuri"};
    private static final Map<UUID,Identifier> CACHE=new HashMap<>();
    public static Identifier texture(UUID id) {return CACHE.computeIfAbsent(id,CitizenSkins::create);}
    private static Identifier create(UUID id) {
        var random=new Random(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),19));
        var base=Identifier.withDefaultNamespace("textures/entity/player/wide/"+FACES[random.nextInt(FACES.length)]+".png");
        NativeImage image=null;
        try(var stream=Minecraft.getInstance().getResourceManager().open(base)) {
            image=NativeImage.read(stream);
            int tunic=color(random,55,170),trousers=color(random,30,95);
            // Preserve all face/hair/skin pixels. Shade only UV islands occupied by clothes.
            cloth(image,16,20,24,12,tunic);
            cloth(image,40,20,16,4,tunic);
            cloth(image,32,52,16,4,tunic);
            cloth(image,0,20,16,12,trousers);
            cloth(image,16,52,16,12,trousers);
            for(int x=16;x<40;x++)for(int y=28;y<=29;y++)image.setPixel(x,y,0xFF533A25);
            // Distinct embroidery, stable even for residents sharing the same face and tunic family.
            int stitch=0xFF000000|((int)id.getLeastSignificantBits()&0xFFFFFF);
            for(int y=21;y<27;y++)image.setPixel(24,y,(y&1)==0?stitch:0xFFD4B67A);
            var key=Identifier.fromNamespaceAndPath("smartsettlements","citizen/"+id);
            Minecraft.getInstance().getTextureManager().register(key,new DynamicTexture(()->"Settlement citizen "+id,image));
            image=null; // TextureManager now owns native/GPU memory.
            return key;
        } catch(Exception failure) {
            if(image!=null)image.close();
            com.mojang.logging.LogUtils.getLogger().warn("Could not create citizen skin {}",id,failure);
            return base;
        }
    }
    private static int color(Random r,int min,int max) {
        return 0xFF000000|(min+r.nextInt(max-min+1))<<16|(min+r.nextInt(max-min+1))<<8|min+r.nextInt(max-min+1);
    }
    private static void cloth(NativeImage image,int x0,int y0,int w,int h,int color) {
        for(int y=y0;y<y0+h;y++)for(int x=x0;x<x0+w;x++) {
            int original=image.getPixel(x,y);if((original>>>24)==0)continue;
            float shade=.65f+(((original>>16)&255)+((original>>8)&255)+(original&255))/2185f;
            int red=Math.min(255,(int)(((color>>16)&255)*shade));
            int green=Math.min(255,(int)(((color>>8)&255)*shade));
            int blue=Math.min(255,(int)((color&255)*shade));
            image.setPixel(x,y,0xFF000000|red<<16|green<<8|blue);
        }
    }
    public static void release(UUID id) {
        var texture=CACHE.remove(id);
        if(texture!=null&&texture.getNamespace().equals("smartsettlements"))Minecraft.getInstance().getTextureManager().release(texture);
    }
    public static void clear() {
        var textures=Minecraft.getInstance().getTextureManager();
        CACHE.values().stream().filter(id->id.getNamespace().equals("smartsettlements")).forEach(textures::release);
        CACHE.clear();
    }
    private CitizenSkins() {}
}

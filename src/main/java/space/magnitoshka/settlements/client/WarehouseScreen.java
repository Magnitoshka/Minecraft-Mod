package space.magnitoshka.settlements.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import space.magnitoshka.settlements.*;

public final class WarehouseScreen extends AbstractContainerScreen<WarehouseMenu> {
    private SettlementNetwork.Snapshot snapshot;
    private final MedievalButton[] resources=new MedievalButton[3];
    private int page;
    private Component feedback;
    private boolean feedbackSuccess;
    private long feedbackUntil;
    public WarehouseScreen(WarehouseMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,300,216);inventoryLabelX=12;inventoryLabelY=112;
    }
    private static Component tr(String key,Object... args) {return Component.translatable("warehouse.smartsettlements."+key,args);}
    public void update(SettlementNetwork.Snapshot next) {
        if(next.settlement()==null||!next.settlement().id.equals(menu.settlementId))return;
        snapshot=next;page=Math.min(page,Math.max(0,(keys().size()-1)/3));updateButtons();
    }
    public void notice(SettlementNetwork.Notice notice) {
        if(!notice.settlement().isEmpty()&&!notice.settlement().equals(menu.settlementId.toString()))return;
        feedback=notice.message();feedbackSuccess=notice.success();feedbackUntil=System.currentTimeMillis()+7000;
    }
    private List<String> keys() {return snapshot==null?List.of():snapshot.settlement().resources.keySet().stream().sorted().toList();}
    @Override protected void init() {
        super.init();
        for(int i=0;i<3;i++) {
            final int row=i;
            resources[i]=new MedievalButton(leftPos+88,topPos+36+i*21,198,19,Component.empty(),()->{
                var keys=keys();int index=page*3+row;
                if(index<keys.size())SettlementNetwork.request("withdrawItem",menu.settlementId.toString(),"",keys.get(index),64);
            });addRenderableWidget(resources[i]);
        }
        addRenderableWidget(new MedievalButton(leftPos+88,topPos+101,18,18,Component.literal("<"),()->{page=Math.max(0,page-1);updateButtons();}));
        addRenderableWidget(new MedievalButton(leftPos+268,topPos+101,18,18,Component.literal(">"),()->{if((page+1)*3<keys().size())page++;updateButtons();}));
        addRenderableWidget(new MedievalButton(leftPos+192,topPos+183,94,18,tr("map"),()->{
            onClose();SettlementNetwork.request("open",menu.settlementId.toString(),"","",0);
        }));
        updateButtons();
    }
    private void updateButtons() {
        var keys=keys();
        for(int i=0;i<3;i++)if(resources[i]!=null) {
            int index=page*3+i;var button=resources[i];button.active=index<keys.size();
            if(!button.active) {button.setMessage(Component.literal("—"));continue;}
            String key=keys.get(index);var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
            var label=Component.literal(snapshot.settlement().resources.get(key)+" · ").append(item.getName(new ItemStack(item)));
            button.setMessage(label);button.setTooltip(Tooltip.create(label.copy().append("\n").append(tr("withdraw"))));
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xC51D1812);
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF2C2017);
        g.fill(x+1,y+1,x+imageWidth-1,y+imageHeight-1,0xFFA68049);
        g.fill(x+3,y+3,x+imageWidth-3,y+imageHeight-3,0xFF473322);
        g.fill(x+7,y+19,x+imageWidth-7,y+20,0xFF967344);
        for(var slot:menu.slots) {
            int sx=x+slot.x,sy=y+slot.y;
            g.fill(sx-1,sy-1,sx+17,sy+17,0xFFBD9E66);g.fill(sx,sy,sx+16,sy+16,0xFF292016);
        }
        g.text(font,tr("deposit"),x+12,y+24,0xFFF2D9A1,false);
        g.text(font,tr("stock"),x+88,y+24,0xFFF2D9A1,false);
        String pageText=(page+1)+" / "+Math.max(1,(keys().size()+2)/3);
        g.text(font,pageText,x+174,y+106,0xFFE1C78F,false);
        if(snapshot!=null) {
            g.text(font,Component.translatable("ui.smartsettlements.capacity",snapshot.settlement().stored(),snapshot.capacity()),x+192,y+128,0xFFF1DEB4,false);
        }
        g.text(font,tr("shiftHint"),x+192,y+145,0xFFD4BC90,false);
        g.text(font,tr("dragHint"),x+192,y+158,0xFFD4BC90,false);
        super.extractRenderState(g,mouseX,mouseY,delta);
        if(feedback!=null&&System.currentTimeMillis()<feedbackUntil&&mouseY>=topPos&&mouseY<topPos+22)g.setTooltipForNextFrame(feedback,mouseX,mouseY);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        boolean show=feedback!=null&&System.currentTimeMillis()<feedbackUntil;
        g.text(font,font.plainSubstrByWidth((show?feedback:title).getString(),276),12,8,show?(feedbackSuccess?0xFFB1D88C:0xFFFFA18B):0xFFF0D39A,false);
        g.text(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFF0D39A,false);
    }
}

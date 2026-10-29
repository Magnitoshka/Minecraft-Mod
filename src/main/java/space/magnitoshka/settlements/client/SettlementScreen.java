package space.magnitoshka.settlements.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import space.magnitoshka.settlements.SettlementNetwork;
import space.magnitoshka.settlements.domain.Resident;

public final class SettlementScreen extends Screen {
    private SettlementNetwork.Snapshot snapshot;
    private int tab, resourcePage, citizenPage, sidebarScroll;
    private String citizen="";
    private EditBox rename,amount;
    private double zoom=1.5,panX,panZ;
    private int mapRight,mapBottom;
    public SettlementScreen(SettlementNetwork.Snapshot snapshot) {
        super(tr("title")); this.snapshot=snapshot; citizen=snapshot.selectedResident();
        if (!citizen.isEmpty()) tab=1;
    }
    private static Component tr(String key,Object... args) { return Component.translatable("ui.smartsettlements."+key,args); }
    public void update(SettlementNetwork.Snapshot next) {
        boolean changed=!Objects.equals(snapshot.settlement()==null?null:snapshot.settlement().id,next.settlement()==null?null:next.settlement().id);
        if (snapshot.settlement()!=null && next.settlement()!=null) {
            if (tab==2 && !snapshot.settlement().resources.equals(next.settlement().resources)) changed=true;
            if (tab==1 && !snapshot.settlement().residents.stream().map(r->r.id).toList().equals(next.settlement().residents.stream().map(r->r.id).toList())) changed=true;
        }
        snapshot=next;
        if (!next.selectedResident().isEmpty()) { citizen=next.selectedResident(); tab=1; changed=true; }
        if (changed) { resourcePage=0; citizenPage=0; panX=panZ=0; rebuildWidgets(); }
    }
    private void button(int x,int y,int w,Component label,Runnable action) {
        boolean scrolled=tab==0 && x>=mapRight+12 && y>=94;
        if(scrolled)y-=sidebarScroll;
        var widget=Button.builder(label,b->action.run()).bounds(x,y,w,20).build();
        if(scrolled) widget.visible=y>=94 && y+20<=height-16;
        addRenderableWidget(widget);
    }
    private void action(String key,String text,long n) {
        if (snapshot.settlement()!=null) SettlementNetwork.request(key,snapshot.settlement().id.toString(),citizen,text,n);
    }
    private long coins() {
        try { return Long.parseLong(amount.getValue()); } catch (NumberFormatException ex) { return 0; }
    }
    @Override protected void init() {
        String savedName=rename==null?null:rename.getValue();
        String savedAmount=amount==null?"10":amount.getValue();
        mapRight=Math.max(76,width-240); mapBottom=height-48;
        button(width-28,8,20,Component.literal("×"),this::onClose);
        if (snapshot.settlement()==null) return;
        for (int i=0;i<3;i++) {
            int index=i;
            button(12+i*100,34,96,tr(new String[]{"overview","citizens","warehouse"}[i]),()->{tab=index;sidebarScroll=0;rebuildWidgets();});
        }
        button(12,height-32,24,Component.literal("+"),()->zoom=Math.min(5,zoom*1.25));
        button(40,height-32,24,Component.literal("−"),()->zoom=Math.max(.25,zoom/1.25));
        button(68,height-32,58,tr("center"),()->{panX=panZ=0;zoom=1.5;});
        int sidebar=mapRight+12;
        var s=snapshot.settlement();
        int listIndex=0;
        for(int i=0;i<snapshot.settlements().size();i++) if(snapshot.settlements().get(i).id().equals(s.id.toString()))listIndex=i;
        final int current=listIndex;
        button(sidebar,64,24,Component.literal("<"),()->selectSettlement(current-1));
        button(width-40,64,24,Component.literal(">"),()->selectSettlement(current+1));
        if (tab==0) {
            rename=new EditBox(font,sidebar,178-sidebarScroll,208,20,tr("rename")); rename.setMaxLength(32); rename.setValue(savedName==null?s.name:savedName); rename.visible=178-sidebarScroll>=94 && 198-sidebarScroll<=height-16; addRenderableWidget(rename);
            button(sidebar,202,208,tr("rename"),()->action("rename",rename.getValue(),0));
            amount=new EditBox(font,sidebar,228-sidebarScroll,208,20,tr("amount")); amount.setMaxLength(18); amount.setValue(savedAmount); amount.visible=228-sidebarScroll>=94 && 248-sidebarScroll<=height-16; addRenderableWidget(amount);
            button(sidebar,252,102,tr("toTreasury"),()->action("depositCoins","",coins()));
            button(sidebar+106,252,102,tr("toWallet"),()->action("withdrawCoins","",coins()));
            button(sidebar,278,208,tr("hire"),()->action("hire","",0));
            button(sidebar,302,208,tr("buyBanner"),()->action("banner","",0));
        } else if (tab==1) {
            int count=Math.max(1,(height-205)/24);
            int from=Math.min(citizenPage*count,Math.max(0,s.residents.size()-1));
            for (int i=from;i<Math.min(from+count,s.residents.size());i++) {
                var r=s.residents.get(i);
                button(sidebar,94+(i-from)*24,208,Component.literal(r.name),()->{citizen=r.id.toString();});
            }
            button(sidebar,height-98,100,tr("previous"),()->{citizenPage=Math.max(0,citizenPage-1);rebuildWidgets();});
            button(sidebar+108,height-98,100,tr("next"),()->{if((citizenPage+1)*count<s.residents.size())citizenPage++;rebuildWidgets();});
            button(sidebar,height-72,100,tr("builder"),()->action("profession","builder",0));
            button(sidebar+108,height-72,100,tr("unemployed"),()->action("profession","unemployed",0));
        } else {
            button(sidebar,94,208,tr("depositHeld"),()->action("depositItem","",0));
            List<String> resources=s.resources.keySet().stream().sorted().toList();
            int count=Math.max(1,(height-178)/26);
            int from=resourcePage*count;
            for(int i=from;i<Math.min(from+count,resources.size());i++) {
                String key=resources.get(i);
                var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
                Component label=Component.literal(s.resources.get(key)+" × ").append(item.getName(new net.minecraft.world.item.ItemStack(item)));
                button(sidebar,122+(i-from)*26,208,label,()->action("withdrawItem",key,16));
            }
            button(sidebar,height-42,100,tr("previous"),()->{resourcePage=Math.max(0,resourcePage-1);rebuildWidgets();});
            button(sidebar+108,height-42,100,tr("next"),()->{if((resourcePage+1)*count<resources.size())resourcePage++;rebuildWidgets();});
        }
    }
    private void selectSettlement(int index) {
        var list=snapshot.settlements(); if(list.isEmpty())return;
        rename=null; amount=null; sidebarScroll=0;
        SettlementNetwork.request("open",list.get(Math.floorMod(index,list.size())).id(),"","",0);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xF0121B24);
        g.text(font,title,12,14,0xFFF3E5BA);
        var s=snapshot.settlement();
        if(s==null) {
            g.text(font,tr("noSettlement"),16,80,0xFFFFFFFF);
            super.extractRenderState(g,mouseX,mouseY,delta); return;
        }
        g.fill(12,62,mapRight,mapBottom,0xFF243A34);
        g.enableScissor(12,62,mapRight,mapBottom);
        int cx=(12+mapRight)/2,cz=(62+mapBottom)/2;
        double scale=zoom;
        // Circle boundary, hall footprint, citizen positions and player marker.
        for (int degree=0;degree<360;degree+=2) {
            double a=Math.toRadians(degree);
            int x=(int)(cx+(Math.cos(a)*snapshot.radius()-panX)*scale);
            int z=(int)(cz+(Math.sin(a)*snapshot.radius()-panZ)*scale);
            g.fill(x,z,x+2,z+2,0xFF81B99A);
        }
        int hx=(int)(cx-panX*scale),hz=(int)(cz-panZ*scale);
        g.fill(hx-(int)(4*scale),hz-(int)(3*scale),hx+(int)(5*scale),hz+(int)(4*scale),0xFFD9B47B);
        for(var r:s.residents) {
            var p=snapshot.positions().get(r.id.toString()); if(p==null)continue;
            int x=(int)(cx+(p[0]-s.x-panX)*scale),z=(int)(cz+(p[1]-s.z-panZ)*scale);
            int color=r.profession.equals("builder")?0xFFFFC65C:0xFFB6BEC6;
            if(r.id.toString().equals(citizen))g.fill(x-3,z-3,x+4,z+4,0xFFFFFFFF);
            g.fill(x-2,z-2,x+3,z+3,color);
        }
        if (minecraft.player!=null && minecraft.player.level().dimension().identifier().toString().equals(s.dimension)) {
            int x=(int)(cx+(minecraft.player.getX()-s.x-panX)*scale),z=(int)(cz+(minecraft.player.getZ()-s.z-panZ)*scale);
            g.fill(x-2,z-2,x+3,z+3,0xFF65CFFF);
        }
        g.text(font,tr("legend"),20,70,0xFFFFFFFF);
        g.text(font,s.x+", "+s.y+", "+s.z,20,mapBottom-14,0xFFD4DDD6);
        g.disableScissor();
        int x=mapRight+12;
        g.text(font,font.plainSubstrByWidth(s.name,145),x+30,70,0xFFF3E5BA);
        if(tab==0) {
            g.enableScissor(x,94,width-8,height-16);
            g.text(font,tr("population",s.residents.size(),snapshot.beds()),x,94-sidebarScroll,0xFFFFFFFF);
            g.text(font,tr("treasury",s.treasury),x,110-sidebarScroll,0xFFFFFFFF);
            g.text(font,tr("wallet",snapshot.wallet()),x,126-sidebarScroll,0xFFFFFFFF);
            g.text(font,tr("capacity",s.stored(),snapshot.capacity()),x,142-sidebarScroll,0xFFFFFFFF);
            g.text(font,tr("foodDays",String.format(java.util.Locale.ROOT,"%.1f",snapshot.foodDays())),x,158-sidebarScroll,0xFFFFFFFF);
            g.text(font,tr("nearHall"),x,330-sidebarScroll,0xFFB4C7B9);
            g.disableScissor();
            if(height<360)g.text(font,tr("scroll"),x,height-12,0xFFB4C7B9);
        } else if(tab==1) {
            Resident selected=s.residents.stream().filter(r->r.id.toString().equals(citizen)).findFirst().orElse(null);
            if(selected!=null) {
                g.text(font,Component.literal(selected.name),20,mapBottom-58,0xFFFFFFFF);
                g.text(font,Component.translatable("profession.smartsettlements."+selected.profession),20,mapBottom-44,0xFFF3E5BA);
                g.text(font,tr("residentStats",selected.level,selected.mood,selected.hungerDays),20,mapBottom-30,0xFFFFFFFF);
            }
        } else g.text(font,tr("capacity",s.stored(),snapshot.capacity()),x,86,0xFFFFFFFF);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.x()>12&&event.x()<mapRight&&event.y()>62&&event.y()<mapBottom&&snapshot.settlement()!=null) {
            var s=snapshot.settlement(); int cx=(12+mapRight)/2,cz=(62+mapBottom)/2;
            for(var r:s.residents) {
                var p=snapshot.positions().get(r.id.toString()); if(p==null)continue;
                double x=cx+(p[0]-s.x-panX)*zoom,z=cz+(p[1]-s.z-panZ)*zoom;
                if(Math.hypot(event.x()-x,event.y()-z)<7) {citizen=r.id.toString();tab=1;rebuildWidgets();return true;}
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(event.x()>12&&event.x()<mapRight&&event.y()>62&&event.y()<mapBottom) {panX-=dx/zoom;panZ-=dy/zoom;return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(x<mapRight) {zoom=Math.clamp(zoom*Math.pow(1.15,dy),.25,5);return true;}
        if(tab==0 && x>=mapRight) {
            sidebarScroll=Math.clamp(sidebarScroll-(int)(dy*24),0,Math.max(0,358-height)); rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { SettlementNetwork.request("close","","","",0); }
}

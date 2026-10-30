package space.magnitoshka.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import space.magnitoshka.settlements.SettlementNetwork;
import space.magnitoshka.settlements.TerrainMap;
import space.magnitoshka.settlements.domain.Building;
import space.magnitoshka.settlements.domain.BuildingType;
import space.magnitoshka.settlements.domain.Resident;

/** A compact management ledger around a schematic parchment map. */
public final class SettlementScreen extends Screen {
    private static final int INK=0xFF493923, GOLD=0xFFD3B074, LIGHT=0xFFF2E2BC;
    private static final String[] TABS={"overview","citizens","warehouse","construction"};
    private static final String[] PROFESSIONS={"unemployed","builder","lumberjack","miner","farmer","courier"};
    private static final String[] TYPES={"house","warehouse","lumber_hut","mine","field"};
    private final TerrainTexture terrainTexture=new TerrainTexture();
    private SettlementNetwork.Snapshot snapshot;
    private space.magnitoshka.settlements.TerrainMap.Survey terrain;
    private Component feedback;
    private boolean feedbackSuccess,feedbackPending;
    private long feedbackUntil;
    private int tab, overviewPage, residentIndex, resourcePage, professionIndex, catalogPage, rosterPage;
    private String citizen="", selectedBuilding="", buildChoice="", professionResident="";
    private boolean buildingDetails;
    private EditBox rename, amount;
    private String renameDraft, amountDraft="10";
    private double zoom=1.25, panX, panZ;
    private int mapLeft, mapRight, mapTop, mapBottom, panelX, panelW;

    public SettlementScreen(SettlementNetwork.Snapshot snapshot) {
        super(tr("title"));
        this.snapshot=snapshot;
        citizen=snapshot.selectedResident();
        if(!citizen.isEmpty())tab=1;
    }

    private static Component tr(String key,Object... args) {
        return Component.translatable("ui.smartsettlements."+key,args);
    }
    public void terrain(space.magnitoshka.settlements.TerrainMap.Survey survey) {
        if(snapshot.settlement()!=null&&survey.settlement().equals(snapshot.settlement().id.toString()))terrain=survey;
    }
    public void notice(space.magnitoshka.settlements.SettlementNetwork.Notice notice) {
        if(!notice.settlement().isEmpty()&&(snapshot.settlement()==null||!notice.settlement().equals(snapshot.settlement().id.toString())))return;
        feedback=notice.message();feedbackSuccess=notice.success();feedbackPending=false;feedbackUntil=System.currentTimeMillis()+7000;
    }
    private static Component profession(String id) { return Component.translatable("profession.smartsettlements."+id); }
    private static Component buildingName(String id) { return Component.translatable("building.smartsettlements."+id); }
    private static int halfWidth(String type) {
        var definition=BuildingType.byId(type);return definition==null?2:definition.halfWidth;
    }
    private static int halfDepth(String type) {
        var definition=BuildingType.byId(type);return definition==null?2:definition.halfDepth;
    }
    private List<Building> buildings() {
        return snapshot.settlement()==null || snapshot.settlement().buildings==null?List.of():snapshot.settlement().buildings;
    }
    private void saveDrafts() {
        if(rename!=null)renameDraft=rename.getValue();
        if(amount!=null)amountDraft=amount.getValue();
    }
    public void update(SettlementNetwork.Snapshot next) {
        var previous=snapshot.settlement();
        var incoming=next.settlement();
        boolean switched=!Objects.equals(previous==null?null:previous.id,incoming==null?null:incoming.id);
        boolean resourcesChanged=previous!=null && incoming!=null && !previous.resources.equals(incoming.resources);
        boolean peopleChanged=previous!=null && incoming!=null && !previous.residents.stream().map(r->r.id).toList()
            .equals(incoming.residents.stream().map(r->r.id).toList());
        snapshot=next;
        if(switched) {
            rename=null; amount=null; renameDraft=null; amountDraft="10";terrain=null;terrainTexture.close();feedback=null;
            citizen=""; selectedBuilding=""; buildChoice=""; buildingDetails=false;
            residentIndex=resourcePage=0;panX=panZ=0;
        }
        if(!next.selectedResident().isEmpty()) {citizen=next.selectedResident();tab=1;peopleChanged=true;}
        if(switched || peopleChanged || tab==0&&overviewPage==3 || tab==2 && resourcesChanged)rebuildWidgets();
    }
    private MedievalButton button(int x,int y,int w,Component label,Runnable action) {
        var button=new MedievalButton(x,y,w,18,label,action);
        addRenderableWidget(button);
        return button;
    }
    private void tab(int value) {
        if(value!=1)SettlementNetwork.request("endDialogue","","","",0);
        saveDrafts();tab=value;buildChoice="";buildingDetails=false;rebuildWidgets();
    }
    private void action(String key,String text,long value) {
        if(snapshot.settlement()!=null)SettlementNetwork.request(key,snapshot.settlement().id.toString(),citizen,text,value);
    }
    private long coins() {
        try {return Long.parseLong(amount==null?amountDraft:amount.getValue());}
        catch(NumberFormatException ex) {return 0;}
    }
    private Resident resident() {
        if(snapshot.settlement()==null || snapshot.settlement().residents.isEmpty())return null;
        var list=snapshot.settlement().residents;
        return list.stream().filter(r->r.id.toString().equals(citizen)).findFirst()
            .orElse(list.get(Math.floorMod(residentIndex,list.size())));
    }
    private void selectResident(int step) {
        SettlementNetwork.request("endDialogue","","","",0);
        var list=snapshot.settlement().residents;if(list.isEmpty())return;
        var current=resident();
        residentIndex=Math.floorMod(list.indexOf(current)+step,list.size());
        citizen=list.get(residentIndex).id.toString();rebuildWidgets();
    }
    private int rosterRows() {return Math.max(1,(height-175)/36);}
    private int resourceRows() {return Math.max(1,(height-190)/21);}

    @Override protected void init() {
        saveDrafts();rename=null;amount=null;
        panelW=Math.clamp((int)(width*.42),150,220);
        panelX=width-panelW-10;
        mapLeft=12;mapRight=panelX-8;mapTop=64;mapBottom=height-42;
        button(width-30,10,18,Component.literal("×"),this::onClose);
        if(snapshot.settlement()==null)return;
        int available=width-24;
        for(int i=0;i<TABS.length;i++) {
            final int value=i;
            int left=12+available*i/4, right=12+available*(i+1)/4-3;
            var navigation=new MedievalButton(left,37,right-left,18,tr(TABS[i]),()->tab(value),()->tab==value);
            navigation.setTooltip(Tooltip.create(tr(TABS[i])));
            addRenderableWidget(navigation);
        }
        button(mapLeft,height-31,19,Component.literal("+"),()->zoom=Math.min(5,zoom*1.25));
        button(mapLeft+23,height-31,19,Component.literal("−"),()->zoom=Math.max(.25,zoom/1.25));
        button(mapLeft+46,height-31,Math.max(36,Math.min(66,mapRight-mapLeft-46)),tr("center"),()->{panX=panZ=0;zoom=1.25;});
        int current=0;
        for(int i=0;i<snapshot.settlements().size();i++)if(snapshot.settlements().get(i).id().equals(snapshot.settlement().id.toString()))current=i;
        final int settlementIndex=current;
        button(panelX+6,67,18,Component.literal("<"),()->selectSettlement(settlementIndex-1));
        button(panelX+panelW-24,67,18,Component.literal(">"),()->selectSettlement(settlementIndex+1));
        switch(tab) {
            case 0 -> overviewWidgets();
            case 1 -> citizenWidgets();
            case 2 -> warehouseWidgets();
            case 3 -> constructionWidgets();
        }
    }
    private void overviewWidgets() {
        int x=panelX+8,w=panelW-16;
        button(x,91,18,Component.literal("<"),()->{saveDrafts();overviewPage=Math.floorMod(overviewPage-1,4);rebuildWidgets();});
        button(x+w-18,91,18,Component.literal(">"),()->{saveDrafts();overviewPage=(overviewPage+1)%4;rebuildWidgets();});
        if(overviewPage==3) {
            var residents=snapshot.settlement().residents;int rows=rosterRows();
            rosterPage=Math.clamp(rosterPage,0,Math.max(0,(residents.size()-1)/rows));
            for(int i=rosterPage*rows;i<Math.min(residents.size(),(rosterPage+1)*rows);i++) {
                var r=residents.get(i);
                var label=Component.literal(r.name+" · ").append(profession(r.profession));
                button(x,116+(i-rosterPage*rows)*36,w,Component.literal(r.name),()->{citizen=r.id.toString();tab=1;rebuildWidgets();})
                    .setTooltip(Tooltip.create(label.copy().append(" · ").append(tr("status."+r.workStatus))));
            }
            button(x,height-51,(w-4)/2,tr("previous"),()->{rosterPage=Math.max(0,rosterPage-1);rebuildWidgets();});
            button(x+(w+4)/2,height-51,(w-4)/2,tr("next"),()->{rosterPage++;rebuildWidgets();});
        } else if(overviewPage==1) {
            amount=new EditBox(font,x,143,w,18,tr("amount"));amount.setMaxLength(18);amount.setValue(amountDraft);addRenderableWidget(amount);
            button(x,168,(w-4)/2,tr("toTreasury"),()->action("depositCoins","",coins()));
            button(x+(w+4)/2,168,(w-4)/2,tr("toWallet"),()->action("withdrawCoins","",coins()));
        } else if(overviewPage==2) {
            rename=new EditBox(font,x,116,w,18,tr("rename"));rename.setMaxLength(32);
            rename.setValue(renameDraft==null?snapshot.settlement().name:renameDraft);addRenderableWidget(rename);
            button(x,138,w,tr("rename"),()->action("rename",rename.getValue(),0));
            button(x,160,w,tr("hire"),()->action("hire","",0)).setTooltip(Tooltip.create(tr("nearHall")));
            button(x,182,w,tr("buyBanner"),()->action("banner","",0)).setTooltip(Tooltip.create(tr("nearHall")));
        }
    }
    private void citizenWidgets() {
        int x=panelX+8,w=panelW-16;
        var r=resident();
        button(x,91,18,Component.literal("<"),()->selectResident(-1));
        button(x+w-18,91,18,Component.literal(">"),()->selectResident(1));
        if(r==null)return;
        citizen=r.id.toString();
        if(!citizen.equals(professionResident)) {
            professionResident=citizen;professionIndex=0;
            for(int i=0;i<PROFESSIONS.length;i++)if(PROFESSIONS[i].equals(r.profession))professionIndex=i;
        }
        button(x,174,18,Component.literal("<"),()->{professionIndex=Math.floorMod(professionIndex-1,PROFESSIONS.length);rebuildWidgets();});
        button(x+w-18,174,18,Component.literal(">"),()->{professionIndex=(professionIndex+1)%PROFESSIONS.length;rebuildWidgets();});
        button(x+21,174,w-42,profession(PROFESSIONS[professionIndex]),()->action("profession",PROFESSIONS[professionIndex],0))
            .setTooltip(Tooltip.create(tr("assignProfession")));
    }
    private void warehouseWidgets() {
        int x=panelX+8,w=panelW-16;
        button(x,107,w,tr("openWarehouse"),()->action("warehouse","",0)).setTooltip(Tooltip.create(tr("nearHall")));
        var keys=snapshot.settlement().resources.keySet().stream().sorted().toList();
        int rows=resourceRows();resourcePage=Math.min(resourcePage,Math.max(0,(keys.size()-1)/rows));
        for(int i=resourcePage*rows;i<Math.min((resourcePage+1)*rows,keys.size());i++) {
            String key=keys.get(i);
            var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
            Component label=Component.literal(snapshot.settlement().resources.get(key)+" · ").append(item.getName(new ItemStack(item)));
            button(x,130+(i-resourcePage*rows)*21,w,label,()->action("withdrawItem",key,16)).setTooltip(Tooltip.create(tr("withdrawSixteen")));
        }
        button(x,height-57,(w-4)/2,tr("previous"),()->{resourcePage=Math.max(0,resourcePage-1);rebuildWidgets();});
        button(x+(w+4)/2,height-57,(w-4)/2,tr("next"),()->{if((resourcePage+1)*rows<keys.size())resourcePage++;rebuildWidgets();});
    }
    private int catalogRows() {return Math.clamp((height-141)/38,1,5);}
    private void constructionWidgets() {
        int x=panelX+8,w=panelW-16;
        if(buildingDetails) {
            button(x,195,w,tr("buildingCatalog"),()->{buildingDetails=false;selectedBuilding="";rebuildWidgets();});
            return;
        }
        int rows=catalogRows(),pages=(TYPES.length+rows-1)/rows;
        catalogPage=Math.clamp(catalogPage,0,pages-1);
        for(int i=catalogPage*rows;i<Math.min((catalogPage+1)*rows,TYPES.length);i++) {
            final int index=i;
            var blueprint=new MedievalButton(x,91+(i-catalogPage*rows)*38,w,18,buildingName(TYPES[i]),()->{
                buildChoice=buildChoice.equals(TYPES[index])?"":TYPES[index];selectedBuilding="";
            },()->buildChoice.equals(TYPES[index]));
            var type=BuildingType.byId(TYPES[i]);
            blueprint.setTooltip(Tooltip.create(buildingName(TYPES[i]).copy().append("\n").append(tr("buildingCost",type.woodCost,type.stoneCost)).append("\n").append(tr("foundationCost"))));
            addRenderableWidget(blueprint);
        }
        button(x,height-42,18,Component.literal("<"),()->{catalogPage=Math.floorMod(catalogPage-1,pages);rebuildWidgets();});
        button(x+w-18,height-42,18,Component.literal(">"),()->{catalogPage=(catalogPage+1)%pages;rebuildWidgets();});
    }
    private void selectSettlement(int index) {
        var list=snapshot.settlements();if(list.isEmpty())return;
        saveDrafts();
        SettlementNetwork.request("open",list.get(Math.floorMod(index,list.size())).id(),"","",0);
    }
    private void text(GuiGraphicsExtractor g,Component value,int x,int y,int maxWidth,int color) {
        g.text(font,font.plainSubstrByWidth(value.getString(),Math.max(1,maxWidth)),x,y,color,false);
    }
    private void centered(GuiGraphicsExtractor g,Component value,int x,int y,int width,int color) {
        String label=font.plainSubstrByWidth(value.getString(),Math.max(1,width));
        g.text(font,label,x+(width-font.width(label))/2,y,color,false);
    }
    private static void frame(GuiGraphicsExtractor g,int x,int y,int w,int h,int inside) {
        g.fill(x+2,y+2,x+w+2,y+h+2,0x77000000);
        g.fill(x,y,x+w,y+h,0xFF241C14);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF977343);
        g.fill(x+3,y+3,x+w-3,y+h-3,inside);
        for(int dx:new int[]{5,w-7})for(int dy:new int[]{5,h-7})g.fill(x+dx,y+dy,x+dx+2,y+dy+2,0xFFD7B575);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xF21E1914);
        for(int y=0;y<height;y+=12)g.fill(0,y,width,y+1,0xFF2A2118);
        g.fill(8,8,width-8,30,0xFF3B2A1C);g.fill(8,29,width-8,30,0xFFB48B50);
        boolean showFeedback=feedback!=null&&System.currentTimeMillis()<feedbackUntil;
        text(g,showFeedback?feedback:title,16,15,width-64,showFeedback?(feedbackPending?GOLD:feedbackSuccess?0xFFA9CB84:0xFFFFAA87):GOLD);
        if(showFeedback&&mouseY>=8&&mouseY<=30)g.setTooltipForNextFrame(feedback,mouseX,mouseY);
        if(snapshot.settlement()==null) {
            frame(g,12,64,width-24,height-90,0xFFD4BE91);
            text(g,tr("noSettlement"),24,86,width-48,INK);
            super.extractRenderState(g,mouseX,mouseY,delta);return;
        }
        frame(g,mapLeft-2,mapTop-2,mapRight-mapLeft+4,mapBottom-mapTop+4,0xFFD8C59B);
        frame(g,panelX,mapTop-2,panelW,height-mapTop-4,0xFF3B2C20);
        drawMap(g,mouseX,mouseY);
        centered(g,Component.literal(snapshot.settlement().name),panelX+28,72,panelW-56,LIGHT);
        g.fill(panelX+7,87,panelX+panelW-7,88,0xFF705334);
        switch(tab) {
            case 0 -> drawOverview(g);
            case 1 -> drawCitizen(g,mouseX,mouseY);
            case 2 -> {
                text(g,tr("capacity",snapshot.settlement().stored(),snapshot.capacity()),panelX+8,92,panelW-16,LIGHT);
                text(g,tr("storageHint"),panelX+8,height-30,panelW-16,0xFFC4AA7D);
            }
            case 3 -> drawConstruction(g,mouseX,mouseY);
        }
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private int centerX() {return (mapLeft+mapRight)/2;}
    private int centerZ() {return (mapTop+mapBottom)/2;}
    private int mapX(double relative) {return (int)Math.round(centerX()+(relative-panX)*zoom);}
    private int mapZ(double relative) {return (int)Math.round(centerZ()+(relative-panZ)*zoom);}
    private int worldDx(double pixel) {return (int)Math.floor((pixel-centerX())/zoom+panX);}
    private int worldDz(double pixel) {return (int)Math.floor((pixel-centerZ())/zoom+panZ);}
    private boolean inMap(double x,double y) {return x>=mapLeft&&x<mapRight&&y>=mapTop&&y<mapBottom;}
    private void drawMap(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        var s=snapshot.settlement();
        g.enableScissor(mapLeft+2,mapTop+2,mapRight-2,mapBottom-2);
        drawTerrain(g);
        int grid=Math.max(12,(int)(16*zoom));
        int offsetX=Math.floorMod(mapX(0)-mapLeft,grid),offsetZ=Math.floorMod(mapZ(0)-mapTop,grid);
        for(int x=mapLeft+offsetX;x<mapRight;x+=grid)g.fill(x,mapTop,x+1,mapBottom,0x226F633F);
        for(int y=mapTop+offsetZ;y<mapBottom;y+=grid)g.fill(mapLeft,y,mapRight,y+1,0x226F633F);
        for(int degree=0;degree<360;degree+=4) {
            double a=Math.toRadians(degree);
            int x=mapX(Math.cos(a)*snapshot.radius()),z=mapZ(Math.sin(a)*snapshot.radius());
            g.fill(x,z,x+1,z+1,0xFF8A6A37);
        }
        drawBuilding(g,s.x,s.z,4,3,true,true,0xFF7E4D2B);
        Component hover=terrainInfo(mouseX,mouseY);
        for(var b:buildings()) {
            int halfW=halfWidth(b.type);
            int color=switch(b.type) {case "field"->0xFF708447;case "mine"->0xFF777369;case "warehouse"->0xFF8F6034;default->0xFF9A7244;};
            drawBuilding(g,b.x,b.z,halfW,halfDepth(b.type),b.complete,b.id.equals(selectedBuilding),color);
            int x=mapX(b.x-s.x),z=mapZ(b.z-s.z);
            if(Math.abs(mouseX-x)<Math.max(5,(halfW+1)*zoom)&&Math.abs(mouseY-z)<Math.max(5,(halfDepth(b.type)+1)*zoom)) {
                hover=buildingName(b.type).copy().append(" · ").append(buildingState(b));
            }
            if(!b.complete) {
                int w=Math.max(9,(int)(6*zoom));
                g.fill(x-w/2,z+5,x+w/2,z+7,0xFF4D3D27);
                int progress=b.totalSteps()<=0?0:(int)((long)Math.clamp(b.completedSteps(),0,b.totalSteps())*w/b.totalSteps());
                g.fill(x-w/2,z+5,x-w/2+progress,z+7,0xFFDAB05F);
            }
        }
        for(var r:s.residents) {
            var p=snapshot.positions().get(r.id.toString());if(p==null)continue;
            int x=mapX(p[0]-s.x),z=mapZ(p[1]-s.z),color=professionColor(r.profession);
            if(r.id.toString().equals(citizen))g.fill(x-3,z-3,x+4,z+4,0xFFFFF2CD);
            g.fill(x-2,z-2,x+3,z+3,0xFF46341F);g.fill(x-1,z-1,x+2,z+2,color);
            if(Math.hypot(mouseX-x,mouseY-z)<5)hover=Component.literal(r.name).append(" · ").append(profession(r.profession));
        }
        if(minecraft.player!=null && minecraft.player.level().dimension().identifier().toString().equals(s.dimension)) {
            int x=mapX(minecraft.player.getX()-s.x),z=mapZ(minecraft.player.getZ()-s.z);
            g.fill(x-1,z-4,x+2,z+5,0xFF367187);g.fill(x-4,z-1,x+5,z+2,0xFF367187);
        }
        if(!buildChoice.isEmpty()&&inMap(mouseX,mouseY)) {
            int dx=worldDx(mouseX),dz=worldDz(mouseY),halfW=halfWidth(buildChoice),halfD=halfDepth(buildChoice);
            boolean valid=previewValid(dx,dz,halfW,halfD);
            int x1=mapX(dx-halfW),x2=mapX(dx+halfW+1),z1=mapZ(dz-halfD),z2=mapZ(dz+halfD+1);
            g.fill(x1,z1,x2,z2,valid?0x665D893C:0x66AA4234);
            border(g,x1,z1,x2,z2,valid?0xFF45662A:0xFF9F3326);
            String problem=placementProblem(dx,dz,halfW,halfD);
            hover=problem.isEmpty()?tr("prepareBuilding"):tr("placement."+problem);
        }
        // Compass and small scale line are anchored to the parchment, not the world.
        int compassX=mapRight-19,compassY=mapTop+20;
        g.fill(compassX,compassY-6,compassX+1,compassY+7,0xFF8C6A3A);
        g.fill(compassX-6,compassY,compassX+7,compassY+1,0xFF8C6A3A);
        g.fill(compassX-1,compassY-4,compassX+2,compassY-1,0xFFA05137);
        g.text(font,"N",compassX-2,compassY-15,INK,false);
        text(g,tr(buildChoice.isEmpty()?"mapHint":"placementHint"),mapLeft+7,mapTop+7,Math.max(1,mapRight-mapLeft-40),INK);
        g.fill(mapLeft+2,mapBottom-28,mapRight-2,mapBottom-2,0xDDDBC69C);
        int legendX=mapLeft+6;
        String[] kinds={"water","sand","land"};int[] colors={0xFF3F76E4,0xFFD9C084,0xFF75994B};
        for(int i=0;i<kinds.length;i++) {
            String label=tr("terrain."+kinds[i]).getString();
            int length=font.width(label)+11;if(legendX+length>=mapRight-3)break;
            g.fill(legendX,mapBottom-24,legendX+5,mapBottom-19,colors[i]);
            g.text(font,label,legendX+7,mapBottom-25,INK,false);legendX+=length;
        }
        text(g,Component.literal(s.x+", "+s.z),mapLeft+7,mapBottom-12,mapRight-mapLeft-14,INK);
        g.disableScissor();
        if(hover!=null&&inMap(mouseX,mouseY))g.setTooltipForNextFrame(hover,mouseX,mouseY);
    }
    private static void border(GuiGraphicsExtractor g,int x1,int y1,int x2,int y2,int color) {
        g.fill(x1,y1,x2,y1+1,color);g.fill(x1,y2-1,x2,y2,color);
        g.fill(x1,y1,x1+1,y2,color);g.fill(x2-1,y1,x2,y2,color);
    }
    private void drawBuilding(GuiGraphicsExtractor g,int x,int z,int halfW,int halfD,boolean complete,boolean selected,int color) {
        var s=snapshot.settlement();
        int x1=mapX(x-s.x-halfW),x2=mapX(x-s.x+halfW+1),z1=mapZ(z-s.z-halfD),z2=mapZ(z-s.z+halfD+1);
        g.fill(x1+1,z1+1,x2+1,z2+1,0x55463823);
        g.fill(x1,z1,x2,z2,complete?color:0xFFB6A77D);
        border(g,x1,z1,x2,z2,selected?0xFFFFE3A1:0xFF655135);
        if(complete) {
            int mid=(z1+z2)/2;
            g.fill(x1+1,z1+1,x2-1,mid,color|0xFF000000);g.fill(x1+1,mid,x2-1,mid+1,0xFFC39E65);
        }
    }
    private void drawTerrain(GuiGraphicsExtractor g) {
        g.fill(mapLeft,mapTop,mapRight,mapBottom,0xFF8A8273);
        if(terrain==null||snapshot.settlement()==null)return;
        var s=snapshot.settlement();
        int x0=mapX(terrain.originX()-s.x),z0=mapZ(terrain.originZ()-s.z);
        int x1=mapX(terrain.originX()+terrain.size()*terrain.step()-s.x);
        int z1=mapZ(terrain.originZ()+terrain.size()*terrain.step()-s.z);
        terrainTexture.draw(g,terrain,x0,z0,x1,z1);
    }
    private Component terrainInfo(double mouseX,double mouseY) {
        if(!inMap(mouseX,mouseY)||snapshot.settlement()==null)return null;
        int x=snapshot.settlement().x+worldDx(mouseX),z=snapshot.settlement().z+worldDz(mouseY);
        int index=terrain==null?-1:terrain.index(x,z);
        if(terrain==null||terrain.kind(index)==TerrainMap.UNKNOWN)return tr("terrainUnknown",x,z);
        String kind=switch(terrain.kind(index)) {
            case TerrainMap.WATER->"water";case TerrainMap.SAND->"sand";case TerrainMap.GRASS->"grass";
            case TerrainMap.STONE->"stone";case TerrainMap.SNOW->"snow";case TerrainMap.FOREST->"forest";
            case TerrainMap.STRUCTURE->"structure";case TerrainMap.LAVA->"lava";default->"land";
        };
        return tr("terrainInfo",tr("terrain."+kind),terrain.heights()[index],x,z);
    }
    private boolean previewValid(int dx,int dz,int halfW,int halfD) {return placementProblem(dx,dz,halfW,halfD).isEmpty();}
    private String placementProblem(int dx,int dz,int halfW,int halfD) {
        for(int x:new int[]{dx-halfW,dx+halfW})for(int z:new int[]{dz-halfD,dz+halfD})
            if(Math.hypot(x,z)>snapshot.radius())return "territory";
        if(Math.abs(dx)<halfW+6&&Math.abs(dz)<halfD+5)return "collision";
        var s=snapshot.settlement();
        for(var b:buildings())if(Math.abs(s.x+dx-b.x)<halfW+halfWidth(b.type)+2&&Math.abs(s.z+dz-b.z)<halfD+halfDepth(b.type)+2)return "collision";
        if(terrain==null)return "unknown";
        int center=terrain.index(s.x+dx,s.z+dz);
        if(terrain.kind(center)==TerrainMap.UNKNOWN)return "unknown";
        for(int z=dz-halfD;z<=dz+halfD;z++)for(int x=dx-halfW;x<=dx+halfW;x++) {
            int cell=terrain.index(s.x+x,s.z+z),kind=terrain.kind(cell);
            if(kind==TerrainMap.UNKNOWN)return "unknown";
            if(kind==TerrainMap.LAVA)return "water";
            if(kind==TerrainMap.STRUCTURE)return "blocked";
        }
        return "";
    }
    private static int professionColor(String profession) {
        return switch(profession) {case "builder"->0xFFE4B34F;case "lumberjack"->0xFF518651;case "miner"->0xFF858A9A;case "farmer"->0xFFCC8550;case "courier"->0xFF6DA5B7;default->0xFFC2B79E;};
    }
    private Component buildingState(Building b) {
        if(b.complete)return tr("buildingReady");
        int percent=b.totalSteps()<=0?0:(int)((long)Math.clamp(b.completedSteps(),0,b.totalSteps())*100/b.totalSteps());
        return tr("buildingProgress",percent);
    }
    private void drawOverview(GuiGraphicsExtractor g) {
        int x=panelX+8,w=panelW-16;
        centered(g,tr(new String[]{"ledgerSummary","ledgerEconomy","ledgerManage","citizens"}[overviewPage]),x+21,96,w-42,GOLD);
        var s=snapshot.settlement();
        if(overviewPage==0) {
            text(g,tr("population",s.residents.size(),snapshot.beds()),x,119,w,LIGHT);
            text(g,tr("treasury",s.treasury),x,134,w,LIGHT);
            text(g,tr("wallet",snapshot.wallet()),x,149,w,LIGHT);
            text(g,tr("capacity",s.stored(),snapshot.capacity()),x,164,w,LIGHT);
            text(g,tr("foodDays",String.format(Locale.ROOT,"%.1f",snapshot.foodDays())),x,179,w,LIGHT);
        } else if(overviewPage==1) {
            text(g,tr("treasury",s.treasury),x,116,w,LIGHT);
            text(g,tr("wallet",snapshot.wallet()),x,130,w,LIGHT);
            text(g,tr("amount"),x,193,w,0xFFC4AA7D);
        }
        if(overviewPage==3) {
            var residents=s.residents;int rows=rosterRows();
            for(int i=rosterPage*rows;i<Math.min(residents.size(),(rosterPage+1)*rows);i++) {
                var r=residents.get(i);
                text(g,profession(r.profession).copy().append(" · ").append(tr("status."+r.workStatus)),x,137+(i-rosterPage*rows)*36,w,0xFFC4AA7D);
            }
        }
        text(g,tr(overviewPage==2?"nearHall":"ledgerHint"),x,height-30,w,0xFFC4AA7D);
    }
    private void drawCitizen(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        int x=panelX+8,w=panelW-16;
        var r=resident();
        if(r==null) {text(g,tr("noCitizens"),x,116,w,LIGHT);return;}
        centered(g,Component.literal(r.name),x+21,96,w-42,GOLD);
        text(g,profession(r.profession),x,116,w,professionColor(r.profession));
        text(g,tr("citizenLevel",r.level,r.experience),x,130,w,LIGHT);
        text(g,tr("citizenMood",r.mood,r.hungerDays),x,144,w,LIGHT);
        String work=r.workStatus==null||r.workStatus.isEmpty()?"idle":r.workStatus;
        text(g,tr("status."+work),x,158,w,0xFFC4AA7D);
        text(g,tr("carriedInventory"),x,199,w,0xFFC4AA7D);
        for(int i=0;i<4;i++) {
            int sx=x+i*23,sy=212;var slot=r.builderInventory.slots[i];
            g.fill(sx,sy,sx+21,sy+21,0xFF241C15);border(g,sx,sy,sx+21,sy+21,0xFF997646);
            if(slot.count<=0)continue;
            var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(slot.item));
            var stack=new ItemStack(item);g.item(stack,sx+2,sy+2);
            String amount=Integer.toString(slot.count);g.text(font,amount,sx+20-font.width(amount),sy+13,0xFFFFFFFF,true);
            if(mouseX>=sx&&mouseX<sx+21&&mouseY>=sy&&mouseY<sy+21)
                g.setTooltipForNextFrame(item.getName(stack).copy().append(" × "+slot.count),mouseX,mouseY);
        }
        if(height>=275&&!r.taskBuilding.isEmpty()) {
            var job=buildings().stream().filter(b->b.id.equals(r.taskBuilding)).findFirst().orElse(null);
            if(job!=null)text(g,tr("assignedSite",buildingName(job.type)),x,242,w,GOLD);
        }
    }
    private void drawConstruction(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        int x=panelX+8,w=panelW-16;
        var b=buildings().stream().filter(value->value.id.equals(selectedBuilding)).findFirst().orElse(null);
        if(buildingDetails&&b!=null) {
            text(g,buildingName(b.type),x,98,w,GOLD);
            text(g,buildingState(b),x,117,w,LIGHT);
            text(g,tr("status."+(b.status==null?"idle":b.status)),x,132,w,0xFFC4AA7D);
            text(g,Component.literal(b.x+", "+b.y+", "+b.z),x,147,w,LIGHT);
            int progress=b.totalSteps()<=0?0:(int)((long)Math.clamp(b.completedSteps(),0,b.totalSteps())*(w-2)/b.totalSteps());
            g.fill(x,161,x+w,167,0xFF211B14);g.fill(x+1,162,x+1+progress,166,0xFFC8A05D);
            if(!b.complete&&b.foundationRemaining>0)text(g,tr("foundationRemaining",b.foundationRemaining),x,177,w,0xFFC4AA7D);
            var builder=snapshot.settlement().residents.stream().filter(r->r.id.toString().equals(b.assignedBuilder)).findFirst().orElse(null);
            if(builder!=null)text(g,tr("assignedBuilder",builder.name),x,220,w,GOLD);
        } else {
            int rows=catalogRows(),pages=(TYPES.length+rows-1)/rows;
            long wood=snapshot.settlement().resources.entrySet().stream().filter(entry->{
                var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.getKey()));
                return item!=null&&item.getDefaultInstance().is(net.minecraft.tags.ItemTags.LOGS);
            }).mapToLong(java.util.Map.Entry::getValue).sum();
            int stone=snapshot.settlement().resources.getOrDefault("minecraft:cobblestone",0);
            for(int i=catalogPage*rows;i<Math.min((catalogPage+1)*rows,TYPES.length);i++) {
                var type=BuildingType.byId(TYPES[i]);int y=111+(i-catalogPage*rows)*38;
                g.item(new ItemStack(net.minecraft.world.item.Items.OAK_LOG),x,y);
                g.item(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE),x+w/2,y);
                g.text(font,Integer.toString(type.woodCost),x+19,y+4,wood>=type.woodCost?0xFFB4D28A:0xFFFF9A85,false);
                g.text(font,Integer.toString(type.stoneCost),x+w/2+19,y+4,stone>=type.stoneCost?0xFFB4D28A:0xFFFF9A85,false);
                if(mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+17)
                    g.setTooltipForNextFrame(tr("costAvailable",type.woodCost,wood,type.stoneCost,stone),mouseX,mouseY);
            }
            centered(g,Component.literal((catalogPage+1)+" / "+pages),x+21,height-37,w-42,GOLD);
            text(g,tr(buildChoice.isEmpty()?"chooseBuilding":"placementHint"),x,height-17,w,0xFFC4AA7D);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(inMap(event.x(),event.y())&&snapshot.settlement()!=null) {
            if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT&&!buildChoice.isEmpty()) {buildChoice="";return true;}
            if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return super.mouseClicked(event,doubleClick);
            var s=snapshot.settlement();
            if(!buildChoice.isEmpty()) {
                int dx=worldDx(event.x()),dz=worldDz(event.y()),halfW=halfWidth(buildChoice);
                // Always ask the authoritative server. Rejections must remain visible in the map.
                action("build",buildChoice,((long)dx<<32)|(dz&0xffffffffL));
                feedback=tr("placementPending");feedbackSuccess=false;feedbackPending=true;feedbackUntil=System.currentTimeMillis()+7000;
                return true;
            }
            for(var r:s.residents) {
                var p=snapshot.positions().get(r.id.toString());if(p==null)continue;
                if(Math.hypot(event.x()-mapX(p[0]-s.x),event.y()-mapZ(p[1]-s.z))<6) {
                    SettlementNetwork.request("endDialogue","","","",0);citizen=r.id.toString();tab=1;rebuildWidgets();return true;
                }
            }
            for(var b:buildings()) {
                int hw=halfWidth(b.type);
                if(Math.abs(event.x()-mapX(b.x-s.x))<Math.max(5,(hw+1)*zoom)&&Math.abs(event.y()-mapZ(b.z-s.z))<Math.max(5,(halfDepth(b.type)+1)*zoom)) {
                    SettlementNetwork.request("endDialogue","","","",0);selectedBuilding=b.id;buildingDetails=true;tab=3;rebuildWidgets();return true;
                }
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(event.button()==InputConstants.MOUSE_BUTTON_LEFT&&inMap(event.x(),event.y())&&buildChoice.isEmpty()) {panX-=dx/zoom;panZ-=dy/zoom;return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(inMap(x,y)) {zoom=Math.clamp(zoom*Math.pow(1.15,dy),.25,5);return true;}
        if(x>=panelX&&tab==1&&dy!=0) {selectResident(dy>0?-1:1);return true;}
        if(x>=panelX&&tab==2&&dy!=0) {
            int maxPage=Math.max(0,(snapshot.settlement().resources.size()-1)/resourceRows());
            resourcePage=Math.clamp(resourcePage+(dy>0?-1:1),0,maxPage);rebuildWidgets();return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void removed() {terrainTexture.close();SettlementNetwork.request("close","","","",0);}
}

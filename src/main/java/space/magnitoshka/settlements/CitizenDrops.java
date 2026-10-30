package space.magnitoshka.settlements;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import space.magnitoshka.settlements.domain.*;

/** Real inventory at death; uses vanilla captureDrops/LivingDrops instead of the hall return queue. */
public final class CitizenDrops {
    public static void drop(CitizenEntity citizen,ServerLevel level,Settlement settlement,Resident resident) {process(citizen,level,settlement,resident,true);}
    public static void suppressed(CitizenEntity citizen,ServerLevel level,Settlement settlement,Resident resident) {process(citizen,level,settlement,resident,false);}
    private static void process(CitizenEntity citizen,ServerLevel level,Settlement settlement,Resident resident,boolean emit) {
        if(resident!=null) {
            citizen.recognizeLegacyHand(resident);
            var job=settlement.buildings.stream().filter(b->b.id.equals(resident.cargoJob)).findFirst().orElse(null);
            for(var slot:resident.builderInventory.slots)if(slot.count>0) {
                var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(slot.item));
                int remaining=slot.count;
                while(remaining>0) {
                    int count=Math.min(remaining,item.getDefaultMaxStackSize());
                    if(emit)citizen.spawnAtLocation(level,new ItemStack(item,count));remaining-=count;
                }
                if(job!=null&&!job.complete) {
                    if(slot.origin.equals("base")) {
                        if(Construction.isWood(slot.item))job.lostWood+=slot.count;
                        else if(slot.item.equals("minecraft:cobblestone"))job.lostStone+=slot.count;
                    } else if(slot.origin.equals("prepaid_fill"))job.lostPrepaidFill+=slot.count;
                }
                slot.count=0;slot.item="";slot.origin="";
            }
        }
        for(var slot:EquipmentSlot.VALUES) {
            var stack=citizen.getItemBySlot(slot);
            if(stack.isEmpty())continue;
            boolean display=slot==EquipmentSlot.MAINHAND&&citizen.isDisplayHand();
            if(emit&&!display&&!EnchantmentHelper.has(stack,EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))
                citizen.spawnAtLocation(level,stack.copy());
            // Remove the source too: vanilla equipment dropping must not emit another copy.
            citizen.setItemSlot(slot,ItemStack.EMPTY);
        }
    }
    private CitizenDrops() {}
}

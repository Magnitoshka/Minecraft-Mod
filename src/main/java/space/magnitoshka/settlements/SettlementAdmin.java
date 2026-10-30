package space.magnitoshka.settlements;

import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import space.magnitoshka.settlements.domain.*;

/** Explicit OP-only editing; normal map actions continue to require settlement ownership. */
public final class SettlementAdmin {
    public static void register(RegisterCommandsEvent event) {
        var root=Commands.literal("settlementadmin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        root.executes(c->{c.getSource().sendSuccess(()->Component.translatable("admin.smartsettlements.help"),false);return 1;});
        root.then(Commands.literal("list").executes(c->{
            var data=SettlementData.get(c.getSource().getServer());
            for(var s:data.state.settlements.values()) c.getSource().sendSuccess(()->Component.literal(s.id+" · "+s.name+" · "+s.dimension+" "+s.x+" "+s.y+" "+s.z),false);
            return data.state.settlements.size();
        }));
        for(String operation:new String[]{"set","add"}) {
            root.then(Commands.literal("wallet").then(Commands.literal(operation)
                .then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(0))
                    .executes(c->wallet(c,operation))))));
            for(String section:new String[]{"treasury","food"}) root.then(Commands.literal(section)
                .then(target().then(Commands.literal(operation).then(Commands.argument("amount",LongArgumentType.longArg(0))
                    .executes(c->edit(c,section,operation))))));
        }
        for(String operation:new String[]{"add","remove"})root.then(Commands.literal("residents")
            .then(target().then(Commands.literal(operation).then(Commands.argument("count",IntegerArgumentType.integer(1,64))
                .executes(c->residents(c,operation))))));
        event.getDispatcher().register(root);
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> target() {
        return Commands.argument("settlement",StringArgumentType.word()).suggests((c,b)->{
            b.suggest("nearest");
            for(var s:SettlementData.get(c.getSource().getServer()).state.settlements.values())b.suggest(s.id.toString());
            return b.buildFuture();
        });
    }
    private static Settlement settlement(CommandContext<CommandSourceStack> c)throws CommandSyntaxException {
        var source=c.getSource();var all=SettlementData.get(source.getServer()).state.settlements;
        String id=StringArgumentType.getString(c,"settlement");
        if(id.equals("nearest"))return all.values().stream().filter(s->s.dimension.equals(source.getLevel().dimension().identifier().toString()))
            .min(Comparator.comparingDouble(s->source.getPosition().distanceToSqr(s.x,s.y,s.z))).orElseThrow(()->error("missing"));
        try {var s=all.get(UUID.fromString(id));if(s!=null)return s;}catch(IllegalArgumentException ignored){}
        throw error("missing");
    }
    private static int wallet(CommandContext<CommandSourceStack> c,String operation)throws CommandSyntaxException {
        var player=EntityArgument.getPlayer(c,"player");var data=SettlementData.get(c.getSource().getServer());
        long n=LongArgumentType.getLong(c,"amount"),old=data.state.wallets.getOrDefault(player.getUUID(),0L);
        if(operation.equals("add")) {if(old>Long.MAX_VALUE-n)throw error("overflow");n+=old;}
        data.state.wallets.put(player.getUUID(),n);data.setDirty();SettlementNetwork.refresh(player);
        return success(c,"wallet",n);
    }
    private static int edit(CommandContext<CommandSourceStack> c,String section,String operation)throws CommandSyntaxException {
        var s=settlement(c);long n=LongArgumentType.getLong(c,"amount");var data=SettlementData.get(c.getSource().getServer());
        if(section.equals("treasury")) {
            if(operation.equals("add")) {if(s.treasury>Long.MAX_VALUE-n)throw error("overflow");n+=s.treasury;}
            s.treasury=n;
        } else {
            long food=s.resources.entrySet().stream().filter(e->ServerSettlements.nutrition(e.getKey())>0).mapToLong(Map.Entry::getValue).sum();
            long free=Construction.capacity(s)-s.stored()+(operation.equals("set")?food:0);
            if(n>free||n>Integer.MAX_VALUE)throw error("capacity");
            if(operation.equals("set")) {s.resources.keySet().removeIf(k->ServerSettlements.nutrition(k)>0);s.foodCredit=0;}
            s.deposit("minecraft:bread",(int)n,Construction.capacity(s));
        }
        data.setDirty();refresh(c.getSource());return success(c,section,n);
    }
    private static int residents(CommandContext<CommandSourceStack> c,String operation)throws CommandSyntaxException {
        var s=settlement(c);int count=IntegerArgumentType.getInteger(c,"count");
        var source=c.getSource();var level=source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(s.dimension)));
        if(level==null)throw error("missing");
        if(operation.equals("add")) {
            int beds=ServerSettlements.countBeds(level,s);
            if(count>(long)beds-s.residents.size())throw error("beds");
            String[] names={"Alden","Rowan","Elara","Bryn","Cedric","Mira","Torin","Lyra","Gareth","Nora","Finley","Freya"};
            for(int i=0;i<count;i++) {
                var r=new Resident(names[level.getRandom().nextInt(names.length)]);s.residents.add(r);ServerSettlements.spawn(level,s,r);
            }
        } else {
            if(count>s.residents.size())throw error("residents");
            for(int i=0;i<count;i++) {var r=s.residents.removeLast();space.magnitoshka.settlements.domain.BuilderAssignments.retire(s,r);var entity=level.getEntity(r.id);if(entity!=null)entity.discard();}
        }
        SettlementData.get(source.getServer()).setDirty();refresh(source);return success(c,"residents",s.residents.size());
    }
    private static void refresh(CommandSourceStack source) {for(var player:source.getServer().getPlayerList().getPlayers())SettlementNetwork.refresh(player);}
    private static int success(CommandContext<CommandSourceStack> c,String key,long value) {
        c.getSource().sendSuccess(()->Component.translatable("admin.smartsettlements.changed",key,value),true);return 1;
    }
    private static CommandSyntaxException error(String key) {return new SimpleCommandExceptionType(Component.translatable("admin.smartsettlements."+key)).create();}
    private SettlementAdmin() {}
}

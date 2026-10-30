package space.magnitoshka.settlements.domain;

import java.util.function.Predicate;

/** Exactly four persistent material slots. Origin preserves reservation ownership when returning cargo. */
public final class BuilderInventory {
    public static final int SLOTS=4,LIMIT=64;
    public static final class Stack {
        public String item="",origin="";
        public int count;
    }
    public Stack[] slots={new Stack(),new Stack(),new Stack(),new Stack()};
    public int count(String origin,Predicate<String> items) {
        int result=0;for(var slot:slots)if(slot.count>0&&slot.origin.equals(origin)&&items.test(slot.item))result+=slot.count;return result;
    }
    public int add(String item,String origin,int amount) {
        int remaining=Math.max(0,amount);
        for(var slot:slots)if(slot.count>0&&slot.item.equals(item)&&slot.origin.equals(origin)) {
            int moved=Math.min(remaining,LIMIT-slot.count);slot.count+=moved;remaining-=moved;
        }
        for(var slot:slots)if(slot.count==0&&remaining>0) {
            int moved=Math.min(remaining,LIMIT);slot.item=item;slot.origin=origin;slot.count=moved;remaining-=moved;
        }
        return amount-remaining;
    }
    public int take(String origin,Predicate<String> items,int amount) {
        int remaining=amount;
        for(var slot:slots)if(slot.count>0&&slot.origin.equals(origin)&&items.test(slot.item)) {
            int moved=Math.min(remaining,slot.count);slot.count-=moved;remaining-=moved;
            if(slot.count==0) {slot.item="";slot.origin="";}
        }
        return amount-remaining;
    }
    public int size() {int total=0;for(var slot:slots)total+=slot.count;return total;}
}

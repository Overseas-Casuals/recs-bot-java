package com.overseascasuals.recsbot.solver;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.HashMap;
import com.overseascasuals.recsbot.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkshopSchedule
{
    Logger LOG = LoggerFactory.getLogger(WorkshopSchedule.class);
    private List<ItemInfo> crafts;
    private List<Item> items; //Just a dupe of crafts, but accessible
    List<Integer> completionHours;
    public int currentIndex = 0; //Used for cycle scheduler to figure out crafts stuff

    Map<RareMaterial,Integer> rareMaterialsRequired;
    
    public WorkshopSchedule(List<Item> crafts)
    {
        completionHours = new ArrayList<Integer>();
        this.crafts = new ArrayList<ItemInfo>();
        items = new ArrayList<Item>();
        rareMaterialsRequired = new HashMap<>();
        setCrafts(crafts);
    }
    
    public void setCrafts(List<Item> newCrafts)
    {
        crafts.clear();
        newCrafts.forEach(item -> {crafts.add(Solver.items[item.ordinal()]);});
        int currentHour = 0;
        completionHours.clear();
        items.clear();
        for(ItemInfo craft : crafts)
        {
            currentHour+=craft.time;
            items.add(craft.item);
            
            completionHours.add(currentHour);
            
            if(craft.materialsRequired!= null)
                for(Entry<RareMaterial, Integer> indivMat : craft.materialsRequired.entrySet())
                    rareMaterialsRequired.put(indivMat.getKey(), rareMaterialsRequired.getOrDefault(indivMat.getKey(), 0) + indivMat.getValue()); 
        }
        
    }
    
    public ItemInfo getCurrentCraft()
    {
        if(currentIndex < crafts.size())
            return crafts.get(currentIndex);
        return null;
    }
    
    public int getNumCrafts()
    {
        return crafts.size();
    }
    
    public List<Item> getItems()
    {
        return items;
    }
    
    public boolean currentCraftCompleted(int hour)
    {
        if(currentIndex >= crafts.size())
            return false;
        
        if(completionHours.get(currentIndex) == hour)
            return true;
        return false;
    }
    
    public int getValueForCurrent(int day, int craftedSoFar, int currentGroove, boolean isEfficient)
    {
        ItemInfo craft = crafts.get(currentIndex);        
        int baseValue = craft.baseValue * Solver.WORKSHOP_BONUS * (100+currentGroove) / 10000;
        int supply = craft.getSupplyOnDay(day) + craftedSoFar;
        int adjustedValue = baseValue * craft.popularityRatio * ItemInfo.getSupplyBucket(supply).multiplier  / 10000;
        
        if(isEfficient)
            adjustedValue *= 2;
            LOG.trace(craft.item+" is worth "+adjustedValue +" with "+currentGroove+" groove at "+ItemInfo.getSupplyBucket(supply)+ " supply ("+supply+") and "+craft.popularityRatio+" popularity");
        
        return adjustedValue;
    }
    
    public boolean currentCraftIsEfficient()
    {
        if(currentIndex > 0 && currentIndex < crafts.size())
            if(crafts.get(currentIndex).getsEfficiencyBonus(crafts.get(currentIndex-1)))
                return true;
        
        return false;
    }
    
    public int getMaterialCost()
    {
        int cost = 0;
        for(ItemInfo craft : crafts)
        {
            cost+=craft.materialValue;
        }
        return cost;
    }
    
    public int getValueWithGrooveEstimate(int day, int startingGroove, boolean rested)
    {
        int craftsAbove4 = getNumCrafts() - 4;
        int daysToGroove = 6 - day;
        if (!rested)
            daysToGroove--;

        //How many days will it take to hit max normally
        int estimatedGroovePerDay = 9;
        int expectedEndingGroove = startingGroove + estimatedGroovePerDay;

        if (craftsAbove4 < 0)
            expectedEndingGroove -= 3;

        int craftingDaysLeft = daysToGroove;
        int fullDays = 0;
        int numRowsOfPartialDay = 0;
        while (craftingDaysLeft > 0 && expectedEndingGroove < Solver.GROOVE_MAX)
        {
            LOG.trace("Have {} crafting days after today, should end at {} groove, seeing what happens tomorrow after we get to {}", craftingDaysLeft, expectedEndingGroove, expectedEndingGroove+estimatedGroovePerDay);
            if (expectedEndingGroove + estimatedGroovePerDay <= Solver.GROOVE_MAX)
            {
                fullDays++;
                expectedEndingGroove += estimatedGroovePerDay;
                craftingDaysLeft--;
                LOG.trace("We can fit in a whole day");
            }
            else
            {
                int grooveToGo = Solver.GROOVE_MAX - expectedEndingGroove;
                numRowsOfPartialDay = (grooveToGo + 1) / 3;
                expectedEndingGroove = Solver.GROOVE_MAX;

                LOG.trace("There's {} groove left to add today, so lets say that's {} rows", grooveToGo, numRowsOfPartialDay);
            }
        }


        float grooveBonus = 0f;
        switch (numRowsOfPartialDay)
        {
            case 1:
                grooveBonus = fullDays + 0.15f;
                break;
            case 2:
                grooveBonus = fullDays + .5f;
                break;
            case 3:
                grooveBonus = fullDays + .65f;
                break;
            case 4:
                grooveBonus = fullDays + 1;
                break;
            default:
                grooveBonus = fullDays;
                break;
        }

        LOG.trace("Groove bonus {}% over {} days, with the last day giving {} rows", grooveBonus*craftsAbove4, daysToGroove, numRowsOfPartialDay);

        float valuePerDay = Solver.averageDayValue;

        grooveBonus = (grooveBonus * craftsAbove4 * valuePerDay) / 100f;

        int grooveValue = 0;

        if (daysToGroove > 0 && grooveBonus != 0)
        {
            grooveValue = (int)grooveBonus;
        }

        int workshopValue = 0;
        HashMap<Item,Integer> numCrafted = new HashMap<Item, Integer>(); 
        currentIndex = 0;
        for(int i=0; i<getNumCrafts(); i++)
        {
            ItemInfo completedCraft = getCurrentCraft();
            boolean efficient = currentCraftIsEfficient();
            workshopValue += getValueForCurrent(day, numCrafted.getOrDefault(completedCraft.item, 0), startingGroove + i*3, efficient);
            currentIndex++;
            int amountCrafted = efficient? 6 : 3;
            numCrafted.put(completedCraft.item, numCrafted.getOrDefault(completedCraft.item, 0) + amountCrafted);
        }
                
        //Allow for the accounting for materials if desired
        return grooveValue + workshopValue - (int)(getMaterialCost() * Solver.materialWeight);
    }
    
    public boolean usesTooMany(Map<Item,Integer> limitedUse)
    {
        if(limitedUse == null)
            return false;
        boolean tooMany = false;
       
        Map<Item, Integer> used = new HashMap<Item,Integer>();
            
            
        for(int i=0; i<items.size(); i++)
        {
            if(!used.containsKey(items.get(i)))
                used.put(items.get(i), 3+(i>0?3:0));
            else
                used.put(items.get(i), used.get(items.get(i)) + 3+(i>0?3:0));
        }
        for(Item key : used.keySet())
        {
            if(limitedUse.containsKey(key) && limitedUse.get(key) < used.get(key))
            {
//                if(Solver.verboseSolverLogging)
//                    System.out.println("Using too many "+key+" in schedule "+items+". Can only use "+limitedUse.get(key)+" but using "+used.get(key));
                return true;
            }
        }
        return false;
    }
    
    public Map<Item, Integer> getLimitedUses()
    {
        return getLimitedUses(null);
    }
    
    public Map<Item, Integer> getLimitedUses(Map<Item,Integer> previousLimitedUses)
    {
        Map<Item,Integer> limitedUses;
        if(previousLimitedUses == null)
            limitedUses = new HashMap<Item,Integer>();
        else
            limitedUses = new HashMap<Item,Integer>(previousLimitedUses);
        
        for(int i=0; i<items.size(); i++)
        {
            if(!limitedUses.containsKey(items.get(i)))
                limitedUses.put(items.get(i), 12);
            
            limitedUses.put(items.get(i), limitedUses.get(items.get(i))-3 - (i>0?3:0));
        }
        
        return limitedUses;
    }
    
    public boolean equals(Object other)
    {
        if(other instanceof WorkshopSchedule)
        {
            WorkshopSchedule otherWorkshop = (WorkshopSchedule)other;
            
            return rareMaterialsRequired.equals(otherWorkshop.rareMaterialsRequired);
            
        }
        return false;
    }
    
    public int hashCode()
    {
        return rareMaterialsRequired.hashCode();
    }
}

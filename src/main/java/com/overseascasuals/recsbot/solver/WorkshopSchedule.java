package com.overseascasuals.recsbot.solver;
import java.util.*;
import java.util.Map.Entry;

import com.overseascasuals.recsbot.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.overseascasuals.recsbot.solver.Solver.NUM_WORKSHOPS;

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
    
    public int getValueForCurrent(int day, int craftedSoFar, int currentGroove, boolean isEfficient, boolean verboseLogging)
    {
        ItemInfo craft = crafts.get(currentIndex);        
        int baseValue = craft.baseValue * Solver.WORKSHOP_BONUS * (100+currentGroove) / 10000;
        int supply = craft.getSupplyOnDay(day) + craftedSoFar;
        int adjustedValue = baseValue * craft.popularityRatio * ItemInfo.getSupplyBucket(supply).multiplier  / 10000;
        
        if(isEfficient)
            adjustedValue *= 2;
        if(verboseLogging)
            LOG.info(craft.item+" is worth "+adjustedValue +" with "+currentGroove+" groove at "+ItemInfo.getSupplyBucket(supply)+ " supply ("+supply+") and "+craft.popularityRatio+" popularity with peak "+craft.peak);
        
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
    
    public WorkshopValue getValueWithGrooveEstimate(int day, int startingGroove, boolean rested)
    {
        boolean verboseLogging = false;
        /*if
                (items.size() == 5 && items.get(0) == Item.BakedPumpkin && items.get(1) == Item.BoiledEgg && items.get(2) == Item.Horn
                        && items.get(3) == Item.Brush && items.get(4) == Item.Horn)
            verboseLogging = true;*/
        int craftsAbove4 = getNumCrafts() - 4;
        int daysToGroove = 6 - day;
        if (!rested)
            daysToGroove--;

        if (verboseLogging)
            LOG.info("Calculating workshop value for day {} and crafts {}  ({} above 4) Rested? {}. Crafting days after this: {}", day + 1, Arrays.toString(items.toArray()), craftsAbove4, rested, daysToGroove);

        //How many days will it take to hit max normally
        int estimatedGroovePerDay = 3 * NUM_WORKSHOPS;
        int expectedStartingGroove = startingGroove + estimatedGroovePerDay;

        boolean penalty = false;

        if (craftsAbove4 < 0)
        {
            penalty = true;
            expectedStartingGroove += NUM_WORKSHOPS * craftsAbove4;

            craftsAbove4 *= -1;
        }

        float grooveBonus = 0;
        for (int i = 0; i < craftsAbove4; i++)
        {
            int craftingDaysLeft = daysToGroove;
            int fullDays = 0;
            int numRowsOfPartialDay = 0;
            int expectedEndingGroove = expectedStartingGroove;
            while (craftingDaysLeft > 0 && expectedEndingGroove < Solver.GROOVE_MAX)
            {
                if(verboseLogging)
                    LOG.info("Have {} crafting days after today, should end at {} groove, seeing what happens tomorrow after we get to {}", craftingDaysLeft, expectedEndingGroove, expectedEndingGroove + estimatedGroovePerDay);
                if (expectedEndingGroove + estimatedGroovePerDay + NUM_WORKSHOPS - 1 <= Solver.GROOVE_MAX)
                {
                    fullDays++;
                    expectedEndingGroove += estimatedGroovePerDay;
                    craftingDaysLeft--;
                    if(verboseLogging)
                    LOG.info("We can fit in a whole day");
                }
                else
                {
                    int grooveToGo = Solver.GROOVE_MAX - expectedEndingGroove;
                    numRowsOfPartialDay = (grooveToGo + 1) / NUM_WORKSHOPS;
                    expectedEndingGroove = Solver.GROOVE_MAX;
                    if(verboseLogging)
                    LOG.info("There's {} groove left to add today for bonus craft #{}, so lets say that's {} rows", grooveToGo, i+1, numRowsOfPartialDay);
                }
            }

            switch (numRowsOfPartialDay) {
                case 1:
                    grooveBonus += fullDays + 0.10f;
                    break;
                case 2:
                    grooveBonus += fullDays + .5f;
                    break;
                case 3:
                    grooveBonus += fullDays + .60f;
                    break;
                case 4:
                    grooveBonus += fullDays + 1;
                    break;
                default:
                    grooveBonus += fullDays;
                    break;
            }

            expectedStartingGroove+=NUM_WORKSHOPS;
            if(verboseLogging)
                LOG.info("Groove bonus {}% over {} days, with the last day giving {} rows", grooveBonus, daysToGroove, numRowsOfPartialDay);
        }
        float valuePerDay = Solver.averageDayValue;

        grooveBonus = (grooveBonus * valuePerDay) / 100f;

        if (penalty)
            grooveBonus *= -1;

        int grooveValue = 0;

        if (daysToGroove > 0 && grooveBonus != 0)
        {
            grooveValue = (int)grooveBonus;
        }

        int workshopValue = 0;
        HashMap<Item,Integer> numCrafted = new HashMap<>();
        currentIndex = 0;
        for(int i=0; i<getNumCrafts(); i++)
        {
            ItemInfo completedCraft = getCurrentCraft();
            boolean efficient = currentCraftIsEfficient();
            int previouslyCrafted = numCrafted.getOrDefault(completedCraft.item, 0);
            int nextGroove = Math.min(startingGroove + i*NUM_WORKSHOPS, Solver.GROOVE_MAX);
            workshopValue += getValueForCurrent(day, previouslyCrafted, nextGroove, efficient, verboseLogging);
            currentIndex++;
            int amountCrafted = efficient? NUM_WORKSHOPS*2 : NUM_WORKSHOPS;
            numCrafted.put(completedCraft.item, previouslyCrafted + amountCrafted);

            if (verboseLogging)
                LOG.info("Processing craft {}, made previously: {}, efficient: {}", completedCraft.item, previouslyCrafted, efficient);
        }
                
        //Allow for the accounting for materials if desired
        WorkshopValue value = new WorkshopValue( workshopValue, grooveValue, getMaterialCost());
        return value;
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

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for(var item : items)
        {
            sb.append(item.getDisplayName()).append(", ");
        }
        sb.setLength(sb.length()-2);

        return sb.toString();
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

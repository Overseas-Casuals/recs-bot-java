package com.overseascasuals.recsbot.solver;
import java.util.*;
import java.util.Map.Entry;

import com.overseascasuals.recsbot.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.overseascasuals.recsbot.solver.Solver.*;


public class WorkshopSchedule
{
    Logger LOG = LoggerFactory.getLogger(WorkshopSchedule.class);

    private final List<ItemInfo> crafts;
    private final List<Item> items; //Just a dupe of crafts, but accessible
    List<Integer> completionHours;
    public int currentIndex = 0; //Used for cycle scheduler to figure out crafts stuff

    Map<RareMaterial,Integer> rareMaterialsRequired;
    private int rank;
    
    public WorkshopSchedule(List<Item> crafts, int rank)
    {
        completionHours = new ArrayList<>();
        this.crafts = new ArrayList<>();
        items = new ArrayList<>();
        rareMaterialsRequired = new HashMap<>();
        setCrafts(crafts);
        this.rank = rank;
    }
    
    public void setCrafts(List<Item> newCrafts)
    {
        crafts.clear();
        newCrafts.forEach(item -> crafts.add(Solver.items[item.ordinal()]));
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

        return completionHours.get(currentIndex) == hour;
    }
    
    public int getValueForCurrent(int day, int craftedSoFar, int currentGroove, boolean isEfficient, boolean verboseLogging)
    {
        ItemInfo craft = crafts.get(currentIndex);        
        //int baseValue = craft.baseValue * Solver.getWorkshopBonus(rank) * (100+currentGroove) / 10000;
        int baseValue = (int)(craft.baseValue * (Solver.getWorkshopBonus(rank)/100f) * (100+currentGroove) / 100);
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
            return crafts.get(currentIndex).getsEfficiencyBonus(crafts.get(currentIndex - 1));
        
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
    
    public WorkshopValue getValueWithGrooveEstimate(int day, int startingGroove, boolean rested, Map<Item,ReservedHelper> reservedHelpers, boolean subSchedule) {
        boolean verboseLogging = false;

        /*if(day == 3 && items.size() == 4 && items.get(0) == Item.Brush && items.get(1) == Item.SharkOil && items.get(2) == Item.Brush
                && items.get(3) == Item.SpruceRoundShield)
            verboseLogging = true;*/

        /*if(day == 1 && items.size() == 5 && items.get(0) == Item.Dressing && items.get(1) == Item.Butter && items.get(2) == Item.Horn
                && items.get(3) == Item.BoiledEgg && items.get(4) == Item.CawlCennin)
            verboseLogging = true;*/

        /*if(day == 3 && items.size() == 6 && items.get(0) == Item.Butter && items.get(1) == Item.Dressing && items.get(2) == Item.Butter
                && items.get(3) == Item.Dressing && items.get(4) == Item.Butter && items.get(5) == Item.Dressing)
            verboseLogging = true;*/

        int expectedGroove = 3;
        int effCrafts = 0;
        for (int i = 1; i < crafts.size(); i++)
        {
            if(crafts.get(i-1).getsEfficiencyBonus(crafts.get(i)))
                effCrafts++;
        }

        int deltaGroove = effCrafts - expectedGroove;
        int daysToGroove = 6 - day;
        if (!rested)
            daysToGroove--;

        if (verboseLogging)
            LOG.info("Calculating workshop value for day {} and crafts {}  ({} above 4) Rested? {}. Crafting days after this: {}", day + 1, Arrays.toString(items.toArray()), deltaGroove, rested, daysToGroove);

        //How many days will it take to hit max normally
        int estimatedGroovePerDay = expectedGroove * Solver.getNumWorkshops(rank);
        int expectedStartingGroove = startingGroove + estimatedGroovePerDay;

        boolean groovePenalty = false;

        if (deltaGroove < 0)
        {
            groovePenalty = true;
            expectedStartingGroove += Solver.getNumWorkshops(rank) * deltaGroove;

            if(expectedStartingGroove < 0)
                expectedStartingGroove = 0;

            deltaGroove *= -1;
        }

        float grooveBonus = 0;
        for (int i = 0; i < deltaGroove; i++)
        {
            int craftingDaysLeft = daysToGroove;
            int fullDays = 0;
            int numRowsOfPartialDay = 0;
            int expectedEndingGroove = expectedStartingGroove;
            while (craftingDaysLeft > 0 && expectedEndingGroove < Solver.getMaxGroove(rank))
            {
                if(verboseLogging)
                    LOG.info("Have {} crafting days after today, should end at {} groove, seeing what happens tomorrow after we get to {}", craftingDaysLeft, expectedEndingGroove, Math.min(expectedEndingGroove + estimatedGroovePerDay, Solver.getMaxGroove(rank)));
                if (expectedEndingGroove + estimatedGroovePerDay + Solver.getNumWorkshops(rank) - 1 <= Solver.getMaxGroove(rank))
                {
                    fullDays++;
                    expectedEndingGroove += estimatedGroovePerDay;
                    craftingDaysLeft--;
                    if(verboseLogging)
                        LOG.info("We can fit in a whole day");
                }
                else
                {
                    int grooveToGo = Solver.getMaxGroove(rank) - expectedEndingGroove;
                    numRowsOfPartialDay = (grooveToGo + 1) / Solver.getNumWorkshops(rank);
                    expectedEndingGroove = Solver.getMaxGroove(rank);
                    if(verboseLogging)
                        LOG.info("There's {} groove left to add today for bonus craft #{}, so lets say that's {} rows", grooveToGo, i+1, numRowsOfPartialDay);
                }
            }

            switch (numRowsOfPartialDay) {
                case 1 -> grooveBonus += fullDays + 0.10f;
                case 2 -> grooveBonus += fullDays + .5f;
                case 3 -> grooveBonus += fullDays + .60f;
                case 4 -> grooveBonus += fullDays + 1;
                default -> grooveBonus += fullDays;
            }

            expectedStartingGroove+=Solver.getNumWorkshops(rank);
            if(verboseLogging)
                LOG.info("Groove bonus {}% over {} days, with the last day giving {} rows", grooveBonus, daysToGroove, numRowsOfPartialDay);
        }
        float valuePerDay = Solver.getAverageDayValue(rank);

        grooveBonus = (grooveBonus * valuePerDay) / 100f;

        if (groovePenalty)
            grooveBonus *= -1;

        int grooveValue = 0;

        if (daysToGroove > 0 && grooveBonus != 0)
        {
            grooveValue = (int)grooveBonus;
        }

        int workshopValue = 0;
        HashMap<Item,Integer> numCrafted = new HashMap<>();
        currentIndex = 0;

        int prepeakBonus = 0;

        int numWorkshops = subSchedule?1:3;

        for(int i=0; i<getNumCrafts(); i++)
        {
            ItemInfo completedCraft = getCurrentCraft();
            boolean efficient = currentCraftIsEfficient();
            int previouslyCrafted = numCrafted.getOrDefault(completedCraft.item, 0);
            int nextGroove = Math.min(startingGroove + i*Solver.getNumWorkshops(rank), Solver.getMaxGroove(rank));
            int currentValue = getValueForCurrent(day, previouslyCrafted, nextGroove, efficient, verboseLogging);

            if((strongRatio62>0 || strongRatio63 >0) && day == 1 && completedCraft.peak == PeakCycle.Cycle2Unknown)
            {
                double ratio = 0;
                if(completedCraft.item.ordinal() < 50)
                    ratio = strongRatio62;
                else
                    ratio = strongRatio63;


                double newSupplyMult = (160 * ratio + 130 * (1-ratio));
                int weightedValue = (int)(currentValue * newSupplyMult / 130);
                int diff  = weightedValue - currentValue;
                if(verboseLogging)
                {
                    LOG.info("Value of current craft {}, times new supply mult {} (from ratio {}) is {}. Diff: {}", currentValue, newSupplyMult, ratio, weightedValue, diff);
                }
                prepeakBonus += diff;
            }

            workshopValue += currentValue;
            currentIndex++;
            int amountCrafted = efficient? numWorkshops*2 : numWorkshops; //Only assume we're making up to 6 crafts
            numCrafted.put(completedCraft.item, previouslyCrafted + amountCrafted);

            if (verboseLogging)
                LOG.info("Processing craft {}, made previously: {}, efficient: {}", completedCraft.item, previouslyCrafted, efficient);
        }


        //Figure out if a penalty should apply for using a future item
        int helperPenalty = 0;
        if(reservedHelpers != null && day < 4)
        {
            for(var kvp : reservedHelpers.entrySet())
            {
                if(verboseLogging)
                    LOG.info("Checking helper {} for main item {}", kvp.getValue().item, kvp.getKey());
                if(!items.contains(kvp.getValue().item)) //We aren't using the helper so it's fine
                    continue;
                if(verboseLogging)
                    LOG.info("We're using helper {}", kvp.getValue().item);
                if(items.contains(kvp.getKey())) //We're using the main item so it's fine
                    continue;
                if(verboseLogging)
                    LOG.info("We're not using main item {}", kvp.getKey());
                ItemInfo mainItem = Solver.items[kvp.getKey().ordinal()];
                if(mainItem.peaksOnOrBeforeDay(day, null)) //Item has peaked already so it's fine
                    continue;
                if(verboseLogging)
                    LOG.info("Main item {} hasn't peaked yet", kvp.getKey());


                //None of the above conditions are true so it's not fine.
                //apply a penalty for x usages (2x if efficient)
                for(int i=0; i<items.size(); i++)
                {
                    if(items.get(i) == kvp.getValue().item)
                    {
                        if(verboseLogging)
                            LOG.info("We're using helper {} in position {}, so that's {}x the penalty of {}", kvp.getValue().item, i, i==0?1:2, kvp.getValue().penalty);
                        helperPenalty+=kvp.getValue().penalty*(i==0?1:2);
                    }
                }
            }
        }

        for(int i=0;i<crafts.size();i++)
        {
            if(crafts.get(i).couldPrePeak(day))
                prepeakBonus+= Solver.prepeakBonus *(i==0?1:2);
        }


        //Allow for the accounting for materials if desired

        var value = new WorkshopValue( workshopValue, grooveValue, getMaterialCost(), helperPenalty, prepeakBonus);

        if(verboseLogging)
            LOG.info("Schedule {} has workshop value {}, grooveValue {}, helperPenalty {}, and prepeak bonus {}. Weighted: {}", items, workshopValue, grooveValue, helperPenalty, prepeakBonus, value.getWeighted());


        return value;
    }
    
    public boolean usesTooMany(Map<Item,Integer> limitedUse, boolean subSchedule, boolean verboseLogging)
    {
        if(limitedUse == null || limitedUse.size() == 0)
            return false;
       
        Map<Item, Integer> used = new HashMap<>();


        /*if(limitedUse.size() == 9 && items.size() == 5 && items.get(0) == Item.CulinaryKnife && items.get(1) == Item.Butter && items.get(2) == Item.Jam
                && items.get(3) == Item.Butter && items.get(4) == Item.Jam)
            verboseLogging = true;*/
        int numWorkshops = 3;
        if(subSchedule)
            numWorkshops = 1;

        for(int i=0; i<items.size(); i++)
        {
            int amountMade = numWorkshops;
            if(i > 0 && Solver.items[items.get(i-1).ordinal()].getsEfficiencyBonus(Solver.items[items.get(i).ordinal()]))
                amountMade += numWorkshops;

            used.put(items.get(i), used.getOrDefault(items.get(i), 0) + amountMade);
        }
        for(Item key : used.keySet())
        {
            if(verboseLogging)
                LOG.info("Comparing used {}: {} to limit: {}", key, used.get(key), limitedUse.get(key));

            if(limitedUse.containsKey(key) && limitedUse.get(key) < used.get(key))
            {
                if(verboseLogging)
                    LOG.info("Using too many "+key+" in schedule "+items+". Can only use "+limitedUse.get(key)+" but using "+used.get(key));
                return true;
            }
        }
        return false;
    }
    public Map<Item, Integer> getLimitedUses(Map<Item,Integer> previousLimitedUses, boolean subSchedule)
    {
        int numWorkshops = 3;
        if(subSchedule)
            numWorkshops = 1;
        Map<Item,Integer> limitedUses;
        if(previousLimitedUses == null)
            limitedUses = new HashMap<>();
        else
            limitedUses = new HashMap<>(previousLimitedUses);
        
        for(int i=0; i<items.size(); i++)
        {
            if(!limitedUses.containsKey(items.get(i)))
                limitedUses.put(items.get(i), 12);

            boolean isEfficient = false;
            if(i > 0)
                isEfficient = Solver.items[items.get(i-1).ordinal()].getsEfficiencyBonus(Solver.items[items.get(i).ordinal()]);
            
            limitedUses.put(items.get(i), Math.max(limitedUses.get(items.get(i))-numWorkshops - (isEfficient?numWorkshops:0), 0));
        }
        
        return limitedUses;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if(items.size() > 0)
        {
            for(var item : items)
            {
                sb.append(item.getDisplayName()).append(", ");
            }
            sb.setLength(sb.length()-2);
        }
        else
            sb.append("No crafts");

        return sb.toString();
    }

    public boolean equals(Object other)
    {
        if(other instanceof WorkshopSchedule)
        {
            WorkshopSchedule otherWorkshop = (WorkshopSchedule)other;
            
            //return rareMaterialsRequired.equals(otherWorkshop.rareMaterialsRequired);
            return items.equals(otherWorkshop.items);
            
        }
        return false;
    }

    public boolean isItemSuperset(Set<Map<RareMaterial, Integer>> otherItemSets)
    {
        for(var itemSet : otherItemSets)
        {
            if(rareMaterialsRequired.equals(itemSet))
                return true;
            boolean isSuperset = true;
            for(var entry : itemSet.entrySet())
            {
                //If we don't need this item or we need less of it
                if(!rareMaterialsRequired.containsKey(entry.getKey()) || rareMaterialsRequired.get(entry.getKey()) < entry.getValue())
                {
                    isSuperset = false;
                    break;
                }
            }
            //If we made it through each item without flipping to false, we're a strict superset of something we already have
            if(isSuperset)
            {
                return true;
            }
        }
        //If we made it through each set without returning true, we aren't a superset of anything
        return false;
    }
    public boolean interferesWithMe(List<Item> subSchedule, boolean verbose)
    {
        int currentHour = 0;
        for(var item : subSchedule)
        {
            if(items.contains(item))
            {
                int lastStartingHour = lastStartingHourForItem(item);
                if(verbose)
                    LOG.info("Item {} is contained in both the workshop and the suggested subschedule. Current hour {}, last starting hour {}", item, currentHour, lastStartingHour);
                if(currentHour < lastStartingHour)
                    return true;
            }
            currentHour += Solver.items[item.ordinal()].time;
        }
        return false;
    }

    private int lastStartingHourForItem(Item item)
    {
        int currentHour = 24;
        for(int i=items.size()-1; i>=0; i--)
        {
            var currentItem = items.get(i);
            currentHour -= Solver.items[currentItem.ordinal()].time;
            if(currentItem==item)
                return currentHour;
        }
        return -1;
    }
    
    public int hashCode()
    {
        //return rareMaterialsRequired.hashCode();
        return items.hashCode();
    }
}

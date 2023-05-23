package com.overseascasuals.recsbot.solver;
import java.util.List;
import java.util.HashMap;
import com.overseascasuals.recsbot.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CycleSchedule
{
    Logger LOG = LoggerFactory.getLogger(CycleSchedule.class);
    int day;
    private int startingGroove;

    private int endingGroove;
    WorkshopSchedule[] workshops = new WorkshopSchedule[3];
    HashMap<Item, Integer> numCrafted;
    
    public CycleSchedule(int day, int groove)
    {
        this.day = day;
        startingGroove = groove;
    }
    
    public void setForAllWorkshops(List<Item> crafts)
    {
        workshops[0] = new WorkshopSchedule(crafts);
        workshops[1] = new WorkshopSchedule(crafts);
        workshops[2] = new WorkshopSchedule(crafts);
    }
    
    public void setWorkshop(int index, List<Item> crafts)
    {
        if(workshops[index] == null)
            workshops[index] = new WorkshopSchedule(crafts);
        else
            workshops[index].setCrafts(crafts);
    }

    public List<Item> getItems()
    {
        return workshops[0].getItems();
    }

    public int getValue()
    {
        return getValue(false);
    }
    public int getValue(boolean verbose)
    {
       numCrafted = new HashMap<>();
       
       for(int i=0; i<workshops.length;i++)
           workshops[i].currentIndex = 0;
       
       int currentGroove = startingGroove;
       
       int totalCowries = 0;
       for(int hour = 4; hour <=24; hour+=2) //Nothing can finish until hour 4
       {
           HashMap<Item, Integer> craftsToAdd = new HashMap<Item,Integer>();
           int grooveToAdd = 0;
           int cowriesThisHour = 0;
           for(int i=0; i<workshops.length;i++)
           {
               if(workshops[i].currentCraftCompleted(hour))
               {
                   ItemInfo completedCraft = workshops[i].getCurrentCraft();
                   boolean efficient = workshops[i].currentCraftIsEfficient();
                   craftsToAdd.put(completedCraft.item, craftsToAdd.getOrDefault(completedCraft.item, 0) + (efficient? 2 : 1));

                   cowriesThisHour += workshops[i].getValueForCurrent(day, numCrafted.getOrDefault(completedCraft.item, 0), currentGroove, efficient, verbose);
                   
                   workshops[i].currentIndex++;
                   if(workshops[i].currentCraftIsEfficient())
                       grooveToAdd++;
               }
           }
           if(verbose && cowriesThisHour > 0)
                LOG.info("hour "+hour+": "+cowriesThisHour);
           
           totalCowries += cowriesThisHour;
           currentGroove += grooveToAdd;
           if(currentGroove > Solver.GROOVE_MAX)
               currentGroove = Solver.GROOVE_MAX;
           craftsToAdd.forEach((k, v) ->  {numCrafted.put(k, numCrafted.getOrDefault(k, 0) + v); });
           
       }
       
       endingGroove = currentGroove;
       
       return totalCowries;
       
    }
    
    public int getMaterialCost()
    {
        int cost = 0;
        for(WorkshopSchedule shop : workshops)
        {
            cost+=shop.getMaterialCost();
        }
        return cost;
    }
    
    @Override
    public String toString()
    {
        return "Day: "+(day+1)+", Items: " + workshops[0].toString() + ", Starting groove: "+startingGroove+", Ending groove: "+endingGroove;
    }
    public boolean equals(Object other)
    {
        if(other instanceof CycleSchedule)
        {
            return workshops.equals(((CycleSchedule)other).workshops);
        }
        return false;
    }
    
    public int hashCode()
    {
        return workshops.hashCode();
    }

    public int getStartingGroove() {
        return startingGroove;
    }
    public int getEndingGroove() {
        return endingGroove;
    }
    public void setStartingGroove(int startingGroove) {
        this.startingGroove = startingGroove;
    }
}

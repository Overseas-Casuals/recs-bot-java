package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BruteForceSchedules extends ArrayList<Map.Entry<WorkshopSchedule, WorkshopValue>>
{
    private static Logger LOG = LoggerFactory.getLogger(BruteForceSchedules.class);

    private List<Item> bestSubItems;
    private CycleSchedule bestRec;
    private int day;
    private int startingGroove;
    public BruteForceSchedules(List<Map.Entry<WorkshopSchedule, WorkshopValue>> list, int day, int startingGroove)
    {
        super(list);
        this.day =day;
        this.startingGroove = startingGroove;
    }

    public void setBestSubItems(HashMap<WorkshopSchedule, WorkshopValue> safeSchedules, boolean rested, Map<Item, ReservedHelper> reservedHelpers, int rank)
    {
        bestRec = new CycleSchedule(day, startingGroove, rank);
        if(size() == 0)
            return;

        boolean verboseLogging = false;
        /*if(get(0).getKey().getItems().get(0)==Item.BoiledEgg &&get(0).getKey().getItems().get(1)==Item.SheepfluffRug && get(0).getKey().getItems().get(2)==Item.Bed)
            verboseLogging = true;*/
        bestRec.setForFirstThreeWorkshops(get(0).getKey().getItems());

        if(rank < 15)
            return;

        int bestValue = 0;

        for(var schedule : safeSchedules.keySet())
        {
            bestRec.setFourthWorkshop(schedule.getItems());
            bestRec.setGrooveBonus(rested, reservedHelpers);
            int currentValue = bestRec.getWeightedValue();
            if(currentValue > bestValue)
            {
                bestValue = currentValue;
                bestSubItems = schedule.getItems();
            }
        }

        bestRec.setFourthWorkshop(bestSubItems);
        bestRec.setGrooveBonus(rested, reservedHelpers);

        if(verboseLogging)
            LOG.info("Best schedule for C{}: {} {} ({})", day+1, bestValue, bestRec.getItems(), bestRec.getSubItems());


        //try second best
        List<Item> secondBestSubItems = null;
        var secondBest = new CycleSchedule(day, startingGroove, rank);
        secondBest.setForFirstThreeWorkshops(get(1).getKey().getItems());
        int secondBestValue = 0;
        for(var schedule : safeSchedules.keySet())
        {
            secondBest.setFourthWorkshop(schedule.getItems());
            secondBest.setGrooveBonus(rested, reservedHelpers);
            int currentValue = secondBest.getWeightedValue();
            if(currentValue > secondBestValue)
            {
                secondBestValue = currentValue;
                secondBestSubItems = schedule.getItems();
            }
        }
        secondBest.setFourthWorkshop(secondBestSubItems);
        secondBest.setGrooveBonus(rested, reservedHelpers);

       if(secondBestValue > bestValue)
        {
            if(verboseLogging)
                LOG.info("Second best schedule with sub is worth more than best with sub");
            this.bestSubItems = secondBestSubItems;
            bestRec = secondBest;
            bestValue = secondBestValue;
        }
    }

    public List<Item> getBestSubItems()
    {
        return bestSubItems;
    }

    public CycleSchedule getBestRec()
    {
        return bestRec;
    }
}

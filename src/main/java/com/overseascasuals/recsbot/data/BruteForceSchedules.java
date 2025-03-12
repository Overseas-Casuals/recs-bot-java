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

    public void setBestSubItems(List<Map.Entry<WorkshopSchedule, WorkshopValue>> safeSchedules, boolean rested, Map<Item, ReservedHelper> reservedHelpers, int rank)
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

        int numChecked = 0;
        int bestIndex = 0;
        for(var schedule : safeSchedules)
        {
            numChecked++;

            bestRec.setFourthWorkshop(schedule.getKey().getItems());
            bestRec.setGrooveBonus(rested, reservedHelpers);
            int currentValue = bestRec.getWeightedValue();
            if(numChecked > 15 && day == 1)
            {
                //LOG.info("Checking schedule "+schedule.getKey().getItems()+". Total day value: "+currentValue+". Best value: "+bestValue);
            }
            if(currentValue > bestValue)
            {
                //LOG.info("New best is the #"+numChecked+" schedule checked");
                bestValue = currentValue;
                bestSubItems = schedule.getKey().getItems();
                bestIndex = numChecked;
            }
            if(numChecked - bestIndex > 500) //If we've checked this many past the last time we had a good schedule, it's safe to quit
            {
                //LOG.info("Checked the best "+numChecked+" out of "+safeSchedules.size()+". Quitting");
                break;
            }
        }

        bestRec.setFourthWorkshop(bestSubItems);
        bestRec.setGrooveBonus(rested, reservedHelpers);

        if(verboseLogging)
            LOG.info("Best schedule for C{}: {} {} ({})", day+1, bestValue, bestRec.getItems(), bestRec.getSubItems());

        CycleSchedule secondBestRec = new CycleSchedule(day, startingGroove, rank);
        secondBestRec.setForFirstThreeWorkshops(get(1).getKey().getItems());
        numChecked = 0;
        bestIndex = 0;
        int secondBestValue = 0;
        List<Item> secondBestSubItems = null;
        for(var schedule : safeSchedules)
        {
            numChecked++;

            secondBestRec.setFourthWorkshop(schedule.getKey().getItems());
            secondBestRec.setGrooveBonus(rested, reservedHelpers);
            int currentValue = secondBestRec.getWeightedValue();
            if(currentValue > secondBestValue)
            {
                //LOG.info("New best is the #"+numChecked+" schedule checked");
                secondBestValue = currentValue;
                secondBestSubItems = schedule.getKey().getItems();
                bestIndex = numChecked;
            }
            if(numChecked - bestIndex > 500) //If we've checked this many past the last time we had a good schedule, it's safe to quit
            {
                //LOG.info("Checked the best "+numChecked+" out of "+safeSchedules.size()+". Quitting");
                break;
            }
        }

        secondBestRec.setFourthWorkshop(secondBestSubItems);
        secondBestRec.setGrooveBonus(rested, reservedHelpers);

        if(secondBestValue>bestValue)
        {
            bestRec=secondBestRec;
            bestSubItems = secondBestSubItems;
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

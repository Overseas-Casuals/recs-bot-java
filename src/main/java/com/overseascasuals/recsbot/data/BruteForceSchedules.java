package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.scheduled.GetPeaksTask;
import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    public void setBestSubItems(List<Item> bestSubItems,List<Item> secondBestSubItems, boolean rested, Map<Item, ReservedHelper> reservedHelpers, int rank)
    {
        this.bestSubItems = bestSubItems;
        bestRec = new CycleSchedule(day, startingGroove, rank);
        bestRec.setForFirstThreeWorkshops(get(0).getKey().getItems());
        bestRec.setFourthWorkshop(bestSubItems);
        bestRec.setGrooveBonus(rested, reservedHelpers);
        int bestValue = bestRec.getWeightedValue();

        //try second best
        if(secondBestSubItems.size() > 0)
        {
            var secondBest = new CycleSchedule(day, startingGroove, rank);
            secondBest.setForFirstThreeWorkshops(get(1).getKey().getItems());
            secondBest.setFourthWorkshop(secondBestSubItems);
            secondBest.setGrooveBonus(rested, reservedHelpers);
            int secondBestValue = secondBest.getWeightedValue();
            LOG.info("Best schedule: {}, second best schedule: {}", bestValue, secondBestValue);
            if(secondBestValue > bestValue)
            {
                LOG.info("Second best schedule with sub is worth more than best with sub");
                this.bestSubItems = secondBestSubItems;
                bestRec = secondBest;
                bestValue = secondBestValue;
            }
        }

        //try all 4 workshops the same
        if (!get(0).getKey().getItems().equals(bestSubItems) && bestSubItems.size() > 0)
        {
            CycleSchedule all4Rec = new CycleSchedule(day, startingGroove, rank);
            all4Rec.setForFirstThreeWorkshops(get(0).getKey().getItems());
            all4Rec.setFourthWorkshop(get(0).getKey().getItems());
            all4Rec.setGrooveBonus(rested, reservedHelpers);
            int all4Value = all4Rec.getWeightedValue();

            if (all4Value > bestValue)
            {
                LOG.info("all 4 the same as best is better: {}", all4Value);
                this.bestSubItems = get(0).getKey().getItems();
                bestRec = all4Rec;
                bestValue = all4Value;
            }
        }

        //try all 4 workshops the same as second best
        if (!get(1).getKey().getItems().equals(secondBestSubItems) && secondBestSubItems.size() > 0)
        {
            CycleSchedule all4Rec = new CycleSchedule(day, startingGroove, rank);
            all4Rec.setForFirstThreeWorkshops(get(1).getKey().getItems());
            all4Rec.setFourthWorkshop(get(1).getKey().getItems());
            all4Rec.setGrooveBonus(rested, reservedHelpers);
            int all4Value = all4Rec.getWeightedValue();

            if (all4Value > bestValue)
            {
                LOG.info("all 4 the same as second best is better: {}", all4Value);
                this.bestSubItems = get(1).getKey().getItems();
                bestRec = all4Rec;
                bestValue = all4Value;
            }
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

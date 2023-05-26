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

    public void setBestSubItems(List<Item> bestSubItems, boolean rested, Map<Item, ReservedHelper> reservedHelpers, int rank)
    {
        this.bestSubItems = bestSubItems;
        bestRec = new CycleSchedule(day, startingGroove, rank);
        bestRec.setForFirstThreeWorkshops(get(0).getKey().getItems());
        bestRec.setFourthWorkshop(bestSubItems);
        bestRec.setGrooveBonus(rested, reservedHelpers);
        if (!get(0).getKey().getItems().equals(bestSubItems) && bestSubItems.size() > 0)
        {
            int bestValue = bestRec.getWeightedValue();
            CycleSchedule all4Rec = new CycleSchedule(day, startingGroove, rank);
            all4Rec.setForFirstThreeWorkshops(get(0).getKey().getItems());
            all4Rec.setFourthWorkshop(get(0).getKey().getItems());
            all4Rec.setGrooveBonus(rested, reservedHelpers);
            int all4Value = all4Rec.getWeightedValue();
            LOG.info("4th WS different: {}, all 4 the same: {}", bestValue, all4Value);

            if (all4Value > bestValue)
            {
                this.bestSubItems = get(0).getKey().getItems();
                bestRec = all4Rec;
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

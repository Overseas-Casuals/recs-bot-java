package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BruteForceSchedules extends ArrayList<Map.Entry<WorkshopSchedule, WorkshopValue>>
{
    //Should be private
    public List<Item> bestSubItems;
    private CycleSchedule bestRec;
    private int day;
    private int startingGroove;
    public BruteForceSchedules(List<Map.Entry<WorkshopSchedule, WorkshopValue>> list, int day, int startingGroove)
    {
        super(list);
        this.day =day;
        this.startingGroove = startingGroove;
    }

    public void setBestSubItems(List<Item> bestSubItems, boolean rested, Map<Item, ReservedHelper> reservedHelpers)
    {
        this.bestSubItems = bestSubItems;
        bestRec = new CycleSchedule(day, startingGroove);
        bestRec.setForFirstThreeWorkshops(get(0).getKey().getItems());
        bestRec.setFourthWorkshop(bestSubItems);
        bestRec.setGrooveBonus(rested, reservedHelpers);
    }

    public CycleSchedule getBestRec()
    {
        return bestRec;
    }
}

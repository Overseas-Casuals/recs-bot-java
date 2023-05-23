package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.WorkshopSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BruteForceSchedules extends ArrayList<Map.Entry<WorkshopSchedule, WorkshopValue>>
{
    public List<Item> bestSubItems;
    public BruteForceSchedules(List<Map.Entry<WorkshopSchedule, WorkshopValue>> list)
    {
        super(list);
    }
}

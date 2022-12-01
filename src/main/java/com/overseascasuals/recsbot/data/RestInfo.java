package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.WorkshopSchedule;

public class RestInfo
{
    public int weightedValue;

    public boolean isWorst;

    public RestInfo(int weightedValue, boolean isWorst) {
        this.weightedValue = weightedValue;
        this.isWorst = isWorst;
    }
}

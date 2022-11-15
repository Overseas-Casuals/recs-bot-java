package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.Solver;

public class WorkshopValue implements Comparable<WorkshopValue> {
    int gross;
    int net;
    int weighted;

    public WorkshopValue(int gross, int materialCost) {
        this.gross = gross;
        this.net = gross-materialCost;
        this.weighted = gross - (int)(materialCost * Solver.materialWeight);
    }

    public int getGross() {
        return gross;
    }

    public int getNet() {
        return net;
    }

    public int getWeighted()
    {
     return weighted;
    }

    @Override
    public int compareTo(WorkshopValue o)
    {
        int comparator = weighted - o.weighted;
        if(comparator == 0)
            comparator = gross - o.gross;
        if(comparator == 0)
            comparator = net - o.net;
        return comparator;
    }
}

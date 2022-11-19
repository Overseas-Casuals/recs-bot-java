package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.Solver;

public class WorkshopValue implements Comparable<WorkshopValue> {
    int gross;
    int net;
    int weighted;
    int groove;

    public WorkshopValue(int gross, int grooveBonus, int materialCost) {
        this.gross = gross;
        this.net = gross-materialCost;
        groove = grooveBonus;
        this.weighted = gross + grooveBonus - (int)(materialCost * Solver.materialWeight);
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

    public int getGroove() {
        return groove;
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

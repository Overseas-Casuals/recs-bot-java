package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.CycleSchedule;

import java.util.List;

public class RestOfWeekRec
{
    private List<CycleSchedule> recs;
    private int worstIndex;
    private boolean rested;

    public RestOfWeekRec(List<CycleSchedule> recs, int worstIndex, boolean rested) {
        this.recs = recs;
        this.worstIndex = worstIndex;
        this.rested = rested;
    }

    public List<CycleSchedule> getRecs() {
        return recs;
    }

    public void setRecs(List<CycleSchedule> recs) {
        this.recs = recs;
    }

    public int getWorstIndex() {
        return worstIndex;
    }

    public boolean isRested() {
        return rested;
    }

    @Override
    public String toString() {
        return "RestOfWeekRec{" +
                "recs=" + recs +
                ", worstIndex=" + worstIndex +
                ", rested=" + rested +
                '}';
    }
}

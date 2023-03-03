package com.overseascasuals.recsbot.data;

import java.util.List;

public class RestOfWeekRec
{
    private List<List<Item>> recs;
    private int worstIndex;
    private boolean rested;

    public RestOfWeekRec(List<List<Item>> recs, int worstIndex, boolean rested) {
        this.recs = recs;
        this.worstIndex = worstIndex;
        this.rested = rested;
    }

    public List<List<Item>> getRecs() {
        return recs;
    }

    public void setRecs(List<List<Item>> recs) {
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

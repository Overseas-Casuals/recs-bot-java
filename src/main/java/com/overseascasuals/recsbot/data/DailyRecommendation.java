package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;

import java.util.*;

public class DailyRecommendation extends ArrayList<Map.Entry<WorkshopSchedule, WorkshopValue>>
{
    boolean restRecommended;
    CycleSchedule bestRec;
    int dailyValue;
    int groovelessValue;
    boolean tentative;
    Set<Item> troublemakers;
    int day;


    public DailyRecommendation(int day, List<Map.Entry<WorkshopSchedule, WorkshopValue>> recs)
    {
        super(recs);
        this.day = day;
        restRecommended = true;
        bestRec = null;
    }

    public DailyRecommendation(int day, List<Map.Entry<WorkshopSchedule, WorkshopValue>> recs, CycleSchedule bestRec, int value)
    {
        super(recs);
        this.day = day;
        restRecommended = false;
        this.bestRec = bestRec;
        dailyValue = value;
        bestRec.startingGroove = 0;
        groovelessValue = bestRec.getValue();
    }

    public boolean isRestRecommended() {
        return restRecommended;
    }

    public void setRestRecommended(boolean restRecommended) {
        this.restRecommended = restRecommended;
    }

    public CycleSchedule getBestRec() {
        return bestRec;
    }

    public void setBestRec(CycleSchedule bestRec) {
        this.bestRec = bestRec;
    }

    public int getDailyValue() {
        return dailyValue;
    }

    public void setDailyValue(int dailyValue) {
        this.dailyValue = dailyValue;
    }

    public int getGroovelessValue() {
        return groovelessValue;
    }

    public void setGroovelessValue(int groovelessValue) {
        this.groovelessValue = groovelessValue;
    }

    public boolean isTentative() {
        return tentative;
    }

    public Set<Item> getTroublemakers() {
        return troublemakers;
    }

    public void setTroublemakers(Set<Item> troublemakers) {
        if(troublemakers == null || troublemakers.size() == 0)
            return;
        tentative = true;
        this.troublemakers = troublemakers;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    private String prettyPrint(Map.Entry<WorkshopSchedule, WorkshopValue> rec)
    {
        return rec.getKey() +"\tGross: "+rec.getValue().getGross()+"\tNet: "+rec.getValue().getNet();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if(tentative)
            sb.append("__**TENTATIVE**__\n");
        if(restRecommended)
            sb.append("Rest.");
        else
        {
            sb.append(bestRec.toString()).append("\tValue: ").append(dailyValue).append("\tGrooveless: ").append(groovelessValue);
        }

        if(tentative)
        {
            sb.append("\nNeed peak info about ").append(Arrays.toString(troublemakers.toArray()));
        }

        sb.append("\nWorkshop Values:");
        for(var rec : this)
        {
            sb.append('\n').append(prettyPrint(rec));
        }

        return sb.toString();
    }
}

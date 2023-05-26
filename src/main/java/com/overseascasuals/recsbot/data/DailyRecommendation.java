package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;

import java.util.*;

public class DailyRecommendation extends ArrayList<Map.Entry<WorkshopSchedule, WorkshopValue>>
{
    int maxRank;
    boolean restRecommended;
    CycleSchedule bestRec;
    int dailyValue;
    int groovelessValue;
    boolean tentative;
    Set<Item> troublemakers;
    Set<Item> bystanders;
    int day;
    CycleSchedule oldRec;
    WorkshopValue oldValue;
    int oldGroovelessValue;


    public DailyRecommendation(int day, int rank, BruteForceSchedules recs, CycleSchedule bestRec, boolean resting)
    {
        this(day, rank, recs, bestRec);
        restRecommended = resting;
    }

    public DailyRecommendation(int day, int rank, BruteForceSchedules recs, CycleSchedule bestRec)
    {
        super(recs);
        this.maxRank = rank;
        this.day = day;
        restRecommended = false;
        this.bestRec = bestRec;
        /*if(rank>=15)
            this.bestRec.setFourthWorkshop(recs.bestSubItems);
        else
            this.bestRec.setFourthWorkshop(new ArrayList<>());*/
        int startingGroove = bestRec.getStartingGroove();
        bestRec.setStartingGroove(0);
        groovelessValue = bestRec.getValue();
        bestRec.setStartingGroove(startingGroove);
        dailyValue = bestRec.getValue();
    }

    public DailyRecommendation(int day, int rank, BruteForceSchedules recs, CycleSchedule bestRec, CycleSchedule oldRec)
    {
        this(day, rank, recs, bestRec);
        int startingGroove = oldRec.getStartingGroove();
        this.oldRec = oldRec;
        oldRec.setStartingGroove(0);
        oldGroovelessValue = oldRec.getValue();
        oldRec.setStartingGroove(startingGroove);
    }

    public DailyRecommendation withRank(int rank)
    {
        this.maxRank = rank;
        return this;
    }

    public boolean isRestRecommended() {
        return restRecommended;
    }


    public CycleSchedule getBestRec() {
        return bestRec;
    }


    public int getDailyValue() {
        return dailyValue;
    }

    public int getGroovelessValue() {
        return groovelessValue;
    }


    public boolean isTentative() {
        return tentative;
    }

    public Set<Item> getTroublemakers() {
        return troublemakers;
    }

    public Set<Item> getBystanders() {
        return bystanders;
    }

    public void setTroublemakers(Map<Item, Boolean> troublemakers, Set<Item> bystanders) {
        if(troublemakers == null || troublemakers.size() == 0)
            return;
        for(var value : troublemakers.values())
            if(!value)
                tentative = true;
        this.troublemakers = troublemakers.keySet();
        this.bystanders = bystanders;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getMaxRank() {
        return maxRank;
    }

    public String prettyPrint(Map.Entry<WorkshopSchedule, WorkshopValue> rec)
    {
        return rec.getKey() +"\tGross: "+rec.getValue().getGross()+"\tNet: "+rec.getValue().getNet()+"\tGroove bonus: "+rec.getValue().getGroove()+"\tPenalty: "+rec.getValue().getPenalty()+"\tPeak bonus: "+rec.getValue().getPeakBonus()+" Total: "+rec.getValue().getWeighted();
    }

    public CycleSchedule getOldRec()
    {
        return oldRec;
    }

    public int getOldGrooveless()
    {
        return oldGroovelessValue;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("Rank ").append(maxRank).append("\n");
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

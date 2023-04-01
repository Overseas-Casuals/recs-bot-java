package com.overseascasuals.recsbot.data;

import com.overseascasuals.recsbot.mysql.Popularity;
import com.overseascasuals.recsbot.solver.Solver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.overseascasuals.recsbot.data.PeakCycle.*;
import static com.overseascasuals.recsbot.data.PeakCycle.Unknown;

public class CraftContext
{
    private static final int[][] SUPPLY_PATH = {{0, 0, -8, 0, 0, 0, 0}, //Unknown
            {-4, -4, 10, 0, 0, 0, 0}, //Cycle2Weak
            {-8, -7, 15, 0, 0, 0, 0}, //Cycle2Strong
            {0, -4, -4, 10, 0, 0, 0}, //Cycle3Weak
            {0, -8, -7, 15, 0, 0, 0}, //Cycle3Strong
            {0, 0, -4, -4, 10, 0, 0}, //Cycle4Weak
            {0, 0, -8, -7, 15, 0, 0}, //Cycle4Strong
            {0, 0, 0, -4, -4, 10, 0}, //5Weak
            {0, 0, 0, -8, -7, 15, 0}, //5Strong
            {0, -1, 5, -4, -4, -4, 10}, //6Weak
            {0, -1, 8, -7, -8, -7, 15}, //6Strong
            {0, -1, 8, -3, -4, -4, -4}, //7Weak
            {0, -1, 8, 0, -7, -8, -7}, //7Strong
            {0, 0, 0, -8, 0, 10, 0}, //4/5
            {0, 0, 0, -4, -4, 10, 0}, //5
            {0, -1, 8, 0, -7, -8, 0}, //6/7
            {-4, -4, 10, 0, 0, 0, 0} //Cycle2Unknown
    };


    private final Set<Item> reservedItems = new HashSet<>();
    private final Map<Item, ReservedHelper> reservedHelpers = new HashMap<>();

    public Map<Integer, List<Item>> dailySchedules = new HashMap<>();

    private static Logger LOG = LoggerFactory.getLogger(CraftContext.class);
    private final Map<Integer, Integer> startingGroovePerDay = new HashMap<>();
    private List<PeakCycle> peaks;
    private List<Integer> popularity;
    private List<int[]> craftedPerDay;

    private int groove = 0;

    private int rested = -1;

    public CraftContext()
    {
        peaks = new ArrayList<>();
        popularity = new ArrayList<>();
        craftedPerDay = new ArrayList<>();
        startingGroovePerDay.put(0,0);
        startingGroovePerDay.put(1,0);
    }

    public CraftContext(CraftContext other)
    {
        this();

        for(int i=0;i<other.popularity.size(); i++)
        {
            peaks.add(other.peaks.get(i));
            popularity.add(other.popularity.get(i));
            craftedPerDay.add(new int[7]);
        }
    }

    public  Map<Item, ReservedHelper> getReservedHelpers()
    {
        return reservedHelpers;
    }
    public int getStartingGroove(int day)
    {
        return startingGroovePerDay.get(day);
    }
    public int getGroove()
    {
        return groove;
    }
    public void setGroove(int newGroove)
    {
        groove = newGroove;
    }
    public void setStartingGroovePerDay(int day, int groove)
    {
        startingGroovePerDay.put(day, groove);
    }
    public int getPopRatio(Item item)
    {
        return popularity.get(item.ordinal());
    }
    public PeakCycle getPeak(Item item)
    {
        return peaks.get(item.ordinal());
    }
    public void setPeak(Item item, PeakCycle peak)
    {
        peaks.set(item.ordinal(), peak);
    }
    public int getRested() {
        return rested;
    }

    public boolean restedByDay(int day)
    {
        return rested > 0 && rested <= day;
    }

    public void setRested(int rested) {
        this.rested = rested;
    }

    public void addInitialData(int pop, PeakCycle peak)
    {
        popularity.add(pop);
        peaks.add(peak);
        craftedPerDay.add(new int[7]);
    }

    public void setCrafted(Item item, int num, int day)
    {
        craftedPerDay.get(item.ordinal())[day]=num;
    }

    public int getCraftedOnDay(Item item, int day)
    {
        return craftedPerDay.get(item.ordinal())[day];
    }

    public void clearCrafted(Item item, int day)
    {
        craftedPerDay.get(item.ordinal())[day]=0;
    }

    private int getCraftedBeforeDay(Item item, int day)
    {
        int sum = 0;
        for(int c=0; c<day; c++)
            sum+=craftedPerDay.get(item.ordinal())[c];

        return sum;
    }

    public int getSupplyAfterCraft(Item item, int day, int newCrafts)
    {
        return getSupplyOnDay(item, day) + newCrafts;
    }

    public int getSupplyOnDay(Item item, int day)
    {
        PeakCycle peak = peaks.get(item.ordinal());
        int supply = SUPPLY_PATH[peak.ordinal()][0];
        for(int c=1;c <= day; c++)
        {
            supply += craftedPerDay.get(item.ordinal())[c-1];
            supply += SUPPLY_PATH[peak.ordinal()][c];
        }

        return supply;
    }

    public boolean peaksOnOrBeforeDay(Item item, int day)
    {
        int time = Solver.getHoursForItem(item);
        PeakCycle peak = peaks.get(item.ordinal());
        if(reservedItems.size()>0 && !reservedItems.contains(item))
            return true;

        if(time == 4) //We can always borrow 4hr crafts
            return true;
        if(peak == Cycle2Weak || peak == Cycle2Strong || peak == Cycle2Unknown)
            return day > 0;
        if (peak == Cycle3Weak || peak == Cycle3Strong || peak == Unknown)
            return day > 1;
        if(peak == Cycle4Weak || peak == Cycle4Strong || peak == Cycle45)
            return day > 2;
        if(peak == Cycle5Weak || peak == Cycle5Strong || peak == Cycle5)
            return day > 3;
        if(peak == Cycle6Weak || peak == Cycle6Strong || peak == Cycle67)
            return day > 4;
        if(peak == Cycle7Weak || peak == Cycle7Strong)
            return day > 5;

        LOG.warn("No peak data found? Peak {} Returning true", peak);
        return true;
    }

    public boolean peaksOnDay(Item item, int day)
    {
        PeakCycle peak = peaks.get(item.ordinal());
        if(peak == Cycle2Weak || peak == Cycle2Strong || peak == Cycle2Unknown)
            return day == 1;
        if (peak == Cycle3Weak || peak == Cycle3Strong)
            return day == 2;
        if(peak == Cycle4Weak || peak == Cycle4Strong)
            return day == 3;
        if(peak == Cycle5Weak || peak == Cycle5Strong || peak == Cycle5)
            return day == 4;
        if(peak == Cycle6Weak || peak == Cycle6Strong)
            return day == 5;
        if(peak == Cycle7Weak || peak == Cycle7Strong)
            return day == 6;

        if(peak == Unknown)
            return day > 1;
        if(peak == Cycle45)
            return day == 3 || day == 4;
        if(peak == Cycle67)
            return day == 5 || day == 6;

        return false;
    }

    public boolean couldPrePeak(Item item, int day)
    {
        PeakCycle peak = peaks.get(item.ordinal());
        if(peak == Cycle45)
            return day==2;
        if(peak == Unknown)
            return day==1;

        return false;
    }

    public String toString(Item item)
    {
        return item+", "+peaks.get(item.ordinal());
    }


}

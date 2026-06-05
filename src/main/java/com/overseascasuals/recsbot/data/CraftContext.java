package com.overseascasuals.recsbot.data;

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

    private Set<Item> reservedItems = new HashSet<>();
    private Map<Item, ReservedHelper> reservedHelpers = new HashMap<>();

    public Map<Integer,ScheduleSet> dailySchedules = new HashMap<>();

    private static Logger LOG = LoggerFactory.getLogger(CraftContext.class);
    private final Map<Integer, Integer> startingGroovePerDay = new HashMap<>();
    private List<PeakCycle> peaks;
    private List<Integer> popularity;
    private byte[] craftedPerDay; //Byte array where each item has 7 bytes
    private int groove = 0;
    private int rested = -1;
    private int week = -1;

    public CraftContext(int week)
    {
        this.week = week;
        craftedPerDay = new byte[Solver.items.length * 7];
        startingGroovePerDay.put(0,0);
        startingGroovePerDay.put(1,0);
    }

    public CraftContext(CraftContext other, int day)
    {
        this(other.week);

        //Ok for these to be references instead of deep copies, these are never modified
        peaks = other.peaks;
        popularity = other.popularity;
        reservedHelpers = other.reservedHelpers;
        reservedItems = other.reservedItems;

        //This must be a deep copy, we modify this all over the place
        for(int d=0;d<=day; d++)
        {
            for(int i = 0; i < craftedPerDay.length; i++)
            {
                craftedPerDay[i] = other.craftedPerDay[i];
            }
            startingGroovePerDay.put(d+1, other.startingGroovePerDay.get(d+1));
            //LOG.info("C"+(d+1)+"'s ending groove was "+startingGroovePerDay.get(d+1));
            dailySchedules.put(d, other.dailySchedules.get(d));
            //LOG.info("C"+(d+1)+"'s schedule was "+dailySchedules.get(d));
        }
        if(other.rested <= day)
            rested = day;
        //LOG.info("Rested C"+(rested+1));

        groove = other.startingGroovePerDay.get(day+1);
        //LOG.info("Current groove is "+groove);
    }

    public int getWeek() { return week; }
    public  Map<Item, ReservedHelper> getReservedHelpers()
    {
        return reservedHelpers;
    }
    public Set<Item> getReservedItems() { return reservedItems; }

    public void clearReserved()
    {
        reservedItems.clear();
        reservedHelpers.clear();
    }
    public int getStartingGroove(int day)
    {
        return startingGroovePerDay.get(day);
    }

    public int getStartingGroove(int day, int rank)
    {
        return startingGroovePerDay.get(day) * Solver.getNumWorkshops(rank) / Solver.getNumWorkshops(Solver.maxIslandRank) ;
    }

    public void setStartingGroove(int day, int groove)
    {
        startingGroovePerDay.put(day, groove);
    }

    public int getGroove()
    {
        return groove;
    }
    public void setGroove(int newGroove)
    {
        groove = newGroove;
    }
    public void addGroove(int grooveDiff) { groove += grooveDiff;}
    public int getPopRatio(Item item)
    {
        return popularity.get(item.ordinal());
    }
    public PeakCycle getPeak(Item item)
    {
        return peaks.get(item.ordinal());
    }
    public int getRested() {
        return rested;
    }
    public void setRested(int rested) {
        this.rested = rested;
    }
    public boolean restedAlready(int today)
    {
        return rested > 0 && rested <= today;
    }
    public void addInitialData(int pop, PeakCycle peak)
    {
        if(popularity == null)
            popularity = new ArrayList<>();
        if(peaks == null)
            peaks = new ArrayList<>();

        popularity.add(pop);
        peaks.add(peak);
    }

    public void setCrafted(Item item, int num, int day)
    {
        craftedPerDay[item.ordinal()*7+day] = (byte)num;
    }

    public int getCraftedOnDay(Item item, int day)
    {
        return craftedPerDay[item.ordinal()*7+day];
    }

    public void clearDayUsage(List<Integer> days)
    {
        for(Integer day : days)
        {
            for(int i = 0; i < Solver.items.length; i++)
            {
                craftedPerDay[i*7+day] = 0;
            }
        }
    }

    public void clearLateDayUsage()
    {
        clearDayUsage(List.of(4,5,6));
    }

    public int getSupplyOnDay(Item item, int day)
    {
        PeakCycle peak = peaks.get(item.ordinal());
        int supply = SUPPLY_PATH[peak.ordinal()][0];
        for(int c=1;c <= day; c++)
        {
            supply += craftedPerDay[item.ordinal()*7+c-1];
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

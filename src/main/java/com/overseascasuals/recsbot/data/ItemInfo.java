package com.overseascasuals.recsbot.data;
import com.overseascasuals.recsbot.messages.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.overseascasuals.recsbot.data.Supply.*;
import static com.overseascasuals.recsbot.data.DemandShift.*;
import static com.overseascasuals.recsbot.data.ItemCategory.*;
import static com.overseascasuals.recsbot.data.PeakCycle.*;

public class ItemInfo
{
    private static Logger LOG = LoggerFactory.getLogger(ItemInfo.class);
    //Contains exact supply values for concrete paths and worst-case supply values for tentative ones
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
            {0, 0, 0, -6, 0, 10, 0}, //4/5
            {0, 0, 0, -4, -4, 10, 0}, //5
            {0, -1, 8, 0, -7, -6, 0}, //6/7
            {-4, -4, 10, 0, 0, 0, 0} //Cycle2Unknown
            };
    
    private static final PeakCycle[][] PEAKS_TO_CHECK = {{Cycle3Weak, Cycle3Strong, Cycle67, Cycle45}, //Day2
            {Cycle4Weak, Cycle4Strong, Cycle6Weak, Cycle5, Cycle67}, //Day3
            {Cycle5Weak, Cycle5Strong, Cycle6Strong, Cycle7Weak, Cycle7Strong}}; //Day4
    
    //Constant info
    public Item item;
    public int baseValue;
    ItemCategory category1;
    ItemCategory category2;
    public int time;
    public Map<RareMaterial, Integer> materialsRequired;
    public int materialValue;
    public int rankUnlocked;
    
    //Weekly info
    public int popularityRatio;
    public PeakCycle peak = Unknown;
    public int[] craftedPerDay;
    
    public ItemInfo(Item i, ItemCategory cat1, ItemCategory cat2, int value, int hours, int rank, Map<RareMaterial,Integer> mats)
    {
        item = i;
        baseValue = value;
        category1 = cat1;
        category2 = cat2;
        time = hours;
        materialsRequired = mats;
        materialValue = 0;
        rankUnlocked = rank;
        
        if(mats != null)
            materialsRequired.forEach((k, v) -> {materialValue+=k.cowrieValue * v;});
        
    }
    
    public boolean getsEfficiencyBonus(ItemInfo other)
    {
        return !(other.item == item) && 
                ((other.category1!=Invalid && (other.category1 == category1 || other.category1 == category2)) ||
                (other.category2!=Invalid && (other.category2 == category1 || other.category2 == category2)));
    }
    
    //Set start-of-week data
    public void setInitialData(int pop, PeakCycle peak)
    {
        popularityRatio = pop;
        this.peak = peak;
        craftedPerDay = new int[7];
    }
    public void setCrafted(int num, int day)
    {
        craftedPerDay[day]=num;
    }

    public void clearCrafted(int day)
    {
        craftedPerDay[day]=0;
    }

    
    private int getCraftedBeforeDay(int day)
    {
        int sum = 0;
        for(int c=0; c<day; c++)
            sum+=craftedPerDay[c];
        
        return sum;
    }

    public int getSupplyAfterCraft(int day, int newCrafts)
    {
        return getSupplyOnDay(day) + newCrafts;
    }
    
    public int getSupplyOnDay(int day)
    {
        int supply = SUPPLY_PATH[peak.ordinal()][0];
        for(int c=1;c <= day; c++)
        {
            supply += craftedPerDay[c-1];
            supply += SUPPLY_PATH[peak.ordinal()][c];
        }
        
        return supply;
    }
    
    public Supply getSupplyBucketOnDay(int day)
    {
        return getSupplyBucket(getSupplyOnDay(day));
    }
    public Supply getSupplyBucketAfterCraft(int day, int newCrafts)
    {
        return getSupplyBucket(getSupplyAfterCraft(day, newCrafts));
    }
    
    public boolean equals(ItemInfo other)
    {
        return item == other.item;
    }
    
    public boolean peaksOnOrBeforeDay(int day, Set<Item> reservedItems)
    {
        if(reservedItems!=null && reservedItems.size()>0 && !reservedItems.contains(item))
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
            
        //If we don't have a confirmed peak day, then it definitely hasn't passed
        return false;
    }

    public boolean couldPrePeak(int day)
    {
        if(peak == Cycle45)
            return day==2;
        if(peak == Unknown)
            return day==1;

        return false;
    }
    
    public static Supply getSupplyBucket(int supply)
    {
        if(supply < -8)
            return Nonexistent;
        if(supply < 0)
            return Insufficient;
        if(supply < 8)
            return Sufficient;
        if(supply < 16)
            return Surplus;
        return Overflowing;
    }
    public String toString()
    {
        return item+", "+peak;
    }

    public int getValueWithSupply(Supply supply)
    {
        return baseValue * supply.multiplier * popularityRatio / 10000;
    }
}

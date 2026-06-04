package com.overseascasuals.recsbot.data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static com.overseascasuals.recsbot.data.Supply.*;
import static com.overseascasuals.recsbot.data.ItemCategory.*;
import static com.overseascasuals.recsbot.data.PeakCycle.*;

public class ItemInfo
{
    private static Logger LOG = LoggerFactory.getLogger(ItemInfo.class);
    
    //Constant info
    public Item item;
    public int baseValue;
    ItemCategory category1;
    ItemCategory category2;
    public int time;
    public Map<RareMaterial, Integer> materialsRequired;
    public int materialValue;
    public int rankUnlocked;
    
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

    public boolean equals(ItemInfo other)
    {
        return item == other.item;
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
    public int getSufficientValue(CraftContext context)
    {
        return baseValue * context.getPopRatio(item) / 100;
    }

    public int getValueOnDay(CraftContext context, int day)
    {
        return baseValue * context.getPopRatio(item) * getSupplyBucket(context.getSupplyOnDay(item, day)).multiplier / 10000;
    }
}

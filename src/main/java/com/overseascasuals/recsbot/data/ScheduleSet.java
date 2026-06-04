package com.overseascasuals.recsbot.data;

import java.util.ArrayList;
import java.util.List;

public class ScheduleSet
{
    public List<Item> items;
    public List<Item> subItems;

    public ScheduleSet(List<Item> items, List<Item> subItems)
    {
        this.items = new ArrayList<>(items);
        this.subItems = new ArrayList<>(subItems);
    }
    public ScheduleSet()
    {
        this.items = new ArrayList<>();
        this.subItems = new ArrayList<>();
    }

    public int size()
    {
        return items.size();
    }

    public List<Item> getItems() {
        return items;
    }

    public List<Item> getSubItems() {
        return subItems;
    }

    @Override
    public String toString() {
        return "ScheduleSet{" +
                "items=" + items +
                ", subItems=" + subItems +
                '}';
    }
}

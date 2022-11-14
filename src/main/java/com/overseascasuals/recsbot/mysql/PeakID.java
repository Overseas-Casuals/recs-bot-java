package com.overseascasuals.recsbot.mysql;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class PeakID implements Serializable
{
    private int week;
    private int day;
    @Column(name="item_id")
    private int itemID;

    public PeakID()
    {}

    public PeakID(int week, int day, int item)
    {
        this.week = week;
        this.day = day;
        this.itemID = item;
    }

    public int getWeek() {
        return week;
    }

    public void setWeek(int week) {
        this.week = week;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getItemID() {
        return itemID;
    }

    public void setItemID(int itemID) {
        this.itemID = itemID;
    }

    @Override
    public String toString() {
        return "PeakID{" +
                "week=" + week +
                ", day=" + day +
                ", itemID=" + itemID +
                '}';
    }
}

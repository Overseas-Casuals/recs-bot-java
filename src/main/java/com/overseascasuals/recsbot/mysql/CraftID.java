package com.overseascasuals.recsbot.mysql;

import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CraftID implements Serializable
{
    private int week;
    private int day;
    private int rank;

    public CraftID(){}

    public CraftID(int week, int day, int rank) {
        this.week = week;
        this.day = day;
        this.rank = rank;
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

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CraftID craftID = (CraftID) o;
        return week == craftID.week && day == craftID.day && rank == craftID.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(week, day, rank);
    }

    @Override
    public String toString() {
        return
                "week=" + week +
                ", day=" + day +
                        ", rank=" + rank;
    }
}

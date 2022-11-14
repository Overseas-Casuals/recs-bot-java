package com.overseascasuals.recsbot.mysql;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Popularity
{
    @Id
    private Integer week;

    private Integer nextPopularity;
    private Integer popularity;

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public Integer getNextPopularity() {
        return nextPopularity;
    }

    public void setNextPopularity(Integer nextPopularity) {
        this.nextPopularity = nextPopularity;
    }

    public Integer getPopularity() {
        return popularity;
    }

    public void setPopularity(Integer popularity) {
        this.popularity = popularity;
    }

    @Override
    public String toString() {
        return "Popularity{" +
                "week=" + week +
                ", nextPopularity=" + nextPopularity +
                ", popularity=" + popularity +
                '}';
    }
}

package com.overseascasuals.recsbot.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class PopularityInstance implements Serializable {
    private int ID;
    private int Ratio;

    public int getID() {
        return ID;
    }

    @JsonProperty("ID")
    public void setID(int ID) {
        this.ID = ID;
    }

    public int getRatio() {
        return Ratio;
    }

    @JsonProperty("Ratio")
    public void setRatio(int ratio) {
        this.Ratio = ratio;
    }

    @Override
    public String toString() {
        return "PopularityInstance{" +
                "id=" + ID +
                ", ratio=" + Ratio +
                '}';
    }
}

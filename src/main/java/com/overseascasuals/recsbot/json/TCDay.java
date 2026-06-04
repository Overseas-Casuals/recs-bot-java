package com.overseascasuals.recsbot.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class TCDay {
    private int popularity;
    private int predictedPopularity;
    private List<ItemSupply> objects;

    public TCDay(){}

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public List<ItemSupply> getObjects() {
        return objects;
    }

    public void setObjects(List<ItemSupply> objects) {this.objects = objects;
    }

    public int getPredictedPopularity() {
        return predictedPopularity;
    }

    public void setPredictedPopularity(int predictedPopularity) {
        this.predictedPopularity = predictedPopularity;
    }

    public void setSupplyDemand(List<ItemSupply> supplyDemand) {
        this.objects = supplyDemand;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("TCDay{")
                .append(", popularity=").append(popularity)
                .append(", predictedPopularity=").append(predictedPopularity)
                .append(", objects=[");
        for(var obj : objects)
        {
            sb.append(obj).append(", ");
        }
        sb.setLength(sb.length()-2);
        sb.append("]}");
        return "TCDay{" +

                "popularity=" + popularity +
                ", predictedPopularity=" + predictedPopularity +
                ", objects=" + objects +
                '}';
    }
}

package com.overseascasuals.recsbot.json;

import com.overseascasuals.recsbot.data.DemandShift;
import com.overseascasuals.recsbot.data.Supply;

public class ItemSupply {
    private int id;
    private Supply supply;
    private DemandShift demand;

    public ItemSupply()
    {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Supply getSupply() {
        return supply;
    }

    public void setSupply(Supply supply) {
        this.supply = supply;
    }

    public DemandShift getDemand() {
        return demand;
    }

    public void setDemand(DemandShift demand) {
        this.demand = demand;
    }

    @Override
    public String toString() {
        return "ItemSupply{" +
                "id=" + id +
                ", supply=" + supply +
                ", demand=" + demand +
                '}';
    }
}

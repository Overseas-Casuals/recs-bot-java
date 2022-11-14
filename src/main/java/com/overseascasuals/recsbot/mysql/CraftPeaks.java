package com.overseascasuals.recsbot.mysql;

import com.overseascasuals.recsbot.data.PeakCycle;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CraftPeaks
{
    @EmbeddedId
    private PeakID peakID;

    private String peak;

    public PeakID getPeakID() {
        return peakID;
    }

    public void setPeakID(PeakID peakID) {
        this.peakID = peakID;
    }

    public PeakCycle getPeak() {
        PeakCycle peakEnum = PeakCycle.Unknown;
        switch(peak)
        {
            case "2S":
                peakEnum = PeakCycle.Cycle2Strong;
                break;
            case "2W":
                peakEnum = PeakCycle.Cycle2Weak;
                break;
            case "3S":
                peakEnum = PeakCycle.Cycle3Strong;
                break;
            case "3W":
                peakEnum = PeakCycle.Cycle3Weak;
                break;
            case "4S":
                peakEnum = PeakCycle.Cycle4Strong;
                break;
            case "4W":
                peakEnum = PeakCycle.Cycle4Weak;
                break;
            case "5S":
                peakEnum = PeakCycle.Cycle5Strong;
                break;
            case "5W":
                peakEnum = PeakCycle.Cycle5Weak;
                break;
            case "6S":
                peakEnum = PeakCycle.Cycle6Strong;
                break;
            case "6W":
                peakEnum = PeakCycle.Cycle6Weak;
                break;
            case "7S":
                peakEnum = PeakCycle.Cycle7Strong;
                break;
            case "7W":
                peakEnum = PeakCycle.Cycle7Weak;
                break;
            case "45":
                peakEnum = PeakCycle.Cycle45;
                break;
            case "5U":
                peakEnum = PeakCycle.Cycle5;
                break;
            case "67":
                peakEnum = PeakCycle.Cycle67;
                break;
            case "2U":
                peakEnum = PeakCycle.Cycle2Unknown;
                break;
        }
        return peakEnum;
    }

    public void setPeak(String peak) {
        this.peak = peak;
    }

    @Override
    public String toString() {
        return "CraftPeak{" +
                "peakID=" + peakID +
                ", peak=" + peak +
                ", peakEnum=" + getPeak().toDisplayName() +
                '}';
    }
}

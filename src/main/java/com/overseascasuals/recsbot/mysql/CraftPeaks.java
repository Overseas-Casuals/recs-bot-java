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

    public void setPeakFromEnum(PeakCycle peakEnum)
    {
        switch(peakEnum)
        {

            case Unknown -> {peak = "U1";
            }
            case Cycle2Weak -> { peak = "2W";
            }
            case Cycle2Strong -> {peak="2S";
            }
            case Cycle3Weak -> { peak = "3W";
            }
            case Cycle3Strong -> { peak = "3S";
            }
            case Cycle4Weak -> { peak = "4W";
            }
            case Cycle4Strong -> { peak = "4S";
            }
            case Cycle5Weak -> { peak = "5W";
            }
            case Cycle5Strong -> { peak = "5S";
            }
            case Cycle6Weak -> { peak = "6W";
            }
            case Cycle6Strong -> { peak = "6S";
            }
            case Cycle7Weak -> { peak = "7W";
            }
            case Cycle7Strong -> { peak = "7S";
            }
            case Cycle45 -> { peak = "45";
            }
            case Cycle5 -> { peak ="5U";
            }
            case Cycle67 -> {peak="67";
            }
            case Cycle2Unknown -> { peak = "2U";
            }
        }
    }

    public String getPeak()
    {
        return peak;
    }

    public PeakCycle getPeakEnum() {
        PeakCycle peakEnum = PeakCycle.Unknown;
        switch(peak)
        {
            case "2S":
                peakEnum = PeakCycle.Cycle2Strong;
                break;
            case "2W", "2U":
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
        }
        return peakEnum;
    }

    public void setPeak(String peak) {
        this.peak = peak;
    }

    @Override
    public String toString() {
        return "CraftPeak{" +
                peakID +
                ", peak=" + peak +
                '}';
    }
}

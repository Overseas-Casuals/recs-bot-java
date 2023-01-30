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
        return switch (peak) {
            case "2S" -> PeakCycle.Cycle2Strong;
            case "2W" -> PeakCycle.Cycle2Weak;
            case "2U" -> PeakCycle.Cycle2Unknown;
            case "3S" -> PeakCycle.Cycle3Strong;
            case "3W" -> PeakCycle.Cycle3Weak;
            case "4S" -> PeakCycle.Cycle4Strong;
            case "4W" -> PeakCycle.Cycle4Weak;
            case "5S" -> PeakCycle.Cycle5Strong;
            case "5W" -> PeakCycle.Cycle5Weak;
            case "6S" -> PeakCycle.Cycle6Strong;
            case "6W" -> PeakCycle.Cycle6Weak;
            case "7S" -> PeakCycle.Cycle7Strong;
            case "7W" -> PeakCycle.Cycle7Weak;
            case "45" -> PeakCycle.Cycle45;
            case "5U" -> PeakCycle.Cycle5;
            case "67" -> PeakCycle.Cycle67;
            default -> PeakCycle.Unknown;
        };
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

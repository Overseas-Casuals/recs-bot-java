package com.overseascasuals.recsbot.data;


public enum Supply
{
    Nonexistent(160),
    Insufficient(130),
    Sufficient(100),
    Surplus(80),
    Overflowing (60);
    
    public final int multiplier;
    
    private Supply(int mult)
    {
        this.multiplier = mult;
    }
}




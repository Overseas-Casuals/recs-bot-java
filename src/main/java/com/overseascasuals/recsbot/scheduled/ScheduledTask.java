package com.overseascasuals.recsbot.scheduled;


import discord4j.core.GatewayDiscordClient;

public interface ScheduledTask extends Runnable
{
    String getCron();
    void initialize(GatewayDiscordClient client, boolean local);
}

package com.overseascasuals.recsbot.scheduled;

import com.overseascasuals.recsbot.scheduled.ScheduledTask;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.VoiceChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class SetChannelTask implements ScheduledTask
{
    @Value("${currentDayChannelID}")
    private String currentDayChannel;
    private String cron = "0 0 8 * * ?";

    private GatewayDiscordClient client;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void setClient(GatewayDiscordClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        var d1 = new Date(1661241600000l);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7 ;

        String newTitle = "Cycle "+(day+1)+" (Season "+week+")";
        System.out.println("Running the cron job on id: "+currentDayChannel + " to new title "+newTitle);

        client.getChannelById(Snowflake.of(currentDayChannel))
                .cast(VoiceChannel.class)
                .flatMap(voiceChannel -> voiceChannel.edit().withName(newTitle))
                .subscribe();
    }
}

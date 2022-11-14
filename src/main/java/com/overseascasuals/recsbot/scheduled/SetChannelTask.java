package com.overseascasuals.recsbot.scheduled;

import com.overseascasuals.recsbot.data.ItemInfo;
import com.overseascasuals.recsbot.scheduled.ScheduledTask;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class SetChannelTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(SetChannelTask.class);
    @Value("${currentDayChannelID}")
    private String currentDayChannel;
    private String cron = "0 0 8 * * ?";

    private GatewayDiscordClient client;
    private VoiceChannel channel;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void initialize(GatewayDiscordClient client) {
        this.client = client;
        channel = client.getChannelById(Snowflake.of(currentDayChannel))
                .cast(VoiceChannel.class).block();
    }

    @Override
    public void run() {
        var d1 = new Date(1661241600000l);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int day = (int)((d2.getTime()-d1.getTime())/86400000) % 7 ;

        String newTitle = "Cycle "+(day+1)+" (Season "+week+")";
        LOG.info("Running the cron job on id: "+currentDayChannel + " to new title "+newTitle);

        channel.edit().withName(newTitle).subscribe();
    }
}

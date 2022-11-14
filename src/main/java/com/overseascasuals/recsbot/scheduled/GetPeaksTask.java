package com.overseascasuals.recsbot.scheduled;

import com.overseascasuals.recsbot.data.ItemInfo;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.NewsChannel;
import discord4j.core.object.entity.channel.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GetPeaksTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(GetPeaksTask.class);
    @Value("${recsChannel}")
    private String recsChannel;
    private String cron = "0 10 8 ? * TUE-FRI";

    private GatewayDiscordClient client;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void initialize(GatewayDiscordClient client) {
        this.client = client;
    }

    @Override
    public void run()
    {
        String peaks = "these are peaks";

        System.out.println("Getting peaks");
        boolean valid = true;

        if(valid) {
            client.getChannelById(Snowflake.of(recsChannel))
                    .cast(TextChannel.class).flatMap(newsChannel -> newsChannel.createMessage("peaks: " + peaks)).subscribe();
        }
        else
        {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            System.out.println("Peaks were invalid. Rescheduling");
            int delay = 10;
            scheduler.schedule(this, delay, TimeUnit.MINUTES);
            scheduler.shutdown();
        }
    }
}

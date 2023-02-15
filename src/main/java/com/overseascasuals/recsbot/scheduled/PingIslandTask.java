package com.overseascasuals.recsbot.scheduled;

import com.overseascasuals.recsbot.json.RestService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class PingIslandTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(PingIslandTask.class);
    @Value("${discord.peaksChannel}")
    private String botTestingChannel;
    private String cron = "0 0/15 * * * ?";

    private MessageChannel channel;

    @Value("${peakDB.url}")
    private String peakDbURL;

    @Value("${mienna}")
    private String miennaID;

    @Autowired
    RestService restService;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void initialize(GatewayDiscordClient client, boolean local) {
        channel = client.getChannelById(Snowflake.of(botTestingChannel))
                .cast(MessageChannel.class).block();
    }

    @Override
    public void run() {

        try{
            LOG.info("Pinging {}: {}",peakDbURL, restService.getURLResponse(peakDbURL+"?week=13"));
        }
        catch(RestClientException e)
        {
            LOG.error("Failed to get response from peak DB. Sleeping.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            LOG.info("Retrying peak DB");
            try{
                LOG.info("Pinging {}: {}",peakDbURL, restService.getURLResponse(peakDbURL+"?week=13"));
            }
            catch(RestClientException e2)
            {
                LOG.error("Failed to get response from peak DB again.", e2);

                channel.createMessage("<@"+miennaID+"> Couldn't connect to peak database").subscribe();
            }
        }
    }
}

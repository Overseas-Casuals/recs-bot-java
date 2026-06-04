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

//@Service
public class RestartIslandTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(RestartIslandTask.class);
    @Value("${discord.peaksChannel}")
    private String botTestingChannel;
    private String cron = "0 59 7 * * ?";

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
            LOG.info("Restarting {}: {}",peakDbURL, restService.postRestart());
        }
        catch(RestClientException e)
        {
            LOG.error("Failed to restart peak DB.", e);

            channel.createMessage("<@"+miennaID+"> Couldn't restart peak database.").subscribe();

        }


    }
}

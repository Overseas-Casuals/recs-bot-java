package com.overseascasuals.recsbot.scheduled;

import com.overseascasuals.recsbot.twitter.RecsTweet;
import discord4j.core.GatewayDiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import twitter4j.TwitterException;

import java.util.Date;

//@Service
public class TweetTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(TweetTask.class);
    @Override
    public String getCron() {
        return "0 0 8 ? * MON";
    }
    private boolean live;

    @Override
    public void initialize(GatewayDiscordClient client, boolean local) {
        live = !local;
    }

    @Override
    public void run() {
        try {
            var d1 = new Date(1661241600000L);
            var d2 = new Date();

            int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
            if(live)
                RecsTweet.sendTweetAPI("Season "+(week+1)+", Cycle 1: "+RecsTweet.rest);
        }
        catch(TwitterException e)
        {
            LOG.error("Couldn't send initial tweet. D:", e);
        }
    }
}

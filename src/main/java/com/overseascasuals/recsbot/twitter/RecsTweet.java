package com.overseascasuals.recsbot.twitter;
import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;

import java.util.stream.Collectors;

public class RecsTweet
{
    public static String rest = "\uD83D\uDCA4\uD83D\uDE34\uD83D\uDCA4";
    private static Logger LOG = LoggerFactory.getLogger(RecsTweet.class);
    public static void sendTweetAPI(String line) throws TwitterException
    {
        Twitter twitter = Twitter.getInstance();

        var response = twitter.v1().tweets().updateStatus(line);

        LOG.info("Tweet response: {}",response.getText());
    }

    //Can no longer get user timeline
    /*public static void sendTweetAsReply(String line) throws TwitterException
    {
        Twitter twitter = Twitter.getInstance();
        var response = twitter.v1().timelines().getUserTimeline();
        if(response.size() > 0)
        {
            StatusUpdate status = StatusUpdate.of(line).inReplyToStatusId(response.get(0).getId());
            twitter.v1().tweets().updateStatus(status);
        }
    }*/
    public static void sendRec(int week, DailyRecommendation rec, boolean live) throws TwitterException
    {
       /* String str = convertRecToString(week, rec);
        LOG.info("{}:\n{}\n\nLength: {}", live?"Tweeting":"Would be tweeting", str, str.length());
        if(live)
            sendTweetAPI(str);*/
    }

    public static String convertRecToString(int week, DailyRecommendation rec)
    {
        StringBuilder str = new StringBuilder("Season ").append(week).append(", Cycle ").append(rec.getDay()+1).append(": ");

        if(rec.isRestRecommended())
        {
            str.append("\n").append(rest);
        }
        else
        {
            boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems());
            str.append("\nWorkshops #1-").append(ws4Diff?3:4).append(":\n∟ ")
                    .append(rec.getBestRec().getItems().stream().map(Item::getDisplayNameWithTime)
                            .collect(Collectors.joining(" - ")));
            if(ws4Diff)
            {
                str.append("\nWorkshop #4:\n∟ ")
                        .append(rec.getBestRec().getSubItems().stream().map(Item::getDisplayNameWithTime)
                                .collect(Collectors.joining(" - ")));
            }

            str.append("\nBase value: ").append(rec.getGroovelessValue());
            if(rec.getBestRec().getStartingGroove() > 0)
                str.append(". With ").append(rec.getBestRec().getStartingGroove()).append(" Groove: ")
                        .append(rec.getDailyValue());
        }

        str.append("\n\n#FF14 #FFXIV #FinalFantasyXIV #IslandSanctuary");
        String toReturn = str.toString();

        if(toReturn.length() >= 280)
            toReturn = toReturn.replace("#FinalFantasyXIV ","");
        if(toReturn.length() >= 280)
            toReturn = toReturn.replace("#FF14 ","");
        if(toReturn.length() >= 280)
            toReturn = toReturn.replace("#FFXIV ","");
        if(toReturn.length() >= 280)
            toReturn = toReturn.replace("#IslandSanctuary ","");
        if(toReturn.length() >= 280)
            toReturn = toReturn.substring(0, 279);
        return toReturn;
    }
}

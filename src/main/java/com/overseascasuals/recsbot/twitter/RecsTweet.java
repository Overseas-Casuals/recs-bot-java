package com.overseascasuals.recsbot.twitter;
import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;
import twitter4j.v1.*;

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
    public static void sendTweetAsReply(String line) throws TwitterException
    {
        Twitter twitter = Twitter.getInstance();
        var response = twitter.v1().timelines().getUserTimeline();
        if(response.size() > 0)
        {
            StatusUpdate status = StatusUpdate.of(line).inReplyToStatusId(response.get(0).getId());
            twitter.v1().tweets().updateStatus(status);
        }
    }
    public static void sendRecAsReply(int week, DailyRecommendation rec, boolean live) throws TwitterException
    {
        String str = convertRecToString(week, rec);
        LOG.info("{}:\n{}\n\nLength: {}", live?"Tweeting":"Would be tweeting", str, str.length());
        if(live)
            sendTweetAsReply(str);
    }

    public static String convertRecToString(int week, DailyRecommendation rec)
    {
        StringBuilder str = new StringBuilder("Season ").append(week).append(", Cycle ").append(rec.getDay()+1).append(": ");

        if(rec.getOldRec() != null)
        {
            str.append("CURRENT DAY UPDATE");
        }
        if(rec.isTentative())
        {
            str.append("TENTATIVE SCHEDULE");
        }
        if(rec.isRestRecommended())
        {
            str.append("\n").append(rest);
        }
        else
        {
            str.append("\n∟ ")
                    .append(rec.getBestRec().getItems().stream().map(Item::getDisplayName)
                            .collect(Collectors.joining(" - ")));
            str.append("\nBase value: ").append(rec.getGroovelessValue());
            if(rec.getBestRec().getStartingGroove() > 0)
                str.append(". With ").append(rec.getBestRec().getStartingGroove()).append(" Groove: ")
                        .append(rec.getDailyValue());

            if(rec.get(0).getValue().getGroove() > 0)
            {
                str.append(" (+")
                        .append(rec.getBestRec().getEndingGroove() - rec.getBestRec().getStartingGroove() - 9)
                        .append(" Groove Bonus)");
            }
        }

        if(rec.getOldRec() != null)
        {
            int diff = rec.getDailyValue() - rec.getOldRec().getValue();
            str.append("\n\nWorth ");
            if(diff > 0)
                str.append("+");
            str.append(diff)
                .append(" Cowries");

            int grooveGenerated = rec.getBestRec().getEndingGroove() - rec.getBestRec().getStartingGroove();
            int oldGrooveGenerated = rec.getOldRec().getEndingGroove() - rec.getOldRec().getStartingGroove();

            if(grooveGenerated != oldGrooveGenerated)
            {
                str.append(" (");
                if(grooveGenerated > oldGrooveGenerated)
                    str.append("+");
                str.append(grooveGenerated-oldGrooveGenerated).append(" Groove.)");
            }
        }
        if(rec.isTentative())
        {
            str.append("\n\nThis rec is still tentative!");
        }


        str.append("\n\n#FF14 #FFXIV #FinalFantasyXIV #IslandSanctuary");
        if(str.length() > 280)
            str.setLength(280);
        return str.toString();
    }
}

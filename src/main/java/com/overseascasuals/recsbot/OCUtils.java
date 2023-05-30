package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.RestOfWeekRec;
import com.overseascasuals.recsbot.data.WorkshopValue;
import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.rest.util.Color;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OCUtils
{
    public static String cowriesEmoji = " <:OC_SeafarerCowrie:1109399604203626536>";
    public static int altsToDisplay = 5;
    private static String getDateStr(int season)
    {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(1661241600000L));
        calendar.add(Calendar.DAY_OF_YEAR, (season-1)*7);
        var month = calendar.get(Calendar.MONTH);
        String dateStr = sdf.format(calendar.getTime());

        calendar.add(Calendar.DAY_OF_YEAR, 6);
        if(calendar.get(Calendar.MONTH) == month)
            sdf = new SimpleDateFormat("d");

        dateStr += " - " + sdf.format(calendar.getTime());
        return dateStr;
    }
    public static MessageCreateSpec generateRecEmbedMessage(int season, DailyRecommendation rec, String c1PeakRole, String squawkboxRole)
    {

        var messageSpec = MessageCreateSpec.builder();


        if(rec.isTentative())
        {
            var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations");
            builder.timestamp(Instant.now());
            messageSpec.content("Tentative rec detected! <@&"+c1PeakRole+">");

            //builder.color(Color.RED);
            if(rec.isRestRecommended())
            {
                builder.addField("Tentative Recommendation",getRestText(), false);
            }

            else
            {
                boolean inline = !(rec.getBestRec().getStartingGroove() != 0 && rec.getBestRec().getGrooveBonus() > 0);
                builder.addField("Tentative Recommendation", rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), inline)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()) + (rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

                if(rec.getBestRec().getStartingGroove() != 0)
                    builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue() + cowriesEmoji, true);

                if(rec.getBestRec().getGrooveBonus() > 0)
                    builder.addField("Estimated Bonus", String.valueOf(rec.getBestRec().getGrooveBonus()), true);
            }
            builder.addField("\u200B", "\u200B", false)
                    .addField("Required Info", rec.getTroublemakers().stream().map(Item::getDisplayName).collect(Collectors.joining(", ")), true)
                    .addField("Optional Info", rec.getBystanders().stream().map(Item::getDisplayName).collect(Collectors.joining(", ")), true);

            /*var timeToComplete = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(9, ChronoUnit.HOURS);
            builder.addField("Estimated Completion", "<t:"+timeToComplete.getEpochSecond()+":R>", true);*/
            messageSpec.addEmbed(builder.build());
        }
        else
        {
            messageSpec.content("<@&"+squawkboxRole+">");
            messageSpec.addEmbed(getGeneralRecEmbed(season, rec, true));
        }


        return messageSpec.build();
    }

    public static EmbedCreateSpec getGeneralRecEmbed(int season, DailyRecommendation rec, boolean mainrecs)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations for Rank "+rec.getMaxRank());

        if(mainrecs)
            builder.title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations");
        builder.timestamp(Instant.now());

        if(rec.isRestRecommended())
            builder.color(Color.SUMMER_SKY).addField("Main Recommendation",getRestText(), false);
        else
        {

            String title = "First 3 Workshops";
            boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems());
            if(!ws4Diff)
                title = "All Workshops Rec";
            builder.color(Color.SEA_GREEN);
            if(rec.getOldRec() != null)
            {
                builder.title("Cycle "+(rec.getDay()+1)+" Update!").color(Color.MOON_YELLOW);
                title = "Updated Recommendation";
            }

            builder.addField(title, rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
            if(rec.getBestRec().getSubItems()!=null && rec.getBestRec().getSubItems().size() > 0 && ws4Diff)
                builder.addField("4th Workshop", rec.getBestRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);

            builder.addField("", "", false);
            builder.addField("Grooveless Value", rec.getGroovelessValue() + (rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

            if(rec.getBestRec().getStartingGroove() != 0)
                    builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue() + cowriesEmoji, true);

            if(rec.getBestRec().getGrooveBonus() > 0)
                builder.addField("Estimated Bonus", String.valueOf(rec.getBestRec().getGrooveBonus()), true);


            if(rec.getOldRec() != null)
            {
                builder.addField("", "", false);

                builder.addField("Original Recommendation", rec.getOldRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
                builder.addField("4th Workshop", rec.getOldRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);

                builder.addField("", "", false);
                builder.addField("Grooveless Value", rec.getOldGrooveless()+(rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

                if(rec.getBestRec().getStartingGroove() != 0)
                        builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getOldRec().getValue()+ cowriesEmoji, true);

                if(rec.getOldRec().getGrooveBonus() > 0)
                    builder.addField("Estimated Bonus", String.valueOf(rec.getOldRec().getGrooveBonus()), true);
            }
        }



        if(rec.size() > 1 && rec.getOldRec() == null)
        {
            if(!mainrecs)
            {
                //Add alts also
                //builder.addField("\u200B", "\u200B", false);
                builder.addField("", "", false);

                StringBuilder altSb = new StringBuilder();
                StringBuilder grossSb = new StringBuilder();
                for(int i = 0; i < altsToDisplay && i < rec.size(); i++)
                {
                    var alt = rec.get(i);
                    String altText = "**"+alt.getValue().getWeighted() +"**\u00A0\u00A0" + alt.getKey().getItems().stream().map(Item::getDisplayName).collect(Collectors.joining(" - "));
                    altSb.append(altText).append('\n');
                    grossSb.append(alt.getValue().getWeighted()).append('\n');
                }
                altSb.setLength(altSb.length()-1);
                grossSb.setLength(grossSb.length()-1);

                builder.addField("Alternatives by Value", altSb.toString(), true);
                       // .addField("Weighted Value", grossSb.toString(), true);
                //.addField("Net", netSb.toString(), true);
            }
            else
            {
                if(rec.isRestRecommended())
                {
                    boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems()) && rec.getBestRec().getSubItems().size()>0;
                    String title = ws4Diff?"First 3 Workshops":"All Workshops";
                    //Show one alt
                    builder.addField("If You Can't Rest...", "||**"+title+":**\n"+rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
                    if(ws4Diff)
                            builder.addField(".", "||**4th Workshop:**\n"+rec.getBestRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
                    builder.addField("Grooveless Value","||"+rec.getGroovelessValue()+"||", true);
                }
                builder.addField("Alternatives", "Missing materials? Forgot to set today's schedule? Taking a break from the island?\n" +
                        "Use ?recsbot in <#1034985297391407126> to learn how to get personalized alternatives!", false);
            }
        }
        return builder.build();
    }

    public static EmbedCreateSpec createCombinedC4Post(int season, List<DailyRecommendation> recs, int total)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Cycle 5-7 Recommendations"/*+" for Rank "+recs.get(0).getMaxRank()*/);
        builder.timestamp(Instant.now());

        builder.color(Color.SEA_GREEN);

        for(int i=0; i<3; i++)
        {
            if(i>0)
                builder.addField("","",false);
            var rec = recs.get(i);

            boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems())&& rec.getBestRec().getSubItems().size()>0;
            if(rec.isRestRecommended())
            {
                builder.addField("Cycle "+(i+5),getRestText(), false);
                String title = ws4Diff?"First 3 Workshops":"All Workshops";
                //Show one alt
                builder.addField("If You Can't Rest...", "||**"+title+":**\n"+rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
                if(ws4Diff && rec.getBestRec().getSubItems().size()>0)
                    builder.addField(".", "||**4th Workshop:**\n"+rec.getBestRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
                builder.addField("Grooveless Value","||"+rec.getGroovelessValue()+"||", true);
            }
            else
            {
                String title = ws4Diff?"First 3 Workshops":"All Workshops";
                builder.addField("Cycle "+(i+5)+" "+title, rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
                if(ws4Diff && rec.getBestRec().getSubItems().size()>0)
                    builder.addField("4th Workshop", rec.getBestRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);

                builder.addField("","",false)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                        .addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue()+ cowriesEmoji, true);
                /*if(rec.getBestRec().getGrooveBonus() > 0)
                {
                    builder.addField("Estimated Bonus", String.valueOf(rec.getBestRec().getGrooveBonus()), true);
                }*/
            }
        }

        //builder.addField("\u200B", "\u200B", false);
        if(total > 0)
        {
            builder.addField("","",false);
            builder.addField("Total Weekly Value", String.format("%,d", total)+cowriesEmoji, false);
        }
        builder.addField("","",false);
        builder.addField("Alternatives", "Missing materials? Forgot to set today's schedule? Taking a break from the island?\n" +
                "Use ?recsbot in <#1034985297391407126> to learn how to get personalized alternatives!", false);

        return builder.build();
    }

    public static EmbedCreateSpec generateNextWeekEmbed(int season, List<CycleSchedule> recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations for Rank "+rank);
        builder.timestamp(Instant.now());
        //var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SUMMER_SKY);
        addPredictiveRec(builder, recs.get(1), 2, false);
        addPredictiveRec(builder, recs.get(4), 3, false);
        addPredictiveRec(builder, recs.get(2), 4, false);
        addPredictiveRec(builder, recs.get(3), 5, false);
        addPredictiveRec(builder, recs.get(0), 6, false);
        builder.addField("Cycle 7", getRestText(),true);

        //messageSpec.addEmbed(builder.build());
        return builder.build();
    }

    private static void addPredictiveRec(EmbedCreateSpec.Builder builder, CycleSchedule rec, int cycle, boolean rest)
    {
        boolean ws4Diff = !rec.getItems().equals(rec.getSubItems()) && rec.getSubItems().size() > 0;
        String recString;
        if(!ws4Diff)
            recString = "**All Workshops:**\n";
        else
            recString = "**First 3 Workshops:**\n";
        recString+=rec.getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"));

        String title = "Cycle "+cycle;
        if(rest)
        {
            recString = "||"+recString+"||";
            title+= " - Rest";
        }

        builder.addField(title, recString, true);
        if(rec.getSubItems().size()>0 && !rec.getSubItems().equals(rec.getItems()))
            builder.addField(".", (rest?"||":"")+"**4th Workshop:**\n"+rec.getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+(rest?"||":""), true);
        if(rec.getRank()>=15)
            builder.addField("", "", false);
    }

    private static String getRestText()
    {
        return "<:zzz:1068453995816964176> Rest <:zzz:1068453995816964176>";
    }

    public static EmbedCreateSpec generateThisWeekEmbed(int season, RestOfWeekRec recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations for Rank "+rank);
        if(rank<0)
            builder.title("Season "+season+" ("+getDateStr(season)+") Fortune-Telling Recommendations");
        builder.timestamp(Instant.now());

        builder.color(Color.SUMMER_SKY);
        int startDay = 8-recs.getRecs().size();

        for(int i=0; i<recs.getRecs().size(); i++)
        {
            CycleSchedule rec = recs.getRecs().get(i);
            addPredictiveRec(builder, rec, startDay+i, !recs.isRested() && i==recs.getWorstIndex());
        }

        if(rank<0)
            builder.addField("Info", "This is a _predictive_ schedule. It's an educated guess, but still a guess at the rest of the season. If you want optimal recommendations, check daily in <#1034941158993952809>.", false);

        return builder.build();
    }
    public static EmbedCreateSpec generateTodayEmbed(int season, int cycle, int hours, List<Map.Entry<WorkshopSchedule, WorkshopValue>> recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(cycle+1)+" Partial Schedule for Rank "+rank);
        builder.timestamp(Instant.now());
        builder.description(hours+" hours remaining");

        builder.color(Color.DEEP_LILAC);


        if(recs != null && recs.size() > 0 && recs.get(0).getKey().getItems().size() > 0)
        {
            StringBuilder altSb = new StringBuilder();
            for(var alt : recs)
            {
                if(alt.getKey().getItems().size() > 0)
                {
                    String altText = "**"+alt.getValue().getWeighted() +"**\u00A0\u00A0" + alt.getKey().getItems().stream().map(Item::getDisplayName).collect(Collectors.joining(" - "));
                    altSb.append(altText).append('\n');
                }
            }
            altSb.setLength(altSb.length()-1);

            builder.addField("Schedules by Value", altSb.toString(), true);
        }
        else
            builder.addField("Schedules by Value", "None available", false);



        return builder.build();
    }

    public static MessageEditSpec addCurrentDay(int day, DailyRecommendation rec, Message origMessage)
    {
        String content = origMessage.getContent();
        content += getArchiveContent(day, rec);
        MessageEditSpec messageEditSpec = MessageEditSpec.builder().contentOrNull(content).build();
        return messageEditSpec;
    }

    public static MessageEditSpec addFinalTotal(List<DailyRecommendation> recs, int week, int total, Message origMessage)
    {
        String content = origMessage.getContent();
        for(int i=0; i<3; i++)
        {
            content += getArchiveContent(4+i, recs.get(i));
        }
        content+="\n**Season "+week+" Total:** "+ String.format("%,d", total)+cowriesEmoji;
        MessageEditSpec messageEditSpec = MessageEditSpec.builder().contentOrNull(content).build();
        return messageEditSpec;
    }

    private static String getArchiveContent(int day, DailyRecommendation rec)
    {
        String content="\n* **C"+(day+1)+":** ";
        if(rec.isRestRecommended())
        {
            content += "Rest";
            return content;
        }

        boolean ws3Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems());
        content+=rec.getGroovelessValue()+cowriesEmoji;
        if(rec.getBestRec().getStartingGroove() > 0)
            content+=" ("+rec.getDailyValue()+cowriesEmoji+" "+rec.getBestRec().getStartingGroove()+" Groove): ";
        content+=rec.getBestRec().getItems().stream().map(Item::getDisplayName)
                .collect(Collectors.joining(" - "));

        if(ws3Diff)
        {
            content+="\n * **WS4:** "+rec.getBestRec().getSubItems().stream().map(Item::getDisplayName)
                    .collect(Collectors.joining(" - "));
        }

        return content;
    }

    public static String newArchiveContent(int nextWeek)
    {
        return "**__Season "+nextWeek+" ("+getDateStr(nextWeek)+")__**";
    }
}

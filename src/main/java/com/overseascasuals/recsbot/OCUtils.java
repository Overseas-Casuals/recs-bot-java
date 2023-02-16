package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.WorkshopValue;
import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.beans.factory.annotation.Value;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OCUtils
{
    private static String cowriesEmoji = " <:OC_BlueShell:1035493003655127071>";
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
                boolean inline = !(rec.getBestRec().getStartingGroove() != 0 && rec.get(0).getValue().getGroove() > 0);
                builder.addField("Tentative Recommendation", rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")), inline)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()) + (rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

                if(rec.getBestRec().getStartingGroove() != 0)
                    builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue() + cowriesEmoji, true);

                if(rec.get(0).getValue().getGroove() > 0)
                    builder.addField("Estimated Bonus", String.valueOf(rec.get(0).getValue().getGroove() * 3), true);
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
            messageSpec.addEmbed(getGeneralRecEmbed(season, rec));
        }


        return messageSpec.build();
    }

    public static EmbedCreateSpec getGeneralRecEmbed(int season, DailyRecommendation rec)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations for Rank "+rec.getMaxRank());

        if(rec.getMaxRank() < 0)
            builder.title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations");
        builder.timestamp(Instant.now());

        if(rec.isRestRecommended())
            builder.color(Color.SUMMER_SKY).addField("Main Recommendation",getRestText(), false);
        else
        {

            String title = "Main Recommendation";
            builder.color(Color.SEA_GREEN);
            if(rec.getOldRec() != null)
            {
                builder.title("Cycle "+(rec.getDay()+1)+" Update!").color(Color.MOON_YELLOW);
                title = "Updated Recommendation";
            }

            boolean inline = !(rec.getBestRec().getStartingGroove() != 0 && rec.get(0).getValue().getGroove() > 0);
            builder.addField(title, rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")), inline)
                    .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()) + (rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

            if(rec.getBestRec().getStartingGroove() != 0)
                    builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue() + cowriesEmoji, true);

            if(rec.get(0).getValue().getGroove() > 0)
                builder.addField("Estimated Bonus", String.valueOf(rec.get(0).getValue().getGroove() * 3), true);

            if(rec.getOldRec() != null)
            {
                boolean oldInline = !(rec.getBestRec().getStartingGroove() != 0 && rec.getOldValue().getGroove() > 0);

                builder.addField("Original Recommendation", rec.getOldRec().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")), oldInline)
                        .addField("Grooveless Value", String.valueOf(rec.getOldGrooveless())+(rec.getBestRec().getStartingGroove() == 0? cowriesEmoji : ""), true);

                if(rec.getBestRec().getStartingGroove() != 0)
                        builder.addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", String.valueOf(rec.getOldRec().getValue())+ cowriesEmoji, true);

                if(rec.getOldValue().getGroove() > 0)
                    builder.addField("Estimated Bonus", String.valueOf(rec.getOldValue().getGroove() * 3), true);
            }
        }



        if(rec.size() > 1 && rec.getOldRec() == null)
        {
            if(rec.getMaxRank() > 0)
            {
                //Add alts also
                //builder.addField("\u200B", "\u200B", false);
                builder.addField("", "", false);

                StringBuilder altSb = new StringBuilder();
                StringBuilder grossSb = new StringBuilder();
                for(var alt : rec)
                {
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
                    CycleSchedule sched = new CycleSchedule(rec.getDay(), 0);
                    sched.setForAllWorkshops(rec.get(0).getKey().getItems());

                    //Show one alt
                    builder.addField("If You Can't Rest...", "||"+rec.get(0).getKey().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n"))+"||", true)
                            .addField("Grooveless Value","||"+sched.getValue()+"||", true);
                }
                builder.addField("Alternatives", "Missing materials? Forgot to set today's schedule? Taking a break from the island?\n" +
                        "Use ?recsbot in <#1034985297391407126> to learn how to get personalized alternatives!", false);
            }
        }
        return builder.build();
    }

    public static MessageCreateSpec createCrimeTimePost(int season, List<DailyRecommendation> recs, List<DailyRecommendation> crimeRecs, String crimeTimeRole, int crimeTotal)
    {
        if(recs == null || recs.size() == 0)
        {
            return MessageCreateSpec.builder().content("No recs returned").build();
        }
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Crime Time Recommendations");
        builder.timestamp(Instant.now());
        var messageSpec = MessageCreateSpec.builder();
        messageSpec.content("<@&"+crimeTimeRole+">");

        builder.color(Color.SEA_GREEN);
        boolean crimeDiff = false;
        boolean crimeTotalDiff = true;

        for(int i=0; i<3; i++)
        {
            var rec = recs.get(i);
            var crimeRec = crimeRecs.get(i);
            if (rec.isRestRecommended() || !crimeRec.getBestRec().getItems().equals(rec.getBestRec().getItems())) {
                crimeDiff = true;
                builder.addField("Crime Time Cycle "+(i+5),  crimeRec.getBestRec().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")) , true)
                        .addField("Grooveless Value",  String.valueOf(crimeRec.getGroovelessValue()), true)
                        .addField("With " + crimeRec.getBestRec().getStartingGroove() + " Groove",  crimeRec.getDailyValue() + cowriesEmoji , true);
            }
            else
            {
                crimeTotalDiff = false;
            }
        }

        if(crimeTotalDiff)
        {
            builder.addField("","",false);
        }
        else if(crimeDiff)
        {
            builder.description("Use the main recommendations except for the days below!");
            builder.addField("","",false);
        }

        else
            builder.description("You use the same schedules as the main recommendations this season!");


        builder.addField("Total Weekly Value", crimeTotal+cowriesEmoji, false);

        messageSpec.addEmbed(builder.build());
        return messageSpec.build();
    }

    public static MessageCreateSpec createCombinedC4Post(int season, List<DailyRecommendation> recs, String squawkboxRole, int total)
    {
        if(recs == null || recs.size() == 0)
        {
            return MessageCreateSpec.builder().content("No recs returned").build();
        }
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Cycle 5-7 Recommendations");
        builder.timestamp(Instant.now());
        var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SEA_GREEN);

        for(int i=0; i<3; i++)
        {
            if(i>0)
                builder.addField("","",false);
            var rec = recs.get(i);

            if(rec.isRestRecommended())
            {
                builder.addField("Cycle "+(i+5),getRestText(), false);
                CycleSchedule sched = new CycleSchedule(rec.getDay(), 0);
                sched.setForAllWorkshops(rec.get(0).getKey().getItems());

                //Show one alt
                builder.addField("If You Can't Rest...", "||"+rec.get(0).getKey().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n"))+"||", true)
                        .addField("Grooveless Value","||"+sched.getValue()+"||", true);
            }
            else
            {
                builder.addField("Cycle "+(i+5), rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")), true)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                        .addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", rec.getDailyValue()+ cowriesEmoji, true);
            }
        }

        //builder.addField("\u200B", "\u200B", false);
        builder.addField("","",false);
        builder.addField("Total Weekly Value", total+cowriesEmoji, false);
        builder.addField("","",false);
        builder.addField("Alternatives", "Missing materials? Forgot to set today's schedule? Taking a break from the island?\n" +
                "Use ?recsbot in <#1034985297391407126> to learn how to get personalized alternatives!", false);

        messageSpec.content("<@&"+squawkboxRole+">");
        messageSpec.addEmbed(builder.build());
        return messageSpec.build();
    }

    public static EmbedCreateSpec generateNextWeekEmbed(int season, List<List<Item>> recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations for Rank "+rank);
        builder.timestamp(Instant.now());
        //var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SUMMER_SKY);
        builder.addField("Cycle 2", recs.get(1).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")),true)
                .addField("Cycle 3", recs.get(4).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")),true)
                .addField("Cycle 4", recs.get(2).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")),true)
                .addField("Cycle 5", recs.get(3).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")),true)
                .addField("Cycle 6", recs.get(0).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n")),true)
                .addField("Cycle 7", getRestText(),true);

        //messageSpec.addEmbed(builder.build());
        return builder.build();
    }

    private static String getRestText()
    {
        return "<:zzz:1068453995816964176> Rest <:zzz:1068453995816964176>";
    }

    public static EmbedCreateSpec generateThisWeekEmbed(int season, List<List<Item>> recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations for Rank "+rank);
        builder.timestamp(Instant.now());

        builder.color(Color.SUMMER_SKY);
        int startDay = 8-recs.size();

        for(int i=0; i<recs.size(); i++)
        {
            String recString;
            if(recs.get(i).size() > 0)
                recString = recs.get(i).stream().map(Item::getDisplayWithEmoji).collect(Collectors.joining("\n"));
            else
                recString = getRestText();
            builder.addField("Cycle "+(startDay+i), recString, true);
        }

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
}

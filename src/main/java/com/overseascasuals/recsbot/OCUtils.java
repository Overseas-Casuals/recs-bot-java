package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.data.WorkshopValue;
import com.overseascasuals.recsbot.solver.WorkshopSchedule;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;

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
    public static MessageCreateSpec generateRecEmbedMessage(int season, DailyRecommendation rec, String c1PeakRole)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations");
        builder.timestamp(Instant.now());
        var messageSpec = MessageCreateSpec.builder();


        if(rec.isTentative())
        {
            messageSpec.content("Tentative rec detected! <@&"+c1PeakRole+">");

            //builder.color(Color.RED);
            if(rec.isRestRecommended())
            {
                builder.addField("Main Recommendation","Rest", false);
            }

            else
            {
                builder.addField("Tentative Recommendation", String.join(" - ",rec.getBestRec().getItems().stream().map(Item::getDisplayName).collect(Collectors.toList())), false)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                        .addField("Estimated Bonus", String.valueOf(rec.get(0).getValue().getGroove() * 3), true);
            }
            builder.addField("\u200B", "\u200B", false)
                    .addField("Required Info", String.join(", ", rec.getTroublemakers().stream().map(Item::getDisplayName).collect(Collectors.toList())), true)
                    .addField("Optional Info", String.join(", ", rec.getBystanders().stream().map(Item::getDisplayName).collect(Collectors.toList())), true);

            /*var timeToComplete = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(9, ChronoUnit.HOURS);
            builder.addField("Estimated Completion", "<t:"+timeToComplete.getEpochSecond()+":R>", true);*/
        }
        else
        {
            if(rec.isRestRecommended())
                builder.color(Color.SUMMER_SKY).addField("Main Recommendation","Rest", false);
            else
            {

                String title = "Main Recommendation";
                builder.color(Color.SEA_GREEN);
                if(rec.size() == 1)
                {
                    builder.title("Cycle "+(rec.getDay()+1)+" Update!").color(Color.MOON_YELLOW);
                    title = "Updated Recommendation";
                }


                builder.addField(title, String.join(" - ",rec.getBestRec().getItems().stream().map(Item::getDisplayName).collect(Collectors.toList())), false)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                        .addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", String.valueOf(rec.getDailyValue()), true);

                if(rec.get(0).getValue().getGroove() > 0)
                    builder.addField("Estimated Bonus", String.valueOf(rec.get(0).getValue().getGroove() * 3), true);
            }


            if(rec.size() > 1)
            {
                //Add alts also
                builder.addField("\u200B", "\u200B", false);

                StringBuilder altSb = new StringBuilder();
                StringBuilder grossSb = new StringBuilder();
                for(var alt : rec)
                {
                    String altText = String.join(" - ", alt.getKey().getItems().stream().map(Item::getDisplayName).collect(Collectors.toList()));
                    altSb.append(altText).append('\n');
                    grossSb.append(alt.getValue().getWeighted()).append('\n');
                }
                altSb.setLength(altSb.length()-1);
                grossSb.setLength(grossSb.length()-1);

                builder.addField("Alternatives", altSb.toString(), true)
                        .addField("Weighted Value", grossSb.toString(), true);
                //.addField("Net", netSb.toString(), true);
            }

        }

        messageSpec.addEmbed(builder.build());
        return messageSpec.build();
    }

    public static MessageCreateSpec generateCrimeTimeEmbed(int season, List<DailyRecommendation> recs)
    {
        if(recs == null || recs.size() == 0)
        {
            return MessageCreateSpec.builder().content("No recs returned").build();
        }
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Crime Time Recommendations");
        builder.timestamp(Instant.now());
        var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SEA_GREEN);

        for(int i=0; i<recs.size(); i++)
        {
            if(i>0)
                builder.addField("\u200B", "\u200B", false);
            var rec = recs.get(i);
            builder.addField("Cycle "+(i+5), rec.getBestRec().getItems().stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")), false)
                    .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                    .addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", String.valueOf(rec.getDailyValue()), true);
        }

        messageSpec.addEmbed(builder.build());
        return messageSpec.build();
    }

    public static EmbedCreateSpec generateNextWeekEmbed(int season, List<List<Item>> recs)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations");
        builder.timestamp(Instant.now());
        //var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SUMMER_SKY);
        builder.addField("Cycle 2", recs.get(1).stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")),false)
                .addField("Cycle 3", recs.get(4).stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")),false)
                .addField("Cycle 4", recs.get(2).stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")),false)
                .addField("Cycle 5", recs.get(3).stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")),false)
                .addField("Cycle 6", recs.get(0).stream().map(Item::getDisplayName).collect(Collectors.joining(" - ")),false)
                .addField("Cycle 7", "Rest",false);

        //messageSpec.addEmbed(builder.build());
        return builder.build();
    }

    public static EmbedCreateSpec generateThisWeekEmbed(int season, List<List<Item>> recs)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations");
        builder.timestamp(Instant.now());

        builder.color(Color.SUMMER_SKY);
        int startDay = 8-recs.size();

        for(int i=0; i<recs.size(); i++)
        {
            String recString;
            if(recs.get(i).size() > 0)
                recString = recs.get(i).stream().map(Item::getDisplayName).collect(Collectors.joining(" - "));
            else
                recString = "Rest";
            builder.addField("Cycle "+(startDay+i), recString, false);
        }

        return builder.build();
    }
    public static EmbedCreateSpec generateTodayEmbed(int season, int cycle, int hours, List<Map.Entry<WorkshopSchedule, WorkshopValue>> recs)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(cycle+1)+" Partial Schedule");
        builder.timestamp(Instant.now());
        builder.description(hours+" hours remaining");

        builder.color(Color.DEEP_LILAC);


        if(recs != null && recs.size() > 0 && recs.get(0).getKey().getItems().size() > 0)
        {
            StringBuilder altSb = new StringBuilder();
            StringBuilder grossSb = new StringBuilder();
            for(var alt : recs)
            {
                if(alt.getKey().getItems().size() > 0)
                {
                    String altText = alt.getKey().getItems().stream().map(Item::getDisplayName).collect(Collectors.joining(" - "));
                    altSb.append(altText).append('\n');
                    grossSb.append(alt.getValue().getWeighted()).append('\n');
                }
            }
            altSb.setLength(altSb.length()-1);
            grossSb.setLength(grossSb.length()-1);

            builder.addField("Schedules", altSb.toString(), true)
                    .addField("Weighted Value", grossSb.toString(), true);
        }
        else
            builder.addField("Schedules", "None available", false);



        return builder.build();
    }
}

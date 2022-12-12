package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.DailyRecommendation;
import com.overseascasuals.recsbot.data.Item;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class OCUtils
{
    public static MessageCreateSpec generateRecEmbedMessage(int season, DailyRecommendation rec, String c1PeakRole)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+", Cycle "+(rec.getDay()+1)+" Recommendations");
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
        var builder = EmbedCreateSpec.builder().title("Season "+season+", Crime Time Recommendations");
        builder.timestamp(Instant.now());
        var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SEA_GREEN);

        for(int i=0; i<recs.size(); i++)
        {
            if(i>0)
                builder.addField("\u200B", "\u200B", false);
            var rec = recs.get(i);
            builder.addField("Cycle "+(i+5), String.join(" - ",rec.getBestRec().getItems().stream().map(Item::getDisplayName).collect(Collectors.toList())), false)
                    .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                    .addField("With "+ rec.getBestRec().getStartingGroove() +" Groove", String.valueOf(rec.getDailyValue()), true);
        }

        messageSpec.addEmbed(builder.build());
        return messageSpec.build();
    }
}

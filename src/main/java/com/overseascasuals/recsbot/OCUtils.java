package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.*;
import com.overseascasuals.recsbot.solver.CycleSchedule;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.rest.util.Color;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class OCUtils
{
    public static String cowriesEmoji = " <:OC_SeafarerCowrie:1109399604203626536>";
    public static int altsToDisplay = 5;
    private static String getDateStr(int season)
    {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d");

        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(timeZone);
        sdf.setTimeZone(timeZone);

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
            messageSpec.content("<@&"+squawkboxRole+">"+getFlavorText(rec));
            messageSpec.addEmbed(getGeneralRecEmbed(season, rec, true));
        }


        return messageSpec.build();
    }

    public static EmbedCreateSpec getPeaksEmbed(List<ItemInfo> items, int week)
    {
        var builder = EmbedCreateSpec.builder().title("Peak Info for Season "+week);
        builder.timestamp(Instant.now());
        String peakStr;
        for(int i=0; i<items.size(); i++)
        {
            ItemInfo info = items.get(i);
            switch(info.popularityRatio)
            {
                case 140 ->{peakStr="<:OC_VeryHigh:1035399592004558878> Very High";}
                case 120 ->{peakStr="<:OC_High:1035379587884003418> High";}
                case 100 ->{peakStr="<:OC_Average:1035379549153800204> Average";}
                case 80 ->{peakStr="<:OC_Low:1035379597052743700> Low";}
                default -> {peakStr="";}
            }
            peakStr+="\n";
            switch(info.peak)
            {
                case Unknown -> {
                    peakStr+="Unknown Peak";
                }
                case Cycle2Weak -> {
                    peakStr+="Cycle 2 Weak";
                }
                case Cycle2Strong -> {
                    peakStr+="Cycle 2 Strong";
                }
                case Cycle3Weak -> {
                    peakStr+="Cycle 3 Weak";
                }
                case Cycle3Strong -> {
                    peakStr+="Cycle 3 Strong";
                }
                case Cycle4Weak -> {
                    peakStr+="Cycle 4 Weak";
                }
                case Cycle4Strong -> {
                    peakStr+="Cycle 4 Strong";
                }
                case Cycle5Weak -> {
                    peakStr+="Cycle 5 Weak";
                }
                case Cycle5Strong -> {
                    peakStr+="Cycle 5 Strong";
                }
                case Cycle6Weak -> {
                    peakStr+="Cycle 6 Weak";
                }
                case Cycle6Strong -> {
                    peakStr+="Cycle 6 Strong";
                }
                case Cycle7Weak -> {
                    peakStr+="Cycle 7 Weak";
                }
                case Cycle7Strong -> {
                    peakStr+="Cycle 7 Strong";
                }
                case Cycle45 -> {
                    peakStr+="Cycle 4 or 5";
                }
                case Cycle5 -> {
                    peakStr+="Cycle 5";
                }
                case Cycle67 -> {
                    peakStr+="Cycle 6 or 7";
                }
                case Cycle2Unknown -> {
                    peakStr+="Cycle 2";
                }
            }
            if(i<items.size()-1)
                peakStr+="\n\u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E";

            builder.addField(info.item.getDisplayNameWithEmoji()+"\u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E \u200E", peakStr, true);
        }
        return builder.build();
    }

    public static EmbedCreateSpec getGeneralRecEmbed(int season, DailyRecommendation rec, boolean mainrecs)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations for Rank "+rec.getMaxRank());

        if(mainrecs)
            builder.title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(rec.getDay()+1)+" Recommendations");
        builder.timestamp(Instant.now());

        if(rec.isRestRecommended())
        {
            builder.color(Color.SUMMER_SKY).addField("Main Recommendation",getRestText(), false);

            boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems()) && rec.getBestRec().getSubItems().size()>0;
            String title = ws4Diff?"First 3 Workshops":"All Workshops";
            //Show one alt
            builder.addField("If You Can't Rest...", "||**"+title+":**\n"+rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
            if(ws4Diff)
                builder.addField(".", "||**4th Workshop**\n"+rec.getBestRec().getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+"||", true);
            builder.addField("Grooveless Value","||"+rec.getGroovelessValue()+"||", true);
        }
        else
        {
            boolean ws4Diff = !rec.getBestRec().getItems().equals(rec.getBestRec().getSubItems()) && rec.getBestRec().getSubItems().size() > 0;

            String title = ws4Diff?"First 3 Workshops":"All Workshops";
            builder.color(Color.SEA_GREEN);
            if(rec.getOldRec() != null)
            {
                builder.title("Cycle "+(rec.getDay()+1)+" Update!").color(Color.MOON_YELLOW);
                title = "Updated Recommendation";
            }

            builder.addField(title, rec.getBestRec().getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
            if(ws4Diff)
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
                builder.addField("Alternatives", "Missing materials? Forgot to set today's schedule? Taking a break from the island?\n" +
                        "Use ?recsbot in <#1034985297391407126> to learn how to get personalized alternatives!", false);
            }
        }
        return builder.build();
    }
    private static List<String> squawks = List.of("*squawk*", "*brawk*", "*SQUAWK*", "*braaaaawk*", "*SQUAAAAAWK*", "*squawk*", "*brawk*","*squawk*", "*brawk*","*squawk*");
    private static List<String> comfort = List.of(" It's okay.", " Don't worry.", " It's intended.", " It's fine.", " Everything's fine.", " *squawk*", "");
    public static String getFlavorText(RestOfWeekRec rec)
    {
        return _getFlavorText(rec.getRecs());
    }

    public static String getFlavorText(List<ArchiveSchedule> schedules)
    {
        List<CycleSchedule> cycles = new ArrayList<>();
        for(int i=0; i<schedules.size(); i++)
        {
            CycleSchedule sched = new CycleSchedule(i+1, 0, Solver.maxIslandRank);
            if(schedules.get(i).getItems().size() > 0)
            {
                sched.setForFirstThreeWorkshops(schedules.get(i).getItems());
                sched.setFourthWorkshop(schedules.get(i).getSubItems());
                cycles.add(sched);
            }
        }
        return _getFlavorText(cycles);
    }
    public static String getFlavorText(DailyRecommendation rec)
    {
        List<CycleSchedule> recs = new ArrayList<>();
        if(!rec.isRestRecommended())
            recs.add(rec.getBestRec());
        return _getFlavorText(recs);
    }
    private static String _getFlavorText(List<CycleSchedule> list)
    {
        var breaks = dayWithEfficiencyBreak(list);
        StringBuilder sb = new StringBuilder(" ");
        if(breaks.size()>0)
        {
            if(Math.random()<.5)
                sb.append(getRandomInList(squawks)).append(" ");

            sb.append("Yes, the break in efficiency bonus on cycle");
            if(breaks.size()>1)
                sb.append("s");
            sb.append(" ");
            var iter = breaks.iterator();
            int first = iter.next();
            sb.append(first+1);
            while(iter.hasNext())
            {
                int next = iter.next();
                if(!iter.hasNext()) //this is the last one
                    sb.append(" and ").append(next+1);
                else
                    sb.append(", ").append(next+1);
            }
            sb.append(" is on purpose.").append(getRandomInList(comfort));

            if(Math.random()<.25)
                sb.append(" ").append(squawks.get((int)(Math.random() * squawks.size())));

            return sb.toString();
        }

        if(Math.random()<.1)
            sb.append(getRandomInList(squawks));

        return sb.toString();
    }

    private static String getRandomInList(List<String> list)
    {
        return list.get((int)(Math.random() * list.size()));
    }

    private static Set<Integer> dayWithEfficiencyBreak(List<CycleSchedule> recs)
    {
        Set<Integer> breaks = new HashSet<>();
        for(var rec:recs)
        {
            var items = rec.getItems();
            for(int i=1;i<items.size();i++)
            {
                if(!Solver.items[items.get(i).ordinal()].getsEfficiencyBonus(Solver.items[items.get(i-1).ordinal()]))
                    breaks.add(rec.getDay());
            }
            items = rec.getSubItems();
            for(int i=1;i<items.size();i++)
            {
                if(!Solver.items[items.get(i).ordinal()].getsEfficiencyBonus(Solver.items[items.get(i-1).ordinal()]))
                    breaks.add(rec.getDay());
            }
        }
        return breaks;
    }

    public static List<EmbedCreateSpec> createCombinedRecPost(int season, List<ArchiveSchedule> recs, int total)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Recommendations"/*+" for Rank "+recs.get(0).getMaxRank()*/);

        List<EmbedCreateSpec> embeds = new ArrayList<>();

        for(int i=0; i<recs.size(); i++)
        {
            if(i>0)
                builder.addField("","",false);
            builder.color(Color.SEA_GREEN);

            var rec = recs.get(i);

            boolean ws4Diff = !rec.getItems().equals(rec.getSubItems())&& rec.getSubItems().size()>0;
            if(rec.getItems().size() == 0)
            {
                builder.addField("Cycle "+(i+2),getRestText(), false);
            }
            else
            {
                String title = ws4Diff?"First 3 Workshops":"All Workshops";
                builder.addField("Cycle "+(i+2), "**"+title+"**\n"+rec.getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
                if(ws4Diff && rec.getSubItems().size()>0)
                    builder.addField(".", "**4th Workshop**\n"+rec.getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);

                builder.addField("","",false)
                        .addField("Grooveless Value", String.valueOf(rec.getGroovelessValue()), true)
                        .addField("With "+ rec.getStartingGroove() +" Groove", rec.getValue()+ cowriesEmoji, true);
            }
            if(i == 2)
            {
                embeds.add(builder.build());
                builder = EmbedCreateSpec.builder();
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
        builder.timestamp(Instant.now());
        embeds.add(builder.build());

        return embeds;
    }

    public static EmbedCreateSpec favorsEmbed(int season, List<List<Item>> favorSchedules)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Personalized Favor Schedules");
        builder.timestamp(Instant.now());
        //var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.BISMARK);
        for(int i=0;i<favorSchedules.size(); i++)
        {
            builder.addField("Schedule #"+(i+1), favorSchedules.get(i).stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
        }
        return builder.build();
    }

    public static EmbedCreateSpec generateNextWeekEmbed(int season, List<CycleSchedule> recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Vacation Recommendations for Rank "+rank);
        builder.timestamp(Instant.now());
        //var messageSpec = MessageCreateSpec.builder();

        builder.color(Color.SUMMER_SKY);
        if(recs.size() == 5) //Old next week, no supply info
        {
            addPredictiveRec(builder, recs.get(1), 2, false, false);
            addPredictiveRec(builder, recs.get(4), 3, false, false);
            addPredictiveRec(builder, recs.get(2), 4, false, false);
            addPredictiveRec(builder, recs.get(3), 5, false, false);
            addPredictiveRec(builder, recs.get(0), 6, false, false);
            builder.addField("Cycle 7", getRestText(),true);
        }
        else if(recs.size()==6) //New next week, supply info!
        {
            for(int i=0;i<recs.size();i++)
            {
                if(recs.get(i) == null)
                    builder.addField("Cycle "+(i+2), getRestText(), rank<15);
                else
                    addPredictiveRec(builder, recs.get(i), i+2, false, false);
            }
        }

        //messageSpec.addEmbed(builder.build());
        return builder.build();
    }

    private static void addPredictiveRec(EmbedCreateSpec.Builder builder, CycleSchedule rec, int cycle, boolean rest, boolean showValues)
    {
        boolean ws4Diff = !rec.getItems().equals(rec.getSubItems()) && rec.getSubItems().size() > 0;
        String recString;
        if(!ws4Diff)
            recString = "**All Workshops**\n";
        else
            recString = "**First 3 Workshops**\n";
        recString+=rec.getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"));

        String title = "Cycle "+cycle;
        if(rest)
        {
            recString = "||"+recString+"||";
            title+= " - Rest";
        }

        builder.addField(title, recString, true);
        if(rec.getSubItems().size()>0 && !rec.getSubItems().equals(rec.getItems()))
            builder.addField(".", (rest?"||":"")+"**4th Workshop**\n"+rec.getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n"))+(rest?"||":""), true);
        if(rec.getRank()>=15)
            builder.addField("", "", false);

        if(showValues)
        {
            int groove = rec.getStartingGroove();
            rec.setStartingGroove(0);
            int grooveless = rec.getValue();
            rec.setStartingGroove(groove);
            builder.addField("Grooveless Value", (rest?"||":"") + grooveless + (rest?"||":""), true);
            builder.addField("", "", false);
        }

    }

    private static String getRestText()
    {
        return "<:zzz:1068453995816964176> Rest <:zzz:1068453995816964176>";
    }

    public static List<EmbedCreateSpec> generateThisWeekEmbed(int season, RestOfWeekRec recs, int rank, int total)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+") Recommendations for Rank "+rank);
        if(rank<0)
            builder.title("Season "+season+" ("+getDateStr(season)+") Fortune-Telling Recommendations");

        int startDay = 8-recs.getRecs().size();

        List<EmbedCreateSpec> embeds = new ArrayList<>();

        for(int i=0; i<recs.getRecs().size(); i++)
        {
            builder.color(Color.SUMMER_SKY);
            CycleSchedule rec = recs.getRecs().get(i);
            addPredictiveRec(builder, rec, startDay+i, !recs.isRested() && i==recs.getWorstIndex(), true);

            if(recs.getRecs().size() > 4 && i == recs.getRecs().size()/2 - 1)
            {
                embeds.add(builder.build());
                builder = EmbedCreateSpec.builder();
            }
        }

        if(total > 0)
        {
            builder.addField("","",false);
            builder.addField("Total Weekly Value", String.format("%,d", total)+cowriesEmoji, false);
        }
        builder.timestamp(Instant.now());
        embeds.add(builder.build());

        return embeds;
    }
    public static EmbedCreateSpec generateTodayEmbed(int season, int cycle, int hours, BruteForceSchedules recs, int rank)
    {
        var builder = EmbedCreateSpec.builder().title("Season "+season+" ("+getDateStr(season)+"), Cycle "+(cycle+1)+" Partial Schedule for Rank "+rank);
        builder.timestamp(Instant.now());
        builder.description(hours+" hours remaining");

        builder.color(Color.DEEP_LILAC);


        if(recs != null && recs.size() > 0 && recs.get(0).getKey().getItems().size() > 0)
        {
            CycleSchedule rec = recs.getBestRec();
            boolean ws4Diff = !rec.getItems().equals(rec.getSubItems()) && rec.getSubItems().size() > 0;
            String title;
            if(!ws4Diff)
                title = "All Workshops";
            else
                title = "First 3 Workshops";

            builder.addField(title, rec.getItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);
            if(rec.getSubItems().size()>0 && !rec.getSubItems().equals(rec.getItems()))
                builder.addField("4th Workshop", rec.getSubItems().stream().map(Item::getDisplayWithEmojiAndTime).collect(Collectors.joining("\n")), true);

            StringBuilder altSb = new StringBuilder();
            for(int i = 0; i < altsToDisplay && i< recs.size(); i++)
            {
                var alt = recs.get(i);
                if(alt.getKey().getItems().size() > 0)
                {
                    String altText = "**"+alt.getValue().getWeighted() +"**\u00A0\u00A0" + alt.getKey().getItems().stream().map(Item::getDisplayName).collect(Collectors.joining(" - "));
                    altSb.append(altText).append('\n');
                }
            }
            altSb.setLength(altSb.length()-1);

            builder.addField("Schedules by Value", altSb.toString(), false);
        }
        else
            builder.addField("Schedules by Value", "None available", false);



        return builder.build();
    }

    public static MessageEditSpec fixBadArchive(Message origMessage)
    {
        String old = origMessage.getContent();
        String corrected = old/*.replace("6722", "6721")
                .replace("36,976", "36,974")*/;
        MessageEditSpec messageEditSpec = MessageEditSpec.builder().contentOrNull(corrected).build();
        return messageEditSpec;
    }

    public static String newArchiveContent(int thisWeek, List<ArchiveSchedule> recs, int total)
    {
        StringBuilder sb = new StringBuilder("**__Season "+thisWeek+" ("+getDateStr(thisWeek)+")__**");

        for(int day = 0; day < recs.size(); day++)
        {
            ArchiveSchedule rec = recs.get(day);
            sb.append("\n* **C"+(day+2)+":** ");
            if(rec.getItems().size() == 0)
            {
                sb.append("Rest");
            }
            else
            {
                boolean ws3Diff = !rec.getItems().equals(rec.getSubItems());
                sb.append(rec.getGroovelessValue()+cowriesEmoji);
                if(rec.getStartingGroove() > 0)
                    sb.append(" ("+rec.getValue()+cowriesEmoji+" "+rec.getStartingGroove()+" Groove)");

                sb.append(": " + rec.getItems().stream().map(Item::getDisplayName)
                        .collect(Collectors.joining(" - ")));

                if(ws3Diff)
                {
                    sb.append("\n * WS4: "+rec.getSubItems().stream().map(Item::getDisplayName)
                            .collect(Collectors.joining(" - ")));
                }
            }

        }
        sb.append("\n**Season "+thisWeek+" Total:** "+ String.format("%,d", total)+cowriesEmoji);
        return sb.toString();
    }
}

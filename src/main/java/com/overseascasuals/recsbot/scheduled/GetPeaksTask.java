package com.overseascasuals.recsbot.scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.overseascasuals.recsbot.OCUtils;
import com.overseascasuals.recsbot.data.ArchiveSchedule;
import com.overseascasuals.recsbot.data.ScheduleSet;
import com.overseascasuals.recsbot.json.RestService;
import com.overseascasuals.recsbot.mysql.*;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.overseascasuals.recsbot.data.Supply.*;
import static com.overseascasuals.recsbot.data.DemandShift.*;

@Service
public class GetPeaksTask implements ScheduledTask
{
    private static Logger LOG = LoggerFactory.getLogger(GetPeaksTask.class);
    @Value("${discord.recsChannel}")
    private String recsChannel;

    @Value("${discord.peaksChannel}")
    private String peaksChannel;

    @Value("${discord.archiveChannel}")
    private String archiveChannelID;

    @Value("${discord.fortunetellerChannelID}")
    private String fortuneChannelID;
    @Value("${tcUrl}")
    private String tcURL;

    @Value("${chinaTcUrl}")
    private String tcChinaURL;

    @Value("${mienna}")
    private String miennaID;

    @Value("${discord.clairvoyantRole}")
    private String clairvoyantRole;

    @Value("${discord.c1HelperRole}")
    String c1PeakRole;
    @Value("${discord.squawkboxRole}")
    String squawkboxRole;

    @Value("${testing.startDay}")
    int startDayOverride;

    @Value("${testing.endDay}")
    int endDayOverride;

    @Value("${testing.week}")
    int weekOverride;

    private boolean local;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Autowired
    RestService restService;

    @Autowired
    Solver solver;

    private String cron = "10 0 8 ? * *";

    private GatewayDiscordClient client;

    public static ObjectMapper objectMapper = new ObjectMapper();

    private MessageChannel channel;
    private MessageChannel peakChannel;
    private Channel archiveChannel;
    private MessageChannel fortuneChannel;

    @Override
    public String getCron()
    {
        return cron;
    }

    @Override
    public void initialize(GatewayDiscordClient client, boolean local) {
        this.client = client;
        channel = client.getChannelById(Snowflake.of(recsChannel))
                .cast(MessageChannel.class).block();
        peakChannel = client.getChannelById(Snowflake.of(peaksChannel))
                .cast(MessageChannel.class).block();
        archiveChannel = client.getChannelById(Snowflake.of(archiveChannelID))
                /*.cast(MessageChannel.class)*/.block();
        fortuneChannel = client.getChannelById(Snowflake.of(fortuneChannelID)).cast(MessageChannel.class).block();
        this.local = local;
    }

    @Override
    public void run()
    {
        var d1 = new Date(1661241600000l);
        var d2 = new Date();

        int week = (int)((d2.getTime()-d1.getTime())/604800000) + 1;
        int actualDay = (int)((d2.getTime()-d1.getTime())/86400000) % 7;

        int startDay = actualDay;
        int endDay = actualDay;

        if(local)
        {
            if(weekOverride != -1)
                week = weekOverride;
            startDay = startDayOverride;
            endDay = endDayOverride;
        }

        LOG.info("Getting info on week {}\nOverrides: week {}, startDay {}, endDay {}",  week, weekOverride, startDayOverride, endDayOverride);

        int peakWeek = (week - 59) % 100 + 59; //159 should be 59. 201 should be 101. 378 should be 78
        List<CraftPeaks> peaksByDay = peakRepository.findPeaksByDay(peakWeek, 3);
        if(peaksByDay == null || peaksByDay.size() < Solver.getNumItems(week))
        {
            if(week < 159)
            {
                LOG.error("This is exclusively 159 and over code. Please leave.");
                return;
            }

            LOG.info("Peaks for week {} not found???? Help.", peakWeek);
            return;
        }

        for(int day=startDay; day<=endDay; day++)
        {
            List<ArchiveSchedule> list;

            try
            {
                list = solver.getDailyRecommendations(week, day, false, peaksByDay);
            } catch (Exception e)
            {
                LOG.error("Error running recs. Rescheduling. ", e);

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                int delay = 15;
                long timestamp = System.currentTimeMillis() + delay * 1000 * 60;
                peakChannel.createMessage("Error running recs. Rescheduling for <t:" + timestamp / 1000 + ":t>").subscribe();
                scheduler.schedule(this, delay, TimeUnit.MINUTES);
                scheduler.shutdown();
                return;
            }

            var peaksArray = peaksByDay.stream().map(CraftPeaks::getPeak).limit(Solver.getNumItems(week)).toArray();
            peakChannel.createMessage("peaks: " + Arrays.toString(peaksArray)).subscribe();

            //Test commands
            {
                //next week
            /*var nextWeekRecs = solver.getVacationRecs(17);
            var nextWeekEmbed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, nextWeekRecs, 17);
            channel.createMessage(nextWeekEmbed).subscribe(message -> LOG.info("Successfully posted high-level next week recs."), error -> LOG.error("Error posting high level next week:", error));
            nextWeekRecs = solver.getVacationRecs(6);
            nextWeekEmbed = OCUtils.generateNextWeekEmbed(solver.getWeek() + 1, nextWeekRecs, 6);
            channel.createMessage(nextWeekEmbed).subscribe(message -> LOG.info("Successfully posted low-level next week recs."), error -> LOG.error("Error posting low level next week:", error));

                //this week
                var thisWeekRecs = solver.getThisWeekResult(10, null);
                var thisWeekEmbed = OCUtils.generateThisWeekEmbed(week, thisWeekRecs, 10, day+1);
                channel.createMessage(thisWeekEmbed).subscribe(message -> LOG.info("Successfully posted low-level this week recs."), error -> LOG.error("Error posting low level this week:", error));
                thisWeekRecs = solver.getThisWeekResult(17, null);
                thisWeekEmbed = OCUtils.generateThisWeekEmbed(week, thisWeekRecs, 17, day+1);
                channel.createMessage(thisWeekEmbed).subscribe(message -> LOG.info("Successfully posted high-level this week recs."), error -> LOG.error("Error posting low level this week:", error));

                //today
            var todayRecs = solver.getRestOfDayRecs(1, 22, 10, null);
            var todayEmbed = OCUtils.generateTodayEmbed(week, 1, 22, todayRecs, 10);
            channel.createMessage(todayEmbed).subscribe(message -> LOG.info("Successfully posted low-level today recs."), error -> LOG.error("Error posting low level today:", error));
            todayRecs = solver.getRestOfDayRecs(1, 22, 20, null);
            todayEmbed = OCUtils.generateTodayEmbed(week, 1, 22, todayRecs, 20);
            channel.createMessage(todayEmbed).subscribe(message -> LOG.info("Successfully posted high-level today recs."), error -> LOG.error("Error posting high level today:", error));

                channel.createMessage("All commands? posted").block();*/
            }

            if (list == null || list.size() == 0)
            {
                if (day == 0)
                    peakChannel.createMessage("<@" + miennaID + "> No recs returned").subscribe();
                continue;
            }

            //Post FT
            var embed = OCUtils.generateThisWeekEmbed(week, solver.fortuneTellerRecs, -1, solver.fortuneValue);
            fortuneChannel.createMessage(MessageCreateSpec.builder().content("<@&" + clairvoyantRole + ">" + OCUtils.getFlavorText(solver.fortuneTellerRecs)).addEmbed(embed).build()).flatMap(Message::publish).subscribe(message -> {
                LOG.info("Successfully posted fortune teller recs: {}", message.getEmbeds());
            }, error -> {
                LOG.error("Error posting fortune-teller recs:", error);
            });

            //Post recs
            var combinedPost = MessageCreateSpec.builder().content("<@&" + squawkboxRole + ">" + OCUtils.getFlavorText(list));
            var recsMessage = OCUtils.createCombinedRecPost(week, list, solver.totalValue);
            combinedPost.addAllEmbeds(recsMessage);
            channel.createMessage(combinedPost.build()).flatMap(Message::publish).subscribe(message -> {
                LOG.info("Successfully posted recs: {}", message.getEmbeds());
            }, error -> {
                LOG.error("Error posting combined post:", error);
            });

            //Post archive
            archiveChannel.getRestChannel().createMessage(OCUtils.newArchiveContent(week, solver.archiveRecs, solver.totalValue)).subscribe(message -> {
                LOG.info("Successfully posted new archive post: {}", message.content());
            }, error -> {
                LOG.error("Error posting new archive post:", error);
            });
        }
    }
}

package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.messages.EventListener;
import com.overseascasuals.recsbot.mysql.CraftPeaks;
import com.overseascasuals.recsbot.mysql.PeakRepository;
import com.overseascasuals.recsbot.mysql.Popularity;
import com.overseascasuals.recsbot.mysql.PopularityRepository;
import com.overseascasuals.recsbot.scheduled.ScheduledTask;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.ZoneId;
import java.util.List;

@EnableScheduling
@Configuration
public class BotConfiguration
{
    private static Logger LOG = LoggerFactory.getLogger(BotConfiguration.class);
    @Value("${token}")
    private String token;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    PeakRepository peakRepository;

    @Autowired
    PopularityRepository popularityRepository;

    @Bean
    public <T extends Event> GatewayDiscordClient gatewayDiscordClient(List<EventListener<T>> eventListeners, List<ScheduledTask> taskList)
    {
        var client = DiscordClientBuilder.create(token)
                .build()
                .login()
                .block();

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    LOG.info(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
                });

        for(EventListener<T> listener : eventListeners)
        {
            client.on(listener.getEventType())
                    .flatMap(listener::execute)
                    .onErrorResume(listener::handleError)
                    .subscribe();
        }
        LOG.debug("Listening to "+eventListeners.size()+" event(s)");

        List<CraftPeaks> peaks = peakRepository.findPeaksByDay(11,3);
        for(var peak : peaks)
        {
            System.out.println(peak);
        }
        Popularity currentPop = popularityRepository.findByWeek(11);
        System.out.println(currentPop);

        for(var task : taskList)
        {
            task.initialize(client);
            taskScheduler.schedule(task, new CronTrigger(task.getCron(), ZoneId.of("UTC")));
            //task.run();
        }
        LOG.debug("Scheduled "+taskList.size()+" task(s)");

        //client.onDisconnect().block();
        return client;
    }

}

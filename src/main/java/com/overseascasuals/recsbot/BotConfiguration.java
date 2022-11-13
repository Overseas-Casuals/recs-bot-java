package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.messages.EventListener;
import com.overseascasuals.recsbot.scheduled.ScheduledTask;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.SpringVersion;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.ZoneId;
import java.util.List;

@EnableScheduling
@Configuration
public class BotConfiguration {
    @Value("${token}")
    private String token;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

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
                    System.out.println(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
                });

        for(EventListener<T> listener : eventListeners)
        {
            client.on(listener.getEventType())
                    .flatMap(listener::execute)
                    .onErrorResume(listener::handleError)
                    .subscribe();
        }
        System.out.println("Listening to "+eventListeners.size()+" event(s)");

        for(var task : taskList)
        {
            task.setClient(client);
            taskScheduler.schedule(task, new CronTrigger(task.getCron(), ZoneId.of("UTC")));
        }
        System.out.println("Scheduled "+taskList.size()+" task(s)");

        client.onDisconnect().block();
        return client;
    }

}

package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.Item;
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
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData;
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
import java.util.ArrayList;
import java.util.List;

@EnableScheduling
@Configuration
public class BotConfiguration
{
    private static Logger LOG = LoggerFactory.getLogger(BotConfiguration.class);
    @Value("${discord.token}")
    private String token;

    @Value("${test}")
    private String testStr;

    @Value("${spring.profiles.active}")
    private String activeProfile;

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
                    LOG.info(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
                });

        for(EventListener<T> listener : eventListeners)
        {
            client.on(listener.getEventType())
                    .flatMap(listener::execute)
                    .onErrorResume(listener::handleError)
                    .subscribe();
        }
        LOG.info("Listening to "+eventListeners.size()+" event(s)");

        for(var task : taskList)
        {
            task.initialize(client);
            taskScheduler.schedule(task, new CronTrigger(task.getCron(), ZoneId.of("UTC")));
            if("local".equals(activeProfile))
                task.run();
        }
        LOG.info("Scheduled "+taskList.size()+" task(s)");
        LOG.info("{}", testStr);

        //registerCommands(client);

        return client;
    }

    private void registerCommands(GatewayDiscordClient client)
    {

        List<ApplicationCommandRequest> commands = new ArrayList<>();
        Long applicationId = client.getRestClient().getApplicationId().block();
        if(applicationId == null)
        {
            LOG.error("Null application ID. Unable to register commands");
            return;
        }

        // Build our command's definition
        ApplicationCommandRequest setPeakRequest = ApplicationCommandRequest.builder()
                .name("set_peak")
                .description("Sets the peak of a craft on Cycle 2")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("item")
                        .description("The item to set the peak of")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .autocomplete(true)
                        .build()
                )
                .addOption(ApplicationCommandOptionData.builder()
                        .name("peak_type")
                        .description("Whether the peak is strong or weak on C2")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Strong").value("strong").build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Weak").value("weak").build())
                        .build())
                .defaultMemberPermissions("0")
                .dmPermission(false)
                .build();
        commands.add(setPeakRequest);

        ApplicationCommandRequest setCraftsRequest = ApplicationCommandRequest.builder()
                .name("set_schedule")
                .description("Overrides the schedule for a certain day")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("cycle")
                        .description("The cycle we're setting the schedule of")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(true)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 2").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 3").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 4").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 5").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 6").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cycle 7").value(6).build())
                        .build()
                )
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_1")
                        .description("The first craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_2")
                        .description("The second craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_3")
                        .description("The third craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_4")
                        .description("The fourth craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_5")
                        .description("The fifth craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_6")
                        .description("The sixth craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
                        .build())
                .defaultMemberPermissions("0")
                .dmPermission(false)
                .build();
        commands.add(setCraftsRequest);


        /* Bulk overwrite commands.
        */
        client.getRestClient().getApplicationService().bulkOverwriteGlobalApplicationCommand(applicationId, commands)
                .doOnNext(cmd -> LOG.info("Successfully registered Global Command " + cmd.name()))
                .doOnError(e -> LOG.error("Failed to register global commands", e))
                .subscribe();
    }
}

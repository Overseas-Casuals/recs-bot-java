package com.overseascasuals.recsbot;

import com.overseascasuals.recsbot.data.Item;
import com.overseascasuals.recsbot.messages.EventListener;
import com.overseascasuals.recsbot.mysql.*;
import com.overseascasuals.recsbot.scheduled.ScheduledTask;
import com.overseascasuals.recsbot.solver.Solver;
import discord4j.common.ReactorResources;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.discordjson.Id;
import discord4j.discordjson.json.*;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import io.netty.channel.unix.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.retry.Retry;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EnableScheduling
@Configuration
@Profile("!test")
public class BotConfiguration implements CommandLineRunner
{
    private static Logger LOG = LoggerFactory.getLogger(BotConfiguration.class);

    @Autowired
    CraftRepository craftRepository;
    @Value("${discord.token}")
    private String token;

    @Value("${test}")
    private String testStr;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private Solver solver;

    public List<ScheduledTask> taskList;

    @Bean
    public <T extends Event, R> GatewayDiscordClient gatewayDiscordClient(List<EventListener<T,R>> eventListeners, List<ScheduledTask> taskList)
    {
        var client = DiscordClientBuilder.create(token)
                .onClientResponse(
                        ResponseFunction.retryWhen(
                                RouteMatcher.any(),
                                Retry.anyOf(Errors.NativeIoException.class)))
                .setReactorResources(ReactorResources.builder()
                        .httpClient(HttpClient.create()
                                .compress(true)
                                .keepAlive(false)
                                .followRedirect(true).secure()).build())
                /*.setReactorResources(ReactorResources.builder()
                        .httpClient(ReactorResources.newHttpClient(ConnectionProvider.builder("custom")
                                .maxIdleTime(Duration.ofMinutes(10))
                                .build()))
                        .build())*/
                .build()
                .login()
                .block();

        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(event -> {
                    User self = event.getSelf();
                    LOG.info(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
                });



        this.taskList = taskList;

        for(var task : taskList)
        {
            task.initialize(client);
            taskScheduler.schedule(task, new CronTrigger(task.getCron(), ZoneId.of("UTC")));

        }
        LOG.info("Scheduled "+taskList.size()+" task(s)");
        LOG.info("{}", testStr);

        if(!("live".equals(activeProfile))) return client;

        for(EventListener<T,R> listener : eventListeners)
        {
            client.on(listener.getEventType()).flatMap(event -> listener.execute(event).onErrorResume(listener::handleError)).subscribe();
        }
        LOG.info("Listening to "+eventListeners.size()+" event(s)");

        //registerCommands(client);
        //deregisterCommands(client);

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
                        .name("rank")
                        .description("The rank we're setting the schedule of")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_1")
                        .description("The first craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_2")
                        .description("The second craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("craft_3")
                        .description("The third craft of the cycle")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .autocomplete(true)
                        .required(false)
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

        ApplicationCommandRequest nextWeekRequest = ApplicationCommandRequest.builder()
                .name("next_week")
                .description("Gets a non-optimized schedule for next season if you're going to be away")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("rank")
                        .description("The rank we're requesting the schedule for")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .build())
                .build();
        commands.add(nextWeekRequest);

        ApplicationCommandRequest thisWeekRequest = ApplicationCommandRequest.builder()
                .name("this_week")
                .description("Gets a non-optimized schedule for the rest of the season if you're going to be away")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("rank")
                        .description("The rank we're requesting the schedule for")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .build())
                .build();
        commands.add(thisWeekRequest);

        ApplicationCommandRequest todayRequest = ApplicationCommandRequest.builder()
                .name("today")
                .description("Gets the best schedule for the hours remaining in the current cycle")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("rank")
                        .description("The rank we're requesting the schedule for")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .build())
                .build();
        commands.add(todayRequest);

        ApplicationCommandRequest altsRequest = ApplicationCommandRequest.builder()
                .name("alts")
                .description("Gets recs for the next cycle with extra restrictions")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("rank")
                        .description("The rank we're requesting the schedule for")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nocraft1")
                        .description("The name of a craft you can't make")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nocraft2")
                        .description("The name of a 2nd craft you can't make")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nocraft3")
                        .description("The name of a 3rd craft you can't make")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .build();
        commands.add(altsRequest);

        ApplicationCommandRequest rerunRequest = ApplicationCommandRequest.builder()
                .name("rerun")
                .description("Re-run recs and post them to the right channel")
                .build();
        commands.add(rerunRequest);

        ApplicationCommandRequest islandRequest = ApplicationCommandRequest.builder()
                .name("push_peaks")
                .description("Pushes peaks to the public database")
                .build();
        commands.add(islandRequest);


        /* Bulk overwrite commands.
        */
        client.getRestClient().getApplicationService().bulkOverwriteGlobalApplicationCommand(applicationId, commands)
                .doOnNext(cmd -> LOG.info("Successfully registered Global Command " + cmd.name()))
                .doOnError(e -> LOG.error("Failed to register global commands", e))
                .subscribe();
    }

    private void deregisterCommands(GatewayDiscordClient client)
    {
        Long applicationId = client.getRestClient().getApplicationId().block();


        var commandIDs = client.getRestClient()
                .getApplicationService()
                .getGlobalApplicationCommands(applicationId).map(ApplicationCommandData::id)
                .map(Id::asLong).collectList()
                .block();

        for(var id : commandIDs)
            client.getRestClient().getApplicationService()
                    .deleteGlobalApplicationCommand(applicationId, id)
                    .subscribe();

// Delete it

    }

    @Override
    public void run(String... args) throws Exception
    {
        //Connect to DB, just to be safe
        LOG.info("Getting crafts from day 2 of week 15");
        var response = craftRepository.findCraftsByDay(15,1, 9);
        LOG.info("Found "+response.getCraft1());
        if(!("local".equals(activeProfile))) return;

        LOG.info("Running Bot config with taskList {}", taskList);
        for(var task : taskList)
        {
            task.run();
        }
        //solver.getRestOfDayRecs(2, 2, 11);
        //solver.getRestOfDayRecs(2, 22, 11);
        Map<Item, Integer> limited = new HashMap<>();
        //solver.getLateDays(8, limited);

    }
}

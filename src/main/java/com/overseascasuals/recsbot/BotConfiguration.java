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
import java.util.*;

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



        //deregisterCommands(client);

        //registerCommands(client);

        this.taskList = taskList;

        for(var task : taskList)
        {
            task.initialize(client, "local".equals(activeProfile));
            if(("local".equals(activeProfile)))
                task.run();
            else
                taskScheduler.schedule(task, new CronTrigger(task.getCron(), ZoneId.of("UTC")));

        }
        LOG.info("Scheduled "+taskList.size()+" task(s)");
        LOG.info("{}", testStr);



        if(("local".equals(activeProfile))) return client;

        for(EventListener<T,R> listener : eventListeners)
        {
            client.on(listener.getEventType()).flatMap(event -> listener.execute(event).onErrorResume(listener::handleError)).subscribe();
        }
        LOG.info("Listening to "+eventListeners.size()+" event(s)");


        return client;
    }

    private void registerCommands(GatewayDiscordClient client)
    {
        long guildId = 1034534280757522442L;

        List<ApplicationCommandRequest> commands = new ArrayList<>();
        Long applicationId = client.getRestClient().getApplicationId().block();
        if(applicationId == null)
        {
            LOG.error("Null application ID. Unable to register commands");
            return;
        }
        /*ApplicationCommandRequest editPostRequest = ApplicationCommandRequest.builder().name("Fix").type(3).build();
        commands.add(editPostRequest);*/

        // Build our command's definition

        ApplicationCommandRequest favorsRequest = ApplicationCommandRequest.builder()
                .name("favors")
                .description("Gets semi-optimized possible schedules to fit one or more of this season's favors")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("favor1")
                        .description("A favor you'd like help making")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("favor2")
                        .description("Another favor you'd like help making")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("favor3")
                        .description("A third favor you'd like help making")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .autocomplete(true)
                        .build())
                .build();
        commands.add(favorsRequest);

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
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat1")
                        .description("The name of a material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat2")
                        .description("The name of a 2nd material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat3")
                        .description("The name of a 3rd material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
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
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat1")
                        .description("The name of a material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat2")
                        .description("The name of a 2nd material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("nomat3")
                        .description("The name of a 3rd material you don't want to use")
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(false)
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Laver").value(1).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sap").value(2).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Copper Ore").value(3).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Rock Salt").value(4).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Sugarcane").value(5).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Cotton Boll").value(6).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder().name("Tinsand").value(0).build())
                        .build())
                .build();
        commands.add(altsRequest);

        ApplicationCommandRequest clear_cache = ApplicationCommandRequest.builder()
                .name("clear_cache")
                .description("Clears the alts cache for a specific key")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("key")
                        .description("The key to clear")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .build())
                .build();
        commands.add(clear_cache);

        /* Bulk overwrite commands.
        */
        client.getRestClient().getApplicationService().bulkOverwriteGuildApplicationCommand(applicationId, guildId, commands)
                .doOnNext(cmd -> LOG.info("Successfully registered Guild Command " + cmd.name()))
                .doOnError(e -> LOG.error("Failed to register guild commands", e))
                .subscribe();
    }

    private void deregisterCommands(GatewayDiscordClient client)
    {
        Long applicationId = client.getRestClient().getApplicationId().block();

        List<String> commandsToRemove = Arrays.asList("alts");

        var commandIDs = client.getRestClient()
                .getApplicationService()
                .getGlobalApplicationCommands(applicationId).collectMap(ApplicationCommandData::id, ApplicationCommandData::name)
                .block();

        for(var id : commandIDs.entrySet())
        {
            if(commandsToRemove.contains(id.getValue()))
            {
                LOG.info("Deleting command name: {}, ID: {}", id.getValue(), id.getKey());
                client.getRestClient().getApplicationService()
                        .deleteGlobalApplicationCommand(applicationId, id.getKey().asLong())
                        .subscribe();
            }

        }
    }

    @Override
    public void run(String... args) throws Exception
    {
        //Connect to DB, just to be safe
        LOG.info("Getting crafts from day 2 of week 15");
        var response = craftRepository.findCraftsByDay(15,1, 9);
        LOG.info("Found "+response.getCraft1());

        long heapSize = Runtime. getRuntime(). maxMemory();
        LOG.info("Max heap: "+heapSize+". Total: "+Runtime.getRuntime().totalMemory());
        if(!("local".equals(activeProfile))) return;

        LOG.info("Ran taskList {}", taskList);



        //solver.getRestOfDayRecs(3, 20, 11, Item.GrowthFormula);
        //solver.getRestOfDayRecs(3, 22, 11, null);=


        //If we don't have this, it's because we haven't run recs at all
        //So run recs to get things all set up
        /*if(!solver.hasRunRecs)
        {
            LOG.info("Haven't run recs yet. Doing so now.");
            solver.getDailyRecommendations(59, 2, true);
        }
        solver.getRestOfDayRecs(2, 22, 18, null);*/
        Thread.sleep(5000); //Idk, just make sure things have a chance to finish running?
    }
}
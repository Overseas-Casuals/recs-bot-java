package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.data.Item;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;


public class AutocompleteListener implements EventListener<ChatInputAutoCompleteEvent>
{
    Logger LOG = LoggerFactory.getLogger(AutocompleteListener.class);
    @Override
    public Class<ChatInputAutoCompleteEvent> getEventType() {
        return ChatInputAutoCompleteEvent.class;
    }

    @Override
    public Mono<Void> execute(ChatInputAutoCompleteEvent event) {
        long time = System.currentTimeMillis();
        String typing = event.getFocusedOption().getValue()
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("").replace(" ","").toLowerCase();


        LOG.info("Getting autocomplete for {}", typing);

        int i=0;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();

        //List<Item> suggestionItems = new ArrayList<>();

        for(Item item : Item.values())
        {
            if(i >= 25)
                break;
            if(item.name().toLowerCase().startsWith(typing))
            {
                //suggestionItems.add(item);
                suggestions.add(ApplicationCommandOptionChoiceData.builder().name(item.getDisplayName()).value(item.name()).build());
                i++;
            }
        }

        LOG.info("Returning {} results in {}ms", i, System.currentTimeMillis()-time);
        /*for(Item item : Item.values())
        {
            if(i >= 25)
                break;
            if(item.name().toLowerCase().contains(typing) && !suggestionItems.contains(item))
            {
                suggestionItems.add(item);
                i++;
            }
        }*/

        /*for(Item item : suggestionItems)
        {
            suggestions.add(ApplicationCommandOptionChoiceData.builder().name(item.getDisplayName()).value(item.name()).build());
        }*/

        return event.respondWithSuggestions(suggestions);
    }
}

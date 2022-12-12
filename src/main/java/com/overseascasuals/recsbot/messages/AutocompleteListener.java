package com.overseascasuals.recsbot.messages;

import com.overseascasuals.recsbot.data.Item;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class AutocompleteListener implements EventListener<ChatInputAutoCompleteEvent>
{

    @Override
    public Class<ChatInputAutoCompleteEvent> getEventType() {
        return ChatInputAutoCompleteEvent.class;
    }

    @Override
    public Mono<Void> execute(ChatInputAutoCompleteEvent event) {
        String typing = event.getFocusedOption().getValue()
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("").replace(" ","").toLowerCase();

        int i=0;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();

        List<Item> suggestionItems = new ArrayList<>();

        for(Item item : Item.values())
        {
            if(i >= 25)
                break;
            if(item.name().toLowerCase().startsWith(typing))
            {
                suggestionItems.add(item);
                i++;
            }
        }
        for(Item item : Item.values())
        {
            if(i >= 25)
                break;
            if(item.name().toLowerCase().contains(typing) && !suggestionItems.contains(item))
            {
                suggestionItems.add(item);
                i++;
            }
        }

        for(Item item : suggestionItems)
        {
            suggestions.add(ApplicationCommandOptionChoiceData.builder().name(item.getDisplayName()).value(item.name()).build());
        }

        return event.respondWithSuggestions(suggestions);
    }
}

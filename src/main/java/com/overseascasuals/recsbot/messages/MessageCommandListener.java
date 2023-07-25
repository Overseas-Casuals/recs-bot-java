package com.overseascasuals.recsbot.messages;


import com.overseascasuals.recsbot.OCUtils;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MessageCommandListener implements EventListener<MessageInteractionEvent, Void> {
    Logger LOG = LoggerFactory.getLogger(MessageCommandListener.class);

    @Override
    public Class<MessageInteractionEvent> getEventType() {
        return MessageInteractionEvent.class;
    }

    @Override
    public Mono<Void> execute(MessageInteractionEvent event) {
        String command = event.getCommandName();
        LOG.info("Processing {} message command", command);
        event.reply("Ok").withEphemeral(true).subscribe();
        if(command.equals("Fix"))
        {
            var toEdit = event.getTargetMessage().block();
            if(toEdit!=null)
            {
                LOG.info("Fixing message ID {} in channel {}", toEdit.getId(), toEdit.getChannelId());

                var edited = OCUtils.fixBadArchive(toEdit);
                //LOG.info("edited message: {}",edited.content());

                toEdit.edit(edited).subscribe(message -> {LOG.info("Successfully edited archive: {}", message.getContent());}, error -> { LOG.error("Error editing archive:",error);});
            }
        }

        return Mono.empty();
    }
}
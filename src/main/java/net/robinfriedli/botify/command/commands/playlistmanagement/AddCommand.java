package net.robinfriedli.botify.command.commands.playlistmanagement;

import java.util.List;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.commands.AbstractPlayableLoadingCommand;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class AddCommand extends AbstractPlayableLoadingCommand {

    private final String definingArgument;
    private Playlist playlist;

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        this(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category, "to");
    }

    public AddCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category, String definingArgument) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category, false, new BlockingTrackLoadingExecutor());
        this.definingArgument = definingArgument;
    }

    @Override
    public void doRun() throws Exception {
        Session session = getContext().getSession();
        if (argumentSet("queue")) {
            AudioQueue queue = Botify.get().getAudioManager().getQueue(getContext().getGuild());
            if (queue.isEmpty()) {
                throw new InvalidCommandException("Queue is empty");
            }

            Playlist playlist = SearchEngine.searchLocalList(session, getToAddString());
            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
            }

            List<Playable> tracks = queue.getTracks();
            addPlayables(playlist, tracks);
        } else {
            if (!argumentSet(definingArgument)) {
                throw new InvalidCommandException(String.format("Expected argument '%s', defining the target playlist. " +
                    "Hint: if you meant to add tracks to the queue, use the queue command instead.", definingArgument));
            }

            String playlistName = getArgumentValue(definingArgument);
            playlist = SearchEngine.searchLocalList(session, playlistName);

            if (playlist == null) {
                throw new NoResultsFoundException(String.format("No local list found for '%s'", playlistName));
            }

            super.doRun();
        }
    }

    @Override
    protected void handleResults(List<Playable> playables) {
        addPlayables(playlist, playables);
    }

    @Override
    protected boolean shouldRedirectSpotify() {
        return false;
    }

    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (items.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }
        Session session = getContext().getSession();
        invoke(() -> items.forEach(item -> {
            item.add();
            session.persist(item);
        }));
    }

    private void addPlayables(Playlist playlist, List<Playable> playables) {
        if ((getTask() != null && getTask().isTerminated()) || Thread.currentThread().isInterrupted()) {
            return;
        }
        Session session = getContext().getSession();
        List<PlaylistItem> items = Lists.newArrayList();
        invoke(() -> {
            playables.forEach(playable -> {
                if (playable instanceof HollowYouTubeVideo && ((HollowYouTubeVideo) playable).isCanceled()) {
                    return;
                }

                items.add(playable.export(playlist, getContext().getUser(), session));
            });
            addToList(playlist, items);
        });
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }

    @Override
    public void withUserResponse(Object option) {
        Session session = getContext().getSession();
        String playlistName = getArgumentValue(definingArgument);

        Playlist playlist = SearchEngine.searchLocalList(session, playlistName);
        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local list found for '%s'", getToAddString()));
        }

        PlayableFactory playableFactory = Botify.get().getAudioManager().createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor());
        List<Playable> playables = playableFactory.createPlayables(false, option);
        addPlayables(playlist, playables);
    }

    protected String getToAddString() {
        return getCommandInput();
    }

}

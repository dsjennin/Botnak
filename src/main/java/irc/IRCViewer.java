package irc;

import face.FaceManager;
import gui.forms.GUIMain;
import irc.account.Task;
import irc.message.Message;
import irc.message.MessageHandler;
import irc.message.MessageQueue;
import lib.pircbot.PircBot;
import lib.pircbot.User;
import thread.heartbeat.BanQueue;
import util.Utils;
import util.settings.Settings;

import java.util.Optional;


public class IRCViewer extends MessageHandler {

    public PircBot getViewer() {
        return Settings.accountManager.getViewer();
    }

    public void doConnect(String channel) {
        channel = channel.startsWith("#") ? channel : "#" + channel;
        Settings.accountManager.addTask(new Task(getViewer(), Task.Type.JOIN_CHANNEL, channel));
        if (Settings.logChat.getValue()) Utils.logChat(null, channel, 0);
        if (!GUIMain.channelSet.contains(channel)) GUIMain.channelSet.add(channel);
        if (Settings.ffzFacesEnable.getValue()) {
            if (FaceManager.doneWithFrankerFaces)
                FaceManager.handleFFZChannel(channel.substring(1));
        }
    }

    /**
     * Leaves a channel and if specified, removes the channel from the
     * channel list.
     *
     * @param channel The channel name to leave (# not included).
     */
    public void doLeave(String channel) {
        if (!channel.startsWith("#")) channel = "#" + channel;
        Settings.accountManager.addTask(new Task(getViewer(), Task.Type.LEAVE_CHANNEL, channel));
        GUIMain.channelSet.remove(channel);
    }

    /**
     * Disconnects from all chats and disposes of the bot.
     *
     * @param forget If true, will forget the user.
     */
    public synchronized void close(boolean forget) {
        GUIMain.log("Logging out of user: " + Settings.accountManager.getUserAccount().getName());
        Settings.accountManager.addTask(new Task(getViewer(), Task.Type.DISCONNECT, null));
        if (forget) {
            Settings.accountManager.setUserAccount(null);
        }
        GUIMain.viewer = null;
        Settings.channelManager.dispose();
    }

    @Override
    public void onMessage(final String channel, final String sender, final String message) {
        MessageQueue.addMessage(new Message(channel, sender, message, false));
    }

    @Override
    public void onAction(final String sender, final String channel, final String action) {
        MessageQueue.addMessage(new Message(channel, sender, action, true));
    }

    @Override
    public void onBeingHosted(final String line) {
        MessageQueue.addMessage(new Message(line, Message.MessageType.HOSTED_NOTIFY)
                .setChannel(Settings.accountManager.getUserAccount().getName()));
    }

    @Override
    public void onHosting(final String channel, final String target, String viewers) {
        Message m = new Message().setChannel(channel).setType(Message.MessageType.HOSTING_NOTIFY);
        if ("-".equals(target)) m.setContent("Exited host mode.");
        else {
            String content = channel + " is now hosting " + target;
            String viewCount;
            if ("-".equals(viewers)) {
                viewCount = ".";
            } else if (viewers.compareTo("1") > 0) {
                viewCount = " for " + viewers + " viewers.";
            } else {
                viewCount = " for " + viewers + " viewer.";
            }
            m.setContent(content + viewCount);
        }
        MessageQueue.addMessage(m);
    }

    @Override
    public void onNewSubscriber(String channel, String line, String newSub) {
        Message m = new Message().setChannel(channel).setType(Message.MessageType.SUB_NOTIFY).setContent(line);
        if (Utils.isMainChannel(channel)) {
            if (line.endsWith("subscribed!")) {//new sub
                if (Settings.subscriberManager.addNewSubscriber(newSub, channel)) return;
            } else {
                //it's the (blah blah has subbed for more than 1 month!)
                //Botnak already handles this, so we can construct this message again since the user feels entitled
                //to tell us they've remained subbed... again
                //the catch is the message they send isn't automatic, so there's a chance it won't be sent (ex: on an IRC client, shy, etc)
                //HOWEVER, we will make sure Botnak does not increment the sub counter for this
                Optional<Subscriber> s = Settings.subscriberManager.getSubscriber(newSub);
                if (s.isPresent()) {
                    if (!s.get().isActive()) {
                        s.get().setActive(true);//fixes issue #87 (I hope)
                    }
                }
                m = m.setExtra(false);//anything other than "null" works
                Settings.subscriberManager.notifyTrayIcon(m.getContent(), true);
            }
        } //else it's someone else's channel, just print the message
        MessageQueue.addMessage(m);
    }

    public void onDisconnect() {
        if (!GUIMain.shutDown && getViewer() != null) {
            GUIMain.logCurrent("Detected a disconnection for the account: " + getViewer().getNick());
            if (Settings.autoReconnectAccounts.getValue())
                Settings.accountManager.createReconnectThread(getViewer().getConnection());
            else {
                GUIMain.logCurrent("Auto-reconnects disabled, please check Preferences -> Auto-Reconnect!");
            }
        }
    }

    public void onClearChat(String channel, String name) {
        if (name != null) {
            BanQueue.addToMap(channel, name);
        } else {
            if (Settings.actuallyClearChat.getValue()) GUIMain.getChatPane(channel).cleanupChat();
            MessageQueue.addMessage(new Message().setChannel(channel).setType(Message.MessageType.BAN_NOTIFY)
                    .setContent("The chat was cleared by a moderator" +
                            (Settings.actuallyClearChat.getValue() ? " (Prevented by Botnak)." : ".")));
        }
    }

    @Override
    public void onJTVMessage(String channel, String line, String tags) {
        MessageQueue.addMessage(new Message().setChannel(channel).setType(Message.MessageType.JTV_NOTIFY).setContent(line));
    }

    @Override
    public void onWhisper(String user, String receiver, String contents) {
        MessageQueue.addMessage(new Message().setType(Message.MessageType.WHISPER_MESSAGE).setSender(user).setContent(contents)
                .setExtra(receiver));
    }

    @Override
    public void onRoomstate(String channel, String tags) {
        if (Utils.isMainChannel(channel)) {
            tags = tags.substring(1);
            String[] parts = tags.split(";");
            for (String part : parts) {
                String[] keyValPairs = part.split("=");
                String key = keyValPairs[0].toLowerCase();
                if (keyValPairs.length <= 1) continue;
                String value = keyValPairs[1];
                if ("subs-only".equalsIgnoreCase(key)) {
                    GUIMain.instance.updateSubsOnly(value);
                } else if ("slow".equalsIgnoreCase(key)) {
                    GUIMain.instance.updateSlowMode(value);
                }
            }
        }
    }

    @Override
    public void onConnect() {
        Settings.channelManager.addUser(new User(getViewer().getNick()));
        GUIMain.channelSet.forEach(this::doConnect);
        GUIMain.updateTitle(null);
    }
}
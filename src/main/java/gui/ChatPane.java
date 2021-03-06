package gui;

import face.FaceManager;
import face.IconEnum;
import face.Icons;
import gui.forms.GUIMain;
import gui.forms.GUIViewerList;
import irc.Donor;
import irc.Subscriber;
import irc.message.Message;
import irc.message.MessageQueue;
import irc.message.MessageWrapper;
import lib.pircbot.User;
import util.Constants;
import util.Utils;
import util.misc.Donation;
import util.settings.Settings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTML;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * All channels are stored in this format.
 */
public class ChatPane implements DocumentListener {

    private JFrame poppedOutPane = null;

    // The timestamp of when we decided to wait to scroll back down
    private long scrollbarTimestamp = -1;

    public void setPoppedOutPane(JFrame pane) {
        poppedOutPane = pane;
    }

    public JFrame getPoppedOutPane() {
        return poppedOutPane;
    }

    public void createPopOut() {
        if (poppedOutPane == null) {
            JFrame frame = new JFrame(getPoppedOutTitle());
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    getScrollPane().setViewportView(getTextPane());
                    scrollToBottom();
                    setPoppedOutPane(null);
                }
            });
            JScrollPane pane = new JScrollPane();
            frame.setIconImage(new ImageIcon(getClass().getResource("/image/icon.png")).getImage());
            pane.setViewportView(getTextPane());
            pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            pane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
            frame.add(pane);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.pack();
            frame.setSize(750, 420);
            frame.setVisible(true);
            setPoppedOutPane(frame);
        }
    }

    /**
     * Keeps track of how many subs this channel gets.
     * TODO: make this a statistic that the user can output to a file ("yesterday sub #")
     */
    private int subCount = 0;

    private int viewerCount = -1;
    private int viewerPeak = 0;

    public void setViewerCount(int newCount) {
        if (newCount > viewerPeak) viewerPeak = newCount;
        viewerCount = newCount;
        if (getPoppedOutPane() != null) poppedOutPane.setTitle(getPoppedOutTitle());
        if (GUIMain.channelPane.getSelectedIndex() == index) GUIMain.updateTitle(getViewerCountString());
    }

    public String getPoppedOutTitle() {
        return chan + " | " + getViewerCountString();
    }

    public String getViewerCountString() {
        if (chan.equalsIgnoreCase("system logs")) return null;
        if (viewerCount == -1) return "Viewer count: Offline";
        return String.format("Viewer count: %s (%s)", format(viewerCount), format(viewerPeak));
    }

    private String format(Object obj) {
        String s = obj.toString();
        int length = s.length();
        if (length < 4) return s;
        //1 000 000
        for (int i = length - 3; i > 0; i -= 3) {
            s = s.substring(0, i) + "," + s.substring(i);
        }
        return s;
    }

    /**
     * This is the main boolean to check to see if this tab should pulse.
     * <p>
     * This boolean checks to see if the tab wasn't toggled, if it's visible (not in a combined tab),
     * and if it's not selected.
     * <p>
     * The global setting will override this.
     *
     * @return True if this tab should pulse, else false.
     */
    public boolean shouldPulse() {
        boolean shouldPulseLocal = (this instanceof CombinedChatPane) ?
                ((CombinedChatPane) this).getActiveChatPane().shouldPulseLoc() : shouldPulseLoc;
        return Settings.showTabPulses.getValue() && shouldPulseLocal && isTabVisible() &&
                GUIMain.channelPane.getSelectedIndex() != index && index != 0;
    }

    private boolean shouldPulseLoc = true;

    /**
     * Determines if this tab should pulse.
     *
     * @return True if this tab is not toggled off, else false. ("Tab Pulsing OFF")
     */
    public boolean shouldPulseLoc() {
        return shouldPulseLoc;
    }

    /**
     * Sets the value for if this tab should pulse or not.
     *
     * @param newBool True (default) if tab pulsing should happen, else false if you wish to
     *                toggle tab pulsing off.
     */
    public void setShouldPulse(boolean newBool) {
        shouldPulseLoc = newBool;
    }


    /**
     * Sets the pulsing boolean if this tab is starting to pulse.
     * <p>
     * Used by the TabPulse class.
     *
     * @param isPulsing True if the tab is starting to pulse, else false to stop pulsing.
     */
    public void setPulsing(boolean isPulsing) {
        this.isPulsing = isPulsing;
    }

    /**
     * Used by the TabPulse class.
     *
     * @return true if the chat pane is currently pulsing, else false.
     */
    public boolean isPulsing() {
        return isPulsing;
    }

    private boolean isPulsing = false;

    //credit to http://stackoverflow.com/a/4047794 for the below
    public boolean isScrollBarFullyExtended(JScrollBar vScrollBar) {
        BoundedRangeModel model = vScrollBar.getModel();
        return (model.getExtent() + model.getValue()) == model.getMaximum();
    }

    public void doScrollToBottom() {
        if (textPane.isVisible()) {
            Rectangle visibleRect = textPane.getVisibleRect();
            visibleRect.y = textPane.getHeight() - visibleRect.height;
            textPane.scrollRectToVisible(visibleRect);
        } else {
            textPane.setCaretPosition(textPane.getDocument().getLength());
        }
    }

    private boolean messageOut = false;

    @Override
    public void insertUpdate(DocumentEvent e) {
        maybeScrollToBottom();
        if (Settings.cleanupChat.getValue()) {
            try {
                if (e.getDocument().getText(e.getOffset(), e.getLength()).contains("\n")) {
                    cleanupCounter++;
                }
            } catch (Exception ignored) {
            }
            if (cleanupCounter > Settings.chatMax.getValue()) {
                /* cleanup every n messages */
                if (!messageOut) {
                    MessageQueue.addMessage(new Message().setType(Message.MessageType.CLEAR_TEXT).setExtra(this));
                    messageOut = true;
                }
            }
        }
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        maybeScrollToBottom();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        maybeScrollToBottom();
    }

    /**
     * Used to queue a scrollToBottom only if the scroll bar is already at the bottom
     * OR
     * It's been more than 10 seconds since we've been scrolled up and have been receiving messages
     */
    private void maybeScrollToBottom() {
        JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
        boolean scrollBarAtBottom = isScrollBarFullyExtended(scrollBar);
        if (scrollBarAtBottom) {
            // We're back at the bottom, reset timer
            scrollbarTimestamp = -1;
            scrollToBottom();
        } else if (scrollbarTimestamp != -1) {
            if (System.currentTimeMillis() - scrollbarTimestamp >= 10 * 1000L) {
                // If the time difference is more than 10 seconds, scroll to bottom anyway after resetting time
                scrollbarTimestamp = -1;
                scrollToBottom();
            }
        } else {
            scrollbarTimestamp = System.currentTimeMillis();
        }
    }

    public void scrollToBottom() {
        // Push the call to "scrollToBottom" back TWO PLACES on the
        // AWT-EDT queue so that it runs *after* Swing has had an
        // opportunity to "react" to the appending of new text:
        // this ensures that we "scrollToBottom" only after a new
        // bottom has been recalculated during the natural
        // revalidation of the GUI that occurs after having
        // appending new text to the JTextArea.
        EventQueue.invokeLater(() -> EventQueue.invokeLater(this::doScrollToBottom));
    }

    private String chan;

    public String getChannel() {
        return chan;
    }

    private int index;

    public void setIndex(int newIndex) {
        index = newIndex;
    }

    public int getIndex() {
        return index;
    }

    private ScrollablePanel scrollablePanel;

    private JTextPane textPane;

    public JTextPane getTextPane() {
        return textPane;
    }

    private JScrollPane scrollPane;

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setScrollPane(JScrollPane pane) {
        scrollPane = pane;
    }

    private boolean isTabVisible = true;

    public boolean isTabVisible() {
        return isTabVisible;
    }

    public void setTabVisible(boolean newBool) {
        isTabVisible = newBool;
    }

    private int cleanupCounter = 0;

    public void resetCleanupCounter() {
        cleanupCounter = 0;
    }

    //TODO make this be in 24 hour if they want
    final SimpleDateFormat format = new SimpleDateFormat("[h:mm a]", Locale.getDefault());

    public String getTime() {
        return format.format(new Date(System.currentTimeMillis()));
    }

    /**
     * You initialize this class with the channel it's for and the text pane you'll be editing.
     *
     * @param channel    The channel ("name") of this chat pane. Ex: "System Logs" or "#gocnak"
     * @param scrollPane The scroll pane for the tab.
     * @param pane       The text pane that shows the messages for the given channel.
     * @param index      The index of the pane in the main GUI.
     */
    public ChatPane(String channel, JScrollPane scrollPane, JTextPane pane, ScrollablePanel panel, int index) {
        chan = channel;
        textPane = pane;
        ((DefaultCaret) textPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        this.index = index;
        this.scrollPane = scrollPane;
        this.scrollablePanel = panel;
        textPane.getDocument().addDocumentListener(this);
    }

    public ChatPane() {
        //Used by the CombinedChatPane class, which calls its super anyways.
    }

    /**
     * This is the main message method when somebody sends a message to the channel.
     *
     * @param m The message from the chat.
     */
    public void onMessage(MessageWrapper m, boolean showChannel) {
        if (textPane == null) return;
        Message message = m.getLocal();
        String sender = message.getSender().toLowerCase();
        String channel = message.getChannel();
        String mess = message.getContent();
        boolean isMe = (message.getType() == Message.MessageType.ACTION_MESSAGE);
        try {
            print(m, "\n" + getTime(), GUIMain.norm);
            User u = Settings.channelManager.getUser(sender, true);
            SimpleAttributeSet user = getUserSet(u);
            if (channel.substring(1).equals(sender)) {
                insertIcon(m, IconEnum.BROADCASTER, null);
            }
            if (u.isOp(channel)) {
                if (!channel.substring(1).equals(sender) && !u.isStaff() && !u.isAdmin() && !u.isGlobalMod()) {//not the broadcaster again
                    insertIcon(m, IconEnum.MOD, null);
                }
            }
            if (u.isGlobalMod()) {
                insertIcon(m, IconEnum.GLOBAL_MOD, null);
            }
            if (Settings.showDonorIcons.getValue()) {
                if (u.isDonor()) {
                    insertIcon(m, u.getDonationStatus(), null);
                }
            }
            if (u.isStaff()) {
                insertIcon(m, IconEnum.STAFF, null);
            }
            if (u.isAdmin()) {
                insertIcon(m, IconEnum.ADMIN, null);
            }
            boolean isSubscriber = u.isSubscriber(channel);
            if (isSubscriber) {
                insertIcon(m, IconEnum.SUBSCRIBER, channel);
            } else {
                if (Utils.isMainChannel(channel)) {
                    Optional<Subscriber> sub = Settings.subscriberManager.getSubscriber(sender);
                    if (sub.isPresent() && !sub.get().isActive()) {
                        insertIcon(m, IconEnum.EX_SUBSCRIBER, channel);
                    }
                }
            }
            if (u.isTurbo()) {
                insertIcon(m, IconEnum.TURBO, null);
            }
            //name stuff
            print(m, " ", GUIMain.norm);
            SimpleAttributeSet userColor = new SimpleAttributeSet(user);
            FaceManager.handleNameFaces(sender, user);
            if (showChannel) {
                print(m, u.getDisplayName(), user);
                print(m, " (" + channel.substring(1) + ")" + (isMe ? " " : ": "), GUIMain.norm);
            } else {
                print(m, u.getDisplayName(), user);
                print(m, (!isMe ? ": " : " "), userColor);
            }
            //keyword?
            SimpleAttributeSet set;
            if (Utils.mentionsKeyword(mess)) {
                set = Utils.getSetForKeyword(mess);
            } else {
                set = (isMe ? userColor : GUIMain.norm);
            }
            //URL, Faces, rest of message
            printMessage(m, mess, set, u);

            if (BotnakTrayIcon.shouldDisplayMentions() && !Utils.isTabSelected(index)) {
                if (mess.toLowerCase().contains(Settings.accountManager.getUserAccount().getName().toLowerCase())) {
                    GUIMain.getSystemTrayIcon().displayMention(m.getLocal());
                }
            }

            if (Utils.isMainChannel(channel))
                //check status of the sub, has it been a month?
                Settings.subscriberManager.updateSubscriber(u, channel, isSubscriber);
            if (shouldPulse())
                GUIMain.instance.pulseTab(this);
        } catch (Exception e) {
            GUIMain.log(e);
        }
    }

    /**
     * Credit: TDuva
     * <p>
     * Cycles through message data, tagging things like Faces and URLs.
     *
     * @param text  The message
     * @param style The default message style to use.
     */
    protected void printMessage(MessageWrapper m, String text, SimpleAttributeSet style, User u) {
        // Where stuff was found
        TreeMap<Integer, Integer> ranges = new TreeMap<>();
        // The style of the stuff (basically metadata)
        HashMap<Integer, SimpleAttributeSet> rangesStyle = new HashMap<>();

        findLinks(text, ranges, rangesStyle);
        findEmoticons(text, ranges, rangesStyle, u, m.getLocal().getChannel());

        // Actually print everything
        int lastPrintedPos = 0;
        for (Map.Entry<Integer, Integer> range : ranges.entrySet()) {
            int start = range.getKey();
            int end = range.getValue();
            if (start > lastPrintedPos) {
                // If there is anything between the special stuff, print that
                // first as regular text
                print(m, text.substring(lastPrintedPos, start), style);
            }
            print(m, text.substring(start, end + 1), rangesStyle.get(start));
            lastPrintedPos = end + 1;
        }
        // If anything is left, print that as well as regular text
        if (lastPrintedPos < text.length()) {
            print(m, text.substring(lastPrintedPos), style);
        }
    }

    private void findLinks(String text, Map<Integer, Integer> ranges, Map<Integer, SimpleAttributeSet> rangesStyle) {
        // Find links
        Constants.urlMatcher.reset(text);
        while (Constants.urlMatcher.find()) {
            int start = Constants.urlMatcher.start();
            int end = Constants.urlMatcher.end() - 1;
            if (!Utils.inRanges(start, ranges) && !Utils.inRanges(end, ranges)) {
                String foundUrl = Constants.urlMatcher.group();
                if (Utils.checkURL(foundUrl)) {
                    ranges.put(start, end);
                    rangesStyle.put(start, Utils.URLStyle(foundUrl));
                }
            }
        }
    }

    private void findEmoticons(String text, Map<Integer, Integer> ranges, Map<Integer, SimpleAttributeSet> rangesStyle, User u, String channel) {
        FaceManager.handleFaces(ranges, rangesStyle, text, FaceManager.FACE_TYPE.NORMAL_FACE, null, null);
        if (u != null && u.getEmotes() != null) {
            FaceManager.handleFaces(ranges, rangesStyle, text, FaceManager.FACE_TYPE.TWITCH_FACE, null, u.getEmotes());
        }
        if (Settings.ffzFacesEnable.getValue() && channel != null) {
            channel = channel.replaceAll("#", "");
            FaceManager.handleFaces(ranges, rangesStyle, text, FaceManager.FACE_TYPE.FRANKER_FACE, channel, null);
        }
    }

    protected void print(MessageWrapper wrapper, String string, SimpleAttributeSet set) {
        if (textPane == null) return;
        Runnable r = () -> {
            try {
                textPane.getStyledDocument().insertString(textPane.getStyledDocument().getLength(), string, set);
            } catch (Exception e) {
                GUIMain.log(e);
            }
        };
        wrapper.addPrint(r);
    }

    /**
     * Handles inserting icons before and after the message.
     *
     * @param m      The message itself.
     * @param status IconEnum.Subscriber for sub message, else pass Donor#getDonationStatus(d#getAmount())
     */
    public void onIconMessage(MessageWrapper m, IconEnum status) {
        try {
            Message message = m.getLocal();
            print(m, "\n", GUIMain.norm);
            for (int i = 0; i < 3; i++) {
                insertIcon(m, status, (status == IconEnum.SUBSCRIBER ? message.getChannel() : null));
            }
            print(m, " " + message.getContent() + (status == IconEnum.SUBSCRIBER ? (" (" + (subCount + 1) + ") ") : " "), GUIMain.norm);
            for (int i = 0; i < 3; i++) {
                insertIcon(m, status, (status == IconEnum.SUBSCRIBER ? message.getChannel() : null));
            }
        } catch (Exception e) {
            GUIMain.log(e);
        }
        boolean shouldIncrement = ((status == IconEnum.SUBSCRIBER) && (m.getLocal().getExtra() == null));//checking for repeat messages
        if (shouldIncrement) subCount++;
    }

    public void onSub(MessageWrapper m) {
        onIconMessage(m, IconEnum.SUBSCRIBER);
    }

    public void onWhisper(MessageWrapper m) {
        SimpleAttributeSet senderSet, receiverSet;

        String sender = m.getLocal().getSender();
        String receiver = (String) m.getLocal().getExtra();
        print(m, "\n" + getTime(), GUIMain.norm);
        User senderUser = Settings.channelManager.getUser(sender, true);
        User receiverUser = Settings.channelManager.getUser(receiver, true);
        senderSet = getUserSet(senderUser);
        receiverSet = getUserSet(receiverUser);

        //name stuff
        print(m, " ", GUIMain.norm);
        FaceManager.handleNameFaces(sender, senderSet);
        FaceManager.handleNameFaces(receiverUser.getNick(), receiverSet);
        print(m, senderUser.getDisplayName(), senderSet);
        print(m, " (whisper)-> ", GUIMain.norm);
        print(m, receiverUser.getDisplayName(), receiverSet);
        print(m, ": ", GUIMain.norm);

        printMessage(m, m.getLocal().getContent(), GUIMain.norm, senderUser);
    }

    private SimpleAttributeSet getUserSet(User u) {
        SimpleAttributeSet user = new SimpleAttributeSet();
        StyleConstants.setFontFamily(user, Settings.font.getValue().getFamily());
        StyleConstants.setFontSize(user, Settings.font.getValue().getSize());
        StyleConstants.setForeground(user, Utils.getColorFromUser(u));
        user.addAttribute(HTML.Attribute.NAME, u.getDisplayName());
        return user;
    }

    public void onDonation(MessageWrapper m) {
        Donation d = (Donation) m.getLocal().getExtra();
        onIconMessage(m, Donor.getDonationStatus(d.getAmount()));
    }

    public void insertIcon(MessageWrapper m, IconEnum type, String channel) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        Icons.BotnakIcon icon = Icons.getIcon(type, channel);
        StyleConstants.setIcon(attrs, icon.getImage());
        try {
            print(m, " ", null);
            print(m, icon.getType().type, attrs);
        } catch (Exception e) {
            GUIMain.log("Exception in insertIcon: ");
            GUIMain.log(e);
        }
    }

    public String getText() {
        return (textPane != null && textPane.getText() != null) ? textPane.getText() : "";
    }

    // Source: http://stackoverflow.com/a/4628879
    // by http://stackoverflow.com/users/131872/camickr & Community
    public void cleanupChat() {
        if (scrollablePanel == null || scrollablePanel.getParent() == null) return;
        if (!(scrollablePanel.getParent() instanceof JViewport)) return;
        JViewport viewport = ((JViewport) scrollablePanel.getParent());
        Point startPoint = viewport.getViewPosition();
        // we are not deleting right before the visible area, but one screen behind
        // for convenience, otherwise flickering.
        if (startPoint == null) return;

        final int start = textPane.viewToModel(startPoint);
        if (start > 0) // not equal zero, because then we don't have to delete anything
        {
            final StyledDocument doc = textPane.getStyledDocument();
            try {
                if (Settings.logChat.getValue() && chan != null) {
                    String[] toRemove = doc.getText(0, start).split("\\n");
                    Utils.logChat(toRemove, chan, 1);
                }
                doc.remove(0, start);
                resetCleanupCounter();
            } catch (Exception e) {
                GUIMain.log("Failed clearing chat: ");
                GUIMain.log(e);
            }
        }
        messageOut = false;
    }

    /**
     * Creates a pane of the given channel.
     *
     * @param channel The channel, also used as the key for the hashmap.
     * @return The created ChatPane.
     */
    public static ChatPane createPane(String channel) {
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JTextPane pane = new JTextPane();
        pane.setEditorKit(Constants.wrapEditorKit);
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setBackground(Color.black);
        pane.setFont(Settings.font.getValue());
        pane.addMouseListener(Constants.listenerURL);
        pane.addMouseListener(Constants.listenerName);
        pane.addMouseListener(Constants.listenerFace);
        ScrollablePanel sp = new ScrollablePanel();
        sp.add(pane, BorderLayout.SOUTH);
        scrollPane.setViewportView(sp);
        return new ChatPane(channel, scrollPane, pane, sp, GUIMain.channelPane.getTabCount() - 1);
    }

    /**
     * Deletes the pane and removes the tab from the tabbed pane.
     */
    public void deletePane() {
        if (Settings.logChat.getValue()) {
            Utils.logChat(getText().split("\\n"), chan, 2);
        }
        GUIViewerList list = GUIMain.viewerLists.get(chan);
        if (list != null) {
            list.dispose();
        }
        if (getPoppedOutPane() != null) {
            getPoppedOutPane().dispose();
        }
        GUIMain.channelPane.removeTabAt(index);
        GUIMain.channelPane.setSelectedIndex(index - 1);
    }

    /**
     * Logs a message to this chat pane.
     *
     * @param message  The message itself.
     * @param isSystem Whether the message is a system log message or not.
     */
    public void log(MessageWrapper message, boolean isSystem) {
        print(message, "\n" + getTime(), GUIMain.norm);
        print(message, " " + (isSystem ? "SYS: " : "") + message.getLocal().getContent(), GUIMain.norm);
    }
}
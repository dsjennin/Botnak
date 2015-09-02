package util;

import gui.forms.GUIMain;
import irc.account.Oauth;
import lib.JSON.JSONArray;
import lib.JSON.JSONObject;
import util.settings.Settings;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Nick on 5/22/2015.
 */
public class APIRequests {

    //Anything Twitch
    public static class Twitch {
        /**
         * Gets stream uptime.
         *
         * @return the current stream uptime.
         */
        public static Response getUptimeString(String channelName) {
            if (channelName.contains("#")) channelName = channelName.replace("#", "");
            Response toReturn = new Response();
            try {
                URL nightdev = new URL("https://nightdev.com/hosted/uptime.php?channel=" + channelName);
                String line = Utils.createAndParseBufferedReader(nightdev.openStream());
                if (!line.isEmpty()) {
                    if (line.contains("is not")) {
                        toReturn.setResponseText("The stream is not live!");
                    } else if (line.contains("No chan")) {
                        toReturn.setResponseText("Error checking uptime, no channel specified!");
                    } else {
                        toReturn.wasSuccessful();
                        toReturn.setResponseText("The stream has been live for: " + line);
                    }
                }
            } catch (Exception ignored) {
                toReturn.setResponseText("Error checking uptime due to Exception!");
            }
            return toReturn;
        }

        /**
         * Checks a channel to see if it's live (streaming).
         *
         * @param channelName The name of the channel to check.
         * @return true if the specified channel is live and streaming, else false.
         */
        public static boolean isChannelLive(String channelName) {
            boolean isLive = false;
            try {
                URL twitch = new URL("https://api.twitch.tv/kraken/streams/" + channelName
                        + "?client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(twitch.openStream());
                if (!line.isEmpty()) {
                    JSONObject jsonObject = new JSONObject(line);
                    isLive = !jsonObject.isNull("stream") && !jsonObject.getJSONObject("stream").isNull("preview");
                }
            } catch (Exception ignored) {
            }
            return isLive;
        }

        /**
         * Gets the amount of viewers for a channel.
         *
         * @param channelName The name of the channel to check.
         * @return The int amount of viewers watching the given channel.
         */
        public static int countViewers(String channelName) {
            int count = -1;
            try {//this could be parsed with JSON, but patterns work, and if it ain't broke...
                URL twitch = new URL("https://api.twitch.tv/kraken/streams/" + channelName
                        + "?client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(twitch.openStream());
                if (!line.isEmpty()) {
                    Matcher m = Constants.viewerTwitchPattern.matcher(line);
                    if (m.find()) {
                        try {
                            count = Integer.parseInt(m.group(1));
                        } catch (Exception ignored) {
                        }//bad Int parsing
                    }
                }
            } catch (Exception e) {
                count = -1;
            }
            return count;
        }

        /**
         * Gets the status of a channel, which is the title and game of the stream.
         *
         * @param channel The channel to get the status of.
         * @return A string array with the status as first index and game as second.
         */
        public static String[] getStatusOfStream(String channel) {
            String[] toRet = {"", ""};
            try {
                if (channel.contains("#")) channel = channel.replace("#", "");
                URL twitch = new URL("https://api.twitch.tv/kraken/channels/" + channel
                        + "?client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(twitch.openStream());
                if (!line.isEmpty()) {
                    JSONObject base = new JSONObject(line);
                    //these are never null, just blank strings at worst
                    toRet[0] = base.getString("status");
                    toRet[1] = base.getString("game");
                }
            } catch (Exception e) {
                GUIMain.log("Failed to get status of stream due to Exception: ");
                GUIMain.log(e);
            }
            return toRet;
        }

        /**
         * Gets the title of a given channel.
         *
         * @param channel The channel to get the title of.
         * @return The title of the stream.
         */
        public static String getTitleOfStream(String channel) {
            String[] status = getStatusOfStream(channel);
            return status[0];
        }

        /**
         * Gets the game of a given channel.
         *
         * @param channel The channel to get the game of.
         * @return An empty string if not playing, otherwise the game being played.
         */
        public static String getGameOfStream(String channel) {
            String[] status = getStatusOfStream(channel);
            return status[1];
        }

        /**
         * Updates the stream's status to a given parameter.
         *
         * @param key     The oauth key which MUST be authorized to edit the status of the stream.
         * @param channel The channel to edit.
         * @param message The message containing the new title/game to update to.
         * @param isTitle If the change is for the title or game.
         * @return The response Botnak has for the method.
         */
        public static Response setStreamStatus(Oauth key, String channel, String message, boolean isTitle) {
            Response toReturn = new Response();
            if (key.canSetTitle()) {
                String add = isTitle ? "title" : "game";
                if (message.split(" ").length > 1) {
                    String toChangeTo = message.substring(message.indexOf(' ') + 1);
                    if (toChangeTo.equals(" ") || toChangeTo.equals("null")) toChangeTo = "";
                    if (toChangeTo.equalsIgnoreCase(isTitle ? getTitleOfStream(channel) : getGameOfStream(channel))) {
                        toReturn.setResponseText("Failed to set " + add + ", the " + add + " is already set to that!");
                    } else {
                        Response status = setStatusOfStream(key.getKey(), channel,
                                isTitle ? toChangeTo : getTitleOfStream(channel),
                                isTitle ? getGameOfStream(channel) : toChangeTo);
                        if (status.isSuccessful()) {
                            toReturn.wasSuccessful();
                            toChangeTo = "".equals(toChangeTo) ? (isTitle ? "(untitled broadcast)" : "(not playing a game)") : toChangeTo;
                            toReturn.setResponseText("Successfully set " + add + " to: \"" + toChangeTo + "\" !");
                        } else {
                            toReturn.setResponseText(status.getResponseText());
                        }
                    }
                } else {
                    toReturn.setResponseText("Failed to set status status of the " + add + ", usage: !set" + add + " (new " + add + ") or \"null\"");
                }
            } else {
                toReturn.setResponseText("This OAuth key cannot update the status of the stream! Try re-authenticating in the Settings GUI!");
            }
            return toReturn;
        }

        /**
         * Sets the status of a stream.
         *
         * @param key     The oauth key which MUST be authorized to edit the status of a stream.
         * @param channel The channel to edit.
         * @param title   The title to set.
         * @param game    The game to set.
         * @return The response Botnak has for the method.
         */
        public static Response setStatusOfStream(String key, String channel, String title, String game) {
            Response toReturn = new Response();
            try {
                if (channel.contains("#")) channel = channel.replace("#", "");
                String request = "https://api.twitch.tv/kraken/channels/" + channel +
                        "?channel[status]=" + URLEncoder.encode(title, "UTF-8") +
                        "&channel[game]=" + URLEncoder.encode(game, "UTF-8") +
                        "&oauth_token=" + key.split(":")[1] + "&_method=put" +
                        "&client_id=qw8d3ve921t0n6e3if07l664f1jn1y7";
                URL twitch = new URL(request);
                String line = Utils.createAndParseBufferedReader(twitch.openStream());
                if (!line.isEmpty() && line.contains(title) && line.contains(game)) {
                    toReturn.wasSuccessful();
                }
            } catch (Exception e) {
                GUIMain.log("Failed to update status due to Exception: ");
                GUIMain.log(e);
                toReturn.setResponseText("Failed to update status due to Exception!");
            }
            return toReturn;
        }

        /**
         * Plays an ad on stream.
         *
         * @param key     The oauth key which MUST be authorized to play a commercial on a stream.
         * @param channel The channel to play the ad for.
         * @param length  How long
         * @return true if the commercial played, else false.
         */
        public static boolean playAdvert(String key, String channel, int length) {
            boolean toReturn = false;
            try {
                length = Utils.capNumber(30, 180, length);//can't be longer than 3 mins/shorter than 30 sec
                if ((length % 30) != 0) length = 30;//has to be divisible by 30 seconds
                if (channel.contains("#")) channel = channel.replace("#", "");
                String request = "https://api.twitch.tv/kraken/channels/" + channel + "/commercial";
                URL twitch = new URL(request);
                HttpURLConnection c = (HttpURLConnection) twitch.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                String toWrite = "length=" + length;
                c.setRequestProperty("Client-ID", "qw8d3ve921t0n6e3if07l664f1jn1y7");
                c.setRequestProperty("Authorization", "OAuth " + key.split(":")[1]);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setRequestProperty("Content-Length", String.valueOf(toWrite.length()));
                OutputStreamWriter op = new OutputStreamWriter(c.getOutputStream());
                op.write(toWrite);
                op.close();
                try {
                    int response = c.getResponseCode();
                    toReturn = (response == 204);
                } catch (Exception e) {
                    GUIMain.log("Failed to get response code due to Exception: ");
                    GUIMain.log(e);
                }
                c.disconnect();
            } catch (Exception e) {
                GUIMain.log("Failed to play advertisement due to Exception: ");
                GUIMain.log(e);
            }
            return toReturn;
        }

        /**
         * Obtains the title and author of a video on Twitch.
         *
         * @param URL The URL to the video.
         * @return The appropriate response.
         */
        public static Response getTitleOfVOD(String URL) {
            Response toReturn = new Response();
            try {
                String ID = "";
                Pattern p = Pattern.compile("/[vcb]/([^&\\?/]+)");
                Matcher m = p.matcher(URL);
                if (m.find()) {
                    ID = m.group().replaceAll("/", "");
                }
                URL request = new URL("https://api.twitch.tv/kraken/videos/" + ID
                        + "?client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(request.openStream());
                if (!line.isEmpty()) {
                    JSONObject init = new JSONObject(line);
                    String title = init.getString("title");
                    JSONObject channel = init.getJSONObject("channel");
                    String author = channel.getString("display_name");
                    toReturn.wasSuccessful();
                    toReturn.setResponseText("Linked Twitch VOD: \"" + title + "\" by " + author);
                }
            } catch (Exception e) {
                toReturn.setResponseText("Failed to parse Twitch VOD due to an Exception!");
            }
            return toReturn;
        }

        /**
         * Uses the main account's OAuth to check for your followed channels, if enabled.
         *
         * @param key The oauth key to use.
         * @return An array of live streams (empty if no one is live).
         */
        public static ArrayList<String> getLiveFollowedChannels(String key) {
            ArrayList<String> toReturn = new ArrayList<>();
            try {
                URL request = new URL("https://api.twitch.tv/kraken/streams/followed?oauth_token="
                        + key + "&limit=100&client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(request.openStream());
                if (!line.isEmpty()) {
                    JSONObject init = new JSONObject(line);
                    JSONArray streams = init.getJSONArray("streams");
                    for (int i = 0; i < streams.length(); i++) {
                        JSONObject stream = streams.getJSONObject(i);
                        JSONObject channel = stream.getJSONObject("channel");
                        toReturn.add(channel.getString("name").toLowerCase());
                    }
                }
            } catch (Exception e) {
                if (!e.getMessage().contains("401")) {
                    GUIMain.log("Failed to get live followed channels due to exception:");
                    GUIMain.log(e);
                }
            }
            return toReturn;
        }

        /**
         * Gets username suggestions when adding a stream.
         *
         * @param partial The partially typed text to prompt a suggestion.
         * @return An array of suggested stream names.
         */
        public static String[] getUsernameSuggestions(String partial) {
            ArrayList<String> toReturn = new ArrayList<>();
            try {
                URL request = new URL("https://api.twitch.tv/kraken/search/channels?limit=10&q=" + partial +
                        "&client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(request.openStream());
                if (!line.isEmpty()) {
                    JSONObject init = new JSONObject(line);
                    JSONArray channels = init.getJSONArray("channels");
                    for (int i = 0; i < channels.length(); i++) {
                        JSONObject channel = channels.getJSONObject(i);
                        toReturn.add(channel.getString("name"));
                    }
                }
            } catch (Exception e) {
                GUIMain.log(e);
            }
            return toReturn.toArray(new String[toReturn.size()]);
        }

        public static String[] getLast20Followers(String channel) {
            ArrayList<String> toReturn = new ArrayList<>();
            try {
                URL request = new URL("https://api.twitch.tv/kraken/channels/" + channel + "/follows?limit=20&" +
                        "client_id=qw8d3ve921t0n6e3if07l664f1jn1y7");
                String line = Utils.createAndParseBufferedReader(request.openStream());
                if (!line.isEmpty()) {
                    JSONObject init = new JSONObject(line);
                    JSONArray follows = init.getJSONArray("follows");
                    for (int i = 0; i < follows.length(); i++) {
                        JSONObject person = follows.getJSONObject(i);
                        JSONObject user = person.getJSONObject("user");
                        toReturn.add(user.getString("name"));
                    }
                }

            } catch (Exception e) {
                GUIMain.log(e);
            }
            return toReturn.toArray(new String[toReturn.size()]);
        }
    }

    //Current playing song
    public static class LastFM {
        /**
         * Gets the currently playing song from LastFM, assuming the LastFM account was set up correctly.
         *
         * @return The name of the song, else an empty string.
         */
        public static Response getCurrentlyPlaying() {
            Response toReturn = new Response();
            if ("".equals(Settings.lastFMAccount.getValue())) {
                toReturn.setResponseText("Failed to fetch current playing song, the user has no last.fm account set!");
                return toReturn;
            }
            //TODO check the song requests engine to see if that is currently playing
            String tracks_url = "http://www.last.fm/user/" + Settings.lastFMAccount.getValue() + "/now";
            try {
                URL request = new URL("http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=" +
                        Settings.lastFMAccount.getValue() + "&api_key=e0d3467ebb54bb110787dd3d77705e1a&format=json");
                String line = Utils.createAndParseBufferedReader(request.openStream());
                JSONObject outermost = new JSONObject(line);
                JSONObject recentTracks = outermost.getJSONObject("recenttracks");
                JSONArray songsArray = recentTracks.getJSONArray("track");
                if (songsArray.length() > 0) {
                    JSONObject mostRecent = songsArray.getJSONObject(0);
                    JSONObject artist = mostRecent.getJSONObject("artist");
                    String artistOfSong = artist.getString("#text");
                    String nameOfSong = mostRecent.getString("name");
                    if (mostRecent.has("@attr")) {//it's the current song
                        toReturn.setResponseText("The current song is: " + artistOfSong + " - " + nameOfSong + " || " + tracks_url);
                    } else {
                        toReturn.setResponseText("The most recent song was: " + artistOfSong + " - " + nameOfSong + " || " + tracks_url);
                    }
                    toReturn.wasSuccessful();
                } else {
                    toReturn.setResponseText("Failed to fetch current song; last.fm shows no recent tracks!");
                }
            } catch (Exception e) {
                toReturn.setResponseText("Failed to fetch current playing song due to an Exception!");
                GUIMain.log(e);
            }
            return toReturn;
        }
    }


    //Youtube video data
    public static class YouTube {
        /**
         * Fetches the title, author, and duration of a linked YouTube video.
         *
         * @param fullURL The URL to the video.
         * @return The appropriate response.
         */
        public static Response getVideoData(String fullURL) {
            Response toReturn = new Response();
            try {
                Pattern p = null;
                if (fullURL.contains("youtu.be/")) {
                    p = Pattern.compile("youtu\\.be/([^&\\?/]+)");
                } else if (fullURL.contains("v=")) {
                    p = Pattern.compile("v=([^&\\?/]+)");
                } else if (fullURL.contains("/embed/")) {
                    p = Pattern.compile("youtube\\.com/embed/([^&\\?/]+)");
                }
                if (p == null) {
                    toReturn.setResponseText("Could not read YouTube URL!");
                    return toReturn;
                }
                Matcher m = p.matcher(fullURL);
                if (m.find()) {
                    String ID = m.group(1);
                    URL request = new URL("https://www.googleapis.com/youtube/v3/videos?id=" + ID +
                            "&part=snippet,contentDetails&key=AIzaSyDVKqwiK_VGelKlNCHtEFWFbDfVuzl9Q8c" +
                            "&fields=items(snippet(title,channelTitle),contentDetails(duration))");
                    String line = Utils.createAndParseBufferedReader(request.openStream());
                    if (!line.isEmpty()) {
                        JSONObject initial = new JSONObject(line);
                        JSONArray items = initial.getJSONArray("items");
                        if (items.length() < 1) {
                            toReturn.setResponseText("Failed to parse YouTube video! Perhaps a bad ID?");
                            return toReturn;
                        }
                        JSONObject juicyDetails = items.getJSONObject(0);
                        JSONObject titleAndChannel = juicyDetails.getJSONObject("snippet");
                        JSONObject duration = juicyDetails.getJSONObject("contentDetails");
                        String title = titleAndChannel.getString("title");
                        String channelName = titleAndChannel.getString("channelTitle");
                        Duration d = Duration.parse(duration.getString("duration"));
                        String time = getTimeString(d);
                        toReturn.setResponseText("Linked YouTube Video: \"" + title + "\" by " + channelName + " [" + time + "]");
                        toReturn.wasSuccessful();
                    }
                }
            } catch (Exception e) {
                toReturn.setResponseText("Failed to parse YouTube video due to an Exception!");
            }
            return toReturn;
        }

        private static String getTimeString(Duration d) {
            int s = (int) d.getSeconds();
            int hours = s / 3600;
            int minutes = (s % 3600) / 60;
            int seconds = (s % 60);
            if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
            else return String.format("%02d:%02d", minutes, seconds);
        }
    }

    //URL Un-shortening
    public static class UnshortenIt {
        /**
         * Fetches the domain of the shortened URL's un-shortened destination.
         *
         * @param shortenedURL The shortened URL string.
         * @return The appropriate response.
         */
        public static Response getUnshortened(String shortenedURL) {
            Response toReturn = new Response();
            toReturn.setResponseText("Failed to un-shorten URL! Click with caution!");
            try {
                URL request = new URL("https://therealurl.appspot.com/?url=" + shortenedURL);
                String line = Utils.createAndParseBufferedReader(request.openStream());
                if (!line.isEmpty()) {
                    if (!line.equals(shortenedURL)) {
                        line = getHost(line);
                        toReturn.setResponseText("Linked Shortened URL directs to: " + line + " !");
                    } else {
                        toReturn.setResponseText("Invalid shortened URL!");
                    }
                }
            } catch (Exception ignored) {
            }
            return toReturn;
        }

        private static String getHost(String webURL) {
            String toReturn = webURL;
            try {
                URL url = new URL(webURL);
                toReturn = url.getHost();
            } catch (Exception ignored) {
            }
            return toReturn;
        }
    }
}
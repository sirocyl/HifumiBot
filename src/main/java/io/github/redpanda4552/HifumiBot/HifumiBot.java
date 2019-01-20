/**
 * This file is part of HifumiBot, licensed under the MIT License (MIT)
 * 
 * Copyright (c) 2018 Brian Wood
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.redpanda4552.HifumiBot;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;

import javax.security.auth.login.LoginException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.github.redpanda4552.HifumiBot.voting.VoteManager;
import io.github.redpanda4552.HifumiBot.wiki.WikiPage;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;


public class HifumiBot {

    private static HifumiBot self;
    private static String discordBotToken, outputChannelId;
    private static String superuserId;
    private static boolean debug = false;
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar HifumiBot-x.y.z.jar <discord-bot-token> <output-channel-id> <superuser-id> [-d]");
            return;
        }
        
        discordBotToken = args[0];
        outputChannelId = args[1];
        superuserId = args[2];
        
        if (args.length >= 4 && args[3].equalsIgnoreCase("-d"))
            debug = true;
        
        System.out.println("Arguments parsed");
        self = new HifumiBot();
    }
    
    public static HifumiBot getSelf() {
        return self;
    }
    
    private final String FULL_GAMES_URL = "https://wiki.pcsx2.net/Complete_List_of_Games";
    private final String DB_LOCATION = "hifumi-commands.db";
    
    private JDA jda;
    private PermissionManager permissionManager;
    private VoteManager voteManager;
    private CommandInterpreter commandInterpreter;
    private DynamicCommandLoader dynamicCommandLoader;
    private EventListener eventListener;
    private Thread monitorThread;
    private Connection connection;
    
    private HashMap<String, String> fullGamesMap = new HashMap<String, String>();
    
    public HifumiBot() {
        self = this;
        
        if (discordBotToken == null || discordBotToken.isEmpty()) {
            System.out.println("Attempted to start with a null or empty Discord bot token!");
            return;
        }
        
        if (outputChannelId == null || outputChannelId.isEmpty()) {
            System.out.println("Output channel id is null or empty! I won't be able to send messages!");
        }
        
        try {
            jda = new JDABuilder(AccountType.BOT)
                    .setToken(discordBotToken)
                    .setAutoReconnect(true)
                    .build().awaitReady();
        } catch (LoginException | IllegalArgumentException | InterruptedException e) {
            e.printStackTrace();
        }
        
        // Database must be ready in order to load dynamic commands
        updateStatus("Starting...");
        
        try {
            File file = new File(DB_LOCATION);
            file.createNewFile();
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_LOCATION);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
        
        // This must be ready BEFORE the command interpreter's constructor fires.
        dynamicCommandLoader = new DynamicCommandLoader(this);
        permissionManager = new PermissionManager(superuserId);
        jda.addEventListener(commandInterpreter = new CommandInterpreter(this));
        commandInterpreter.refreshCommandMap();
        
        try {
            Elements anchors = Jsoup.connect(FULL_GAMES_URL).maxBodySize(0).get().getElementsByClass("wikitable").first().getElementsByTag("a");
            
            for (Element anchor : anchors)
                fullGamesMap.put(anchor.attr("title"), WikiPage.BASE_URL + anchor.attr("href"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        voteManager = new VoteManager(this);
        jda.addEventListener(eventListener = new EventListener(this));
        startMonitor();
        updateStatus(">help" + (debug ? " [Debug Mode]" : ""));
    }
    
    private void updateStatus(String str) {
        jda.getPresence().setGame(Game.watching(str));
    }
    
    public JDA getJDA() {
        return jda;
    }
    
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }
    
    public VoteManager getVoteManager() {
        return voteManager;
    }
    
    public CommandInterpreter getCommandInterpreter() {
        return commandInterpreter;
    }
    
    public DynamicCommandLoader getDynamicCommandLoader() {
        return dynamicCommandLoader;
    }
    
    public EventListener getEventListener() {
        return eventListener;
    }
    
    public HashMap<String, String> getFullGamesMap() {
        return fullGamesMap;
    }
    
    public Connection getDatabaseConnection() {
        return connection;
    }
    
    public void shutdown(boolean reload) {
        HifumiBot.getSelf().getJDA().getPresence().setGame(Game.watching("Shutting Down..."));
        stopMonitor();
        jda.shutdown();
        
        if (reload)
            self = new HifumiBot();
    }
    
    public void stopMonitor() {
        monitorThread.interrupt();
    }
    
    public void startMonitor() {
        monitorThread = new Thread(new BuildMonitor(jda.getTextChannelById(outputChannelId)), "BuildMonitor");
        monitorThread.start();
        System.out.println("BuildMonitor thread started");
    }
    
    public Message sendMessage(MessageChannel channel, MessageEmbed embed) {
        return channel.sendMessage(embed).complete();
    }
    
    public Message sendMessage(MessageChannel channel, String... strArr) {
        MessageBuilder mb = new MessageBuilder();
        
        for (String str : strArr) {
            mb.append(str);
        }
        
        return sendMessage(channel, mb.build());
    }
    
    public Message sendMessage(MessageChannel channel, Message msg) {
        return channel.sendMessage(msg).complete();
    }
}

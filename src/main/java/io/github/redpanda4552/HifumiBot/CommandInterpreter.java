/**
 * This file is part of HifumiBot, licensed under the MIT License (MIT)
 * 
 * Copyright (c) 2017 Brian Wood
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import org.apache.commons.lang3.ArrayUtils;

import io.github.redpanda4552.HifumiBot.command.AbstractCommand;
import io.github.redpanda4552.HifumiBot.command.CommandDev;
import io.github.redpanda4552.HifumiBot.command.CommandDynCmd;
import io.github.redpanda4552.HifumiBot.command.CommandHelp;
import io.github.redpanda4552.HifumiBot.command.CommandReload;
import io.github.redpanda4552.HifumiBot.command.CommandWarez;
import io.github.redpanda4552.HifumiBot.command.CommandWiki;
import io.github.redpanda4552.HifumiBot.command.DynamicCommand;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class CommandInterpreter extends ListenerAdapter {

    public static final String PREFIX = ">";
    
    private HashMap<String, AbstractCommand> commandMap = new HashMap<String, AbstractCommand>();
    
    /**
     * @param hifumiBot - Required because we are instancing commands before
     * HifumiBot.self has been assigned.
     */
    public CommandInterpreter(HifumiBot hifumiBot) {
        //commandMap.put("cpu", new CommandCPU());
        commandMap.put("reload", new CommandReload(hifumiBot));
        commandMap.put("wiki", new CommandWiki(hifumiBot));
        commandMap.put("warez", new CommandWarez(hifumiBot));
        commandMap.put("dev", new CommandDev(hifumiBot));
        commandMap.put("dyncmd", new CommandDynCmd(hifumiBot));
        
        ResultSet res = hifumiBot.getDynamicCommandLoader().getDynamicCommands();
        
        try {
            if (res == null)
                 return;
            
            while (res.next()) {
                commandMap.put(res.getString("name"), new DynamicCommand(hifumiBot, res.getBoolean("admin"), res.getString("helpText"), res.getString("title"), res.getString("body"), res.getString("imageUrl")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        commandMap.put("help", new CommandHelp(hifumiBot));
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        Member sender = event.getMember();
        Message message = event.getMessage();
        String[] args = message.getContentDisplay().split(" ");
        String command = args[0].toLowerCase();
        args = ArrayUtils.remove(args, 0);
        AbstractCommand toExecute;
        
        if (!command.startsWith(PREFIX))
            return;
        
        command = command.replaceFirst(PREFIX, "");
        
        if ((toExecute = commandMap.get(command)) != null)
            toExecute.run(channel, sender, args);
    }
    
    public HashMap<String, AbstractCommand> getCommandMap() {
        return commandMap;
    }
}

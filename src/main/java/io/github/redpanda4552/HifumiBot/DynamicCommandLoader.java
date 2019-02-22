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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class DynamicCommandLoader {
    
    private final String DYNCMD_TABLE = "commands_v2";
    private final String DYNCMD_TABLE_LEGACY = "commands";
    
    private Connection connection;

    public DynamicCommandLoader(HifumiBot hifumiBot) {
        this.connection = hifumiBot.getDatabaseConnection();
        
        try {
            PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + DYNCMD_TABLE + " (name TEXT PRIMARY KEY, helpText TEXT, category TEXT, admin BOOLEAN, title TEXT, body TEXT, imageUrl TEXT);");
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        migrateLegacy();
    }
    
    private boolean checkLegacyExists() {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DYNCMD_TABLE_LEGACY + "';");
            ResultSet res = ps.executeQuery();
            
            while (res.next()) {
                if (res.getString("name").equals(DYNCMD_TABLE_LEGACY)) {
                    ps.close();
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    private void migrateLegacy() {
        if (!checkLegacyExists())
            return;
        
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + DYNCMD_TABLE_LEGACY + ";");
            ResultSet res = ps.executeQuery();
            
            while (res.next()) {
                PreparedStatement ps2 = connection.prepareStatement("INSERT INTO " + DYNCMD_TABLE + " (name, helpText, category, admin, title, body, imageUrl) VALUES (?, ?, ?, ?, ?, ?, ?);");
                ps2.setString(1, res.getString("name"));
                ps2.setString(2, res.getString("helpText"));
                ps2.setString(3, null);
                ps2.setBoolean(4, res.getBoolean("admin"));
                ps2.setString(5, res.getString("title"));
                ps2.setString(6, res.getString("body"));
                ps2.setString(7, res.getString("imageUrl"));
                ps2.executeUpdate();
            }
            ps.close();
            ps = connection.prepareStatement("DROP TABLE IF EXISTS " + DYNCMD_TABLE_LEGACY + ";");
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public ArrayList<String> getCategories() {
        ArrayList<String> ret = new ArrayList<String>();
        
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT DISTINCT category FROM " + DYNCMD_TABLE + ";");
            ResultSet res = ps.executeQuery();
            
            while (res.next())
                ret.add(res.getString("category"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return ret;
    }
    
    public ResultSet getDynamicCommands() {
        try {
            return connection.prepareStatement("SELECT * FROM " + DYNCMD_TABLE + ";").executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    public boolean insertCommand(String name, String helpText, String category) {
        // Check that the attempted command doesn't already exist in the interpreter. Prevents overrides of things like reload.
        if (HifumiBot.getSelf().getCommandInterpreter().getCommandMap().containsKey(name))
            return false;
        
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO " + DYNCMD_TABLE + " (name, helpText, category, admin, title, body, imageUrl) VALUES (?, ?, ?, ?, ?, ?, ?);");
            ps.setString(1, name);
            ps.setString(2, helpText);
            ps.setString(3, category);
            ps.setBoolean(4, false);
            ps.setString(5, null);
            ps.setString(6, null);
            ps.setString(7, null);
            ps.executeUpdate();
            HifumiBot.getSelf().getCommandInterpreter().refreshCommandMap();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    public boolean updateCommand(String name, String attribute, String value) {
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE " + DYNCMD_TABLE + " SET " + attribute + " = ? WHERE name = ?;");
            
            // Lets not SQL inject ourselves. Return false if the attribute is not one we are expecting.
            switch (attribute) {
            case "name":
            case "helpText":
            case "category":
            case "title":
            case "body":
            case "imageUrl":
                ps.setString(1, value);
                break;
            case "admin":
                ps.setBoolean(1, Boolean.parseBoolean(value));
                break;
            default:
                return false;
            }
            
            ps.setString(2, name);
            ps.executeUpdate();
            HifumiBot.getSelf().getCommandInterpreter().refreshCommandMap();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    public boolean removeCommand(String name) {
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM " + DYNCMD_TABLE + " WHERE name = ?;");
            ps.setString(1, name);
            ps.executeUpdate();
            HifumiBot.getSelf().getCommandInterpreter().refreshCommandMap();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }
}

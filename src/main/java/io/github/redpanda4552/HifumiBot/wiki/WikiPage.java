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
package io.github.redpanda4552.HifumiBot.wiki;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WikiPage {

    public static final String BASE_URL = "https://wiki.pcsx2.net";
    
    private Document page;
    
    private String title, wikiPageUrl, coverArtUrl;
    private HashMap<String, RegionSet> regionSets = new HashMap<String, RegionSet>();
    private ArrayList<String> knownIssues = new ArrayList<String>();
    
    public WikiPage(String url) {
        try {
            wikiPageUrl = url;
            page = Jsoup.connect(url).get();
            title = page.getElementById("firstHeading").ownText();
            Element infoBox = page.getElementsByClass("infobox").first();
            Elements tables = infoBox.getElementsByTag("table");
            coverArtUrl = BASE_URL + infoBox.getElementsByTag("img").first().attr("src");
            
            int skips = 0;
            
            for (Element table : tables) {
                // If one of the major tables (filters out the spacer tables for icons)
                if (table.attr("width").equals("100%")) {
                    // Skip the first two of these (ratings and languages)
                    if (skips++ < 2)
                        continue;
                    
                    Elements tableRows = table.getElementsByTag("tr");
                    RegionSet regionSet = new RegionSet();
                    
                    // This is going to be hard-coded, AF, but for what it is, not worth making a super high level system.
                    for (Element tableRow : tableRows) {
                        if (regionSet.getRegion() == null) {
                            regionSet.setRegion(tableRow.text());
                        } else if (regionSet.getSerial() == null) {
                            Element cell = tableRow.getElementsByTag("td").last();
                            regionSet.setSerial(cell.ownText());
                        } else if (regionSet.getRelease() == null) {
                            Element cell = tableRow.getElementsByTag("td").last();
                            regionSet.setRelease(cell.text());
                        } else if (regionSet.getCRC() == null) {
                            Element cell = tableRow.getElementsByTag("td").last();
                            regionSet.setCRC(cell.ownText().replace("?", "").trim());
                        } else if (regionSet.getWindowsStatus() == null) {
                            Element cell = tableRow.getElementsByTag("td").last();
                            regionSet.setWindowsStatus(cell.text().replace("?", "").trim());
                            //regionSet.setWindowsStatusColor(Integer.parseInt(tableRow.attr("bgcolor").replace("#", "0x"), 16));
                        } else if (regionSet.getLinuxStatus() == null) {
                            Element cell = tableRow.getElementsByTag("td").last();
                            regionSet.setLinuxStatus(cell.text().replace("?", "").trim());
                            //regionSet.setLinuxStatusColor(Integer.parseInt(tableRow.attr("bgcolor").replace("#", "0x"), 16));
                        }
                    }
                    
                    regionSets.put(regionSet.getRegion(), regionSet);
                }
            }
            
            Element currentElement = page.getElementById("mw-content-text").getElementById("Known_Issues");
            
            if (currentElement != null && currentElement.hasParent())
                currentElement = currentElement.parent();
            
            while (currentElement != null && currentElement.nextElementSibling() != null) {
                currentElement = currentElement.nextElementSibling();
                
                if (currentElement.tagName().equals("h2")) {
                    break;
                } else if (currentElement.tagName().equals("h3")) {
                    knownIssues.add(currentElement.text());
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getWikiPageUrl() {
        return wikiPageUrl;
    }
    
    public String getCoverArtUrl() {
        return coverArtUrl;
    }
    
    public HashMap<String, RegionSet> getRegionSets() {
        return regionSets;
    }
    
    public ArrayList<String> getKnownIssues() {
        return knownIssues;
    }
}
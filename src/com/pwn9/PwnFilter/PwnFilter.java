/*
 * PwnFilter -- Regex-based User Filter Plugin for Bukkit-based Minecraft servers.
 * Copyright (c) 2013 Pwn9.com. Tremor77 <admin@pwn9.com> & Sage905 <patrick@toal.ca>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 */

package com.pwn9.PwnFilter;

import com.google.common.collect.MapMaker;
import com.pwn9.PwnFilter.api.ClientManager;
import com.pwn9.PwnFilter.api.FilterClient;
import com.pwn9.PwnFilter.command.pfcls;
import com.pwn9.PwnFilter.command.pfdumpcache;
import com.pwn9.PwnFilter.command.pfmute;
import com.pwn9.PwnFilter.command.pfreload;
import com.pwn9.PwnFilter.listener.*;
import com.pwn9.PwnFilter.rules.RuleChain;
import com.pwn9.PwnFilter.rules.RuleManager;
import com.pwn9.PwnFilter.util.FileUtil;
import com.pwn9.PwnFilter.util.LogManager;
import com.pwn9.PwnFilter.util.PointManager;
import com.pwn9.PwnFilter.util.Tracker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

/**
 * A Regular Expression (REGEX) Chat Filter For Bukkit with many great features
 *
 * @author tremor77
 * @version $Id: $Id
 */

// TODO: Add support for Books
// TODO: Enable configuration management /pfset /pfsave
// TODO: It's powerful.  Now, make it easier.
// TODO: Make 'base' files that users can pull in to get started quickly (eg: swearing.txt, hate.txt, etc.)
// TODO: Multiverse-support? (Different configs for different worlds)
public class PwnFilter extends JavaPlugin {

    private static PwnFilter _instance;

    // Metrics data
    private Metrics metrics;
    /** Constant <code>matchTracker</code> */
    public static Tracker matchTracker;
    private Metrics.Graph eventGraph;

    /** Constant <code>killedPlayers</code> */
    public static ConcurrentMap<Player, String> killedPlayers = new MapMaker().concurrencyLevel(2).weakKeys().makeMap();

    // Filter switches
    /** Constant <code>decolor=false</code> */
    public static boolean decolor = false;
    /** Constant <code>pwnMute=false</code> */
    public static boolean pwnMute = false;

    /** Constant <code>lastMessage</code> */
    public static ConcurrentMap<Player, String> lastMessage = new MapMaker().concurrencyLevel(2).weakKeys().makeMap();
    /** Constant <code>economy</code> */
    public static Economy economy = null;

    private File textDir;

    /**
     * <p>Constructor for PwnFilter.</p>
     */
    public PwnFilter() {
        _instance = this;
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link com.pwn9.PwnFilter.PwnFilter} object.
     */
    public static PwnFilter getInstance() {
        return _instance;
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad() {

        // Set up the Log manager.
        LogManager.getInstance(getLogger(),getDataFolder());

        // Initialize the Rule Manager
        RuleManager.init(this);

        //Try to migrate old rules.txt file
        RuleManager.getInstance().migrateRules(getDataFolder());


    }

    /**
     * <p>onEnable.</p>
     */
    public void onEnable() {

        // Initialize Configuration
        saveDefaultConfig();

        // Now get our configuration
        configurePlugin();

        // Set up a Vault economy for actions like "fine" (optional)
        setupEconomy();

        // Initialize the DataCache
        DataCache.init(this);

        DataCache.getInstance().addPermissions(getDescription().getPermissions());

        // Initialize Points Manager if its enabled
        PointManager.setup(this);

        // Activate Plugin Metrics
        activateMetrics();

        //Load up our listeners
        ClientManager clientManager = ClientManager.getInstance();
        clientManager.registerClient(new PwnFilterCommandListener(this), this);
        clientManager.registerClient(new PwnFilterInvListener(this), this);
        clientManager.registerClient(new PwnFilterPlayerListener(this), this);
        clientManager.registerClient(new PwnFilterServerCommandListener(this), this);
        clientManager.registerClient(new PwnFilterSignListener(this), this);
        clientManager.registerClient(new PwnFilterBookListener(this), this);


        // The Entity Death handler, for custom death messages.
        getServer().getPluginManager().registerEvents(new PwnFilterEntityListener(),this);
        // The DataCache handler, for async-safe player info (name/world/permissions)
        getServer().getPluginManager().registerEvents(new PlayerCacheListener(), this);

        // Start the DataCache
        DataCache.getInstance().start();

        // Enable the listeners
        clientManager.enableClients();

        // Set up Command Handlers
        getCommand("pfreload").setExecutor(new pfreload(this));
        getCommand("pfcls").setExecutor(new pfcls(this));
        getCommand("pfmute").setExecutor(new pfmute(this));
        getCommand("pfdumpcache").setExecutor(new pfdumpcache());

    }

    /**
     * <p>onDisable.</p>
     */
    public void onDisable() {

        ClientManager.getInstance().unregisterClients();

        HandlerList.unregisterAll(this); // Unregister all remaining handlers.

        // Shutdown the DataCache
        DataCache.getInstance().stop();

        LogManager.getInstance().stop();

    }

    /**
     * <p>activateMetrics.</p>
     */
    public void activateMetrics() {
        // Activate Plugin Metrics
        try {
            if (metrics == null) {
                metrics = new Metrics(this);

                eventGraph = metrics.createGraph("Rules by Event");
                updateMetrics();

                Metrics.Graph matchGraph = metrics.createGraph("Matches");
                matchTracker = new Tracker("Matches");

                matchGraph.addPlotter(matchTracker);
            }
            metrics.start();


        } catch (IOException e) {
            LogManager.logger.fine(e.getMessage());
        }

    }

    /**
     * <p>updateMetrics.</p>
     */
    public void updateMetrics() {

        ArrayList<String> activeListenerNames = new ArrayList<String>();
        for (FilterClient f : ClientManager.getInstance().getActiveClients()) {
            activeListenerNames.add(f.getShortName());
        }

        // Remove old plotters
        for (Metrics.Plotter p : eventGraph.getPlotters()) {
            if (!activeListenerNames.contains(p.getColumnName())) {
                eventGraph.removePlotter(p);
            }
        }

        // Add new plotters
        for (final FilterClient f : ClientManager.getInstance().getActiveClients()) {
            final String eventName = f.getShortName();
            eventGraph.addPlotter(new Metrics.Plotter(eventName) {
                @Override
                public int getValue() {
                    RuleChain r = f.getRuleChain();
                    if (r != null) {
                        return r.ruleCount(); // Number of rules for this event type
                    } else
                        return 0;
                }
            });
        }

    }

    /**
     * <p>configurePlugin.</p>
     */
    public void configurePlugin() {

        if (getConfig().getBoolean("logfile")) {
            LogManager.getInstance().start();
        } else { // Needed during configuration reload to turn off logging if the option changes
            LogManager.getInstance().stop();
        }

        RuleManager.getInstance().setRuleDir(getConfig().getString("ruledirectory"));

        // For Actionrespondfile
        String textDirectory = getConfig().getString("textdir","textfiles");
        if (textDirectory.startsWith("/")) {
            textDir = new File(textDirectory);
        } else {
            textDir = new File(getDataFolder(),textDirectory);
        }
        try {
            if (!textDir.exists()) {
                if (textDir.mkdirs())
                    LogManager.logger.info("Created directory for textfiles: " + textDir.getAbsolutePath());
            }
        } catch (SecurityException ex) {
            LogManager.logger.warning("Unable to access/create textfile directory: " + textDir.getAbsolutePath());
        }

        LogManager.setRuleLogLevel(getConfig().getString("loglevel", "info"));
        LogManager.setDebugMode(getConfig().getString("debug"));

        decolor = getConfig().getBoolean("decolor");

        // Other modules will pull their data directly from the configuration. (Eg: PointManager)

    }

    /**
     * <p>Getter for the field <code>textDir</code>.</p>
     *
     * @return a {@link java.io.File} object.
     */
    public File getTextDir() {
        return textDir;
    }

    private void setupEconomy() {

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                LogManager.logger.info("Vault found. Enabling actions requiring Vault");
                return;
            }
        }
        LogManager.logger.info("Vault dependency not found.  Disabling actions requiring Vault");

    }

    /**
     * <p>getBufferedReader.</p>
     *
     * @param filename a {@link java.lang.String} object.
     * @return a {@link java.io.BufferedReader} object.
     * @throws java.io.FileNotFoundException if any.
     */
    public BufferedReader getBufferedReader(String filename) throws FileNotFoundException {

        if (textDir == null) throw new FileNotFoundException("Could not open Textfile Directory.");

        File textfile = FileUtil.getFile(getTextDir(), filename, false);
        if (textfile == null) throw new FileNotFoundException("Unable to open file: " + filename);

        FileInputStream fs  = new FileInputStream(textfile);
        return new BufferedReader(new InputStreamReader(fs));
    }

    //TODO: Handle this better
    /**
     * <p>addKilledPlayer.</p>
     *
     * @param p a {@link org.bukkit.entity.Player} object.
     * @param message a {@link java.lang.String} object.
     */
    public static void addKilledPlayer(Player p, String message) {
        killedPlayers.put(p, message);
    }



}


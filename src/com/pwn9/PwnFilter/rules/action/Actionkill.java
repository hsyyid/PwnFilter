/*
 * PwnFilter -- Regex-based User Filter Plugin for Bukkit-based Minecraft servers.
 * Copyright (c) 2013 Pwn9.com. Tremor77 <admin@pwn9.com> & Sage905 <patrick@toal.ca>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 */

package com.pwn9.PwnFilter.rules.action;

import com.pwn9.PwnFilter.FilterState;
import com.pwn9.PwnFilter.PwnFilter;
import com.pwn9.PwnFilter.util.DefaultMessages;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Kill a player with a customized Death Message
 *
 * @author ptoal
 * @version $Id: $Id
 */
@SuppressWarnings("UnusedDeclaration")
public class Actionkill implements Action {
    // Message to apply to this kick action
    String messageString;

    /** {@inheritDoc} */
    public void init(String s)
    {
        messageString = DefaultMessages.prepareMessage(s, "burnmsg");
    }

    /** {@inheritDoc} */
    public boolean execute(final FilterState state ) {
        if ( state.getPlayer() == null ) return false;

        PwnFilter.addKilledPlayer(state.getPlayer(), state.playerName + " " + messageString);
        state.addLogMessage("Killed by Filter: " + state.playerName + " " + messageString);

       BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                state.getPlayer().setHealth(0);
            }
        };
        task.runTask(state.plugin);
        return true;
    }
}

package com.planetcrushers.bukkit.NFChunk;

import java.lang.Math;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import java.text.DecimalFormat;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;

public class NFChunk extends JavaPlugin
{
    int chunkLoaderId = 179;

    Logger log = Logger.getLogger("Minecraft");

    public void onEnable()
    {
        log.info("NFChunk enabled!");
    }

    public void onDisable()
    {
        log.info("NFChunk disabled!");
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {
        if(cmd.getName().equalsIgnoreCase("chunk"))
        {
            if(args.length > 0)
            {
                if("check".equals(args[0]))
                {
                    checkChunk(sender);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void checkChunk(CommandSender sender)
    {
        if(sender instanceof Player)
            checkChunkPlayer((Player)(sender));
    }

    private void checkChunkPlayer(Player player)
    {
        Location loc = player.getLocation();
        Chunk chunk = loc.getChunk();
        boolean found = false;

        for(int x = 0; x < 16; x++)
        {
            for(int y = 0; y < 128; y++)
            {
                for(int z = 0; z < 16; z++)
                {
                    Block b = chunk.getBlock(x, y, z);
                    int bid = b.getTypeId();

                    if(bid == chunkLoaderId)
                    {
                        String msg = "[NFChunk] Chunk loader block found at " + Integer.toString(x + 16*chunk.getX()) + ", " +
                            Integer.toString(y) + ", " + Integer.toString(z + 16*chunk.getZ());
                        player.sendMessage(msg);
                        found = true;
                        break;
                    }
                }
            }
        }

        if(!found)
        {
            player.sendMessage("[NFChunk] No chunk loader block found in this chunk.");
        }
    }
}


package com.planetcrushers.bukkit.NFItemWatch;

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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;

public class NFItemWatch extends JavaPlugin
{
    // The number of loose item entities in the world before we start to complain.
    private int WarnThreshold = 1024;

    // The number of loose item entities in the world before we automatically purge them.
    // Set to 0 or below to disable automatic purging.
    private int PurgeThreshold = 0;

//    final private int WarnThreshold = 20;
    
    // The distance away entities will be considered in the same cluster.
    final private double ClusterThreshold = 5.1;

    // The number of clusters to print to the log.
    final private int LoggedClusters = 5;

    // How often, in ticks, to check.
    private int CheckTime = 6000;

    // True if invoked by a command, rather than as a timed event.
    private boolean invoked = false;

    // The destination to which messages will be written.
    private NFMsgRouter msgRouter = null;

    Logger log = Logger.getLogger("Minecraft");

    public void onEnable()
    {
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                invoked = false;
                msgRouter = new NFMsgRouterBroadcast(Bukkit.getServer(), log);
                checkItems(Bukkit.getServer());
            }
        }, 1200L, CheckTime);

        log.info("NFItemWatch enabled!");
    }

    public void onDisable()
    {
        log.info("NFItemWatch disabled!");
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {
        if(cmd.getName().equalsIgnoreCase("nfitem"))
        {
            invoked = true;
            msgRouter = new NFMsgRouterSender(sender);
            checkItems(Bukkit.getServer());
            return true;
        }
        return false;
    }

    public void checkItems(Server srv)
    {
        List<World> world_list = srv.getWorlds();
        for(World w : world_list)
        {
            msgRouter.info("[NFItemWatch] Checking world " + w.getName() );
            checkWorld(srv, w);
        }
    }

    // Count the number of item entities in a world and decide if a
    // warning is necessary.
    public void checkWorld(Server srv, World w)
    {
        int item_count = 0;
        List<Entity> ent_list = w.getEntities();
        for(Entity e : ent_list)
        {
            if(e instanceof Item)
                item_count++;
        }
        msgRouter.info("[NFItemWatch] World '" + w.getName() + "' has " + Integer.toString(ent_list.size()) + " entities (" + Integer.toString(item_count) + " items).");
        if(item_count > WarnThreshold)
        {
            String msg = "[NFItemWatch] Too many item entities (" + Integer.toString(item_count) + ") in world '" + w.getName() + "'!";
            msgRouter.send(msg);
            checkPlayers(srv, w);
            checkClusters(srv, w, ent_list);
        }
        else if(invoked)
        {
            // If manually invoked, perform the player and cluster checks anyway
            checkPlayers(srv, w);
            checkClusters(srv, w, ent_list);
        }

        if((PurgeThreshold > 0) && (item_count > PurgeThreshold))
        {
            srv.broadcastMessage("[NFItemWatch] Automatically purging item entities.");
            purgeItems(ent_list);
        }
    }

    public void purgeItems(List<Entity> ent_list)
    {
        for(Entity e : ent_list)
        {
            if(e instanceof Item)
            {
                e.remove();
            }
        }
    }

    // Warn all players how many item entities are near them.
    public void checkPlayers(Server srv, World w)
    {
        List<Player> player_list = w.getPlayers();
        for(Player p : player_list)
        {
            int item_count = 0;

            List<Entity> ent_list = p.getNearbyEntities(128.0, 128.0, 128.0);
            for(Entity e : ent_list)
            {
                if(e instanceof Item)
                    item_count++;
            }
            p.sendMessage("[NFItemWatch] Item entities near you: " + Integer.toString(item_count));
            log.info("[NFItemWatch] Item entities near " + p.getName() + ": " + Integer.toString(item_count));
            msgRouter.send("[NFItemWatch] Item entities near " + p.getName() + ": " + Integer.toString(item_count));
        }
    }

    class Cluster
    {
        public Location loc;
        public int count;
    }

    class ClusterComparator implements Comparator<Cluster>
    {
        public int compare(Cluster c1, Cluster c2)
        {
            if(c1.count > c2.count)
                return -1;
            else if(c1.count < c2.count)
                return 1;

            return 0;
        }
    }

    // Organize the item entities in a world into 'clusters' of ones that
    // are close together, and report where the biggest clusters are.
    public void checkClusters(Server srv, World w, List<Entity> ent_list)
    {
        List<Cluster> clusters = new ArrayList<Cluster>();

        for(Entity e : ent_list)
        {
            if(!(e instanceof Item))
                continue;

            boolean found_cluster = false;
            for(Cluster c : clusters)
            {
                if(isNearCluster(e, c))
                {
                    c.count += 1;
                    found_cluster = true;
                    break;
                }
            }

            if(!found_cluster)
            {
                Cluster new_c = new Cluster();
                new_c.loc = e.getLocation();
                new_c.count = 1;
                clusters.add(new_c);
            }
        }

        ClusterComparator sorter = new ClusterComparator();
        Collections.sort(clusters, sorter);

        int i = 0;
        DecimalFormat df = new DecimalFormat("#.#");
        msgRouter.send("[NFItemWatch] Largest clusters:");
        for(Cluster c : clusters)
        {
            i++;
            String msg = "[NFItemWatch]    Cluster " + Integer.toString(i) + ": ";
            msg += df.format(c.loc.getX()) + ",";
            msg += df.format(c.loc.getY()) + ",";
            msg += df.format(c.loc.getZ()) + ", Count=";
            msg += Integer.toString(c.count);

            msgRouter.send(msg);

            if(i == LoggedClusters)
                break;
        }
    }

    // Decide if an item entity is close enough to a cluster to be
    // counted as part of it.
    public boolean isNearCluster(Entity e, Cluster c)
    {
        Location loc1 = e.getLocation();
        Location loc2 = c.loc;

        double diffX = loc1.getX() - loc2.getX();
        double diffY = loc1.getY() - loc2.getY();
        double diffZ = loc1.getZ() - loc2.getZ();

        if(Math.sqrt(diffX*diffX + diffY*diffY + diffZ*diffZ) < ClusterThreshold)
            return true;

        return false;
    }

    // TODO: Split these out into separate class files
    public interface NFMsgRouter
    {
        public void send(String msg);
        public void info(String msg);
    }

    // A message router that broadcasts regular messages to the whole
    // server and sends info messages to the server log.  Used when the
    // check is done as a timed event.
    private class NFMsgRouterBroadcast implements NFMsgRouter
    {
        private Server srv = null;
        private Logger log = null;

        public NFMsgRouterBroadcast(Server srv, Logger log)
        {
            this.srv = srv;
            this.log = log;
        }

        public void send(String msg)
        {
            srv.broadcastMessage(msg);
        }
        public void info(String msg)
        {
            log.info(msg);
        }
    }

    // A message router that sends all messages to the entity that
    // requested the check (i.e., a player or the console).
    private class NFMsgRouterSender implements NFMsgRouter
    {
        private CommandSender cmdSender = null;

        public NFMsgRouterSender(CommandSender sender)
        {
            cmdSender = sender;
        }

        public void send(String msg)
        {
            cmdSender.sendMessage(msg);
        }
        public void info(String msg)
        {
            cmdSender.sendMessage(msg);
        }
    }
}


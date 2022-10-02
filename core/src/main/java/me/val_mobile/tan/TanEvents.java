/*
    Copyright (C) 2022  Val_Mobile

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.val_mobile.tan;

import me.val_mobile.data.ModuleEvents;
import me.val_mobile.data.RSVPlayer;
import me.val_mobile.data.toughasnails.DataModule;
import me.val_mobile.realisticsurvival.RealisticSurvivalPlugin;
import me.val_mobile.utils.DisplayTask;
import me.val_mobile.utils.RSVItem;
import me.val_mobile.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static me.val_mobile.tan.ThirstCalculateTask.DEFAULT_SATURATION;
import static me.val_mobile.tan.ThirstCalculateTask.MAXIMUM_THIRST;

public class TanEvents extends ModuleEvents implements Listener {

    private final RealisticSurvivalPlugin plugin;
    private final FileConfiguration config;
    private final boolean tempEnabled;
    private final TanModule module;
    private final boolean thirstEnabled;

    public TanEvents(TanModule module, RealisticSurvivalPlugin plugin) {
        super(module, plugin);
        this.module = module;
        this.plugin = plugin;
        this.config = module.getUserConfig().getConfig();
        this.tempEnabled = config.getBoolean("Temperature.Enabled");
        this.thirstEnabled = config.getBoolean("Thirst.Enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (shouldEventBeRan(player)) {
            if (tempEnabled || thirstEnabled) {
                RSVPlayer rsvplayer = RSVPlayer.getPlayers().get(player.getUniqueId());
                if (tempEnabled) {
                    if (!TemperatureCalculateTask.hasTask(player.getUniqueId())) {
                        new TemperatureCalculateTask(plugin, rsvplayer).start();
                    }
                }
                if (thirstEnabled) {
                    if (!ThirstCalculateTask.hasTask(player.getUniqueId())) {
                        new ThirstCalculateTask(plugin, rsvplayer).start();
                    }
                }
                if (!DisplayTask.hasTask(player.getUniqueId())) {
                    new DisplayTask(plugin, rsvplayer).start();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerVelocity(PlayerVelocityEvent event)
    {
        Player player = event.getPlayer(); // get the player

        if (!event.isCancelled()) {
            if (shouldEventBeRan(player)) {
                Vector velocity = player.getVelocity(); // get the player's current velocity

                // check if the player is moving up
                if (velocity.getY() > 0) {
                    double jumpVelocity = 0.42; // default jump velocity

                    // if the player has the jump boost effect
                    if (player.hasPotionEffect(PotionEffectType.JUMP)) {
                        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP);  // get the jump boost effect

                        // add the jump boost to the velocity to factor in the increased velocity
                        jumpVelocity += ((double) jumpBoost.getAmplifier() + 1) * 0.1;
                    }

                    Location loc = player.getLocation(); // get the player's location
                    Block block = loc.getBlock(); // get the block at that location
                    Material blockMaterial = block.getType(); // get the material of the block

                    // check if the player is not climbing
                    switch (blockMaterial) {
                        case LADDER, VINE, TWISTING_VINES, TWISTING_VINES_PLANT, WEEPING_VINES, WEEPING_VINES_PLANT -> {
                        }
                        default -> {
                            // check if the player's jump velocity is equal to the player's Y velocity
                            if (Utils.doublesEquals(velocity.getY(), jumpVelocity)) {
                                // check if the player is not flying or swimming
                                if (!(player.getLocation().getBlock().isLiquid() || player.isRiptiding() || player.isFlying())) {
                                    ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(player.getUniqueId());

                                    if (task != null) {
                                        task.setJumping(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (shouldEventBeRan(player)) {
            UUID id = player.getUniqueId();
            if (thirstEnabled) {
                ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(id);

                if (task != null) {
                    task.setThirstLvl(config.getDouble("Thirst.DefaultThirst"));
                    task.setSaturationLvl(config.getDouble("Thirst.DefaultSaturation"));
                }
            }

            if (tempEnabled) {
                TemperatureCalculateTask task = TemperatureCalculateTask.getTasks().get(id);
                if (task != null) {
                    task.setTemp(config.getDouble("Temperature.DefaultTemperature"));
                }
            }

            if (HypothermiaTask.hasTask(id)) {
                HypothermiaTask.getTasks().get(id).cancel();
                HypothermiaTask.getTasks().remove(id);
            }

            if (HyperthermiaTask.hasTask(id)) {
                HyperthermiaTask.getTasks().get(id).cancel();
                HyperthermiaTask.getTasks().remove(id);
            }

            if (DehydrationTask.hasTask(id)) {
                DehydrationTask.getTasks().get(id).cancel();
                DehydrationTask.getTasks().remove(id);
            }

            if (ParasiteTask.hasTask(id)) {
                ParasiteTask.getTasks().get(id).cancel();
                ParasiteTask.getTasks().remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        if (!event.isCancelled()) {
            if (shouldEventBeRan(player)) {
                GameMode mode = event.getNewGameMode();
                if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                    if (tempEnabled || thirstEnabled) {
                        RSVPlayer rsvplayer = RSVPlayer.getPlayers().get(player.getUniqueId());
                        if (tempEnabled) {
                            if (!TemperatureCalculateTask.hasTask(player.getUniqueId())) {
                                new TemperatureCalculateTask(plugin, rsvplayer).start();
                            }
                        }
                        if (thirstEnabled) {
                            if (!ThirstCalculateTask.hasTask(player.getUniqueId())) {
                                new ThirstCalculateTask(plugin, rsvplayer).start();
                            }
                        }
                        if (!DisplayTask.hasTask(player.getUniqueId())) {
                            new DisplayTask(plugin, rsvplayer).start();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrink(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (shouldEventBeRan(player)) {
            GameMode mode = player.getGameMode();
            Action action = event.getAction();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                boolean parasites = false;

                switch (action) {
                    case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> {
                        if (config.getBoolean("Thirst.SaturationRestoration.Drinking.Enabled")) {
                            if (player.isSneaking()) {
                                Location playerLoc = player.getLocation();
                                Block block;
                                Location eye = player.getEyeLocation().add(player.getLocation().getDirection());
                                double maxDistance = config.getDouble("Thirst.SaturationRestoration.Drinking.MaxDistance");

                                RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), eye.getDirection(), maxDistance, FluidCollisionMode.ALWAYS, true);

                                if (result != null) {
                                    block = result.getHitBlock();
                                    if (block != null) {
                                        if (block.getType() == Material.WATER && Utils.isSourceLiquid(block)) {
                                            if (playerLoc.distance(block.getLocation()) < maxDistance) {
                                                ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(player.getUniqueId());

                                                if (task != null) {
                                                    double thirstPoints = config.getDouble("Thirst.SaturationRestoration.Drinking.ThirstPoints");
                                                    double saturationPoints = config.getDouble("Thirst.SaturationRestoration.Drinking.SaturationPoints");

                                                    task.setThirstLvl(Math.min(task.getThirstLvl() + thirstPoints, MAXIMUM_THIRST));
                                                    task.setSaturationLvl(Math.min(task.getThirstLvl() + saturationPoints, task.getThirstLvl()));

                                                    if (config.getBoolean("Thirst.SaturationRestoration.Drinking.Sound.Enabled")) {
                                                        String soundName = config.getString("Thirst.SaturationRestoration.Drinking.Sound.Sound");
                                                        float volume = (float) config.getDouble("Thirst.SaturationRestoration.Drinking.Sound.Volume");
                                                        float pitch = (float) config.getDouble("Thirst.SaturationRestoration.Drinking.Sound.Pitch");

                                                        Utils.playSound(playerLoc, soundName, volume, pitch);
                                                    }


                                                    // Parasites code

                                                    Location waterSourceLoc = block.getLocation();
                                                    World world = waterSourceLoc.getWorld();
                                                    Block air;

                                                    int airAmount = 0;
                                                    int caveAirAmount = 0;

                                                    for (int x = -3; x < 2; x++) {
                                                        for (int y = -3; y < 2; y++) {
                                                            for (int z = -3; z < 2; z++) {
                                                                air = world.getBlockAt(x + (int) waterSourceLoc.getX(), y + (int) waterSourceLoc.getY(), z + (int) waterSourceLoc.getZ());
                                                                if (air.getType() == Material.AIR) {
                                                                    airAmount++;
                                                                }
                                                                else if (air.getType() == Material.CAVE_AIR) {
                                                                    caveAirAmount++;
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (caveAirAmount > airAmount) {
                                                        // cave water
                                                        if (config.getBoolean("Thirst.Parasites.CaveWater.Enabled")) {
                                                            if (Math.random() <= config.getDouble("Thirst.Parasites.CaveWater.Chance")) {
                                                                parasites = true;
                                                            }
                                                        }
                                                    }
                                                    else {
                                                        Biome biome = block.getBiome();
                                                        switch (biome) {
                                                            case RIVER, FROZEN_RIVER -> {
                                                                // river water
                                                                if (config.getBoolean("Thirst.Parasites.RiverWater.Enabled")) {
                                                                    if (Math.random() <= config.getDouble("Thirst.Parasites.RiverWater.Chance")) {
                                                                        parasites = true;
                                                                    }
                                                                }
                                                            }
                                                            case OCEAN, COLD_OCEAN, DEEP_LUKEWARM_OCEAN, LUKEWARM_OCEAN, DEEP_OCEAN, DEEP_COLD_OCEAN, DEEP_FROZEN_OCEAN, FROZEN_OCEAN, DEEP_WARM_OCEAN, WARM_OCEAN -> {
                                                                // ocean water
                                                                if (config.getBoolean("Thirst.Parasites.SeaWater.Enabled")) {
                                                                    if (Math.random() <= config.getDouble("Thirst.Parasites.SeaWater.Chance")) {
                                                                        parasites = true;
                                                                    }
                                                                }
                                                            }
                                                            default -> {
                                                                // regular water
                                                                if (config.getBoolean("Thirst.Parasites.RegularWater.Enabled")) {
                                                                    if (Math.random() <= config.getDouble("Thirst.Parasites.RegularWater.Chance")) {
                                                                        parasites = true;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    case LEFT_CLICK_AIR -> {
                        if (config.getBoolean("Thirst.SaturationRestoration.Raining.Enabled")) {
                            if (player.getWorld().hasStorm()) {
                                if (Utils.isExposedToSky(player)) {
                                    Location loc = player.getLocation();
                                    if (Math.abs(loc.getPitch() + 90F) < 0.01) {
                                        ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(player.getUniqueId());

                                        if (task != null) {
                                            double thirstPoints = config.getDouble("Thirst.SaturationRestoration.Raining.ThirstPoints");
                                            double saturationPoints = config.getDouble("Thirst.SaturationRestoration.Raining.SaturationPoints");

                                            task.setThirstLvl(Math.min(task.getThirstLvl() + thirstPoints, MAXIMUM_THIRST));
                                            task.setSaturationLvl(Math.min(task.getThirstLvl() + saturationPoints, task.getThirstLvl()));

                                            if (config.getBoolean("Thirst.SaturationRestoration.Raining.Sound.Enabled")) {
                                                String soundName = config.getString("Thirst.SaturationRestoration.Raining.Sound.Sound");
                                                float volume = (float) config.getDouble("Thirst.SaturationRestoration.Raining.Sound.Volume");
                                                float pitch = (float) config.getDouble("Thirst.SaturationRestoration.Raining.Sound.Pitch");
                                                Utils.playSound(loc, soundName, volume, pitch);
                                            }

                                            // regular water
                                            if (config.getBoolean("Thirst.Parasites.Rain.Enabled")) {
                                                if (Math.random() <= config.getDouble("Thirst.Parasites.Rain.Chance")) {
                                                    parasites = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (parasites) {
                    if (!ParasiteTask.hasTask(player.getUniqueId())) {
                        new ParasiteTask(plugin, RSVPlayer.getPlayers().get(player.getUniqueId())).startRunnable();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (shouldEventBeRan(player)) {
            GameMode mode = player.getGameMode();

            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                ItemStack item = event.getItem();

                String name = RSVItem.isRSVItem(item) ? RSVItem.getNameFromItem(item) : item.getType().toString();
                Set<String> keys = config.getConfigurationSection("Thirst.SaturationRestoration.Foods").getKeys(false);

                if (name != null) {
                    if (keys.contains(name)) {
                        ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(player.getUniqueId());

                        if (task != null) {
                            double thirstPoints;
                            double saturationPoints;

                            if (name.equals("canteen_filled")) {
                                String canteenDrink = RealisticSurvivalPlugin.getUtil().getNbtTag(item, "rsvdrink", PersistentDataType.STRING);
                                if ("Unpurified Water".equals(canteenDrink)) {
                                    thirstPoints = config.getDouble("Thirst.SaturationRestoration.Foods.POTION.ThirstPoints");
                                    saturationPoints = config.getDouble("Thirst.SaturationRestoration.Foods.POTION.SaturationPoints");

                                    int durability = RealisticSurvivalPlugin.getUtil().getNbtTag(item, "rsvdurability", PersistentDataType.INTEGER);

                                    if (durability > 0) {
                                        Utils.changeDurability(item, -1, false);
                                    }
                                    else {
                                        RSVItem.addNbtTag(item, "rsvitem", "canteen_empty", PersistentDataType.STRING);
                                        item.setType(Material.GLASS_BOTTLE);
                                        event.setCancelled(true);
                                    }

                                    // unpurified water
                                    if (config.getBoolean("Thirst.Parasites.UnpurifiedWaterBottle.Enabled")) {
                                        if (Math.random() <= config.getDouble("Thirst.Parasites.UnpurifiedWaterBottle.Chance")) {
                                            if (!ParasiteTask.hasTask(player.getUniqueId())) {
                                                new ParasiteTask(plugin, RSVPlayer.getPlayers().get(player.getUniqueId())).startRunnable();
                                            }
                                        }
                                    }
                                }
                                else {
                                    canteenDrink = canteenDrink.toLowerCase().replace(' ', '_');

                                    thirstPoints = config.getDouble("Thirst.SaturationRestoration.Foods." + canteenDrink + ".ThirstPoints");
                                    saturationPoints = config.getDouble("Thirst.SaturationRestoration.Foods." + canteenDrink + ".SaturationPoints");
                                }
                            }
                            else {
                                thirstPoints = config.getDouble("Thirst.SaturationRestoration.Foods." + name + ".ThirstPoints");
                                saturationPoints = config.getDouble("Thirst.SaturationRestoration.Foods." + name + ".SaturationPoints");
                            }

                            task.setThirstLvl(Math.min(task.getThirstLvl() + thirstPoints, MAXIMUM_THIRST));
                            task.setSaturationLvl(Math.min(task.getThirstLvl() + saturationPoints, task.getThirstLvl()));

                            if (name.equals(item.getType().toString()) && item.getType() == Material.POTION) {
                                if (((PotionMeta) item.getItemMeta()).getBasePotionData().getType() == PotionType.WATER) {
                                    // unpurified water
                                    if (config.getBoolean("Thirst.Parasites.UnpurifiedWaterBottle.Enabled")) {
                                        if (Math.random() <= config.getDouble("Thirst.Parasites.UnpurifiedWaterBottle.Chance")) {
                                            if (!ParasiteTask.hasTask(player.getUniqueId())) {
                                                new ParasiteTask(plugin, RSVPlayer.getPlayers().get(player.getUniqueId())).startRunnable();
                                            }
                                        }
                                    }
                                }
                            }

                            if (name.equals("juice_chorus_fruit")) {
                                if (config.getBoolean("Items.juice_chorus_fruit.Teleport.Enabled")) {
                                    Location loc = player.getLocation();
                                    final double RADIUS = config.getDouble("Items.juice_chorus_fruit.Teleport.MaxRadius");
                                    double x = loc.getX() + Math.random() * RADIUS;
                                    double z = loc.getZ() + Math.random() * RADIUS;

                                    Location newLoc = new Location(loc.getWorld(), x, loc.getWorld().getHighestBlockYAt((int) Math.round(x), (int) Math.round(z)), z, loc.getYaw(), loc.getPitch());

                                    player.teleport(newLoc);

                                    if (config.getBoolean("Items.juice_chorus_fruit.Teleport.Sound.Enabled")) {
                                        String soundName = config.getString("Items.juice_chorus_fruit.Teleport.Sound.Sound");
                                        float volume = (float) config.getDouble("Items.juice_chorus_fruit.Teleport.Sound.Volume");
                                        float pitch = (float) config.getDouble("Items.juice_chorus_fruit.Teleport.Sound.Pitch");
                                        Utils.playSound(loc, soundName, volume, pitch);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();

        if (!event.isCancelled()) {
            if (shouldEventBeRan(e)) {
                if (e instanceof Player) {
                    ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(e.getUniqueId());
                    GameMode mode = ((Player) e).getGameMode();

                    if (mode.equals(GameMode.SURVIVAL) || mode.equals(GameMode.ADVENTURE)) {

                        if (task != null) {
                            if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
                                if (config.getBoolean("Thirst.SaturationRestoration.Drowning.Enabled")) {
                                    double thirstPoints = config.getDouble("Thirst.SaturationRestoration.Drowning.ThirstPoints");
                                    double saturationPoints = config.getDouble("Thirst.SaturationRestoration.Drowning.SaturationPoints");

                                    task.setThirstLvl(Math.min(task.getThirstLvl() + thirstPoints, MAXIMUM_THIRST));
                                    task.setSaturationLvl(Math.min(task.getThirstLvl() + saturationPoints, task.getThirstLvl()));
                                }
                            }
                            double parasiteExhaustionMultiplier = config.getBoolean("Thirst.Parasites.MultiplyExhaustionRates.Enabled") ? config.getDouble("Thirst.Parasites.MultiplyExhaustionRates.Value") : 1D;
                            task.setExhaustionLvl(task.getExhaustionLvl() + (config.getDouble("Thirst.ExhaustionLevelIncrease.TakingDamage") * parasiteExhaustionMultiplier));
                        }
                    }
                }
            }
        }
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onRegenerate(EntityRegainHealthEvent event) {
        if (!event.isCancelled()) {
            Entity e = event.getEntity();
            if (e instanceof Player) {
                if (shouldEventBeRan(e)) {
                    ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(e.getUniqueId());

                    if (task != null) {
                        double parasiteExhaustionMultiplier = config.getBoolean("Thirst.Parasites.MultiplyExhaustionRates.Enabled") ? config.getDouble("Thirst.Parasites.MultiplyExhaustionRates.Value") : 1D;
                        task.setExhaustionLvl(task.getExhaustionLvl() + (config.getDouble("Thirst.ExhaustionLevelIncrease.RegeneratingHealth") * parasiteExhaustionMultiplier));
                    }
                }
            }
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!event.isCancelled()) {
            if (shouldEventBeRan(player)) {
                ItemStack itemMainHand = player.getInventory().getItemInMainHand();
                Block block = event.getBlock();
                Material material = block.getType();
                Location loc = block.getLocation().add(0D, 0.15D, 0D);

                Set<String> iceBlocks = config.getConfigurationSection("Items.ice_cube.BlockDrops").getKeys(false);
                Set<String> magmaBlocks = config.getConfigurationSection("Items.magma_shard.BlockDrops").getKeys(false);

                if (iceBlocks.contains(material.toString())) {
                    if (Utils.isHoldingPickaxe(player)) {
                        if (!itemMainHand.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
                            Utils.harvestFortune(config.getConfigurationSection("Items.ice_cube.BlockDrops." + material), RSVItem.getItem("ice_cube"), itemMainHand, loc);
                        }
                    }
                }
                if (magmaBlocks.contains(material.toString())) {
                    if (Utils.isHoldingPickaxe(player)) {
                        if (!itemMainHand.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
                            Utils.harvestFortune(config.getConfigurationSection("Items.magma_shard.BlockDrops." + material), RSVItem.getItem("ice_cube"), itemMainHand, loc);
                        }
                    }
                }
                ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(player.getUniqueId());

                if (task != null) {
                    double parasiteExhaustionMultiplier = config.getBoolean("Thirst.Parasites.MultiplyExhaustionRates.Enabled") ? config.getDouble("Thirst.Parasites.MultiplyExhaustionRates.Value") : 1D;
                    task.setExhaustionLvl(task.getExhaustionLvl() + (config.getDouble("Thirst.ExhaustionLevelIncrease.BreakingBlock") * parasiteExhaustionMultiplier));
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        if (shouldEventBeRan(player)) {
            String oldWorld = event.getFrom().toString();

            if (!module.getAllowedWorlds().contains(oldWorld)) {
                if (tempEnabled || thirstEnabled) {
                    RSVPlayer rsvplayer = RSVPlayer.getPlayers().get(player.getUniqueId());
                    if (tempEnabled) {
                        if (!TemperatureCalculateTask.hasTask(player.getUniqueId())) {
                            new TemperatureCalculateTask(plugin, rsvplayer).start();
                        }
                    }
                    if (thirstEnabled) {
                        if (!ThirstCalculateTask.hasTask(player.getUniqueId())) {
                            new ThirstCalculateTask(plugin, rsvplayer).start();
                        }
                    }
                    if (!DisplayTask.hasTask(player.getUniqueId())) {
                        new DisplayTask(plugin, rsvplayer).start();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSwap(PlayerItemHeldEvent event) {
        if (!event.isCancelled()) {
            Player p = event.getPlayer();
            if (shouldEventBeRan(p)) {
                ItemStack item = p.getInventory().getItem(event.getNewSlot());

                if (RSVItem.isRSVItem(item)) {
                    if (RSVItem.getNameFromItem(item).equals("thermometer")) {
                        if (!ThermometerTask.hasTask(p.getUniqueId())) {
                            new ThermometerTask(plugin, RSVPlayer.getPlayers().get(p.getUniqueId())).start();
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!event.isCancelled()) {
            Entity e = event.getEntity();
            if (e instanceof Player) {
                if (shouldEventBeRan(e)) {
                    Player p = (Player) e;
                    ItemStack item = event.getItem().getItemStack();

                    if (RSVItem.isRSVItem(item)) {
                        if (RSVItem.getNameFromItem(item).equals("thermometer")) {
                            if (!ThermometerTask.hasTask(p.getUniqueId())) {
                                new ThermometerTask(plugin, RSVPlayer.getPlayers().get(p.getUniqueId())).start();
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPotionFill(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (shouldEventBeRan(player)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack item = event.getItem();

                Block b = event.getClickedBlock();
                Material mat = b.getType();

                if (RSVItem.isRSVItem(item)) {
                    if (RSVItem.getNameFromItem(item).contains("canteen_empty")) {
                        if (mat == Material.WATER) {
                            RSVItem.addNbtTag(item, "rsvitem", "canteen_filled", PersistentDataType.STRING);
                            RSVItem.addNbtTag(item, "rsvdrink", "Unpurified Water", PersistentDataType.STRING);
                            Utils.changeDurability(item, 1, false);
                        }
                        else if (mat == Material.CAULDRON) {
                            BlockData data = b.getBlockData();
                           if (((Levelled) data).getLevel() > 0) {
                               RSVItem.addNbtTag(item, "rsvitem", "canteen_filled", PersistentDataType.STRING);
                               RSVItem.addNbtTag(item, "rsvdrink", "Unpurified Water", PersistentDataType.STRING);

                               Utils.changeDurability(item, 1, false);
                               ((Levelled) data).setLevel(((Levelled) data).getLevel() - 1);
                               b.setBlockData(data);
                           }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCauldronFill(CauldronLevelChangeEvent event) {
        Entity e = event.getEntity();
        if (shouldEventBeRan(e)) {
            if (e instanceof Player) {
                Player player = (Player) e;
                ItemStack item = player.getInventory().getItemInMainHand();

                if (RSVItem.isRSVItem(item)) {
                    if (event.getReason() == CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY) {
                        if (RSVItem.getNameFromItem(item).contains("juice_")) {
                            event.setCancelled(true);
                        }
                        if (RSVItem.getNameFromItem(item).equals("canteen_filled")) {
                            if (RealisticSurvivalPlugin.getUtil().getNbtTag(item, "rsvdrink", PersistentDataType.STRING).equals("Unpurified Water")) {
                                int durability = RealisticSurvivalPlugin.getUtil().getNbtTag(item, "rsvdurability", PersistentDataType.INTEGER);

                                if (durability > 0) {
                                    Utils.changeDurability(item, -1, false);
                                }
                                else {
                                    RSVItem.addNbtTag(item, "rsvitem", "canteen_empty", PersistentDataType.STRING);
                                    RSVItem.addNbtTag(item, "rsvdrink", "", PersistentDataType.STRING);
                                    item.setType(Material.GLASS_BOTTLE);
                                    event.setCancelled(true);
                                }
                            }
                            else {
                                event.setCancelled(true);
                            }
                        }
                    }
                    if (event.getReason() == CauldronLevelChangeEvent.ChangeReason.BOTTLE_FILL) {
                        if (RSVItem.getNameFromItem(item).equals("canteen_empty")) {
                            RSVItem.addNbtTag(item, "rsvitem", "canteen_filled", PersistentDataType.STRING);
                            RSVItem.addNbtTag(item, "rsvdrink", "Unpurified Water", PersistentDataType.STRING);
                            item.setType(Material.POTION);
                            Utils.changeDurability(item, 1, false);
                        }
                    }
                }
            }
        }
    }
}

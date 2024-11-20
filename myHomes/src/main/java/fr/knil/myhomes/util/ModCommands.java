package fr.knil.myhomes.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;


public class ModCommands {
	private static final File DATA_DIRECTORY = new File("myHome_files"); // Répertoire des fichiers
    private static final File HOME_FILE = new File(DATA_DIRECTORY, "home_positions.json"); // Fichier des homes
    private static final File SPAWN_FILE = new File(DATA_DIRECTORY, "spawn_locations.json"); // Fichier des spawns
    private static final File WARP_FILE = new File(DATA_DIRECTORY, "warp_locations.json"); // Fichier des spawns
    
    private static final Map<String, Map<String, BlockPos>> savedHomes = new HashMap<>();
    private static final Map<String, BlockPos> warpLocations = new HashMap<>();
    private static final MutablePosition spawn = new MutablePosition();    
    private static final Gson GSON = new Gson();
    
    private static final Map<UUID, TeleportRequest> teleportRequests = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        // Créer le répertoire si nécessaire
        if (!DATA_DIRECTORY.exists()) {
            if (DATA_DIRECTORY.mkdirs()) {
                System.out.println("Created directory: " + DATA_DIRECTORY.getAbsolutePath());
            } else {
                System.err.println("Failed to create directory: " + DATA_DIRECTORY.getAbsolutePath());
            }
        }
    }

    public static void registerCommands() {
        loadHomes();
        loadSpawn();
        loadWarps();        

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {            
        	
        	// Commande /sethome <nom>
            dispatcher.register(literal("sethome")
                .then(argument("name", string())
                    .executes(context -> setHome(context, getString(context, "name"))))
            );

            // Commande /home <nom>
            dispatcher.register(literal("home")
                .then(argument("name", string())
                    .executes(context -> teleportHome(context, getString(context, "name"))))
            );

            // Commande /homes
            dispatcher.register(literal("homes")
                .executes(ModCommands::listHomes)
            );

            // Commande /delhome <nom>
            dispatcher.register(literal("delhome")
                .then(argument("name", string())
                    .executes(context -> deleteHome(context, getString(context, "name"))))
            );
            
         // Commande /tp <joueur>
            dispatcher.register(literal("tp")
                .then(argument("player", StringArgumentType.string())
                    .executes(context -> teleportToPlayer(context, StringArgumentType.getString(context, "player"))))
            );

            // Commande /tphere <joueur>
            dispatcher.register(literal("tphere")
                .then(argument("player", StringArgumentType.string())
                    .executes(context -> teleportHere(context, StringArgumentType.getString(context, "player"))))
            );
            
            // Commande /tpa <joueur>
            dispatcher.register(literal("tpa")
            	    .then(argument("player", string())
            	        .executes(context -> sendTeleportRequest(context, getString(context, "player"))))
            	);

            // Commande /tpyes
            dispatcher.register(literal("tpyes")
            	    .executes(ModCommands::acceptTeleportRequest));

            // Commande /tpno
            dispatcher.register(literal("tpno")
            	    .executes(ModCommands::denyTeleportRequest));
           
            
            // commande /spawn
            dispatcher.register(literal("spawn")
            	    .executes(ModCommands::teleportToSpawn));
            
            // commande /setspawn            
            dispatcher.register(literal("setspawn")
            	        .executes(context -> setSpawnPoint(context))
            	);        
            
            // commande /setwarp <nom>
            dispatcher.register(literal("setwarp")
            	    .then(argument("name", string())
            	        .executes(context -> setWarpPoint(context, getString(context, "name"))))
            	);
            
            //commande /delwarp <nom>
            dispatcher.register(literal("delwarp")
            	    .then(argument("name", string())
            	        .executes(context -> deleteWarp(context, getString(context, "name"))))
            	);
                         
         // Commande /warp <nom>
            dispatcher.register(literal("warp")
                .then(argument("name", string())
                    .executes(context -> teleportToWarp(context, getString(context, "name"))))
            );
            
         // commande /warps
            dispatcher.register(literal("warps")
            	    .executes(ModCommands::listWarps));   
         
        });
    }
    
 // Classe interne pour gérer une demande de téléportation
    private static class TeleportRequest {
        final UUID requesterId;
        final ScheduledFuture<?> timeoutTask;

        public TeleportRequest(UUID requesterId, ScheduledFuture<?> timeoutTask) {
            this.requesterId = requesterId;
            this.timeoutTask = timeoutTask;
        }
    }
    
    
    private static int sendTeleportRequest(CommandContext<ServerCommandSource> context, String targetName) throws CommandSyntaxException {
        ServerPlayerEntity requester = context.getSource().getPlayer();
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (target == null) {
            requester.sendMessage(Text.literal("Player '" + targetName + "' not found."), false);
            return 0;
        }

        UUID targetId = target.getUuid();

        // Vérifie si une demande est déjà en attente
        if (teleportRequests.containsKey(targetId)) {
            requester.sendMessage(Text.literal("A teleport request is already pending for " + target.getEntityName() + "."), false);
            return 0;
        }

        // Planifie un timeout pour la demande
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            teleportRequests.remove(targetId);
            target.sendMessage(Text.literal("Teleport request from " + requester.getEntityName() + " has expired."), false);
        }, 30, TimeUnit.SECONDS);

        // Enregistre la demande
        teleportRequests.put(targetId, new TeleportRequest(requester.getUuid(), timeoutTask));

        // Envoie un message au joueur cible
        target.sendMessage(Text.literal(requester.getEntityName() + " wants to teleport to you. Use /tpyes to accept or /tpno to deny.").formatted(Formatting.YELLOW), false);
        requester.sendMessage(Text.literal("Teleport request sent to " + target.getEntityName() + "."), false);

        return 1;
    }

    
    private static int acceptTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayer();
        UUID targetId = target.getUuid();

        // Vérifie s'il y a une demande en attente
        TeleportRequest request = teleportRequests.remove(targetId);
        if (request == null) {
            target.sendMessage(Text.literal("No teleport request to accept."), false);
            return 0;
        }

        // Annule le timeout
        request.timeoutTask.cancel(false);

        // Téléporte le joueur demandeur
        ServerPlayerEntity requester = context.getSource().getServer().getPlayerManager().getPlayer(request.requesterId);
        if (requester != null) {
            requester.requestTeleport(target.getX(), target.getY(), target.getZ());
            requester.sendMessage(Text.literal("Teleport request accepted. You have been teleported to " + target.getEntityName() + "."), false);
            target.sendMessage(Text.literal(requester.getEntityName() + " has been teleported to you."), false);
        } else {
            target.sendMessage(Text.literal("Player who sent the request is no longer online."), false);
        }

        return 1;
    }
    
    private static int denyTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayer();
        UUID targetId = target.getUuid();

        // Vérifie s'il y a une demande en attente
        TeleportRequest request = teleportRequests.remove(targetId);
        if (request == null) {
            target.sendMessage(Text.literal("No teleport request to deny."), false);
            return 0;
        }

        // Annule le timeout
        request.timeoutTask.cancel(false);

        // Notifie le joueur demandeur
        ServerPlayerEntity requester = context.getSource().getServer().getPlayerManager().getPlayer(request.requesterId);
        if (requester != null) {
            requester.sendMessage(Text.literal("Teleport request to " + target.getEntityName() + " has been denied."), false);
        }

        target.sendMessage(Text.literal("You denied the teleport request from " + (requester != null ? requester.getEntityName() : "an offline player") + "."), false);

        return 1;
    }
    
    
    private static int setWarpPoint(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos currentPos = player.getBlockPos();

        warpLocations.put(name, currentPos);

        // Sauvegarder dans le fichier
        try (FileWriter writer = new FileWriter(WARP_FILE)) {
            Map<String, Map<String, Integer>> rawData = new HashMap<>();
            warpLocations.forEach((key, pos) -> {
                Map<String, Integer> coords = new HashMap<>();
                coords.put("x", pos.getX());
                coords.put("y", pos.getY());
                coords.put("z", pos.getZ());
                rawData.put(key, coords);
            });
            GSON.toJson(rawData, writer);
        } catch (IOException e) {
            System.err.println("Failed to save spawn points: " + e.getMessage());
        }

        player.sendMessage(Text.literal("Warp point '" + name + "' set!"), false);
        return 1;
    }
    
    private static int setSpawnPoint(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos currentPos = player.getBlockPos();
        
        spawn.set(currentPos.getX(), currentPos.getY(), currentPos.getZ());

        // Sauvegarder dans le fichier
        try (FileWriter writer = new FileWriter(SPAWN_FILE)) {
        	Map<String, Integer> coords = new HashMap<>();
        	coords.put("x", currentPos.getX());
            coords.put("y", currentPos.getY());
            coords.put("z", currentPos.getZ());
            GSON.toJson(coords, writer);
        } catch (IOException e) {
            System.err.println("Failed to save spawn points: " + e.getMessage());
        }

        player.sendMessage(Text.literal("Spawn point set!"), false);
        return 1;
    }
    
    
    private static void saveWarps() {
        try (FileWriter writer = new FileWriter(WARP_FILE)) {
            Map<String, Map<String, Integer>> rawData = new HashMap<>();

            // Convertir les BlockPos en données JSON
            warpLocations.forEach((name, pos) -> {
                Map<String, Integer> posData = new HashMap<>();
                posData.put("x", pos.getX());
                posData.put("y", pos.getY());
                posData.put("z", pos.getZ());
                rawData.put(name, posData);
            });

            GSON.toJson(rawData, writer);
        } catch (IOException e) {
            System.err.println("Failed to save spawn locations: " + e.getMessage());
        }
    }
    
        
    private static void loadSpawn() {
        if (SPAWN_FILE.exists()) {
            try (FileReader reader = new FileReader(SPAWN_FILE)) {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> coords = GSON.fromJson(reader, type);

                if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                    int x = coords.get("x");
                    int y = coords.get("y");
                    int z = coords.get("z");
                    spawn.set(x, y, z);
                }
                
            } catch (IOException e) {
                System.err.println("Failed to load spawn points: " + e.getMessage());
            }
        }
    }
    
    
    private static void loadWarps() {
        if (WARP_FILE.exists()) {
            try (FileReader reader = new FileReader(WARP_FILE)) {
                Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
                Map<String, Map<String, Integer>> rawData = GSON.fromJson(reader, type);

                // Convertir les données en BlockPos
                rawData.forEach((name, coords) -> {
                    if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                        int x = coords.get("x");
                        int y = coords.get("y");
                        int z = coords.get("z");
                        warpLocations.put(name, new BlockPos(x, y, z));
                    }
                });
            } catch (IOException e) {
                System.err.println("Failed to load spawn points: " + e.getMessage());
            }
        }
    }
    
    
    private static int teleportToSpawn(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos targetPos = new BlockPos(spawn.getX(),spawn.getY(),spawn.getZ());

        player.requestTeleport(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        player.sendMessage(Text.literal("Teleported to spawn."), false);
        return 1;
    }
    
    
    private static int teleportToWarp(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos targetPos = warpLocations.get(name);

        if (targetPos == null) {
            player.sendMessage(Text.literal("Spawn point '" + name + "' not found."), false);
            return 0;
        }

        player.requestTeleport(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        player.sendMessage(Text.literal("Teleported to " + name + "."), false);
        return 1;
    }
    
   
    
    @SuppressWarnings("unchecked")
	private static int deleteWarp(CommandContext<ServerCommandSource> context, String name) {
        ServerCommandSource source = context.getSource();

        // Vérifier si le spawn existe
        if (!warpLocations.containsKey(name)) {
            source.sendFeedback((Supplier<Text>) Text.literal("Spawn location '" + name + "' does not exist."), false);
            return 0;
        }

        // Supprimer le spawn de la mémoire
        warpLocations.remove(name);

        // Sauvegarder les changements dans le fichier
        saveWarps();

        source.sendFeedback((Supplier<Text>) Text.literal("Spawn location '" + name + "' has been deleted."), false);
        return 1;
    }
    
    
    private static int setHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();
        BlockPos currentPos = player.getBlockPos();

        // Récupérer ou créer la map des homes du joueur
        savedHomes.computeIfAbsent(playerId, k -> new HashMap<>());

        // Ajouter ou remplacer le home
        savedHomes.get(playerId).put(name, currentPos);

        // Sauvegarder dans le fichier
        saveHomes();

        // Retourner un message au joueur
        player.sendMessage(Text.literal("Home '" + name + "' saved at: " + currentPos.getX() + ", " + currentPos.getY() + ", " + currentPos.getZ()), false);
        return 1;
    }

    private static int teleportHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || !savedHomes.get(playerId).containsKey(name)) {
            player.sendMessage(Text.literal("Home '" + name + "' not found! Use /sethome <name> first."), false);
            return 0;
        }

        BlockPos homePos = savedHomes.get(playerId).get(name);

        // Téléporter le joueur à la position sauvegardée en utilisant requestTeleport
        player.networkHandler.requestTeleport(
            homePos.getX() + 0.5, // Ajustement pour centrer sur le bloc
            homePos.getY(),
            homePos.getZ() + 0.5,
            player.getYaw(), // Conserve l'orientation du joueur
            player.getPitch()
        );

        player.sendMessage(Text.literal("Teleported to home '" + name + "'!"), false);
        return 1;
    }


    private static int listHomes(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || savedHomes.get(playerId).isEmpty()) {
            player.sendMessage(Text.literal("You have no homes set! Use /sethome <name> to create one."), false);
            return 0;
        }

        // Afficher la liste des homes
        StringBuilder homeList = new StringBuilder("Your homes: ");
        savedHomes.get(playerId).keySet().forEach(name -> homeList.append(name).append(", "));
        player.sendMessage(Text.literal(homeList.substring(0, homeList.length() - 2)), false);
        return 1;
    }
    
    
    private static int listWarps(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        // Vérifier s'il existe des warps
        if (warpLocations.isEmpty()) {
            player.sendMessage(Text.literal("You have no warp set! Use /setwarp <name> to create one."), false);
            return 0;
        }

        // Afficher la liste des warps
        StringBuilder warpList = new StringBuilder("Your warp: ");
        warpLocations.keySet().forEach(name -> warpList.append(name).append(", "));
        player.sendMessage(Text.literal(warpList.substring(0, warpList.length() - 2)), false);
        return 1;
    }
    

    private static int deleteHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || !savedHomes.get(playerId).containsKey(name)) {
            player.sendMessage(Text.literal("Home '" + name + "' not found!"), false);
            return 0;
        }

        // Supprimer le home
        savedHomes.get(playerId).remove(name);

        // Sauvegarder dans le fichier
        saveHomes();

        player.sendMessage(Text.literal("Home '" + name + "' has been deleted."), false);
        return 1;
    }

    private static void loadHomes() {
        if (HOME_FILE.exists()) {
            try (FileReader reader = new FileReader(HOME_FILE)) {
                // Lis le JSON dans une Map de Map<String, Map<String, Integer>>
                Type type = new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {}.getType();
                Map<String, Map<String, Map<String, Integer>>> rawData = GSON.fromJson(reader, type);

                // Convertir les données brutes en BlockPos
                rawData.forEach((uuid, homes) -> {
                    Map<String, BlockPos> homeMap = new HashMap<>();
                    homes.forEach((name, posData) -> {
                        int x = posData.get("x");
                        int y = posData.get("y");
                        int z = posData.get("z");
                        homeMap.put(name, new BlockPos(x, y, z));
                    });
                    savedHomes.put(uuid, homeMap);
                });

            } catch (IOException e) {
                System.err.println("Failed to load home positions: " + e.getMessage());
            }
        }
    }

    private static void saveHomes() {
        try (FileWriter writer = new FileWriter(HOME_FILE)) {
            Map<String, Map<String, Map<String, Integer>>> rawData = new HashMap<>();

            // Convertir les BlockPos en données brutes pour JSON
            savedHomes.forEach((uuid, homes) -> {
                Map<String, Map<String, Integer>> homeData = new HashMap<>();
                homes.forEach((name, pos) -> {
                    Map<String, Integer> posData = new HashMap<>();
                    posData.put("x", pos.getX());
                    posData.put("y", pos.getY());
                    posData.put("z", pos.getZ());
                    homeData.put(name, posData);
                });
                rawData.put(uuid, homeData);
            });

            GSON.toJson(rawData, writer);
        } catch (IOException e) {
            System.err.println("Failed to save home positions: " + e.getMessage());
        }
    }
    
    private static int teleportToPlayer(CommandContext<ServerCommandSource> context, String targetName) {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
        ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (targetPlayer == null) {
            sourcePlayer.sendMessage(Text.literal("Player '" + targetName + "' not found!"), false);
            return 0;
        }

        sourcePlayer.networkHandler.requestTeleport(
            targetPlayer.getX(),
            targetPlayer.getY(),
            targetPlayer.getZ(),
            targetPlayer.getYaw(),
            targetPlayer.getPitch()
        );

        sourcePlayer.sendMessage(Text.literal("Teleported to player '" + targetName + "'!"), false);
        return 1;
    }

    private static int teleportHere(CommandContext<ServerCommandSource> context, String targetName) {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
        ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (targetPlayer == null) {
            sourcePlayer.sendMessage(Text.literal("Player '" + targetName + "' not found!"), false);
            return 0;
        }

        targetPlayer.networkHandler.requestTeleport(
            sourcePlayer.getX(),
            sourcePlayer.getY(),
            sourcePlayer.getZ(),
            sourcePlayer.getYaw(),
            sourcePlayer.getPitch()
        );

        sourcePlayer.sendMessage(Text.literal("Player '" + targetName + "' teleported to you!"), false);
        return 1;
    }

}

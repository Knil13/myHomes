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
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class ModCommands {
	private static final File DATA_DIRECTORY = new File("myHome_files"); // Répertoire des fichiers
    private static final File HOME_FILE = new File(DATA_DIRECTORY, "home_positions.json"); // Fichier des homes
    private static final File SPAWN_FILE = new File(DATA_DIRECTORY, "spawn_locations.json"); // Fichier des spawns
    private static final File WARP_FILE = new File(DATA_DIRECTORY, "warp_locations.json"); // Fichier des spawns 
   
    private static final Map<String, Map<String, teleportPoint>> savedHomes = new HashMap<>();
    private static final Map<String, teleportPoint> warpLocations = new HashMap<>();
    private static final Map<String, teleportPoint> backLocations = new HashMap<>();
    private static final teleportPoint spawn = new teleportPoint();    
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
            
         // commande /back
            dispatcher.register(literal("back")
            	    .executes(ModCommands::backTeleport));  
            
         // commande /test
            dispatcher.register(literal("test")
            	    .executes(ModCommands::test));
                        
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
    
    
    private static int test(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
   	 	ServerPlayerEntity player = context.getSource().getPlayer();  
   	 	String playerName = player.getEntityName();
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.test", playerName)), false);

        return 1;
   }
   
    
    private static int sendTeleportRequest(CommandContext<ServerCommandSource> context, String targetName) throws CommandSyntaxException {
        ServerPlayerEntity requester = context.getSource().getPlayer();
        String requesterName = requester.getEntityName();
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (target == null) {
            requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.player_not_found", targetName)), false);
            return 0;
        }

        UUID targetId = target.getUuid();

        // Vérifie si une demande est déjà en attente
        if (teleportRequests.containsKey(targetId)) {            
            requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.request_already_pending", targetName)), false);
            return 0;
        }

        // Planifie un timeout pour la demande
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            teleportRequests.remove(targetId);
            requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_request_expired", targetName)), false);
            target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.target_request_expired", requesterName)), false);
        }, 30, TimeUnit.SECONDS);

        // Enregistre la demande
        teleportRequests.put(targetId, new TeleportRequest(requester.getUuid(), timeoutTask));

        // Envoie un message au joueur cible        
        target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.target_teleport_request", requesterName)), false);
        requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_teleport_request", targetName)), false);

        return 1;
    }

    
    @SuppressWarnings("unused")
	private static int acceptTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayer();
        String targetName = target.getEntityName();
        UUID targetId = target.getUuid();

        // Vérifie s'il y a une demande en attente
        TeleportRequest request = teleportRequests.remove(targetId);
        if (request == null) {        	
        	target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.no_teleport_request")), false);
            return 0;
        }

        // Annule le timeout
        request.timeoutTask.cancel(false);

        //save du /back
        saveBack(context);
        
        // Téléporte le joueur demandeur             
        ServerPlayerEntity requester = context.getSource().getServer().getPlayerManager().getPlayer(request.requesterId);
        String requesterName = requester.getEntityName();
        if (requester != null) {
           
            requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_teleport_request_accepted")), false);
            requester.requestTeleport(target.getX(), target.getY(), target.getZ());
            requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.you_teleport_to_target", targetName)), false);
            target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_teleport_to_you", requesterName)), false);
        } else {
            target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_not_online")), false);            
        }

        return 1;
    }
    
    private static int denyTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayer();
        String targetName = target.getEntityName();
        UUID targetId = target.getUuid();

        // Vérifie s'il y a une demande en attente
        TeleportRequest request = teleportRequests.remove(targetId);
        if (request == null) {
        	target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.no_teleport_request")), false);
            return 0;
        }

        // Annule le timeout
        request.timeoutTask.cancel(false);

        // Notifie le joueur demandeur
        ServerPlayerEntity requester = context.getSource().getServer().getPlayerManager().getPlayer(request.requesterId);
        String requesterName = requester.getEntityName();
        if (requester != null) {            
        	requester.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.requester_request_denied", targetName)), false);
        }
        
        Text offline_player =Text.literal(TranslationManager.translate("message.myhomes.offline_player"));        
        target.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.target_request_denied", (requester != null ? requesterName : offline_player))), false);
        
        return 1;
    }
    
    
    private static int setWarpPoint(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos currentPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();

        String w = world.getRegistryKey().getValue().toString();
        
        teleportPoint tp = new teleportPoint(w, currentPos.getX(), currentPos.getY(), currentPos.getZ(), player.getYaw(), player.getPitch());

        warpLocations.put(name, tp);
        
     // Sauvegarder les changements dans le fichier
        saveWarps();        

        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warp_set", name, currentPos.getX(),currentPos.getY(),currentPos.getZ())), false);
        return 1;
    }
    
    private static int setSpawnPoint(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos currentPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        String w = world.getRegistryKey().getValue().toString();
        
        spawn.set(w, currentPos.getX(), currentPos.getY(), currentPos.getZ(), player.getYaw(), player.getPitch());

        // Sauvegarder dans le fichier
        try (FileWriter writer = new FileWriter(SPAWN_FILE)) {
        	Map<String, Object> coords = new HashMap<>();
        	coords.put("world", w);
        	coords.put("x", currentPos.getX());
            coords.put("y", currentPos.getY());
            coords.put("z", currentPos.getZ());
            coords.put("yaw", player.getYaw());
            coords.put("pitch", player.getPitch());
            GSON.toJson(coords, writer);
        } catch (IOException e) {
            System.err.println("Failed to save spawn points: " + e.getMessage());
        }
        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.spawn_set", currentPos.getX(),currentPos.getY(),currentPos.getZ())), false);
        return 1;
    }
    
    
    private static void saveWarps() {
    	// Sauvegarder dans le fichier
        try (FileWriter writer = new FileWriter(WARP_FILE)) {
            Map<String, Map<String, Object>> rawData = new HashMap<>();
            warpLocations.forEach((key, pos) -> {
                Map<String, Object> coords = new HashMap<>();
                coords.put("world", pos.getWorld());
                coords.put("x", pos.getX());
                coords.put("y", pos.getY());
                coords.put("z", pos.getZ());
                coords.put("yaw", pos.getYaw());
                coords.put("pitch", pos.getPitch()); 
                rawData.put(key, coords);
            });
            GSON.toJson(rawData, writer);
        } catch (IOException e) {
            System.err.println("Failed to save spawn points: " + e.getMessage());
        }
    }
    
    
    private static void saveHomes() {
        try (FileWriter writer = new FileWriter(HOME_FILE)) {
            Map<String, Map<String, Map<String, Object>>> rawData = new HashMap<>();

            // Convertir les BlockPos en données brutes pour JSON
            savedHomes.forEach((uuid, homes) -> {
                Map<String, Map<String, Object>> homeData = new HashMap<>();
                homes.forEach((key, pos) -> {
                    Map<String, Object> coords = new HashMap<>();
                    coords.put("world", pos.getWorld());
                    coords.put("x", pos.getX());
                    coords.put("y", pos.getY());
                    coords.put("z", pos.getZ());
                    coords.put("yaw", pos.getYaw());
                    coords.put("pitch", pos.getPitch()); 
                    homeData.put(key, coords);
                });
                rawData.put(uuid, homeData);
            });

            GSON.toJson(rawData, writer);
        } catch (IOException e) {
            System.err.println("Failed to save home positions: " + e.getMessage());
        }
    }
    
        
    private static void loadSpawn() {
        if (SPAWN_FILE.exists()) {
            try (FileReader reader = new FileReader(SPAWN_FILE)) {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> coords = GSON.fromJson(reader, type);

                if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                    String w = (String) coords.get("world");
                	double x = (double) coords.get("x");
                    double y = (double) coords.get("y");
                    double z = (double) coords.get("z");
                    double yaw = (double) coords.get("yaw");
                    double pitch = (double) coords.get("pitch");
                    spawn.set(w, x, y, z, yaw, pitch);
                }
                
            } catch (IOException e) {
                System.err.println("Failed to load spawn points: " + e.getMessage());
            }
        }
    }
    
    
    private static void loadWarps() {
        if (WARP_FILE.exists()) {
            try (FileReader reader = new FileReader(WARP_FILE)) {
                Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
                Map<String, Map<String, Object>> rawData = GSON.fromJson(reader, type);

                // Convertir les données en BlockPos
                rawData.forEach((name, coords) -> {
                    if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                    	String w = (String) coords.get("world");
                    	double x = (double) coords.get("x");
                        double y = (double) coords.get("y");
                        double z = (double) coords.get("z");
                        double yaw = (double) coords.get("yaw");
                        double pitch = (double) coords.get("pitch");
                        warpLocations.put(name, new teleportPoint(w, x, y, z, yaw, pitch));
                    }
                });
            } catch (IOException e) {
                System.err.println("Failed to load spawn points: " + e.getMessage());
            }
        }
    }
    
    
    private static void loadHomes() {
        if (HOME_FILE.exists()) {
            try (FileReader reader = new FileReader(HOME_FILE)) {
                // Lis le JSON dans une Map de Map<String, Map<String, Integer>>
                Type type = new TypeToken<Map<String, Map<String, Map<String, Object>>>>() {}.getType();
                Map<String, Map<String, Map<String, Object>>> rawData = GSON.fromJson(reader, type);

                // Convertir les données brutes en BlockPos
                rawData.forEach((uuid, homes) -> {
                    Map<String, teleportPoint> playerHomes = new HashMap<>();
                    
                    homes.forEach((name, coords) -> {
                    	String w = (String) coords.get("world");
                    	double x = (double) coords.get("x");
                        double y = (double) coords.get("y");
                        double z = (double) coords.get("z");
                        double yaw = (double) coords.get("yaw");
                        double pitch = (double) coords.get("pitch");
                        playerHomes.put(name, new teleportPoint(w, x, y, z, yaw, pitch));
                    });
                    savedHomes.put(uuid, playerHomes);
                });

            } catch (IOException e) {
                System.err.println("Failed to load home positions: " + e.getMessage());
            }
        }
    }
        
    
    private static int teleportToSpawn(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
      //save du /back
        saveBack(context);
        
        PlayerTeleport.teleportPlayer(player, spawn);
        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.spawn_teleport")), false);
        return 1;
    }
    
    
    private static int teleportToWarp(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        teleportPoint tp = warpLocations.get(name);

        if (tp == null) {            
            player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warp_not_exist", name)), false);
            return 0;
        }
        
      //save du /back
        saveBack(context);

        PlayerTeleport.teleportPlayer(player, tp);
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warp_teleport", name)), false);
        return 1;
    }
    
    private static int saveBack(CommandContext<ServerCommandSource> context) {
    	ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos currentPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        String w = world.getRegistryKey().getValue().toString();
        
        teleportPoint tp = new teleportPoint(w, currentPos.getX(), currentPos.getY(), currentPos.getZ(), player.getYaw(), player.getPitch());
        backLocations.remove(player.getUuidAsString());
        backLocations.put(player.getUuidAsString(), tp);
        return 1;
    }
    
    
    private static int backTeleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
    	 ServerPlayerEntity player = context.getSource().getPlayer();
         String playerId = player.getUuidAsString();

         // Vérifier si le joueur a des homes sauvegardés
         if (!backLocations.containsKey(playerId)) {           
             player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.no_back")), false);
             return 0;
         }                
         
         teleportPoint tp = backLocations.get(playerId);
         
         //save du /back
         saveBack(context);
         
         PlayerTeleport.teleportPlayer(player, tp);       
        
         player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.back")), false);
         return 1;
    }
    
     
    
    private static int setHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();
        BlockPos currentPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        String w = world.getRegistryKey().getValue().toString();
        
        teleportPoint tp = new teleportPoint(w, currentPos.getX(), currentPos.getY(), currentPos.getZ(), player.getYaw(), player.getPitch());

        // Récupérer ou créer la map des homes du joueur
        savedHomes.computeIfAbsent(playerId, k -> new HashMap<>());

        // Ajouter ou remplacer le home
        savedHomes.get(playerId).put(name, tp);

        // Sauvegarder dans le fichier
        saveHomes();

        // Retourner un message au joueur        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.home_set", name, currentPos.getX(), currentPos.getY(), currentPos.getZ())), false);
        return 1;
    }

    private static int teleportHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || !savedHomes.get(playerId).containsKey(name)) {        
            player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.home_not_exist", name)), false);
            return 0;
        }

        teleportPoint tp = savedHomes.get(playerId).get(name);
        
      //save du /back
        saveBack(context);
        
        PlayerTeleport.teleportPlayer(player, tp);         
        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.home_teleport", name)), false);
        return 1;
    }


    private static int listHomes(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || savedHomes.get(playerId).isEmpty()) {            
            player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.no_homes")), false);
            return 0;
        }

        // Afficher la liste des homes        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.homes")), false);
        
        StringBuilder homeList = new StringBuilder();
        savedHomes.get(playerId).keySet().forEach(name -> homeList.append(name).append(", "));
        player.sendMessage(Text.literal(homeList.substring(0, homeList.length() - 2)), false);
        return 1;
    }
    
    
    private static int listWarps(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        // Vérifier s'il existe des warps
        if (warpLocations.isEmpty()) {        	
        	player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.no_warps")), false);
            return 0;
        }

        // Afficher la liste des warps        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warps")), false);
        StringBuilder warpList = new StringBuilder();
        warpLocations.keySet().forEach(name -> warpList.append(name).append(", "));
        player.sendMessage(Text.literal(warpList.substring(0, warpList.length() - 2)), false);
        return 1;
    }
    

    private static int deleteHome(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String playerId = player.getUuidAsString();

        // Vérifier si le joueur a des homes sauvegardés
        if (!savedHomes.containsKey(playerId) || !savedHomes.get(playerId).containsKey(name)) {        	
        	player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.home_not_exist")), false);
            return 0;
        }

        // Supprimer le home
        savedHomes.get(playerId).remove(name);

        // Sauvegarder dans le fichier
        saveHomes();
        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.home_deleted", name)), false);
        return 1;
    }

    
	private static int deleteWarp(CommandContext<ServerCommandSource> context, String name) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        // Vérifier si le spawn existe
        if (!warpLocations.containsKey(name)) {        	
        	player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warp_not_exist", name)), false);
            return 0;
        }

        // Supprimer le spawn de la mémoire
        warpLocations.remove(name);

        // Sauvegarder les changements dans le fichier
        saveWarps();
        
        player.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.warp_deleted", name)), false);
        return 1;
    }
    

    
    
    private static int teleportToPlayer(CommandContext<ServerCommandSource> context, String targetName) {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
        ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (targetPlayer == null) {            
            sourcePlayer.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.player_not_found", targetName)), false);
            return 0;
        }
        
        //save du /back
        saveBack(context);
        sourcePlayer.networkHandler.requestTeleport(
            targetPlayer.getX(),
            targetPlayer.getY(),
            targetPlayer.getZ(),
            targetPlayer.getYaw(),
            targetPlayer.getPitch()
        );

        sourcePlayer.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.you_teleport_to_target", targetName)), false);
                
        return 1;
    }

    private static int teleportHere(CommandContext<ServerCommandSource> context, String targetName) {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
        ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (targetPlayer == null) {        	
        	sourcePlayer.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.player_not_found", targetName)), false);
            return 0;
        }
        
        //save du /back
        saveBack(context);
        
        targetPlayer.networkHandler.requestTeleport(
            sourcePlayer.getX(),
            sourcePlayer.getY(),
            sourcePlayer.getZ(),
            sourcePlayer.getYaw(),
            sourcePlayer.getPitch()
        );
        
        sourcePlayer.sendMessage(Text.literal(TranslationManager.translate("message.myhomes.target_teleport_to_you", targetName)), false);
        return 1;
    }

}

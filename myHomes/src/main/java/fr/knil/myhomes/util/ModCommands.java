package fr.knil.myhomes.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;


public class ModCommands {
    private static final File DATA_FILE = new File("home_positions.json");
    private static final Map<String, Map<String, BlockPos>> savedHomes = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void registerCommands() {
        loadHomes();

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
            
         // Commande /tp pour tous les joueurs
            dispatcher.register(literal("tp")
                .then(argument("player", StringArgumentType.string())
                    .executes(context -> teleportToPlayer(context, StringArgumentType.getString(context, "player"))))
            );

            // Commande /tphere
            dispatcher.register(literal("tphere")
                .then(argument("player", StringArgumentType.string())
                    .executes(context -> teleportHere(context, StringArgumentType.getString(context, "player"))))
            );
        });
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
        if (DATA_FILE.exists()) {
            try (FileReader reader = new FileReader(DATA_FILE)) {
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
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
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

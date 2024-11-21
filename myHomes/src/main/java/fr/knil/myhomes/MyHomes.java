package fr.knil.myhomes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Language;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.knil.myhomes.util.ModCommands;

public class MyHomes implements ModInitializer {
	public static final String MOD_ID = "myhomes";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Lancement de myHomes");
		
		ModCommands.registerCommands();
		
		
		Language language = Language.getInstance(); // Langue par défaut du serveur

        List<String> testKeys = List.of(
            "message.myhomes.player_not_found",
            "message.myhomes.home_set",
            "message.myhomes.spawn_set"
        );

        for (String key : testKeys) {
            if (language.hasTranslation(key)) {
                System.out.println("Translation for key '" + key + "' is loaded.");
            } else {
                System.out.println("Translation for key '" + key + "' is NOT loaded.");
            }
        }
	}
}
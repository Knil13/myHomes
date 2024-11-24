package fr.knil.myhomes.util;import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class TranslationManager {
    private static final Gson gson = new Gson();
    private static Map<String, String> translations = new HashMap<>();
    private static String serverLanguage = "en_us"; // Langue par défaut

    // Charger la langue depuis le fichier de configuration
    private static void loadServerLanguage() {
        try (InputStream stream = TranslationManager.class.getResourceAsStream("/config/myhomes-server-config.json")) {
            if (stream != null) {
                JsonObject jsonObject = gson.fromJson(new InputStreamReader(stream), JsonObject.class);
                serverLanguage = jsonObject.get("language").getAsString();
            } else {
                System.err.println("Config file not found, using default language: " + serverLanguage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Charger les traductions en mémoire
    public static void loadTranslations() {
        loadServerLanguage(); // Charger la langue configurée
        String path = "/assets/myhomes/lang/" + serverLanguage + ".json";

        try (InputStream stream = TranslationManager.class.getResourceAsStream(path)) {
            if (stream != null) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                translations = gson.fromJson(new InputStreamReader(stream), type);
            } else {
                System.err.println("Translation file not found: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Méthode pour récupérer une traduction
    public static String translate(String key, Object... args) {
        String template = translations.getOrDefault(key, key);  // Retourne la clé si non trouvée
        return String.format(template, args);  // Remplace les placeholders avec les arguments
    }
}
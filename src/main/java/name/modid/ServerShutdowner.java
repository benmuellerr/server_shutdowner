package name.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Properties;

public class ServerShutdowner implements ModInitializer {
    public static final String MOD_ID = "server-shutdowner";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("server-shutdowner.properties");

    private int shutdownHour;
    private int shutdownMinute;

    private boolean shutdownTriggered = false;

    // Verhindert, dass eine Countdown-Meldung mehrfach erscheint.
    private int lastCountdownSecond = -1;

    @Override
    public void onInitialize() {
        LOGGER.info("Server Shutdowner wird geladen...");

        loadConfig();

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void loadConfig() {
        Properties properties = new Properties();

        // Standardwerte
        properties.setProperty("shutdown-hour", "23");
        properties.setProperty("shutdown-minute", "00");

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                properties.load(input);

                LOGGER.info("Konfiguration geladen: {}", CONFIG_FILE);
            } catch (IOException e) {
                LOGGER.error("Konfigurationsdatei konnte nicht gelesen werden!", e);
            }
        } else {
            try {
                Files.createDirectories(CONFIG_FILE.getParent());

                try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
                    properties.store(
                            output,
                            "Server Shutdowner configuration"
                    );
                }

                LOGGER.info("Neue Konfigurationsdatei erstellt: {}", CONFIG_FILE);
            } catch (IOException e) {
                LOGGER.error("Konfigurationsdatei konnte nicht erstellt werden!", e);
            }
        }

        shutdownHour = parseInt(
                properties.getProperty("shutdown-hour"),
                23,
                0,
                23
        );

        shutdownMinute = parseInt(
                properties.getProperty("shutdown-minute"),
                0,
                0,
                59
        );

        LOGGER.info(
                "Server wird täglich um {}:{} heruntergefahren.",
                String.format("%02d", shutdownHour),
                String.format("%02d", shutdownMinute)
        );
    }

    private int parseInt(
            String value,
            int defaultValue,
            int min,
            int max
    ) {
        try {
            int parsed = Integer.parseInt(value);

            if (parsed < min || parsed > max) {
                LOGGER.warn(
                        "Ungültiger Wert '{}'. Verwende {}.",
                        value,
                        defaultValue
                );

                return defaultValue;
            }

            return parsed;
        } catch (NumberFormatException e) {
            LOGGER.warn(
                    "Ungültiger Wert '{}'. Verwende {}.",
                    value,
                    defaultValue
            );

            return defaultValue;
        }
    }

    private void onServerTick(MinecraftServer server) {
        LocalTime now = LocalTime.now();

        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        int currentSecond = now.getSecond();

        boolean correctHour = currentHour == shutdownHour;
        boolean correctMinute = currentMinute == shutdownMinute;

        if (correctHour && correctMinute) {

            int secondsUntilShutdown = 60 - currentSecond;

            /*
             * Countdown:
             * 60 Sekunden = 1 Minute
             * 30 Sekunden
             * letzte 10 Sekunden
             */
            boolean showCountdown =
                    secondsUntilShutdown == 60 ||
                    secondsUntilShutdown == 30 ||
                    (secondsUntilShutdown >= 1 && secondsUntilShutdown <= 10);

            if (showCountdown && lastCountdownSecond != secondsUntilShutdown) {
                lastCountdownSecond = secondsUntilShutdown;

                if (secondsUntilShutdown == 60) {
                    sendMessage(
                            server,
                            "§eDer Server wird in 1 Minute heruntergefahren!"
                    );
                } else {
                    sendMessage(
                            server,
                            "§eDer Server wird in "
                                    + secondsUntilShutdown
                                    + " Sekunden heruntergefahren!"
                    );
                }
            }

            /*
             * Bei Sekunde 59 befinden wir uns praktisch direkt vor
             * dem eingestellten Zeitpunkt.
             */
            if (currentSecond == 59 && !shutdownTriggered) {
                shutdownTriggered = true;

                sendMessage(
                        server,
                        "§cDer Server wird jetzt heruntergefahren!"
                );

                LOGGER.info("Shutdown-Zeit erreicht. Server wird heruntergefahren...");

                server.halt(false);
            }

        } else {
            // Neue Minute / neuer Tag -> Countdown zurücksetzen.
            lastCountdownSecond = -1;
            shutdownTriggered = false;
        }
    }

    private void sendMessage(MinecraftServer server, String message) {
        LOGGER.info(message.replace("§e", "").replace("§c", ""));

        server.getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(message),
                false
        );
    }
}
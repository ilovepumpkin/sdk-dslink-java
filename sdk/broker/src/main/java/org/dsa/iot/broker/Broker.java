package org.dsa.iot.broker;

import org.dsa.iot.broker.client.ClientManager;
import org.dsa.iot.broker.config.Arguments;
import org.dsa.iot.broker.config.broker.BrokerConfig;
import org.dsa.iot.broker.config.broker.BrokerFileConfig;
import org.dsa.iot.broker.config.broker.BrokerMemoryConfig;
import org.dsa.iot.broker.server.ServerManager;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Samuel Grenier
 */
public class Broker {

    private static final Logger LOGGER = LoggerFactory.getLogger(Broker.class);
    private final ClientManager clients;
    private final BrokerConfig config;
    private ServerManager server;

    public Broker(BrokerConfig config) {
        this(config, new ClientManager());
    }

    public Broker(BrokerConfig config, ClientManager clients) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        this.clients = clients;
        this.config = config;
        config.readAndUpdate();
    }

    protected void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }, "Broker-Shutdown-Hook"));
    }

    /**
     * Starts the broker.
     *
     * @see BrokerMemoryConfig For the default configurations.
     */
    public void start() {
        stop();
        try {
            LOGGER.info("Broker is starting");
            JsonObject serverConf = config.getConfig().get("server");
            server = new ServerManager(clients, serverConf);
            server.start();
        } catch (Exception e) {
            stop();
        }
    }

    /**
     * Shuts down the broker.
     */
    public void stop() {
        if (server != null) {
            LOGGER.info("Broker is shutting down");
            server.stop();
        }
    }

    /**
     * Creates a standard broker with unmodified functionality.
     *
     * @param args Arguments of the program.
     * @return A broker instance.
     */
    public static Broker create(String[] args) {
        Arguments parsed = new Arguments();
        if (!parsed.parse(args)) {
            return null;
        }

        if (!parsed.runServer()) {
            parsed.displayHelp();
            return null;
        }

        BrokerConfig conf = new BrokerFileConfig();
        Broker b = new Broker(conf);
        b.addShutdownHook();
        return b;
    }
}
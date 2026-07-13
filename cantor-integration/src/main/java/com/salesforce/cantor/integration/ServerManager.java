package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.grpc.CantorOnGrpc;
import com.salesforce.cantor.server.CantorEnvironment;
import com.salesforce.cantor.server.grpc.GrpcServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages the lifecycle of a Cantor server for integration testing.
 * Starts the server, provides a client, and shuts down when done.
 */
public class ServerManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerManager.class);

    private int port;
    private final String storageType;
    private GrpcServer server;
    private Cantor client;

    public ServerManager(String storageType) {
        this.storageType = storageType;
    }

    /**
     * Starts the Cantor server with the configured backend.
     */
    public void start() throws IOException {
        Config config = ConfigFactory.load("cantor-server")
                .withValue("cantor.storage.type", ConfigValueFactory.fromAnyRef(storageType));

        this.port = config.getInt("cantor.grpc.port");
        logger.info("Starting Cantor server on port {} with backend: {}", port, storageType);

        CantorEnvironment environment = new CantorEnvironment(config);
        this.server = new GrpcServer(environment);
        this.server.start();

        // Create the gRPC client pointing at the server
        this.client = new CantorOnGrpc("localhost:" + port);

        logger.info("Server started successfully.");
    }

    /**
     * Returns the Cantor client connected to the running server.
     */
    public Cantor getClient() {
        return this.client;
    }

    /**
     * Shuts down the server.
     */
    public void stop() {
        logger.info("Shutting down server...");
        if (this.server != null) {
            this.server.shutdown();
        }
        logger.info("Server stopped.");
    }
}
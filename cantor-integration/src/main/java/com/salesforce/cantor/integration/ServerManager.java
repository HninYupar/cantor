package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.grpc.CantorOnGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerManager.class);

    private final String host;
    private int port;
    private Cantor client;

    public ServerManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        logger.info("Connecting to Cantor server at {}:{}.", host, port);
        this.client = new CantorOnGrpc(host + ":" + port);
        logger.info("Connected successfully.");
    }

    public Cantor getClient() {
        return this.client;
    }
}
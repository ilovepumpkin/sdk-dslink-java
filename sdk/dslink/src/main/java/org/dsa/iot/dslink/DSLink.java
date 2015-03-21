package org.dsa.iot.dslink;

import org.dsa.iot.dslink.connection.NetworkClient;
import org.dsa.iot.dslink.link.Requester;
import org.dsa.iot.dslink.link.Responder;
import org.dsa.iot.dslink.node.NodeManager;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * @author Samuel Grenier
 */
public class DSLink {

    private final NodeManager nodeManager;
    private final Requester requester;
    private final Responder responder;
    private NetworkClient client;

    /**
     * @param handler DSLink handler
     * @param client Initialized client endpoint
     */
    protected DSLink(DSLinkHandler handler,
                     NetworkClient client) {
        if (client == null)
            throw new NullPointerException("client");
        this.client = client;
        this.nodeManager = new NodeManager();
        if (client.isRequester()) {
            requester = new Requester(handler);
            responder = null;
            requester.setDSLink(this);
        } else if (client.isResponder()) {
            requester = null;
            responder = new Responder(handler);
            responder.setDSLink(this);
        } else {
            requester = null;
            responder = null;
        }
    }

    /**
     * @return The network client to write data to.
     */
    public NetworkClient getClient() {
        return client;
    }

    /**
     * @return Requester of the singleton client, can be null
     */
    public Requester getRequester() {
        return requester;
    }

    /**
     * @return Node manager of the link.
     */
    public NodeManager getNodeManager() {
        return nodeManager;
    }

    /**
     * Sets the default data handler to the remote endpoint.
     */
    public void setDefaultDataHandlers() {
        if (client.isRequester()) {
            client.setResponseDataHandler(new Handler<JsonArray>() {
                @Override
                public void handle(JsonArray event) {
                    for (Object object : event) {
                        JsonObject json = (JsonObject) object;
                        requester.parse(json);
                    }
                }
            });
        } else if (client.isResponder()) {
            client.setRequestDataHandler(new Handler<JsonArray>() {
                @Override
                public void handle(JsonArray event) {
                    for (Object object : event) {
                        JsonObject json = (JsonObject) object;
                        responder.parse(json);
                    }
                }
            });
        }
    }
}

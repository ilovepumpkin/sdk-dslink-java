package org.dsa.iot.historian.database;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.DSLinkProvider;
import org.dsa.iot.dslink.link.Requester;
import org.dsa.iot.dslink.methods.requests.SetRequest;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValuePair;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.historian.stats.GetHistory;
import org.dsa.iot.historian.utils.QueryData;
import org.dsa.iot.historian.utils.WatchUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Samuel Grenier
 */
public class Watch {
    private static final Logger LOGGER = LoggerFactory.getLogger(Watch.class);

    private final ReentrantReadWriteLock rtLock = new ReentrantReadWriteLock();
    private final List<Handler<QueryData>> rtHandlers = new ArrayList<>();
    private final WatchGroup group;
    private final Node node;

    private Node realTimeValue;
    private String watchedPath;

    private Node lastWrittenValue;
    private Node startDate;
    private Node endDate;
    private boolean enabled;

    // Values that must be handled before the buffer queue
    private long lastWrittenTime;
    private Value lastValue;

    public WatchUpdate getLastWatchUpdate() {
        if (lastWatchUpdate == null) {
            Value value = node.getValue();
            if (value != null) {
                SubscriptionValue subscriptionValue = new SubscriptionValue(watchedPath, value, null, null, null, null);
                lastWatchUpdate = new WatchUpdate(this, subscriptionValue);
            }
        }
        return lastWatchUpdate;
    }

    private WatchUpdate lastWatchUpdate;

    public Watch(final WatchGroup group, Node node) {
        this.group = group;
        this.node = node;
    }

    public Node getNode() {
        return node;
    }

    public WatchGroup getGroup() {
        return group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPath() {
        return watchedPath;
    }

    public void handleLastWritten(Value value) {
        if (value == null) {
            return;
        }

        lastWrittenValue.setValue(value);
        value = new Value(value.getTimeStamp());
        if (startDate != null) {
            startDate.setValue(value);
            startDate = null;
        }

        endDate.setValue(value);
        lastWrittenTime = value.getTime();
    }

    public void setLastWrittenTime(long time) {
        lastWrittenTime = time;
    }

    public long getLastWrittenTime() {
        return lastWrittenTime;
    }

    public void setLastValue(Value value) {
        lastValue = value;
    }

    public Value getLastValue() {
        return lastValue;
    }

    public void unsubscribe() {
        group.removeFromWatches(this);
        removeFromSubscriptionPool();

        node.delete();
    }

    private void removeFromSubscriptionPool() {
        DatabaseProvider provider = group.getDb().getProvider();
        SubscriptionPool pool = provider.getPool();
        pool.unsubscribe(watchedPath, this);
    }

    public void init(Permission perm, Database db) {
        watchedPath = node.getName().replaceAll("%2F", "/").replaceAll("%2E", ".");
        initData(node);

        createUnsubscribeAction(perm);

        new OverwriteHistoryAction(this, node, perm, db);
        GetHistory.initAction(node, getGroup().getDb());

        addGetHistoryActionAlias();

        group.addWatch(this);
    }

    private void createUnsubscribeAction(Permission perm) {
        NodeBuilder b = node.createChild("unsubscribe");
        b.setSerializable(false);
        b.setDisplayName("Unsubscribe");
        b.setAction(new Action(perm, new Handler<ActionResult>() {
            @Override
            public void handle(ActionResult event) {
                unsubscribe();
            }
        }));
        b.build();
    }

    public void addGetHistoryActionAlias() {
        JsonObject mergePathsObject = new JsonObject();
        mergePathsObject.put("@", "merge");
        mergePathsObject.put("type", "paths");

        String linkPath = node.getLink().getDSLink().getPath();
        String getHistoryPath = String.format("%s%s/getHistory", linkPath, node.getPath());
        JsonArray array = new JsonArray();
        array.add(getHistoryPath);
        mergePathsObject.put("val", array);
        Value mergeValue = new Value(mergePathsObject);

        Requester requester = getRequester();
        String actionAliasPath = watchedPath + "/@@getHistory";
        requester.set(new SetRequest(actionAliasPath, mergeValue), null);
    }

    private Requester getRequester() {
        DSLinkHandler handler = node.getLink().getHandler();
        DSLinkProvider linkProvider = handler.getProvider();
        String dsId = handler.getConfig().getDsIdWithHash();
        DSLink link = linkProvider.getRequesters().get(dsId);
        return link.getRequester();
    }

    protected void initData(final Node node) {
        realTimeValue = node;

        {
            NodeBuilder b = node.createChild("enabled");
            b.setDisplayName("Enabled");
            b.setWritable(Writable.CONFIG);
            b.setValueType(ValueType.BOOL);
            b.setValue(new Value(true));
            b.getListener().setValueHandler(new Handler<ValuePair>() {
                @Override
                public synchronized void handle(ValuePair event) {
                    enabled = event.getCurrent().getBool();
                    String path = node.getName().replaceAll("%2F", "/").replaceAll("%2E", ".");
                    SubscriptionPool pool = group.getDb().getProvider().getPool();
                    if (enabled) {
                        pool.subscribe(path, Watch.this);
                    } else {
                        pool.unsubscribe(path, Watch.this);
                    }
                }
            });
            Node n = b.build();
            enabled = n.getValue().getBool();
        }

        try {
            NodeBuilder b = node.createChild("startDate");
            b.setDisplayName("Start Date");
            b.setValueType(ValueType.TIME);
            Node n = b.build();
            if (n.getValue() == null) {
                startDate = n;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set start date", e);
        }

        {
            NodeBuilder b = node.createChild("endDate");
            b.setDisplayName("End Date");
            b.setValueType(ValueType.TIME);
            endDate = b.build();
        }

        {
            NodeBuilder b = node.createChild("lwv");
            b.setDisplayName("Last Written Value");
            b.setValueType(ValueType.DYNAMIC);
            lastWrittenValue = b.build();
        }
    }

    /**
     * Called when the watch receives data.
     *
     * @param sv Received data.
     */
    public void onData(SubscriptionValue sv) {
        Value v = sv.getValue();
        realTimeValue.setValue(v);
        if (group.canWriteOnNewData()) {
            group.write(this, sv);
        } else {
            lastWatchUpdate = new WatchUpdate(this, sv);
        }
    }

    public void addHandler(Handler<QueryData> handler) {
        if (handler == null) {
            return;
        }
        rtLock.writeLock().lock();
        try {
            rtHandlers.add(handler);
        } finally {
            rtLock.writeLock().unlock();
        }
    }

    public void removeHandler(Handler<QueryData> handler) {
        rtLock.writeLock().lock();
        try {
            rtHandlers.remove(handler);
        } finally {
            rtLock.writeLock().unlock();
        }
    }

    public void notifyHandlers(QueryData data) {
        rtLock.readLock().lock();
        try {
            for (Handler<QueryData> h : rtHandlers) {
                h.handle(data);
            }
        } finally {
            rtLock.readLock().unlock();
        }
    }
}

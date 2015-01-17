package org.dsa.iot.responder;

import org.dsa.iot.responder.node.Node;
import org.dsa.iot.responder.node.NodeManager;
import org.dsa.iot.responder.node.exceptions.DuplicateException;
import org.dsa.iot.responder.node.exceptions.NoSuchPath;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Samuel Grenier
 */
public class NodeTest {

    @Test
    public void nodeAdditions() {
        NodeManager manager = new NodeManager();

        Node nodeA = new Node("A");
        manager.addRootNode(nodeA);

        Assert.assertNotNull(manager.getNode("A"));
        Assert.assertNotNull(manager.getNode("/A"));
        Assert.assertNotNull(manager.getNode("/A/"));
        Assert.assertNotNull(manager.getNode("/A//"));

        Assert.assertNull(manager.getNode("/B"));
        Node nodeB = new Node("B");
        manager.addRootNode(nodeB);

        Assert.assertNotNull(manager.getNode("B"));
        Assert.assertNotNull(manager.getNode("/B"));
        Assert.assertNotNull(manager.getNode("/B/"));
        Assert.assertNotNull(manager.getNode("/B//"));

        Assert.assertNull(manager.getNode("/A/B"));
        Assert.assertNull(manager.getNode("/B/A"));

        Assert.assertNull(nodeA.getChildren());
        nodeA.addChild(new Node("A"));
        Assert.assertNotNull(nodeA.getChildren());

        Assert.assertNotNull(manager.getNode("A/A"));
        Assert.assertNotNull(manager.getNode("/A/A"));
        Assert.assertNotNull(manager.getNode("/A/A/"));
        Assert.assertNotNull(nodeA.getChild("A"));
        Assert.assertNull(nodeA.getChild("/A"));
    }

    @Test
    public void nodeRemovals() {
        NodeManager manager = new NodeManager();
        Node a = manager.addRootNode(new Node("A"));
        a.addChild(new Node("A_A"));
        a.addChild(new Node("A_B"));

        a.removeChild("A_A");
        a.removeChild(new Node("A_B"));

        Assert.assertNotNull(manager.getNode("/A"));
        Assert.assertNull(manager.getNode("/A/A_A"));
        Assert.assertNull(manager.getNode("/A/A_B"));

        Assert.assertNotNull(a.getChildren());
        Assert.assertTrue(manager.getChildren("A").isEmpty());
        Assert.assertTrue(a.getChildren().isEmpty());
    }

    @Test
    public void children() {
        NodeManager manager = new NodeManager();
        Node a = manager.addRootNode(new Node("A"));
        a.addChild(new Node("A_A"));
        a.addChild(new Node("A_B"));

        Assert.assertNotNull(manager.getChildren("A"));
        Assert.assertNotNull(manager.getChildren("/A"));
        Assert.assertNotNull(manager.getChildren("/A/"));

        Assert.assertEquals(2, manager.getChildren("A").size());
        Assert.assertEquals(2, a.getChildren().size());

    }

    @Test(expected = DuplicateException.class)
    public void duplicateRootNodes() {
        NodeManager manager = new NodeManager();
        manager.addRootNode(new Node("A"));
        manager.addRootNode(new Node("A"));
    }

    @Test
    public void illegalPathInput() {
        NodeManager manager = new NodeManager();

        boolean emptyPath = false;
        boolean nullPath = false;

        try {
            manager.getNode("");
        } catch (IllegalArgumentException e) {
            emptyPath = true;
        }

        try {
            manager.getNode(null);
        } catch (IllegalArgumentException e) {
            nullPath = true;
        }

        Assert.assertTrue(emptyPath);
        Assert.assertTrue(nullPath);
    }

    @Test(expected = NoSuchPath.class)
    public void noSuchPath() {
        new NodeManager().getChildren("nothing");
    }
}

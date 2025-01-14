/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.workflow.core.node;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.NodeContainer;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.impl.NodeContainerImpl;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.kie.api.definition.process.Connection;

public class CompositeNode extends StateBasedNode implements NodeContainer, EventNodeInterface {

    private static final long serialVersionUID = 510l;

    private NodeContainer nodeContainer;
    private Map<String, CompositeNode.NodeAndType> inConnectionMap = new HashMap<String, CompositeNode.NodeAndType>();
    private Map<String, CompositeNode.NodeAndType> outConnectionMap = new HashMap<String, CompositeNode.NodeAndType>();
    private boolean cancelRemainingInstances = true;
    private boolean autoComplete = true;

    public CompositeNode() {
        this.nodeContainer = new NodeContainerImpl();
    }

    public org.kie.api.definition.process.Node getNode(long id) {
        return nodeContainer.getNode(id);
    }

    @Override
    public org.kie.api.definition.process.Node getNodeByUniqueId(String s) {
        throw new UnsupportedOperationException();
    }

    public NodeContainer getNodeContainer() {
        return nodeContainer;
    }

    public org.kie.api.definition.process.Node internalGetNode(long id) {
        return getNode(id);
    }

    public org.kie.api.definition.process.Node[] getNodes() {
        List<org.kie.api.definition.process.Node> subNodes = new ArrayList<org.kie.api.definition.process.Node>();
        for (org.kie.api.definition.process.Node node : nodeContainer.getNodes()) {
            if (!(node instanceof CompositeNode.CompositeNodeStart) &&
                    !(node instanceof CompositeNode.CompositeNodeEnd)) {
                subNodes.add(node);
            }
        }
        return subNodes.toArray(new org.kie.api.definition.process.Node[subNodes.size()]);
    }

    public org.kie.api.definition.process.Node[] internalGetNodes() {
        return getNodes();
    }

    public void addNode(org.kie.api.definition.process.Node node) {
        // TODO find a more elegant solution for this
        // preferrable remove id setting from this class
        // and delegate to GUI command that drops node
        if (node.getId() <= 0) {
            long id = 0;
            for (org.kie.api.definition.process.Node n : nodeContainer.getNodes()) {
                if (n.getId() > id) {
                    id = n.getId();
                }
            }
            ((Node) node).setId(++id);
        }
        nodeContainer.addNode(node);
        ((Node) node).setParentContainer(this);
    }

    protected void internalAddNode(org.kie.api.definition.process.Node node) {
        addNode(node);
    }

    public void removeNode(org.kie.api.definition.process.Node node) {
        nodeContainer.removeNode(node);
        ((Node) node).setParentContainer(null);
    }

    protected void internalRemoveNode(org.kie.api.definition.process.Node node) {
        removeNode(node);
    }

    @Override
    public boolean acceptsEvent(String type, Object event, Function<String, Object> varResolver) {
        for (org.kie.api.definition.process.Node node : internalGetNodes()) {
            if (node instanceof EventNodeInterface) {
                if (((EventNodeInterface) node).acceptsEvent(type, event, varResolver)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void linkIncomingConnections(String inType, long inNodeId, String inNodeType) {
        linkIncomingConnections(inType, new NodeAndType(nodeContainer, inNodeId, inNodeType));
    }

    public void linkIncomingConnections(String inType, CompositeNode.NodeAndType inNode) {
        CompositeNode.NodeAndType oldNodeAndType = inConnectionMap.get(inType);
        if (oldNodeAndType != null) {
            if (oldNodeAndType.equals(inNode)) {
                return;
            } else {
                // remove old start nodes + connections
                List<Connection> oldInConnections =
                        oldNodeAndType.getNode().getIncomingConnections(oldNodeAndType.getType());
                if (oldInConnections != null) {
                    for (Connection connection : new ArrayList<Connection>(oldInConnections)) {
                        if (connection.getFrom() instanceof CompositeNodeStart) {
                            removeNode(connection.getFrom());
                            ((ConnectionImpl) connection).terminate();
                        }
                    }
                }
            }
        }
        inConnectionMap.put(inType, inNode);
        if (inNode != null) {
            List<Connection> connections = getIncomingConnections(inType);
            for (Connection connection : connections) {
                CompositeNodeStart start = new CompositeNodeStart(this, connection.getFrom(), inType);
                internalAddNode(start);
                if (inNode.getNode() != null) {
                    new ConnectionImpl(
                            start, Node.CONNECTION_DEFAULT_TYPE,
                            inNode.getNode(), inNode.getType());
                }
            }
        }
    }

    public void linkOutgoingConnections(long outNodeId, String outNodeType, String outType) {
        linkOutgoingConnections(new NodeAndType(this, outNodeId, outNodeType), outType);
    }

    public void linkOutgoingConnections(CompositeNode.NodeAndType outNode, String outType) {
        CompositeNode.NodeAndType oldNodeAndType = outConnectionMap.get(outType);
        if (oldNodeAndType != null) {
            if (oldNodeAndType.equals(outNode)) {
                return;
            } else {
                // remove old end nodes + connections
                List<Connection> oldOutConnections =
                        oldNodeAndType.getNode().getOutgoingConnections(oldNodeAndType.getType());
                for (Connection connection : new ArrayList<Connection>(oldOutConnections)) {
                    if (connection.getTo() instanceof CompositeNodeEnd) {
                        removeNode(connection.getTo());
                        ((ConnectionImpl) connection).terminate();
                    }
                }
            }
        }
        outConnectionMap.put(outType, outNode);
        if (outNode != null) {
            List<Connection> connections = getOutgoingConnections(outType);
            for (Connection connection : connections) {
                CompositeNodeEnd end = new CompositeNodeEnd(this, connection.getTo(), outType);
                internalAddNode(end);
                if (outNode.getNode() != null) {
                    new ConnectionImpl(
                            outNode.getNode(), outNode.getType(),
                            end, Node.CONNECTION_DEFAULT_TYPE);
                }
            }
        }
    }

    public CompositeNode.NodeAndType getLinkedIncomingNode(String inType) {
        return inConnectionMap.get(inType);
    }

    public CompositeNode.NodeAndType internalGetLinkedIncomingNode(String inType) {
        return inConnectionMap.get(inType);
    }

    public CompositeNode.NodeAndType getLinkedOutgoingNode(String outType) {
        return outConnectionMap.get(outType);
    }

    public CompositeNode.NodeAndType internalGetLinkedOutgoingNode(String outType) {
        return outConnectionMap.get(outType);
    }

    public Map<String, CompositeNode.NodeAndType> getLinkedIncomingNodes() {
        return inConnectionMap;
    }

    public Map<String, CompositeNode.NodeAndType> getLinkedOutgoingNodes() {
        return outConnectionMap;
    }

    public void validateAddIncomingConnection(final String type, final Connection connection) {
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedIncomingNode(type);
        if (((Node) connection.getFrom()).getParentContainer() == this) {
            if (nodeAndType != null) {
                throw new IllegalArgumentException("Cannot link incoming connection type more than once: " + type);
            }
        } else {
            if (nodeAndType != null) {
                NodeImpl node = (NodeImpl) nodeAndType.getNode();
                if (node != null) {
                    node.validateAddIncomingConnection(nodeAndType.getType(), connection);
                }
            }
        }
    }

    public void addIncomingConnection(String type, Connection connection) {
        if (((Node) connection.getFrom()).getParentContainer() == this) {
            linkOutgoingConnections(connection.getFrom().getId(), connection.getFromType(), Node.CONNECTION_DEFAULT_TYPE);
        } else {
            super.addIncomingConnection(type, connection);
            CompositeNode.NodeAndType inNode = internalGetLinkedIncomingNode(type);
            if (inNode != null) {
                CompositeNodeStart start = new CompositeNodeStart(this, connection.getFrom(), type);
                internalAddNode(start);
                NodeImpl node = (NodeImpl) inNode.getNode();
                if (node != null) {
                    new ConnectionImpl(
                            start, Node.CONNECTION_DEFAULT_TYPE,
                            inNode.getNode(), inNode.getType());
                }
            }
        }
    }

    public void validateAddOutgoingConnection(final String type, final Connection connection) {
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedOutgoingNode(type);
        if (((Node) connection.getTo()).getParentContainer() == this) {
            if (nodeAndType != null) {
                throw new IllegalArgumentException("Cannot link outgoing connection type more than once: " + type);
            }
        } else {
            if (nodeAndType != null) {
                NodeImpl node = (NodeImpl) nodeAndType.getNode();
                if (node != null) {
                    ((NodeImpl) nodeAndType.getNode()).validateAddOutgoingConnection(nodeAndType.getType(), connection);
                }
            }
        }
    }

    public void addOutgoingConnection(String type, Connection connection) {
        if (((Node) connection.getTo()).getParentContainer() == this) {
            linkIncomingConnections(
                    Node.CONNECTION_DEFAULT_TYPE,
                    connection.getTo().getId(), connection.getToType());
        } else {
            super.addOutgoingConnection(type, connection);
            CompositeNode.NodeAndType outNode = internalGetLinkedOutgoingNode(type);
            if (outNode != null) {
                CompositeNodeEnd end = new CompositeNodeEnd(this, connection.getTo(), type);
                internalAddNode(end);
                NodeImpl node = (NodeImpl) outNode.getNode();
                if (node != null) {
                    new ConnectionImpl(
                            outNode.getNode(), outNode.getType(),
                            end, Node.CONNECTION_DEFAULT_TYPE);
                }
            }
        }
    }

    public void validateRemoveIncomingConnection(final String type, final Connection connection) {
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedIncomingNode(type);
        if (nodeAndType != null) {
            for (Connection inConnection : nodeAndType.getNode().getIncomingConnections(nodeAndType.getType())) {
                if (((CompositeNodeStart) inConnection.getFrom()).getInNodeId() == connection.getFrom().getId()) {
                    ((NodeImpl) nodeAndType.getNode()).validateRemoveIncomingConnection(nodeAndType.getType(), inConnection);
                    return;
                }
            }
            throw new IllegalArgumentException(
                    "Could not find internal incoming connection for node");
        }
    }

    public void removeIncomingConnection(String type, Connection connection) {
        super.removeIncomingConnection(type, connection);
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedIncomingNode(type);
        if (nodeAndType != null) {
            for (Connection inConnection : nodeAndType.getNode().getIncomingConnections(nodeAndType.getType())) {
                if (((CompositeNodeStart) inConnection.getFrom()).getInNodeId() == connection.getFrom().getId()) {
                    org.kie.api.definition.process.Node compositeNodeStart = inConnection.getFrom();
                    ((ConnectionImpl) inConnection).terminate();
                    internalRemoveNode(compositeNodeStart);
                    return;
                }
            }
            throw new IllegalArgumentException(
                    "Could not find internal incoming connection for node");
        }
    }

    public void validateRemoveOutgoingConnection(final String type, final Connection connection) {
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedOutgoingNode(type);
        if (nodeAndType != null) {
            for (Connection outConnection : nodeAndType.getNode().getOutgoingConnections(nodeAndType.getType())) {
                if (((CompositeNodeEnd) outConnection.getTo()).getOutNodeId() == connection.getTo().getId()) {
                    ((NodeImpl) nodeAndType.getNode()).validateRemoveOutgoingConnection(nodeAndType.getType(), outConnection);
                    return;
                }
            }
            throw new IllegalArgumentException(
                    "Could not find internal outgoing connection for node");
        }
    }

    public void removeOutgoingConnection(String type, Connection connection) {
        super.removeOutgoingConnection(type, connection);
        CompositeNode.NodeAndType nodeAndType = internalGetLinkedOutgoingNode(type);
        if (nodeAndType != null) {
            for (Connection outConnection : nodeAndType.getNode().getOutgoingConnections(nodeAndType.getType())) {
                if (((CompositeNodeEnd) outConnection.getTo()).getOutNodeId() == connection.getTo().getId()) {
                    org.kie.api.definition.process.Node compositeNodeEnd = outConnection.getTo();
                    ((ConnectionImpl) outConnection).terminate();
                    internalRemoveNode(compositeNodeEnd);
                    return;
                }
            }
            throw new IllegalArgumentException(
                    "Could not find internal outgoing connection for node");
        }
    }

    public boolean isCancelRemainingInstances() {
        return cancelRemainingInstances;
    }

    public void setCancelRemainingInstances(boolean cancelRemainingInstances) {
        this.cancelRemainingInstances = cancelRemainingInstances;
    }

    public boolean isAutoComplete() {
        return autoComplete;
    }

    public void setAutoComplete(boolean autoComplete) {
        this.autoComplete = autoComplete;
    }

    public static class NodeAndType implements Serializable {

        private static final long serialVersionUID = 510l;

        private NodeContainer nodeContainer;
        private long nodeId;
        private String type;
        private transient org.kie.api.definition.process.Node node;

        public NodeAndType(NodeContainer nodeContainer, long nodeId, String type) {
            if (type == null) {
                throw new IllegalArgumentException(
                        "Node or type may not be null!");
            }
            this.nodeId = nodeId;
            this.type = type;
            this.nodeContainer = nodeContainer;
        }

        public NodeAndType(org.kie.api.definition.process.Node node, String type) {
            if (node == null || type == null) {
                throw new IllegalArgumentException(
                        "Node or type may not be null!");
            }
            this.nodeId = node.getId();
            this.node = node;
            this.type = type;
        }

        public org.kie.api.definition.process.Node getNode() {
            if (node == null) {
                try {
                    node = nodeContainer.getNode(nodeId);
                } catch (IllegalArgumentException e) {
                    // unknown node id, returning null
                }
            }
            return node;
        }

        public long getNodeId() {
            return nodeId;
        }

        public String getType() {
            return type;
        }

        public boolean equals(Object o) {
            if (o instanceof NodeAndType) {
                return nodeId == ((NodeAndType) o).nodeId
                        && type.equals(((NodeAndType) o).type);
            }
            return false;
        }

        public int hashCode() {
            return 7 * (int) nodeId + 13 * type.hashCode();
        }

    }

    public static class CompositeNodeStart extends NodeImpl {

        private static final long serialVersionUID = 510l;

        private CompositeNode parentNode;
        private long inNodeId;
        private transient org.kie.api.definition.process.Node inNode;
        private String inType;

        public CompositeNodeStart(CompositeNode parentNode, org.kie.api.definition.process.Node outNode, String outType) {
            setName("Composite node start");
            this.inNodeId = outNode.getId();
            this.inNode = outNode;
            this.inType = outType;
            this.parentNode = parentNode;
            setMetaData("hidden", true);
        }

        public org.kie.api.definition.process.Node getInNode() {
            if (inNode == null) {
                inNode = ((NodeContainer) parentNode.getParentContainer()).internalGetNode(inNodeId);
            }
            return inNode;
        }

        public long getInNodeId() {
            return inNodeId;
        }

        public String getInType() {
            return inType;
        }

    }

    public static class CompositeNodeEnd extends NodeImpl {

        private static final long serialVersionUID = 510l;

        private CompositeNode parentNode;
        private long outNodeId;
        private transient org.kie.api.definition.process.Node outNode;
        private String outType;

        public CompositeNodeEnd(CompositeNode parentNode, org.kie.api.definition.process.Node outNode, String outType) {
            setName("Composite node end");
            this.outNodeId = outNode.getId();
            this.outNode = outNode;
            this.outType = outType;
            this.parentNode = parentNode;
            setMetaData("hidden", true);
        }

        public org.kie.api.definition.process.Node getOutNode() {
            if (outNode == null) {
                outNode = ((NodeContainer) parentNode.getParentContainer()).internalGetNode(outNodeId);
            }
            return outNode;
        }

        public long getOutNodeId() {
            return outNodeId;
        }

        public String getOutType() {
            return outType;
        }

    }

    @Override
    public String getVariableName() {
        return null;
    }

}

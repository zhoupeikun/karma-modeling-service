/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.modeling.alignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.modeling.common.ModelingParams;
import edu.isi.modeling.common.Namespaces;
import edu.isi.modeling.common.Prefixes;
import edu.isi.modeling.common.Uris;
import edu.isi.modeling.config.ModelingConfiguration;
import edu.isi.modeling.learner.SemanticTypeMapping;
import edu.isi.modeling.ontology.OntologyManager;
import edu.isi.modeling.rep.ColumnNode;
import edu.isi.modeling.rep.CompactLink;
import edu.isi.modeling.rep.CompactObjectPropertyLink;
import edu.isi.modeling.rep.CompactSubClassLink;
import edu.isi.modeling.rep.DefaultLink;
import edu.isi.modeling.rep.InternalNode;
import edu.isi.modeling.rep.Label;
import edu.isi.modeling.rep.LabeledLink;
import edu.isi.modeling.rep.LinkFrequency;
import edu.isi.modeling.rep.LinkPriorityComparator;
import edu.isi.modeling.rep.LinkStatus;
import edu.isi.modeling.rep.LinkType;
import edu.isi.modeling.rep.Node;
import edu.isi.modeling.rep.NodeType;
import edu.isi.modeling.rep.ObjectPropertyLink;
import edu.isi.modeling.rep.ObjectPropertyType;
import edu.isi.modeling.rep.SubClassLink;

public class GraphBuilder {

	static Logger logger = LoggerFactory.getLogger(GraphBuilder.class);

	protected DirectedWeightedMultigraph<Node, DefaultLink> graph;
	protected OntologyManager ontologyManager;
	
	private NodeIdFactory nodeIdFactory;
	
	private HashSet<String> visitedSourceTargetPairs; 

	// HashMaps
	
	private HashMap<String, Node> idToNodeMap;
	private HashMap<String, LabeledLink> idToLinkMap;

	private HashMap<String, Set<Node>> uriToNodesMap;
	private HashMap<String, Set<LabeledLink>> uriToLinksMap;
	
	private HashMap<NodeType, Set<Node>> typeToNodesMap;
	private HashMap<LinkType, Set<LabeledLink>> typeToLinksMap;

	private HashMap<LinkStatus, Set<LabeledLink>> statusToLinksMap;
	
	private HashMap<String, Set<String>> uriClosure;

	private Set<Node> forcedNodes;
	
	// To be used in matching semantic types with graph nodes
	private HashSet<String> modelIds;
	private HashMap<String, Integer> linkCountMap;
	private HashMap<String, Integer> nodeDataPropertyCount; // nodeId + dataPropertyUri --> count
	private HashMap<String, Set<Node>> nodeDataProperties; // nodeId + dataPropertyUri --> ColumnNode
	private HashMap<String, Set<LabeledLink>> nodeIncomingLinks;
	private HashMap<String, Set<LabeledLink>> nodeOutgoingLinks;
	private HashMap<String, Set<SemanticTypeMapping>> semanticTypeMatches; // nodeUri + dataPropertyUri --> SemanticType Mapping
	private HashMap<String, List<LabeledLink>> patternLinks;
	private int numberOfModelLinks = 0;

	// Constructor
	
	public GraphBuilder(OntologyManager ontologyManager, boolean addThingNode) { 
		
		this.ontologyManager = ontologyManager;
		this.nodeIdFactory = new NodeIdFactory();

		this.idToNodeMap = new HashMap<String, Node>();
		this.idToLinkMap = new HashMap<String, LabeledLink>();
		this.uriToNodesMap = new HashMap<String, Set<Node>>();
		this.uriToLinksMap = new HashMap<String, Set<LabeledLink>>();
		this.typeToNodesMap = new HashMap<NodeType, Set<Node>>();
		this.typeToLinksMap = new HashMap<LinkType, Set<LabeledLink>>();
		this.statusToLinksMap = new HashMap<LinkStatus, Set<LabeledLink>>();
		
		this.uriClosure = new HashMap<String, Set<String>>();

		this.graph = new DirectedWeightedMultigraph<Node, DefaultLink>(DefaultLink.class);
		
		this.visitedSourceTargetPairs = new HashSet<String>();
			
		this.modelIds = new HashSet<String>();
		this.linkCountMap = new HashMap<String, Integer>();
		this.nodeDataPropertyCount = new HashMap<String, Integer>();
		this.semanticTypeMatches = new HashMap<String, Set<SemanticTypeMapping>>();
		this.patternLinks = new HashMap<String, List<LabeledLink>>();

		this.nodeDataProperties= new HashMap<String,Set<Node>>(); 
		
		this.nodeIncomingLinks = new HashMap<String, Set<LabeledLink>>();
		this.nodeOutgoingLinks = new HashMap<String, Set<LabeledLink>>();
		
		this.forcedNodes = new HashSet<Node>();
		if (addThingNode) 
			this.initialGraph();
		
	}
	
	public GraphBuilder(OntologyManager ontologyManager, DirectedWeightedMultigraph<Node, DefaultLink> graph, boolean useOriginalWeights) {
		
		this(ontologyManager, false);
		if (graph == null)
			return;
		
		for (Node node : graph.vertexSet()) {
			
			this.addNode(node);
			// building NodeIdFactory
			if (node.getLabel() != null) {
				nodeIdFactory.getNodeId(node.getUri());
			}						
		}
		
		Node source;
		Node target;
		
		for (DefaultLink link : graph.edgeSet()) {
			
			source = link.getSource();
			target = link.getTarget();
			
			DefaultLink l = link.getCopy(link.getId());
			if (useOriginalWeights)
				this.addLink(source, target, l, link.getWeight());
			else
				this.addLink(source, target, l);
		}
		
		logger.debug("graph has been loaded.");
	}
	
	public NodeIdFactory getNodeIdFactory() {
		return nodeIdFactory;
	}

	public OntologyManager getOntologyManager() {
		return this.ontologyManager;
	}
	
	public DirectedWeightedMultigraph<Node, DefaultLink> getGraph() {
		return this.graph;
	}
	
	public void setGraph(DirectedWeightedMultigraph<Node, DefaultLink> graph) {
		this.graph = graph;
	}
	
	public HashMap<String, Node> getIdToNodeMap() {
		return idToNodeMap;
	}

	public HashMap<String, LabeledLink> getIdToLinkMap() {
		return idToLinkMap;
	}

	public HashMap<String, Set<Node>> getUriToNodesMap() {
		return uriToNodesMap;
	}

	public HashMap<String, Set<LabeledLink>> getUriToLinksMap() {
		return uriToLinksMap;
	}

	public HashMap<NodeType, Set<Node>> getTypeToNodesMap() {
		return typeToNodesMap;
	}

	public HashMap<LinkType, Set<LabeledLink>> getTypeToLinksMap() {
		return typeToLinksMap;
	}

	public HashMap<LinkStatus, Set<LabeledLink>> getStatusToLinksMap() {
		return statusToLinksMap;
	}
	
	public Set<String> getModelIds() {
		return Collections.unmodifiableSet(modelIds);
	}

	public HashMap<String, Integer> getLinkCountMap() {
		return linkCountMap;
	}
	
	public HashMap<String, Integer> getNodeDataPropertyCount() {
		return nodeDataPropertyCount;
	}

	public HashMap<String, Set<SemanticTypeMapping>> getSemanticTypeMatches() {
		return semanticTypeMatches;
	}

	public int getNumberOfModelLinks() {
		return numberOfModelLinks;
	}

	public HashMap<String, Set<Node>> getNodeDataProperties() {
		return nodeDataProperties;
	}

	public final HashMap<String, Set<LabeledLink>> getIncomingLinksMap() {
		return nodeIncomingLinks;
	}
	
	public final HashMap<String, Set<LabeledLink>> getOutgoingLinksMap() {
		return nodeOutgoingLinks;
	}
	
	public HashMap<String, List<LabeledLink>> getPatternLinks() {
		return patternLinks;
	}

	public void resetOntologyMaps() {
		String[] currentUris = this.uriClosure.keySet().toArray(new String[0]);
		this.uriClosure.clear();
		for (String uri : currentUris)
			computeUriClosure(uri);
	}

	public boolean addNodeAndUpdate(Node node) {
		return this.addNodeAndUpdate(node, null);
	}
	
	public boolean addNodeAndUpdate(Node node, Set<Node> addedNodes) {
		boolean result = addNode(node);
		if (!result) 
			return result;

		if (!ModelingConfiguration.getOntologyAlignment()) 
			return result;

		if (addedNodes == null) 
			addedNodes = new HashSet<Node>();
		addedNodes.add(node);
		if (node instanceof InternalNode) 
			addClosureAndUpdateLinks((InternalNode)node, addedNodes);
		
		return result;
	}
	
	public InternalNode copyNodeAndUpdate(Node node, boolean copyLinksToColumnNodes) {
		InternalNode copyNode = null;
		if (node instanceof InternalNode) {
			copyNode = this.copyNode((InternalNode)node, copyLinksToColumnNodes);
		} else {
			logger.error("only can copy an internal node");
			return null;
		}
		return copyNode;
	}
	
	private void addClosureAndUpdateLinks(InternalNode node, Set<Node> addedNodes) {
		
		if (addedNodes == null) addedNodes = new HashSet<Node>();
		if (node instanceof InternalNode) {

			long start = System.currentTimeMillis();
			float elapsedTimeSec;

			addedNodes.add(node);
			
			if (ModelingConfiguration.getNodeClosure()) {
				addNodeClosure(node, addedNodes);
			}
			
			long addNodesClosure = System.currentTimeMillis();
			elapsedTimeSec = (addNodesClosure - start)/1000F;
			logger.debug("time to add nodes closure: " + elapsedTimeSec);

			updateLinks();
			
			// if we consider the set of current nodes as S1 and the set of new added nodes as S2:
			// (*) the direction of all the subclass links between S1 and S2 is from S2 to S1
			//		This is because superclasses of all the nodes in S1 are already added to the graph. 
			// (*) the direction of all the object property links between S1 and S2 is from S1 to S2
			//		This is because all the nodes that are reachable from S1 are added to the graph before adding new nodes in S2.
			long updateLinks = System.currentTimeMillis();
			elapsedTimeSec = (updateLinks - addNodesClosure)/1000F;
			logger.debug("time to update links of the graph: " + elapsedTimeSec);
			
//			updateLinksFromThing();
			
//			long updateLinksFromThing = System.currentTimeMillis();
//			elapsedTimeSec = (updateLinksFromThing - updateLinks)/1000F;
//			logger.debug("time to update links to Thing (root): " + elapsedTimeSec);

			logger.debug("total number of nodes in graph: " + this.graph.vertexSet().size());
			logger.debug("total number of links in graph: " + this.graph.edgeSet().size());

		}
		
	}
	
	public void addClosureAndUpdateLinks(Set<InternalNode> internalNodes, Set<Node> addedNodes) {
		
		logger.debug("<enter");
		if (addedNodes == null) addedNodes = new HashSet<Node>();

		long start = System.currentTimeMillis();
		float elapsedTimeSec;

		if (internalNodes != null) {
			Node[] nodes = internalNodes.toArray(new Node[0]);
			for (Node node : nodes)
				if (this.idToNodeMap.containsKey(node.getId()))
					addNodeClosure(node, addedNodes);
		}

		long addNodesClosure = System.currentTimeMillis();
		elapsedTimeSec = (addNodesClosure - start)/1000F;
		logger.debug("time to add nodes closure: " + elapsedTimeSec);

		updateLinks();
		
		long updateLinks = System.currentTimeMillis();
		elapsedTimeSec = (updateLinks - addNodesClosure)/1000F;
		logger.debug("time to update links of the graph: " + elapsedTimeSec);
		
		logger.debug("total number of nodes in graph: " + this.graph.vertexSet().size());
		logger.debug("total number of links in graph: " + this.graph.edgeSet().size());

		logger.debug("exit>");		
	}
	
	public boolean addNode(Node node) {
		
		logger.debug("<enter");

		if (node == null) {
			logger.error("The node is null.");
			return false;
		}
		
		if (idToNodeMap.get(node.getId()) != null) {
			logger.debug("The node with id=" + node.getId() + " already exists in the graph.");
			return false;
		}
		
		if (node instanceof InternalNode) {
			String uri = node.getUri();
			Label label = this.ontologyManager.getUriLabel(uri);
			if (label == null) {
				logger.debug("The resource " + uri + " does not exist in the ontology.");
				return false;
			}
			node.getLabel().setNs(label.getNs());
			node.getLabel().setPrefix(label.getPrefix());
		}
		
		if(node.isForced())
			this.forcedNodes.add(node);
		
		this.graph.addVertex(node);
		
		this.idToNodeMap.put(node.getId(), node);
//		logger.info("Added in idToNodeMap:" + node.getId());
		Set<Node> nodesWithSameUri = uriToNodesMap.get(node.getUri());
		if (nodesWithSameUri == null) {
			nodesWithSameUri = new HashSet<Node>();
			uriToNodesMap.put(node.getUri(), nodesWithSameUri);
		}
		nodesWithSameUri.add(node);
		
		Set<Node> nodesWithSameType = typeToNodesMap.get(node.getType());
		if (nodesWithSameType == null) {
			nodesWithSameType = new HashSet<Node>();
			typeToNodesMap.put(node.getType(), nodesWithSameType);
		}
		nodesWithSameType.add(node);
		
		if (node.getModelIds() != null)
			this.modelIds.addAll(node.getModelIds());
					
		this.uriClosure.put(node.getUri(), null);

		logger.debug("exit>");		
		return true;
	}
	
	public InternalNode copyNode(InternalNode node, boolean copyLinksToColumnNodes) {
		
		if (node == null) {
			logger.error("input node is null");
			return null;
		}
		
		String id = this.nodeIdFactory.getNodeId(node.getUri());
		InternalNode newNode = new InternalNode(id, node.getLabel());
		
		if (!this.addNode(newNode)) {
			logger.error("could not add the new node " + newNode.getId());
			return null;
		}
		
		Node source , target;
		String newId;
		Set<DefaultLink> incomingLinks = this.getGraph().incomingEdgesOf(node);
		if (incomingLinks != null) {
			for (DefaultLink l : incomingLinks) {
				source = l.getSource();
				if (source instanceof ColumnNode) continue;
				target = newNode;
				newId = LinkIdFactory.getLinkId(l.getUri(), source.getId(), target.getId());
				DefaultLink copyLink = l.getCopy(newId);
				this.addLink(source, target, copyLink, l.getWeight());
			}
		}
		
		Set<DefaultLink> outgoingLinks = this.getGraph().outgoingEdgesOf(node);
		if (outgoingLinks != null) {
			for (DefaultLink l : outgoingLinks) {
				source = newNode;
				target = l.getTarget();
				if (!copyLinksToColumnNodes && target instanceof ColumnNode) continue; // skip links to column nodes
				newId = LinkIdFactory.getLinkId(l.getUri(), source.getId(), target.getId());
				DefaultLink copyLink = l.getCopy(newId);
				this.addLink(source, target, copyLink, l.getWeight());
			}
		}
		
		return newNode;

	}
	
	public Set<Node> getForcedNodes() {
		return this.forcedNodes;
	}

	protected boolean sourceIsConnectedToTarget(Node source, Node target) {
		return this.visitedSourceTargetPairs.contains(source.getId() + target.getId());
	}
	
	public boolean addLink(Node source, Node target, DefaultLink link, Double weight) {
		if (addLink(source, target, link)) {
			if (weight != null) changeLinkWeight(link, weight);
			return true;
		}
		return false;
	}
	
	public boolean addLink(Node source, Node target, DefaultLink link) {

		logger.debug("<enter");

		if (source == null || target == null || link == null) {
			logger.error("Source, target, and the link cannot be null.");
			return false;
		}
		
		if (this.idToLinkMap.containsKey(link.getId())) {
			logger.debug("The link with id=" + link.getId() + " already exists in the graph");
			return false;
		}
		
		if (!this.idToNodeMap.containsKey(source.getId())) {
			logger.debug("The link source " + source.getId() + " does not exist in the graph");
			return false;
		}

		if (!this.idToNodeMap.containsKey(target.getId())) {
			logger.debug("The link target " + target.getId() + " does not exist in the graph");
			return false;
		}

		if (link instanceof LabeledLink) {
			String uri = link.getUri();
			Label label = this.ontologyManager.getUriLabel(uri);
			if (label == null) {
				logger.debug("The resource " + uri + " does not exist in the ontology.");
				return false;
			}
			((LabeledLink)link).getLabel().setNs(label.getNs());
			((LabeledLink)link).getLabel().setPrefix(label.getPrefix());
		}
			
		this.graph.addEdge(source, target, link);
		
		this.visitedSourceTargetPairs.add(source.getId() + target.getId());
		
		double w = computeWeight(link);
		
		this.graph.setEdgeWeight(link, w);
				
		if (link instanceof CompactLink) {
			logger.debug("exit>");		
			return true;
		}
			
		// update the corresponding lists and hashmaps
		LabeledLink labeledLink = null;
//		if (link instanceof LabeledLink)
		labeledLink = (LabeledLink)link;
		
		this.idToLinkMap.put(labeledLink.getId(), labeledLink);
		
		Set<LabeledLink> linksWithSameUri = uriToLinksMap.get(labeledLink.getUri());
		if (linksWithSameUri == null) {
			linksWithSameUri = new HashSet<LabeledLink>();
			uriToLinksMap.put(labeledLink.getUri(), linksWithSameUri);
		}
		linksWithSameUri.add(labeledLink);
				
		Set<LabeledLink> linksWithSameType = typeToLinksMap.get(labeledLink.getType());
		if (linksWithSameType == null) {
			linksWithSameType = new HashSet<LabeledLink>();
			typeToLinksMap.put(labeledLink.getType(), linksWithSameType);
		}
		linksWithSameType.add(labeledLink);
		
		if (labeledLink.getStatus() != LinkStatus.Normal) {
			Set<LabeledLink> linksWithSameStatus = statusToLinksMap.get(labeledLink.getStatus());
			if (linksWithSameStatus == null) { 
				linksWithSameStatus = new HashSet<LabeledLink>();
				statusToLinksMap.put(labeledLink.getStatus(), linksWithSameStatus);
			}
		}

		Set<LabeledLink> inLinks = nodeIncomingLinks.get(target.getId());
		if(inLinks == null) inLinks = new HashSet<>();
		inLinks.add(labeledLink);
		nodeIncomingLinks.put(target.getId(), inLinks);
		
		Set<LabeledLink> outLinks = nodeOutgoingLinks.get(source.getId());
		if(outLinks == null) outLinks = new HashSet<>();
		outLinks.add(labeledLink);
		nodeOutgoingLinks.put(source.getId(), outLinks);
		
		
		if (source instanceof InternalNode && target instanceof ColumnNode) {

			String key = source.getId() + link.getUri();
			Integer count = this.nodeDataPropertyCount.get(key);
			if (count == null) this.nodeDataPropertyCount.put(key, 1);
			else this.nodeDataPropertyCount.put(key, count.intValue() + 1);
			
			Set<Node> dataPropertyColumnNodes = this.nodeDataProperties.get(key);
			if (dataPropertyColumnNodes == null) {
				dataPropertyColumnNodes = new HashSet<Node>();
				this.nodeDataProperties.put(key, dataPropertyColumnNodes);
			}
			dataPropertyColumnNodes.add(target);
			
			key = source.getUri() + link.getUri();
			Set<SemanticTypeMapping> SemanticTypeMappings = this.semanticTypeMatches.get(key);
			if (SemanticTypeMappings == null) {
				SemanticTypeMappings = new HashSet<SemanticTypeMapping>();
				this.semanticTypeMatches.put(key, SemanticTypeMappings);
			}
			SemanticTypeMappings.add(new SemanticTypeMapping(null, null, (InternalNode)source, labeledLink, (ColumnNode)target));
		}
				
		if (labeledLink.getModelIds() != null) {
			this.modelIds.addAll(labeledLink.getModelIds());
			this.numberOfModelLinks++;
		}
		
		this.updateLinkCountMap(link);
		
		logger.debug("exit>");		
		return true;
	}
	
	public void savePatternLink(LabeledLink l) {
		String key = l.getSource().getUri() + l.getUri() + l.getTarget().getUri();
		List<LabeledLink> links = this.patternLinks.get(key);
		if (links == null) {
			links = new LinkedList<LabeledLink>();
			this.patternLinks.put(key, links);
		}
		links.add(l);
	}
	
	private double computeWeight(DefaultLink link) {
		double w = 0.0;
		
		if (link instanceof LabeledLink && ((LabeledLink)link).getStatus() == LinkStatus.PreferredByUI)
			w = ModelingParams.PROPERTY_UI_PREFERRED_WEIGHT;
		
		if (link instanceof LabeledLink && 
				((LabeledLink)link).getModelIds() != null &&
				!((LabeledLink)link).getModelIds().isEmpty()) 
			w = ModelingParams.PATTERN_LINK_WEIGHT;

		if (link instanceof LabeledLink && ((LabeledLink)link).getStatus() == LinkStatus.ForcedByUser)
			w = ModelingParams.PROPERTY_USER_PREFERRED_WEIGHT;
		
		if (w != 0.0)
			return w;
		
		if (link instanceof ObjectPropertyLink && ((ObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.Direct)
			w = ModelingParams.PROPERTY_DIRECT_WEIGHT;
		else if (link instanceof CompactObjectPropertyLink && ((CompactObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.Direct)
			w = ModelingParams.PROPERTY_DIRECT_WEIGHT;
		else if (link instanceof ObjectPropertyLink && ((ObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.Indirect)
			w = ModelingParams.PROPERTY_INDIRECT_WEIGHT;
		else if (link instanceof CompactObjectPropertyLink && ((CompactObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.Indirect)
			w = ModelingParams.PROPERTY_INDIRECT_WEIGHT;
		else if (link instanceof ObjectPropertyLink && ((ObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithOnlyDomain)
			w = ModelingParams.PROPERTY_WITH_ONLY_DOMAIN_WEIGHT;
		else if (link instanceof CompactObjectPropertyLink && ((CompactObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithOnlyDomain)
			w = ModelingParams.PROPERTY_WITH_ONLY_DOMAIN_WEIGHT;
		else if (link instanceof ObjectPropertyLink && ((ObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithOnlyRange)
			w = ModelingParams.PROPERTY_WITH_ONLY_RANGE_WEIGHT;
		else if (link instanceof CompactObjectPropertyLink && ((CompactObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithOnlyRange)
			w = ModelingParams.PROPERTY_WITH_ONLY_RANGE_WEIGHT;
		else if (link instanceof ObjectPropertyLink && ((ObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithoutDomainAndRange)
			w = ModelingParams.PROPERTY_WITHOUT_DOMAIN_RANGE_WEIGHT;
		else if (link instanceof CompactObjectPropertyLink && ((CompactObjectPropertyLink)link).getObjectPropertyType() == ObjectPropertyType.WithoutDomainAndRange)
			w = ModelingParams.PROPERTY_WITHOUT_DOMAIN_RANGE_WEIGHT;
		else if (link instanceof SubClassLink)
			w = ModelingParams.SUBCLASS_WEIGHT;
		else if (link instanceof CompactSubClassLink)
			w = ModelingParams.SUBCLASS_WEIGHT;
		else 
			w = ModelingParams.PROPERTY_DIRECT_WEIGHT;
		
		return w;
	}
	
	public void changeLinkStatus(LabeledLink link, LinkStatus newStatus) {

		if (link == null) return;
		LabeledLink graphLink = this.getIdToLinkMap().get(link.getId()); // use the graph link, not the tree link sent by the alignment (steiner tree has a copy of the graph link)
		if (graphLink == null) return;
		
		LinkStatus oldStatus = graphLink.getStatus();
		if (newStatus == oldStatus)
			return;
		
		graphLink.setStatus(newStatus);
		link.setStatus(newStatus);
		this.changeLinkWeight(graphLink, computeWeight(graphLink));
		
		Set<LabeledLink> linksWithOldStatus = this.statusToLinksMap.get(oldStatus);
		if (linksWithOldStatus != null) linksWithOldStatus.remove(graphLink);

		if (newStatus == LinkStatus.Normal) // we don't need to index normal links 
			return;
		
		Set<LabeledLink> linksWithNewStatus = this.statusToLinksMap.get(newStatus);
		if (linksWithNewStatus == null) {
			linksWithNewStatus = new HashSet<LabeledLink>();
			statusToLinksMap.put(newStatus, linksWithNewStatus);
		}
		linksWithNewStatus.add(graphLink);
	}
	
	public void changeLinkWeight(DefaultLink link, double weight) {
		this.graph.setEdgeWeight(link, weight);
	}
	
	public boolean removeLink(DefaultLink link) {
		
		if (link == null) {
			logger.debug("The link is null.");
			return false;
		}
		
		if (link instanceof CompactLink)
			return false;

		if (idToLinkMap.get(link.getId()) == null) {
			logger.debug("The link with id=" + link.getId() + " does not exists in the graph.");
			return false;
		}
		
		logger.debug("removing the link " + link.getId() + "...");
		
		if (!this.graph.removeEdge(link))
			return false;

		// update hashmaps

		if (link instanceof LabeledLink) {
			this.idToLinkMap.remove(link.getId());
	
			Set<LabeledLink> linksWithSameUri = uriToLinksMap.get(link.getUri());
			if (linksWithSameUri != null) 
				linksWithSameUri.remove(link);
			
			Set<LabeledLink> linksWithSameType = typeToLinksMap.get(((LabeledLink)link).getType());
			if (linksWithSameType != null) 
				linksWithSameType.remove(link);
			
			Set<LabeledLink> linksWithSameStatus = statusToLinksMap.get(((LabeledLink)link).getStatus());
			if (linksWithSameStatus != null) 
				linksWithSameStatus.remove(link);
			
			Node source = link.getSource();
			Set<LabeledLink> sourceLinks = nodeOutgoingLinks.get(source.getId());
			if(sourceLinks != null)
				sourceLinks.remove(link);
			
			Node target = link.getTarget();
			Set<LabeledLink> targetLinks = nodeIncomingLinks.get(target.getId());
			if(targetLinks != null)
				targetLinks.remove(link);
		}
		
		return true;
	}
	
	public boolean removeNode(Node node) {
		
		if (node == null) {
			logger.error("The node is null");
			return false;
		}
		
		if (idToNodeMap.get(node.getId()) == null) {
			logger.error("The node with id=" + node.getId() + " does not exists in the graph.");
			return false;
		}

		logger.debug("removing the node " + node.getId() + "...");
		
		Set<DefaultLink> incomingLinks = this.graph.incomingEdgesOf(node);
		if (incomingLinks != null) {
			DefaultLink[] incomingLinksArray = incomingLinks.toArray(new DefaultLink[0]);
			for (DefaultLink inLink: incomingLinksArray) {
				this.removeLink(inLink);
			}
		}

		Set<DefaultLink> outgoingLinks = this.graph.outgoingEdgesOf(node);
		if (outgoingLinks != null) {
			DefaultLink[] outgoingLinksArray = outgoingLinks.toArray(new DefaultLink[0]);
			for (DefaultLink outLink: outgoingLinksArray) {
				this.removeLink(outLink);
			}
		}
		
		if(node.isForced())
			this.forcedNodes.remove(node);
		
		if (!this.graph.removeVertex(node))
			return false;
		
		// updating hashmaps
		
		this.idToNodeMap.remove(node.getId());
		
		Set<Node> nodesWithSameUri = uriToNodesMap.get(node.getUri());
		if (nodesWithSameUri != null) 
			nodesWithSameUri.remove(node);
		
		Set<Node> nodesWithSameType = typeToNodesMap.get(node.getType());
		if (nodesWithSameType != null) 
			nodesWithSameType.remove(node);
		
		logger.debug("total number of nodes in graph: " + this.graph.vertexSet().size());
		logger.debug("total number of links in graph: " + this.graph.edgeSet().size());
		
		return true;
	}
	
	public LinkFrequency getMoreFrequentLinkBetweenNodes(String sourceUri, String targetUri) {

		List<String> possibleLinksFromSourceToTarget = new ArrayList<String>();

		HashSet<String> objectPropertiesDirect;
		HashSet<String> objectPropertiesIndirect;
		HashSet<String> objectPropertiesWithOnlyDomain;
		HashSet<String> objectPropertiesWithOnlyRange;
		HashMap<String, Label> objectPropertiesWithoutDomainAndRange = 
				ontologyManager.getObjectPropertiesWithoutDomainAndRange();

		possibleLinksFromSourceToTarget.clear();

		objectPropertiesDirect = ontologyManager.getObjectPropertiesDirect(sourceUri, targetUri);
		if (objectPropertiesDirect != null) possibleLinksFromSourceToTarget.addAll(objectPropertiesDirect);

		objectPropertiesIndirect = ontologyManager.getObjectPropertiesIndirect(sourceUri, targetUri);
		if (objectPropertiesIndirect != null) possibleLinksFromSourceToTarget.addAll(objectPropertiesIndirect);

		objectPropertiesWithOnlyDomain = ontologyManager.getObjectPropertiesWithOnlyDomain(sourceUri);
		if (objectPropertiesWithOnlyDomain != null) possibleLinksFromSourceToTarget.addAll(objectPropertiesWithOnlyDomain);

		objectPropertiesWithOnlyRange = ontologyManager.getObjectPropertiesWithOnlyRange(targetUri);
		if (objectPropertiesWithOnlyRange != null) possibleLinksFromSourceToTarget.addAll(objectPropertiesWithOnlyRange);

		if (ontologyManager.isSubClass(sourceUri, targetUri, true)) 
			possibleLinksFromSourceToTarget.add(Uris.RDFS_SUBCLASS_URI);

		if (objectPropertiesWithoutDomainAndRange != null) {
			possibleLinksFromSourceToTarget.addAll(objectPropertiesWithoutDomainAndRange.keySet());
		}
		
//		Collection<String> userLinks = this.sourceToTargetLinks.get(sourceUri + "---" + targetUri);
//		if (userLinks != null) {
//			for (String s : userLinks)
//				possibleLinksFromSourceToTarget.add(s);
//		}

		String selectedLinkUri1 = null;
		int maxCount1 = 0;

		String selectedLinkUri2 = null;
		int maxCount2 = 0;

		String selectedLinkUri3 = null;
		int maxCount3 = 0;

		String selectedLinkUri4 = null;
		int maxCount4 = 0;

		String key;
		
		if (possibleLinksFromSourceToTarget != null  && possibleLinksFromSourceToTarget.size() > 0) {

			for (String linkUri : possibleLinksFromSourceToTarget) {
				key = "domain:" + sourceUri + ",link:" + linkUri + ",range:" + targetUri;
				Integer count1 = this.linkCountMap.get(key);
				if (count1 != null && count1.intValue() > maxCount1) {
					maxCount1 = count1.intValue();
					selectedLinkUri1 = linkUri;
				}
			}
			
			for (String linkUri : possibleLinksFromSourceToTarget) {
				key = "range:" + targetUri + ",link:" + linkUri ;
				Integer count2 = this.linkCountMap.get(key);
				if (count2 != null && count2.intValue() > maxCount2) {
					maxCount2 = count2.intValue();
					selectedLinkUri2 = linkUri;
				}
			}
			
			for (String linkUri : possibleLinksFromSourceToTarget) {
				key = "domain:" + sourceUri + ",link:" + linkUri;
				Integer count3 = this.linkCountMap.get(key);
				if (count3 != null && count3.intValue() > maxCount3) {
					maxCount3 = count3.intValue();
					selectedLinkUri3 = linkUri;
				}
			}

			for (String linkUri : possibleLinksFromSourceToTarget) {
				key = "link:" + linkUri;
				Integer count4 = this.linkCountMap.get(key);
				if (count4 != null && count4.intValue() > maxCount4) {
					maxCount4 = count4.intValue();
					selectedLinkUri4 = linkUri;
				}
			}

		} else {
			logger.error("Something is going wrong. There should be at least one possible object property between " +
					sourceUri + " and " + targetUri);
			return null;
		}
		
		String selectedLinkUri;
		int maxCount;
		int type;

		if (selectedLinkUri1 != null && selectedLinkUri1.trim().length() > 0) {
			selectedLinkUri = selectedLinkUri1;
			maxCount = maxCount1;
			type = 1; // match domain and link and range
		} else if (selectedLinkUri2 != null && selectedLinkUri2.trim().length() > 0) {
			selectedLinkUri = selectedLinkUri2;
			maxCount = maxCount2;
			type = 2; // match link and range
		} else if (selectedLinkUri3 != null && selectedLinkUri3.trim().length() > 0) {
			selectedLinkUri = selectedLinkUri3;
			maxCount = maxCount3;
			type = 3; // match domain and link
		} else if (selectedLinkUri4 != null && selectedLinkUri4.trim().length() > 0) {
			selectedLinkUri = selectedLinkUri4;
			maxCount = maxCount4;
			type = 4; // match link label
		} else {
			if (objectPropertiesDirect != null && objectPropertiesDirect.size() > 0) {
				selectedLinkUri = objectPropertiesDirect.iterator().next();
				type = 5;
			} else 	if (objectPropertiesIndirect != null && objectPropertiesIndirect.size() > 0) {
				selectedLinkUri = objectPropertiesIndirect.iterator().next();
				type = 6;
			} else 	if (objectPropertiesWithOnlyDomain != null && objectPropertiesWithOnlyDomain.size() > 0) {
				selectedLinkUri = objectPropertiesWithOnlyDomain.iterator().next();
				type = 7;
			} else 	if (objectPropertiesWithOnlyRange != null && objectPropertiesWithOnlyRange.size() > 0) {
				selectedLinkUri = objectPropertiesWithOnlyRange.iterator().next();;
				type = 8;
			} else if (ontologyManager.isSubClass(sourceUri, targetUri, true)) {
				selectedLinkUri = Uris.RDFS_SUBCLASS_URI;
				type = 9;
			} else {	// if (objectPropertiesWithoutDomainAndRange != null && objectPropertiesWithoutDomainAndRange.keySet().size() > 0) {
				selectedLinkUri = new ArrayList<String>(objectPropertiesWithoutDomainAndRange.keySet()).get(0);
				type = 10;
			}

			maxCount = 0;
		} 
		
		LinkFrequency lf = new LinkFrequency(selectedLinkUri, type, maxCount);
		
		return lf;
		
	}
		
	private void initialGraph() {
		
		logger.debug("<enter");
		// Add Thing to the Graph 
		if (ModelingConfiguration.getThingNode()) {
			String id = nodeIdFactory.getNodeId(Uris.THING_URI);
			Label label = new Label(Uris.THING_URI, Namespaces.OWL, Prefixes.OWL);
			Node thing = new InternalNode(id, label);
			addNode(thing);
		}
		
		logger.debug("exit>");
	}

	private void updateLinkCountMap(DefaultLink link) {

		String key, sourceUri, targetUri, linkUri;
		Integer count;
		Node source, target;
		
		source = link.getSource();
		target = link.getTarget();
		
		sourceUri = source.getUri();
		targetUri = target.getUri();
		linkUri = link.getUri();

//		if (link instanceof DataPropertyLink) return;

		if (target instanceof InternalNode) {
			key = "domain:" + sourceUri + ",link:" + linkUri + ",range:" + targetUri;
			count = this.linkCountMap.get(key);
			if (count == null) this.linkCountMap.put(key, 1);
			else this.linkCountMap.put(key, count.intValue() + 1);
			
			key = "range:" + targetUri + ",link:" + linkUri ;
			count = this.linkCountMap.get(key);
			if (count == null) this.linkCountMap.put(key, 1);
			else this.linkCountMap.put(key, count.intValue() + 1);
		}
		
		key = "domain:" + sourceUri + ",link:" + linkUri;
		count = this.linkCountMap.get(key);
		if (count == null) this.linkCountMap.put(key, 1);
		else this.linkCountMap.put(key, count.intValue() + 1);

		key = "link:" + linkUri;
		count = this.linkCountMap.get(key);
		if (count == null) this.linkCountMap.put(key, 1);
		else this.linkCountMap.put(key, count.intValue() + 1);
	}

	private HashSet<String> getUriDirectConnections(String uri) {
		
		HashSet<String> uriDirectConnections = new HashSet<String>();
		
		HashSet<String> opDomainClasses = null;
		HashMap<String, Label> superClasses = null;

		// We don't need to add subclasses of each class separately.
		// The only place in which we add children is where we are looking for domain class of a property.
		// In this case, in addition to the domain class, we add all of its children too.
		
		opDomainClasses = ontologyManager.getDomainsGivenRange(uri, true);
		superClasses = ontologyManager.getSuperClasses(uri, false);

		if (opDomainClasses != null)
			uriDirectConnections.addAll(opDomainClasses);
		if (superClasses != null)
			uriDirectConnections.addAll(superClasses.keySet());

		return uriDirectConnections;
	}

	private Set<String> computeUriClosure(String uri) {
		
		Set<String> closure = this.uriClosure.get(uri);
		if (closure != null) 
			return closure;
	
		closure = new HashSet<String>();
		List<String> closedList = new ArrayList<String>();
		HashMap<String, Set<String>> dependentUrisMap = new HashMap<String, Set<String>>();
		computeUriClosureRecursive(uri, closure, closedList, dependentUrisMap);
		if (closedList.contains(uri) && !closure.contains(uri))
			closure.add(uri);
		
		int count = 1;
		while (count != 0) {
			count = 0;
			for (String s : dependentUrisMap.keySet()) {
				Set<String> temp = this.uriClosure.get(s);
				Set<String> dependentUris = dependentUrisMap.get(s);
				for (String ss : dependentUris) {
					if (!temp.contains(ss)) { temp.add(ss); count++;}
					if (this.uriClosure.get(ss) != null) {
						String[] cc = this.uriClosure.get(ss).toArray(new String[0]);
						for (String c : cc) {
							if (!temp.contains(c)) {temp.add(c); count++;}
						}
					}
						
				}
			}
		}

		return closure;
	}

	private void computeUriClosureRecursive(String uri, Set<String> closure, 
			List<String> closedList, HashMap<String, Set<String>> dependentUrisMap) {
		
		logger.debug("<enter");
		
		closedList.add(uri);
		Set<String> currentClosure = this.uriClosure.get(uri);
		if (currentClosure != null) {
			closure.addAll(currentClosure);
			return;
		}

		HashSet<String> uriDirectConnections = getUriDirectConnections(uri);
		if (uriDirectConnections.size() == 0) {
			this.uriClosure.put(uri, new HashSet<String>());
		} else {
			for (String c : uriDirectConnections) {
				if (closedList.contains(c)) {
					Set<String> dependentUris = dependentUrisMap.get(uri);
					if (dependentUris == null) {
						dependentUris = new HashSet<String>();
						dependentUrisMap.put(uri, dependentUris);
					}
					if (!dependentUris.contains(c)) dependentUris.add(c);
					continue;
				}
				if (!closure.contains(c)) closure.add(c);
				if (!closedList.contains(c)) closedList.add(c);
				Set<String> localClosure = new HashSet<String>();
				computeUriClosureRecursive(c, localClosure, closedList, dependentUrisMap);
				for (String s : localClosure)
					if (!closure.contains(s)) closure.add(s);
			}
			this.uriClosure.put(uri, closure);
		}
		
		logger.debug("exit>");
	}
	
	public List<Node> getNodeClosure(Node node) {
		
		List<Node> nodeClosure = new ArrayList<Node>();
		if (node instanceof ColumnNode) return nodeClosure;
		
		String uri = node.getUri();
		Set<String> closure = this.uriClosure.get(uri); 
		if (closure == null) {  // the closure has already been computed.
			closure = computeUriClosure(uri);
		} 
		for (String s : closure) {
			Set<Node> nodes = uriToNodesMap.get(s);
			if (nodes != null) nodeClosure.addAll(nodes);
		}
		return nodeClosure;
	}
	
	/**
	 * returns all the new nodes that should be added to the graph due to adding the input node
	 * @param node
	 * @param closure: contains all the nodes that are connected to the input node 
	 * by ObjectProperty or SubClass links
	 * @return
	 */
	private void addNodeClosure(Node node, Set<Node> newAddedNodes) {

		logger.debug("<enter");
		
		if (newAddedNodes == null) newAddedNodes = new HashSet<Node>();
		
		String uri = node.getUri();
		if (this.uriClosure.get(uri) != null) // the closure is already computed and added to the graph.
			return;

		Set<String> uriClosure = computeUriClosure(uri);

		for (String c : uriClosure) {
			Set<Node> nodesOfSameUri = this.uriToNodesMap.get(c);
			if (nodesOfSameUri == null || nodesOfSameUri.size() == 0) { // the internal node is not added to the graph before
				Node nn = new InternalNode(nodeIdFactory.getNodeId(c), 
						ontologyManager.getUriLabel(c));
				if (addNode(nn)) newAddedNodes.add(nn);
			} 
		}
		
		logger.debug("exit>");
	}
	
	private void updateLinks() {
		
		logger.debug("<enter");
		
		Set<Node> nodeSet = this.typeToNodesMap.get(NodeType.InternalNode);
		if (nodeSet == null || nodeSet.isEmpty())
			return;
		
		List<Node> nodes = new ArrayList<Node>(nodeSet);
		logger.debug("number of internal nodes: " + nodes.size());
		
		Node source;
		Node target;
		String sourceUri;
		String targetUri;

		String id1 = null, id2 = null;
		
		for (int i = 0; i < nodes.size(); i++) {
			
			Node n1 = nodes.get(i);
			for (int j = i+1; j < nodes.size(); j++) {

				Node n2 = nodes.get(j);

				if (n1.equals(n2))
					continue;

				if (this.visitedSourceTargetPairs.contains(n1.getId() + n2.getId()))
					continue;
				if (this.visitedSourceTargetPairs.contains(n2.getId() + n1.getId()))
					continue;
				
				source = n1;
				target = n2;

				sourceUri = source.getUri();
				targetUri = target.getUri();

				id1 = LinkIdFactory.getLinkId(Uris.DEFAULT_LINK_URI, source.getId(), target.getId());
				id2 = LinkIdFactory.getLinkId(Uris.DEFAULT_LINK_URI, target.getId(), source.getId());
				CompactLink link = null; 

//				boolean connected = false;
				boolean sourceConnectedToTarget = false;
				boolean targetConnectedToSource = false;
				
				// order of adding the links is based on the ascending sort of their weight value
				

				if (ModelingConfiguration.getPropertiesDirect()) {
					if (this.ontologyManager.isConnectedByDirectProperty(sourceUri, targetUri)) {
						logger.debug( sourceUri + " and " + targetUri + " are connected by a direct object property.");
						link = new CompactObjectPropertyLink(id1, ObjectPropertyType.Direct);
						addLink(source, target, link);
						sourceConnectedToTarget = true;
					}
					if (this.ontologyManager.isConnectedByDirectProperty(targetUri, sourceUri)) {
						logger.debug( targetUri + " and " + sourceUri + " are connected by a direct object property.");
						link = new CompactObjectPropertyLink(id2, ObjectPropertyType.Direct);
						addLink(target, source, link);
						targetConnectedToSource = true;
					}				
				}
				

				if (ModelingConfiguration.getPropertiesIndirect()) {
					if (!sourceConnectedToTarget && this.ontologyManager.isConnectedByIndirectProperty(sourceUri, targetUri)) { 
						logger.debug( sourceUri + " and " + targetUri + " are connected by an indirect object property.");
						link = new CompactObjectPropertyLink(id1, ObjectPropertyType.Indirect);
						addLink(source, target, link);
						sourceConnectedToTarget = true;
					}
					if (!targetConnectedToSource && this.ontologyManager.isConnectedByIndirectProperty(targetUri, sourceUri)) { 
						logger.debug( targetUri + " and " + sourceUri + " are connected by an indirect object property.");
						link = new CompactObjectPropertyLink(id2, ObjectPropertyType.Indirect);
						addLink(target, source, link);
						targetConnectedToSource = true;
					}
				}
				

				if (ModelingConfiguration.getPropertiesWithOnlyRange()) {
					if (!sourceConnectedToTarget && this.ontologyManager.isConnectedByDomainlessProperty(sourceUri, targetUri)) { 
						logger.debug( sourceUri + " and " + targetUri + " are connected by an object property whose range is " + sourceUri + " or " + targetUri);
						link = new CompactObjectPropertyLink(id1, ObjectPropertyType.WithOnlyRange);
						addLink(source, target, link);
						sourceConnectedToTarget = true;
					}

					if (!targetConnectedToSource && this.ontologyManager.isConnectedByDomainlessProperty(targetUri, sourceUri)) { 
						logger.debug( targetUri + " and " + sourceUri + " are connected by an object property whose range is " + targetUri + " or " + sourceUri);
						link = new CompactObjectPropertyLink(id2, ObjectPropertyType.WithOnlyRange);
						addLink(target, source, link);
						targetConnectedToSource = true;
					}
				}
				
				if (ModelingConfiguration.getPropertiesWithOnlyDomain()) {
					if (!sourceConnectedToTarget && this.ontologyManager.isConnectedByRangelessProperty(sourceUri, targetUri)) { 
						logger.debug( sourceUri + " and " + targetUri + " are connected by an object property whose domain is " + sourceUri + " or " + targetUri);
						link = new CompactObjectPropertyLink(id1, ObjectPropertyType.WithOnlyDomain);
						addLink(source, target, link);
						sourceConnectedToTarget = true;
					}
					if (!targetConnectedToSource && this.ontologyManager.isConnectedByRangelessProperty(targetUri, sourceUri)) { 
						logger.debug( targetUri + " and " + sourceUri + " are connected by an object property whose domain is " + targetUri + " or " + sourceUri);
						link = new CompactObjectPropertyLink(id2, ObjectPropertyType.WithOnlyDomain);
						addLink(target, source, link);
						targetConnectedToSource = true;
					}
				}
				
				if (ModelingConfiguration.getPropertiesWithoutDomainRange() && !sourceConnectedToTarget && !targetConnectedToSource) {
					if (this.ontologyManager.isConnectedByDomainlessAndRangelessProperty(sourceUri, targetUri)) {// ||
	//						this.ontologyManager.isConnectedByDomainlessAndRangelessProperty(targetUri, sourceUri)) { 
						link = new CompactObjectPropertyLink(id1, ObjectPropertyType.WithoutDomainAndRange);
						addLink(source, target, link);
						link = new CompactObjectPropertyLink(id2, ObjectPropertyType.WithoutDomainAndRange);
						addLink(target, source, link);
						sourceConnectedToTarget = true;
						targetConnectedToSource = true;
					}
				}

				if (!sourceConnectedToTarget && ModelingConfiguration.getPropertiesSubClass()) {
					if (this.ontologyManager.isSubClass(sourceUri, targetUri, false)) {
						logger.debug( sourceUri + " and " + targetUri + " are connected by a subClassOf relation.");
						link = new CompactSubClassLink(id1);
						addLink(source, target, link);
						sourceConnectedToTarget = true;
					}
					if (!targetConnectedToSource && this.ontologyManager.isSubClass(targetUri, sourceUri, false)) {
						logger.debug( targetUri + " and " + sourceUri + " are connected by a subClassOf relation.");
						link = new CompactSubClassLink(id2);
						addLink(target, source, link);
						targetConnectedToSource = true;
					}
				}
				
				if (!sourceConnectedToTarget && !targetConnectedToSource) {
					this.visitedSourceTargetPairs.add(n1.getId() + n2.getId());
					logger.debug("did not put a link between (" + n1.getId() + ", " + n2.getId() + ")");
				}
			}
		}

		logger.debug("exit>");
	}
	
	public List<LabeledLink> getPossibleLinks(String sourceId, String targetId) {
		return getPossibleLinks(sourceId, targetId, null, null);
	}
	
	public List<LabeledLink> getPossibleLinks(String sourceId, String targetId, LinkType linkType, 
			ObjectPropertyType objectProertyType) {
		
		List<LabeledLink> sortedLinks = new ArrayList<LabeledLink>();

		Node source = this.idToNodeMap.get(sourceId);
		Node target = this.idToNodeMap.get(targetId);
		
		if (source == null || target == null) {
			logger.debug("Cannot find source or target in the graph.");
			return sortedLinks;
		}

		if (source instanceof ColumnNode || target instanceof ColumnNode) {
			logger.debug("Source or target is a column node.");
			return sortedLinks;
		}

		String sourceUri, targetUri;
		sourceUri = source.getUri();
		targetUri = target.getUri();

		HashSet<String> links = 
				this.ontologyManager.getPossibleUris(sourceUri, targetUri);

		String id;
		Label label;
		ObjectPropertyType linkObjectPropertyType; 

		for (String uri : links) {
			
			if (linkType == LinkType.SubClassLink && !uri.equalsIgnoreCase(Uris.RDFS_SUBCLASS_URI))
				continue;
			
			if (linkType == LinkType.ObjectPropertyLink && uri.equalsIgnoreCase(Uris.RDFS_SUBCLASS_URI))
				continue;
			
			linkObjectPropertyType = ontologyManager.getObjectPropertyType(sourceUri, targetUri, uri);
			if (linkType == LinkType.ObjectPropertyLink && 
					objectProertyType != null && 
					objectProertyType != ObjectPropertyType.None &&
					objectProertyType != linkObjectPropertyType)
				continue;
			
			id = LinkIdFactory.getLinkId(uri, sourceId, targetId);
			label = new Label(ontologyManager.getUriLabel(uri));
			
			LabeledLink newLink;
			if (uri.equalsIgnoreCase(Uris.RDFS_SUBCLASS_URI))
				newLink = new SubClassLink(id);
			else
				newLink = new ObjectPropertyLink(id, label, linkObjectPropertyType);
			
			sortedLinks.add(newLink);
		}
		
		Collections.sort(sortedLinks, new LinkPriorityComparator());
		
		return sortedLinks;
	}
	
	public List<LabeledLink> getLinks(String sourceId, String targetId) {
		
		List<LabeledLink> links  = new LinkedList<LabeledLink>();

		Node source = this.getIdToNodeMap().get(sourceId);
		if (source == null) return links;

		Node target = this.getIdToNodeMap().get(targetId);
		if (target == null) return links;

		Set<DefaultLink> allLinks = this.getGraph().getAllEdges(source, target);
		if (allLinks != null) {
			for (DefaultLink l : allLinks) {
				if (l instanceof LabeledLink) {
					links.add((LabeledLink)l);
				}
			}
		}
		
		return links;
	}

	public List<LabeledLink> getIncomingLinks(String nodeId) {
		
		List<LabeledLink> incomingLinks  = new LinkedList<LabeledLink>();

		Node node = this.getIdToNodeMap().get(nodeId);
		if (node == null) return incomingLinks;
		
		Set<DefaultLink> allIncomingLinks = this.getGraph().incomingEdgesOf(node);
		if (allIncomingLinks != null) {
			for (DefaultLink l : allIncomingLinks) {
				if (l instanceof LabeledLink) {
					incomingLinks.add((LabeledLink)l);
				}
			}
		}
		
		return incomingLinks;
	}
	
	public List<LabeledLink> getOutgoingLinks(String nodeId) {
		
		List<LabeledLink> outgoingLinks  = new LinkedList<LabeledLink>();

		Node node = this.getIdToNodeMap().get(nodeId);
		if (node == null) return outgoingLinks;
		
		Set<DefaultLink> allOutgoingLinks = this.getGraph().outgoingEdgesOf(node);
		if (allOutgoingLinks != null) {
			for (DefaultLink l : allOutgoingLinks) {
				if (l instanceof LabeledLink) {
					outgoingLinks.add((LabeledLink)l);
				}
			}
		}
		
		return outgoingLinks;
	}


}
package cn.bitloom.autiva.agentic.flow.graph;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The type Graph.
 *
 * @author ningyu
 */
public final class Graph {

    private final Map<String, VertexNode> vertexMap;

    private Graph(){
        this.vertexMap=new HashMap<>();
    }

    /**
     * Add vertex graph.
     *
     * @param id    the id
     * @param type  the type
     * @param param the param
     */
    public void addVertex(String id, String type, VertexParam param) {
        VertexNode node = new VertexNode();
        node.setId(id);
        node.setType(type);
        node.setParams(param);
        vertexMap.put(id, node);
    }

    /**
     * Add edge graph.
     *
     * @param tailId the tail id
     * @param headId the head id
     * @param param  the param
     */
    public void addArc(String tailId, String headId, ArcParam param) {
        VertexNode tail = vertexMap.get(tailId);
        VertexNode head = vertexMap.get(headId);

        if (tail == null || head == null) {
            throw new IllegalArgumentException("节点不存在: " + tailId + " -> " + headId);
        }

        ArcNode arc = new ArcNode();
        arc.setTailVexId(tailId);
        arc.setHeadVexId(headId);
        arc.setParms(param);

        // 出边插入头部
        arc.setTailLink(tail.getFirstOut());
        tail.setFirstOut(arc);

        // 入边插入头部
        arc.setHeadLink(head.getFirstIn());
        head.setFirstIn(arc);

    }

    /**
     * Builder graph.
     *
     * @return the graph
     */
    public static Graph create() {
        return new Graph();
    }

    /**
     * Create graph from json graph.
     *
     * @param json the json
     * @return the graph
     */
    public static Graph createGraphFromJson(JSONObject json) {
        Graph graph = Graph.create();

        // 添加节点
        JSONArray nodes = json.getJSONArray("nodeList");
        for (Object obj : nodes) {
            JSONObject nodeJson = (JSONObject) obj;
            VertexParam param = nodeJson.getObject("params", VertexParam.class);
            graph.addVertex(
                    nodeJson.getString("id"),
                    nodeJson.getString("type"),
                    param
            );
        }

        // 添加边
        JSONArray arcs = json.getJSONArray("arcList");
        for (Object obj : arcs) {
            JSONObject arcJson = (JSONObject) obj;
            ArcParam param = arcJson.getObject("params", ArcParam.class);
            graph.addArc(
                    arcJson.getString("tailVexId"),
                    arcJson.getString("headVexId"),
                    param
            );
        }

        return graph;
    }

    /**
     * 获取节点
     *
     * @param id the id
     * @return the vertex
     */
    public VertexNode getVertex(String id) {
        return vertexMap.get(id);
    }

    /**
     * 获取所有出边（从 tail 出发）
     *
     * @param tailId the tail id
     * @return the out edges
     */
    public List<ArcNode> getOutArc(String tailId) {
        VertexNode node = vertexMap.get(tailId);
        List<ArcNode> list = new ArrayList<>();
        for (ArcNode arc = node.getFirstOut(); arc != null; arc = arc.getTailLink()) {
            list.add(arc);
        }
        return list;
    }

    /**
     * 获取所有入边（指向 head）
     *
     * @param headId the head id
     * @return the in edges
     */
    public List<ArcNode> getInArc(String headId) {
        VertexNode node = vertexMap.get(headId);
        List<ArcNode> list = new ArrayList<>();
        for (ArcNode arc = node.getFirstIn(); arc != null; arc = arc.getHeadLink()) {
            list.add(arc);
        }
        return list;
    }

    /**
     * 获取所有邻接节点（出边目标）
     *
     * @param tailId the tail id
     * @return the adjacent vertices
     */
    public List<VertexNode> getAdjacentVertex(String tailId) {
        return this.getOutArc(tailId).stream()
                .map(arc -> vertexMap.get(arc.getHeadVexId()))
                .collect(Collectors.toList());
    }


    /**
     * 找出所有入度为 0 的节点（工作流起点）
     *
     * @return the list
     */
    public List<VertexNode> getRootVertex() {
        Set<String> allHeads = vertexMap.values().stream()
                .flatMap(v -> this.getOutArc(v.getId()).stream())
                .map(ArcNode::getHeadVexId)
                .collect(Collectors.toSet());

        return vertexMap.values().stream()
                .filter(v -> !allHeads.contains(v.getId()))
                .collect(Collectors.toList());
    }

}

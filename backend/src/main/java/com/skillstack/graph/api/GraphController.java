package com.skillstack.graph.api;

import com.skillstack.graph.application.GraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GraphController {
    private final GraphService graph;

    public GraphController(GraphService graph) { this.graph = graph; }

    @GetMapping("/graph")
    public GraphDtos.GraphResponse graph(Authentication authentication) { return graph.graph(authentication.getName()); }

    @GetMapping("/nodes/{id}")
    public GraphDtos.NodeResponse node(Authentication authentication, @PathVariable UUID id) { return graph.getNode(authentication.getName(), id); }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public GraphDtos.NodeResponse createNode(Authentication authentication, @Valid @RequestBody GraphDtos.CreateNodeRequest request) {
        return graph.createNode(authentication.getName(), request);
    }

    @PatchMapping("/nodes/{id}")
    public GraphDtos.NodeResponse updateNode(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody GraphDtos.UpdateNodeRequest request) {
        return graph.updateNode(authentication.getName(), id, request);
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNode(Authentication authentication, @PathVariable UUID id) { graph.deleteNode(authentication.getName(), id); }

    @PostMapping("/edges")
    @ResponseStatus(HttpStatus.CREATED)
    public GraphDtos.EdgeResponse createEdge(Authentication authentication, @Valid @RequestBody GraphDtos.CreateEdgeRequest request) {
        return graph.createEdge(authentication.getName(), request);
    }

    @PatchMapping("/edges/{id}")
    public GraphDtos.EdgeResponse updateEdge(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody GraphDtos.UpdateEdgeRequest request) {
        return graph.updateEdge(authentication.getName(), id, request);
    }

    @DeleteMapping("/edges/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEdge(Authentication authentication, @PathVariable UUID id) { graph.deleteEdge(authentication.getName(), id); }

    @GetMapping("/search")
    public GraphDtos.SearchResponse search(Authentication authentication, @RequestParam String q) { return graph.search(authentication.getName(), q); }
}

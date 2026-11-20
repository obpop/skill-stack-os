package com.skillstack.graph.persistence;

import com.skillstack.graph.domain.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphNodeRepository extends JpaRepository<GraphNode, UUID> {
    List<GraphNode> findAllByOwnerSubjectOrderByCreatedAtAsc(String subject);
    Optional<GraphNode> findByIdAndOwnerSubject(UUID id, String subject);
    Optional<GraphNode> findByOwnerSubjectAndExternalKey(String subject, String externalKey);
    long countByOwnerSubject(String subject);

    @Query(value = """
        select n.* from graph_node n
        join app_user u on u.id = n.owner_id
        left join project p on p.graph_node_id = n.id and p.owner_id = n.owner_id
        where u.subject = :subject and (
            strpos(lower(n.title), lower(:query)) > 0 or
            strpos(lower(coalesce(n.description, '')), lower(:query)) > 0 or
            strpos(lower(n.node_type), lower(:query)) > 0 or
            strpos(lower(n.domain), lower(:query)) > 0 or
            strpos(lower(n.status), lower(:query)) > 0 or
            strpos(lower(coalesce(p.title, '')), lower(:query)) > 0 or
            strpos(lower(coalesce(p.description, '')), lower(:query)) > 0 or
            strpos(lower(coalesce(p.project_status, '')), lower(:query)) > 0 or
            strpos(lower(coalesce(p.repository_url, '')), lower(:query)) > 0 or
            strpos(lower(coalesce(p.live_url, '')), lower(:query)) > 0
        )
        order by n.importance desc, n.title asc
        """, nativeQuery = true)
    List<GraphNode> search(@Param("subject") String subject, @Param("query") String query);
}

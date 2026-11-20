package com.skillstack.user;

import com.skillstack.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccountDataExportService {
    private static final String EXPORT_SQL = """
            SELECT jsonb_build_object(
                'formatVersion', 2,
                'exportedAt', now(),
                'account', to_jsonb(u) - 'id',
                'profile', (SELECT to_jsonb(p) - 'user_id' FROM user_profile p WHERE p.user_id = u.id),
                'nodes', COALESCE((SELECT jsonb_agg(to_jsonb(n) - 'owner_id' ORDER BY n.created_at)
                    FROM graph_node n WHERE n.owner_id = u.id), '[]'::jsonb),
                'edges', COALESCE((SELECT jsonb_agg(to_jsonb(e) - 'owner_id' ORDER BY e.created_at)
                    FROM graph_edge e WHERE e.owner_id = u.id), '[]'::jsonb),
                'projects', COALESCE((SELECT jsonb_agg(to_jsonb(p) - 'owner_id' ORDER BY p.created_at)
                    FROM project p WHERE p.owner_id = u.id), '[]'::jsonb),
                'proof', COALESCE((SELECT jsonb_agg(to_jsonb(p) - 'owner_id' ORDER BY p.created_at)
                    FROM proof_of_work p WHERE p.owner_id = u.id), '[]'::jsonb),
                'intersectionDecisions', COALESCE((SELECT jsonb_agg(to_jsonb(i) - 'owner_id' ORDER BY i.created_at)
                    FROM intersection_decision i WHERE i.owner_id = u.id), '[]'::jsonb),
                'imports', COALESCE((SELECT jsonb_agg(to_jsonb(i) - 'owner_id' ORDER BY i.created_at)
                    FROM import_job i WHERE i.owner_id = u.id), '[]'::jsonb),
                'recommendations', COALESCE((SELECT jsonb_agg(to_jsonb(r) - 'owner_id' ORDER BY r.generated_at)
                    FROM recommendation_result r WHERE r.owner_id = u.id), '[]'::jsonb),
                'aiOperations', COALESCE((SELECT jsonb_agg(to_jsonb(a) - 'owner_id' ORDER BY a.created_at)
                    FROM ai_operation a WHERE a.owner_id = u.id), '[]'::jsonb),
                'backgroundJobs', COALESCE((SELECT jsonb_agg((to_jsonb(j) - 'owner_id') - 'lease_owner' ORDER BY j.created_at)
                    FROM background_job j WHERE j.owner_id = u.id), '[]'::jsonb),
                'timeline', COALESCE((SELECT jsonb_agg(to_jsonb(t) - 'owner_id' ORDER BY t.created_at)
                    FROM skill_timeline_event t WHERE t.owner_id = u.id), '[]'::jsonb),
                'publication', (SELECT to_jsonb(pp) - 'owner_id' FROM public_profile pp WHERE pp.owner_id = u.id),
                'publicShares', COALESCE((SELECT jsonb_agg((to_jsonb(ps) - 'token_hash') - 'public_profile_id' ORDER BY ps.created_at)
                    FROM public_profile_share ps
                    JOIN public_profile pp ON pp.id = ps.public_profile_id
                    WHERE pp.owner_id = u.id), '[]'::jsonb)
            )::text
            FROM app_user u
            WHERE u.subject = :subject
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AccountDataExportService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonNode export(String subject) {
        var json = jdbc.sql(EXPORT_SQL).param("subject", subject).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("account"));
        try {
            return objectMapper.readTree(json);
        } catch (Exception problem) {
            throw new IllegalStateException("Unable to serialize account export", problem);
        }
    }
}

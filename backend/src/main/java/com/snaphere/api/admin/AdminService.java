package com.snaphere.api.admin;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.CursorCodec;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.config.PlaceTaskConfig;
import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.place.PlaceRepository;
import com.snaphere.api.place.PlaceService;
import com.snaphere.api.place.PlaceReadCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final JdbcClient jdbc;
    private final PlaceRepository places;
    private final PlaceService placeService;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;
    private final PlaceReadCache placeCache;

    public AdminService(JdbcClient jdbc, PlaceRepository places, PlaceService placeService,
                        @Qualifier(PlaceTaskConfig.PLACE_TASK_EXECUTOR) TaskExecutor executor,
                        PlatformTransactionManager transactionManager, PlaceReadCache placeCache) {
        this.jdbc = jdbc;
        this.places = places;
        this.placeService = placeService;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.placeCache = placeCache;
    }

    @Transactional
    public PlaceDtos.PlaceDetail updatePlaceRadius(String placeId, int radius) {
        long id = ExternalIds.parse(placeId, "plc", ErrorCode.PLACE_NOT_FOUND);
        int changed = jdbc.sql("UPDATE places SET verify_radius_m=:radius,updated_at=now() WHERE place_id=:id AND status='ACTIVE'")
                .param("radius", radius).param("id", id).update();
        if (changed == 0) throw new ApiException(ErrorCode.PLACE_NOT_FOUND);
        return placeService.detail(placeId, "ko", null);
    }

    @Transactional
    public PlaceDtos.Region updateRegionRadius(int areaCode, int radius) {
        int changed = jdbc.sql("UPDATE regions SET default_event_verify_radius_m=:radius WHERE area_code=:area")
                .param("radius", radius).param("area", areaCode).update();
        if (changed == 0) throw new ApiException(ErrorCode.COMMON_404);
        placeCache.evictRegions();
        return places.regions().stream().filter(r -> r.areaCode() == areaCode).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String,Object> updateEventRadius(String eventId, Integer radius) {
        long id = ExternalIds.parse(eventId, "evt", ErrorCode.COMMON_404);
        int changed = jdbc.sql("UPDATE events SET verify_radius_m=:radius,updated_at=now() WHERE event_id=:id AND status='ACTIVE'")
                .param("radius", radius, Types.INTEGER).param("id", id).update();
        if (changed == 0) throw new ApiException(ErrorCode.COMMON_404);
        return event(id);
    }

    @Transactional
    public Map<String,Object> updateEvent(String eventId, BatchDtos.AdminEventRequest body) {
        long id = ExternalIds.parse(eventId, "evt", ErrorCode.COMMON_404);
        var current = event(id);
        String title = body.title() == null ? (String) current.get("title") : body.title();
        String overview = body.overview() == null ? (String) current.get("overview") : body.overview();
        java.time.LocalDate start = body.startDate() == null ? (java.time.LocalDate) current.get("startDate") : body.startDate();
        java.time.LocalDate end = body.endDate() == null ? (java.time.LocalDate) current.get("endDate") : body.endDate();
        if (end.isBefore(start)) throw new ApiException(ErrorCode.COMMON_422);
        Integer radius = body.verifyRadiusM() == null ? (Integer) current.get("verifyRadiusM") : body.verifyRadiusM();
        jdbc.sql("""
                UPDATE events SET title=:title,overview=:overview,start_date=:start,end_date=:end,
                  verify_radius_m=:radius,updated_at=now() WHERE event_id=:id
                """).param("title",title).param("overview",overview).param("start",start).param("end",end)
                .param("radius",radius,Types.INTEGER).param("id",id).update();
        return event(id);
    }

    @Transactional
    public BatchDtos.ReportResult resolveReport(String reportId, BatchDtos.ResolveReportRequest body) {
        long id = ExternalIds.parse(reportId, "rpt", ErrorCode.REPORT_NOT_FOUND);
        ReportRow report = report(id, true);
        if (!"PENDING".equals(report.status())) throw new ApiException(ErrorCode.COMMON_409);
        String action = body.action();
        if (!List.of("RESTORE","HIDE","DELETE","REJECT").contains(action)) throw new ApiException(ErrorCode.COMMON_400);
        if ("PLACE".equals(report.targetType())) {
            String status = switch (action) { case "RESTORE" -> "ACTIVE"; case "HIDE" -> "HIDDEN"; case "DELETE" -> "DELETED"; default -> null; };
            if (status != null) jdbc.sql("UPDATE places SET status=:status,updated_at=now() WHERE place_id=:id")
                    .param("status",status).param("id",report.targetId()).update();
        } else if ("POST".equals(report.targetType())) {
            String status = switch (action) { case "RESTORE" -> "ACTIVE"; case "HIDE" -> "HIDDEN"; case "DELETE" -> "DELETED"; default -> null; };
            if (status != null) jdbc.sql("UPDATE posts SET status=:status,updated_at=now() WHERE post_id=:id")
                    .param("status",status).param("id",report.targetId()).update();
        }
        String reportStatus = "REJECT".equals(action) ? "REJECTED" : "RESOLVED";
        String storedAction = switch (action) {
            case "HIDE" -> "HIDE";
            case "DELETE" -> "DELETE";
            default -> "KEEP";
        };
        jdbc.sql("""
                UPDATE reports SET status=:status,action=:action,reviewed_at=now() WHERE report_id=:id
                """).param("status",reportStatus).param("action",storedAction).param("id",id).update();
        return reportResult(id);
    }

    public CursorPage<BatchDtos.ReportResult> reports(String status, String targetType, String cursor, int size) {
        if (size < 1 || size > 50) throw new ApiException(ErrorCode.COMMON_400);
        if (status != null && !List.of("PENDING","RESOLVED","REJECTED").contains(status))
            throw new ApiException(ErrorCode.COMMON_400);
        if (targetType != null && !List.of("POST","PLACE").contains(targetType))
            throw new ApiException(ErrorCode.COMMON_400);
        Long after = CursorCodec.decode(cursor);
        StringBuilder sql = new StringBuilder("""
                SELECT report_id,reporter_id,target_type,target_id,reason,status,action,created_at,reviewed_at
                FROM reports WHERE 1=1
                """);
        if (status != null) sql.append(" AND status=:status");
        if (targetType != null) sql.append(" AND target_type=:targetType");
        if (after != null) sql.append(" AND report_id<:after");
        sql.append(" ORDER BY report_id DESC LIMIT :limit");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("limit", size + 1);
        if (status != null) spec = spec.param("status", status);
        if (targetType != null) spec = spec.param("targetType", targetType);
        if (after != null) spec = spec.param("after", after);
        List<BatchDtos.ReportResult> rows = spec.query((rs,n) -> new BatchDtos.ReportResult(
                ExternalIds.report(rs.getLong(1)), rs.getObject(2).toString(), rs.getString(3),
                externalTarget(rs.getString(3),rs.getLong(4)), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getObject(8,OffsetDateTime.class),
                rs.getObject(9,OffsetDateTime.class))).list();
        boolean hasNext = rows.size() > size;
        List<BatchDtos.ReportResult> items = hasNext ? rows.subList(0,size) : rows;
        String next = hasNext ? CursorCodec.encode(ExternalIds.parse(
                items.get(items.size()-1).reportId(),"rpt",ErrorCode.COMMON_400)) : null;
        return new CursorPage<>(List.copyOf(items),next,hasNext);
    }

    public BatchDtos.BatchRun moderate(String placeId, BatchDtos.ModerationRequest body) {
        long source = ExternalIds.parse(placeId,"plc",ErrorCode.PLACE_NOT_FOUND);
        if ("RESTORE".equals(body.action())) {
            int changed = jdbc.sql("UPDATE places SET status='ACTIVE',updated_at=now() WHERE place_id=:id AND status='HIDDEN'")
                    .param("id",source).update();
            if (changed == 0) throw new ApiException(ErrorCode.PLACE_NOT_FOUND);
            long run = createModerationRun("SUCCESS",0);
            return moderationRun(run);
        }
        if (!"HIDE_AND_REASSIGN".equals(body.action())) throw new ApiException(ErrorCode.COMMON_400);
        Long target = body.targetPlaceId() == null ? null : ExternalIds.parse(body.targetPlaceId(),"plc",ErrorCode.PLACE_NOT_FOUND);
        long run = createModerationRun("QUEUED",0);
        executor.execute(() -> executeModeration(run,source,target));
        return moderationRun(run);
    }

    public void executeModeration(long runId, long source, Long requestedTarget) {
        jdbc.sql("UPDATE batch_runs SET status='RUNNING',started_at=now() WHERE run_id=:id").param("id",runId).update();
        try {
            transactions.executeWithoutResult(status -> {
                Long target = requestedTarget != null ? validateOfficial(requestedTarget) : nearestOfficial(source);
                if (target == null) throw new ApiException(ErrorCode.COMMON_422);
                jdbc.sql("UPDATE places SET status='HIDDEN',updated_at=now() WHERE place_id=:id").param("id",source).update();
                int moved = 0;
                while (true) {
                    int chunk = jdbc.sql("""
                            UPDATE posts SET place_id=:target,updated_at=now() WHERE post_id IN (
                              SELECT post_id FROM posts WHERE place_id=:source LIMIT 500)
                            """).param("target",target).param("source",source).update();
                    moved += chunk;
                    if (chunk < 500) break;
                }
                jdbc.sql("UPDATE reports SET status='RESOLVED',action='HIDE',reviewed_at=now() WHERE target_type='PLACE' AND target_id=:id AND status='PENDING'")
                        .param("id",source).update();
                jdbc.sql("UPDATE batch_runs SET status='SUCCESS',processed_count=:count,started_at=coalesce(started_at,now()),finished_at=now() WHERE run_id=:id")
                        .param("count",moved).param("id",runId).update();
            });
        } catch (RuntimeException e) {
            jdbc.sql("UPDATE batch_runs SET status='FAIL',failed_count=1,finished_at=now() WHERE run_id=:id")
                    .param("id",runId).update();
        }
    }

    private Map<String,Object> event(long id) {
        return jdbc.sql("""
                SELECT event_id,title,overview,start_date,end_date,verify_radius_m,area_code,place_id
                FROM events WHERE event_id=:id AND status='ACTIVE'
                """).param("id",id).query((rs,n)-> {
                    Map<String,Object> map=new java.util.LinkedHashMap<>();
                    map.put("eventId",ExternalIds.event(rs.getLong(1))); map.put("title",rs.getString(2));
                    map.put("overview",rs.getString(3)); map.put("startDate",rs.getObject(4,java.time.LocalDate.class));
                    map.put("endDate",rs.getObject(5,java.time.LocalDate.class)); map.put("verifyRadiusM",rs.getObject(6));
                    map.put("areaCode",rs.getInt(7)); map.put("placeId",ExternalIds.place(rs.getLong(8))); return map;
                }).optional().orElseThrow(()->new ApiException(ErrorCode.COMMON_404));
    }

    private ReportRow report(long id, boolean lock) {
        return jdbc.sql("SELECT target_type,target_id,status FROM reports WHERE report_id=:id"+(lock?" FOR UPDATE":""))
                .param("id",id).query((rs,n)->new ReportRow(rs.getString(1),rs.getLong(2),rs.getString(3))).optional()
                .orElseThrow(()->new ApiException(ErrorCode.REPORT_NOT_FOUND));
    }
    private BatchDtos.ReportResult reportResult(long id) {
        return jdbc.sql("""
                SELECT report_id,reporter_id,target_type,target_id,reason,status,action,created_at,reviewed_at
                FROM reports WHERE report_id=:id
                """).param("id",id).query((rs,n) -> new BatchDtos.ReportResult(
                ExternalIds.report(rs.getLong(1)),rs.getObject(2).toString(),rs.getString(3),
                externalTarget(rs.getString(3),rs.getLong(4)),rs.getString(5),rs.getString(6),
                rs.getString(7),rs.getObject(8,OffsetDateTime.class),rs.getObject(9,OffsetDateTime.class)))
                .single();
    }
    private Long validateOfficial(long id) { return jdbc.sql("SELECT place_id FROM places WHERE place_id=:id AND place_type='OFFICIAL' AND status='ACTIVE'")
            .param("id",id).query(Long.class).optional().orElseThrow(()->new ApiException(ErrorCode.PLACE_NOT_FOUND)); }
    private Long nearestOfficial(long source) { return jdbc.sql("""
            SELECT target.place_id FROM places origin JOIN places target ON target.place_type='OFFICIAL' AND target.status='ACTIVE'
            WHERE origin.place_id=:source AND origin.geom IS NOT NULL AND target.geom IS NOT NULL
            ORDER BY ST_Distance(origin.geom,target.geom),target.place_id LIMIT 1
            """).param("source",source).query(Long.class).optional().orElse(null); }
    private long createModerationRun(String status,int failed) { return jdbc.sql("INSERT INTO batch_runs(job_type,status,failed_count,started_at) VALUES ('PLACE_MODERATION',:status,:failed,now()) RETURNING run_id")
            .param("status",status).param("failed",failed).query(Long.class).single(); }
    private BatchDtos.BatchRun moderationRun(long run) { return jdbc.sql("SELECT run_id,job_type,status,processed_count,failed_count,started_at FROM batch_runs WHERE run_id=:id")
            .param("id",run).query((rs,n)->new BatchDtos.BatchRun(ExternalIds.run(rs.getLong(1)),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getInt(5),rs.getObject(6,OffsetDateTime.class))).single(); }
    private static String externalTarget(ReportRow row) { return externalTarget(row.targetType(),row.targetId()); }
    private static String externalTarget(String type,long id) { return switch(type){case "PLACE"->ExternalIds.place(id);case "POST"->ExternalIds.post(id);default->Long.toString(id);}; }
    private record ReportRow(String targetType,long targetId,String status) { }
}

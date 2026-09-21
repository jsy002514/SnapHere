package com.snaphere.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 응답과 분리해 돌리는 작업용 스레드 풀. (PST-019, SYS-015)
 *
 * <p>이미지 후처리는 사진 한 장에 수 MB 를 내려받아 다시 인코딩한다. 요청 처리 스레드에서 하면
 * 등록 응답이 그만큼 늦어지고, 톰캣 스레드가 I/O 로 묶인다.
 *
 * <p>큐 길이를 제한한다. 무한 큐를 두면 후처리가 밀릴 때 메모리에 작업이 쌓이다가 프로세스가
 * 죽고, 그때까지 밀린 후처리는 전부 사라진다. 큐가 차면 호출한 스레드가 직접 실행해
 * (CallerRuns) 유입 속도를 늦춘다 — 등록이 느려지지만 유실되지는 않는다.
 *
 * <p>작업 규모가 커지면 이 풀 대신 별도 워커와 메시지 큐로 옮긴다. 그때 바꿀 곳은 이 파일과
 * 리스너 하나다.
 *
 * <p>{@code @EnableScheduling} 도 여기서 켠다. 주기 배치(JOB-013)는 개별 빈이
 * {@code snaphere.jobs.enabled} 로 스스로 꺼지므로 이 설정은 항상 두어도 된다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /** 이미지 후처리 전용 풀. 다른 비동기 작업과 섞지 않는다 — 하나가 밀려도 나머지는 돈다. */
    public static final String IMAGE_PROCESSING_EXECUTOR = "imageProcessingExecutor";

    @Bean(IMAGE_PROCESSING_EXECUTOR)
    public TaskExecutor imageProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("img-post-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중인 후처리를 마칠 시간을 준다. 중간에 끊기면 파생 객체만 남는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

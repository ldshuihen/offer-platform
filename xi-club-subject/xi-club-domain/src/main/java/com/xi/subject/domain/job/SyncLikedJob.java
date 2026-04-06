package com.xi.subject.domain.job;

import com.xi.subject.domain.service.SubjectLikedDomainService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 同步点赞数据
 * 查询点赞
 * 的数据交互，我们可以和 redis 直接来做，持久化的数据，通过数据库查询即可，这个数据如何去同步到数据库，我们就采取的定时任务 xxl-job 定期来刷数据。
 * @author: ChickenWing
 * @date: 2024/1/8
 */
@Component
@Slf4j
public class SyncLikedJob {

    @Resource
    private SubjectLikedDomainService subjectLikedDomainService;

    /**
     * 同步点赞数据任务
     */
    @XxlJob("syncLikedJobHandler")
    public void syncLikedJobHandler() throws Exception {
        XxlJobHelper.log("syncLikedJobHandler.start");
        try {
            subjectLikedDomainService.syncLiked();
        } catch (Exception e) {
            XxlJobHelper.log("syncLikedJobHandler.error" + e.getMessage());
        }
    }

}

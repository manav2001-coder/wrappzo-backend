package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Submission;
import com.wrapzzo.backend.model.SubmissionStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<Submission> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<Submission> findBySubmittedByIdOrderByCreatedAtDesc(UUID userId);

    List<Submission> findByCampaignIdAndSubmittedByIdOrderByCreatedAtDesc(UUID campaignId, UUID userId);

    List<Submission> findByCompanyIdAndStatusInOrderByCreatedAtDesc(UUID companyId, List<SubmissionStatus> statuses);

    /** One batched query for every campaign's submission counts by status -- not a per-campaign query (PRD 4.7). */
    @Query("select s.campaign.id as campaignId, s.status as status, count(s) as cnt from Submission s "
            + "where s.campaign.id in :campaignIds group by s.campaign.id, s.status")
    List<CampaignSubmissionStat> countByCampaignIdInGroupByStatus(@Param("campaignIds") Collection<UUID> campaignIds);
}

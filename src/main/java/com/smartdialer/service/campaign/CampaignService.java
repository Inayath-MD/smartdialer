package com.smartdialer.service.campaign;

import com.smartdialer.dto.CampaignDto;
import com.smartdialer.dto.CreateCampaignRequest;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Transactional
    public CampaignDto createCampaign(CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
                request.name(),
                request.dialingMode(),
                request.maxConcurrentCalls() != null ? request.maxConcurrentCalls() : 10
        );
        if (request.targetAbandonmentRate() != null) {
            campaign.setTargetAbandonmentRate(request.targetAbandonmentRate());
        }
        if (request.maxAttemptsPerBorrower() != null) {
            campaign.setMaxAttemptsPerBorrower(request.maxAttemptsPerBorrower());
        }
        if (request.cooldownPeriodSeconds() != null) {
            campaign.setCooldownPeriodSeconds(request.cooldownPeriodSeconds());
        }

        Campaign saved = campaignRepository.save(campaign);
        log.info("Created campaign id={} name='{}' mode={}", saved.getId(), saved.getName(), saved.getDialingMode());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public CampaignDto getCampaign(Long id) {
        return toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaigns() {
        return campaignRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public CampaignDto startCampaign(Long id) {
        Campaign campaign = findEntityById(id);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign id={} started", id);
        return toDto(saved);
    }

    @Transactional
    public CampaignDto pauseCampaign(Long id) {
        Campaign campaign = findEntityById(id);
        campaign.setStatus(CampaignStatus.PAUSED);
        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign id={} paused", id);
        return toDto(saved);
    }

    public Campaign findEntityById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id: " + id));
    }

    public CampaignDto toDto(Campaign campaign) {
        return new CampaignDto(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getDialingMode(),
                campaign.getMaxConcurrentCalls(),
                campaign.getTargetAbandonmentRate(),
                campaign.getMaxAttemptsPerBorrower(),
                campaign.getCooldownPeriodSeconds(),
                campaign.getCreatedAt()
        );
    }
}

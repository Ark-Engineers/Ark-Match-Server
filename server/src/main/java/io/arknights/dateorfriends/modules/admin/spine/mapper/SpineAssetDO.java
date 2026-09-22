package io.arknights.dateorfriends.modules.admin.spine.mapper;

import java.time.LocalDateTime;

public class SpineAssetDO {
    private Long id;
    private String assetKey;
    private String name;
    private Integer type;
    private String idleAnimation;
    private String moveAnimation;
    private Double displayScale;
    private Integer raceCount;
    private Integer firstPlaceCount;
    private Integer secondPlaceCount;
    private Integer thirdPlaceCount;
    private Integer unplacedCount;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public void setAssetKey(String assetKey) {
        this.assetKey = assetKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getIdleAnimation() {
        return idleAnimation;
    }

    public void setIdleAnimation(String idleAnimation) {
        this.idleAnimation = idleAnimation;
    }

    public String getMoveAnimation() {
        return moveAnimation;
    }

    public void setMoveAnimation(String moveAnimation) {
        this.moveAnimation = moveAnimation;
    }

    public Double getDisplayScale() {
        return displayScale;
    }

    public void setDisplayScale(Double displayScale) {
        this.displayScale = displayScale;
    }

    public Integer getRaceCount() {
        return raceCount;
    }

    public void setRaceCount(Integer raceCount) {
        this.raceCount = raceCount;
    }

    public Integer getFirstPlaceCount() {
        return firstPlaceCount;
    }

    public void setFirstPlaceCount(Integer firstPlaceCount) {
        this.firstPlaceCount = firstPlaceCount;
    }

    public Integer getSecondPlaceCount() {
        return secondPlaceCount;
    }

    public void setSecondPlaceCount(Integer secondPlaceCount) {
        this.secondPlaceCount = secondPlaceCount;
    }

    public Integer getThirdPlaceCount() {
        return thirdPlaceCount;
    }

    public void setThirdPlaceCount(Integer thirdPlaceCount) {
        this.thirdPlaceCount = thirdPlaceCount;
    }

    public Integer getUnplacedCount() {
        return unplacedCount;
    }

    public void setUnplacedCount(Integer unplacedCount) {
        this.unplacedCount = unplacedCount;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

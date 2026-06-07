package com.covenant.platform.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingDetails {
    private String trackingId;
    private String logisticsProvider;
    private LocalDateTime deliveryDate;
}

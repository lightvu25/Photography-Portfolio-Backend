package com.photography.backend.gallery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoUploadSignatureResponse {

    private String signature;
    private long timestamp;
    private String apiKey;
    private String cloudName;
    private String folder;
    private String publicId;
    private String uploadUrl;
}

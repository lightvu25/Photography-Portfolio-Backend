package com.photography.backend.gallery.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PhotoUploadSignatureRequest {

    private String fileName;
    private String contentType;
    private Long fileSizeBytes;
}

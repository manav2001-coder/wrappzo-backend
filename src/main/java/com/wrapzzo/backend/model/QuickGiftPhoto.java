package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One recipient photo of a Quick Gift / PEL order (V76). Numbered from 1 in the order they were added. */
@Entity
@Table(name = "quick_gift_photos")
@Getter
@NoArgsConstructor
public class QuickGiftPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quick_gift_request_id", nullable = false)
    private QuickGiftRequest quickGiftRequest;

    @Column(nullable = false)
    private int position;

    @Column(name = "photo_key", nullable = false)
    private String photoKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    QuickGiftPhoto(QuickGiftRequest request, int position, String photoKey, String fileName, String contentType) {
        this.quickGiftRequest = request;
        this.position = position;
        this.photoKey = photoKey;
        this.fileName = fileName;
        this.contentType = contentType;
    }
}

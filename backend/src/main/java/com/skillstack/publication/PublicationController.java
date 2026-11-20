package com.skillstack.publication;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PublicationController {
    private final PublicationService publication;

    public PublicationController(PublicationService publication) {
        this.publication = publication;
    }

    @GetMapping("/publication")
    public PublicationDtos.OwnerResponse owner(Authentication authentication) {
        return publication.owner(authentication.getName());
    }

    @PutMapping("/publication")
    public PublicationDtos.OwnerResponse update(Authentication authentication,
                                                @Valid @RequestBody PublicationDtos.UpdateRequest request) {
        return publication.update(authentication.getName(), request);
    }

    @PostMapping("/publication/publish")
    public PublicationDtos.OwnerResponse publish(Authentication authentication) {
        return publication.publish(authentication.getName());
    }

    @PostMapping("/publication/unpublish")
    public PublicationDtos.OwnerResponse unpublish(Authentication authentication) {
        return publication.unpublish(authentication.getName());
    }

    @PostMapping("/publication/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationDtos.ShareCreated createShare(Authentication authentication,
                                                    @Valid @RequestBody(required = false) PublicationDtos.CreateShareRequest request) {
        return publication.createShare(authentication.getName(), request);
    }

    @DeleteMapping("/publication/shares/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(Authentication authentication, @PathVariable UUID id) {
        publication.revokeShare(authentication.getName(), id);
    }

    @PostMapping("/publication/timeline")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationDtos.OwnerResponse addTimeline(Authentication authentication,
                                                     @Valid @RequestBody PublicationDtos.CreateTimelineRequest request) {
        return publication.addTimeline(authentication.getName(), request);
    }

    @DeleteMapping("/publication/timeline/{id}")
    public PublicationDtos.OwnerResponse deleteTimeline(Authentication authentication, @PathVariable UUID id) {
        return publication.deleteTimeline(authentication.getName(), id);
    }

    @GetMapping("/public/profiles/{slug}")
    public ResponseEntity<PublicationDtos.PublicView> publicProfile(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publication.publicBySlug(slug));
    }

    @GetMapping("/public/shares/{token}")
    public ResponseEntity<PublicationDtos.PublicView> publicShare(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publication.publicByShareToken(token));
    }
}

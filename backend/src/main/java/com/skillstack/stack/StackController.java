package com.skillstack.stack;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StackController {
    private final StackService stacks;
    public StackController(StackService stacks) { this.stacks = stacks; }

    @GetMapping("/stack/summary")
    public StackDtos.Summary summary(Authentication authentication) { return stacks.summary(authentication.getName()); }

    @PostMapping("/intersections/discover")
    public StackDtos.Discovery discover(Authentication authentication, @Valid @RequestBody StackDtos.DiscoverRequest request) {
        return stacks.discover(authentication.getName(), request);
    }

    @GetMapping("/develop")
    public StackDtos.Develop develop(Authentication authentication) { return stacks.develop(authentication.getName()); }
}

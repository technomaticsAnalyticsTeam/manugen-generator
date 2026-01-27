package ru.leyman.manugen.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.leyman.manugen.dto.GenerationRequest;
import ru.leyman.manugen.service.GenerationService;

@RestController
@RequestMapping("gen")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    public void generate(@RequestBody GenerationRequest request) {
        generationService.generate(request);
    }

}

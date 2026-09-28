package edu.stankin.cogoalmain.web.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/")
@Tag(name = "Hello world controller", description = "Тестовый контроллер")
public class HelloController {

    @GetMapping(value = "HelloWorld")
    @Operation(summary = "Привет мир эндпоинт да!")
    public String HelloWorldEndpoint(){
        return "Hello World!";
    }
}
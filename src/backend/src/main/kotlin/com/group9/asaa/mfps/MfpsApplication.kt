package com.group9.asaa.mfps

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.PropertySource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@ComponentScan("com.group9.asaa")
@PropertySource("classpath:secrets-dev.properties")
class MfpsApplication

fun main(args: Array<String>) {
	runApplication<MfpsApplication>(*args)
}


@RestController
@RequestMapping("/mfps")
class MfpsController {
	@RequestMapping("/system-status")
	fun status(): ResponseEntity<String> {
		return ResponseEntity.ok().body("Mfps System is running")
	}
}

package uy.edu.um.nexo.mnatural.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa las tareas con @Scheduled (por ejemplo BookingExpirationTask)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

package cz.cvut.ismd.assistant.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cz.cvut.ismd.assistant")
public class SemanticModelingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemanticModelingAssistantApplication.class, args);
    }
}

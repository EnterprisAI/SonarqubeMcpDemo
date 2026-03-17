package com.example.mcp.SonarqubeMcpDemo.controller;

import com.example.mcp.SonarqubeMcpDemo.tools.SonarQubeMcpTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint that accepts a plain-text question, passes it to GitHub Models
 * (via Spring AI), and lets the AI call SonarQube tools automatically to answer.
 *
 * POST /chat
 * Body: plain text question, e.g. "What bugs does sonarqube-mcp-demo have?"
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, SonarQubeMcpTools sonarQubeMcpTools) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a code quality assistant with access to SonarQube.
                        Use the available tools to fetch real data and give concise, helpful answers.
                        Always mention the project key when reporting results.
                        """)
                .defaultTools(sonarQubeMcpTools)
                .build();
    }

    @PostMapping(consumes = "text/plain", produces = "text/plain")
    public String chat(@RequestBody String question) {
        return chatClient.prompt(question).call().content();
    }
}

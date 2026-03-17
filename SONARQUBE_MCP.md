# SonarQube MCP Server

A Spring Boot application that acts as a **Model Context Protocol (MCP) server**, exposing SonarQube code quality data as tools that AI assistants (like Claude) can call directly.

---

## What is MCP?

The **Model Context Protocol (MCP)** is an open protocol that lets AI assistants call external tools and data sources in a standardised way. Instead of building custom integrations for each AI, you implement one MCP server and any compatible AI client can use it.

```
AI Assistant (e.g. Claude Desktop)
        │
        │  MCP Protocol (STDIO or SSE)
        ▼
┌─────────────────────────┐
│  SonarQube MCP Server   │   ← this application
│  (Spring Boot + AI MCP) │
└─────────────────────────┘
        │
        │  SonarQube Web API (HTTP)
        ▼
┌─────────────────────────┐
│  SonarQube Instance     │
└─────────────────────────┘
```

---

## Project Structure

```
src/main/java/com/example/mcp/SonarqubeMcpDemo/
├── Application.java                     # Spring Boot entry point
├── client/
│   └── SonarQubeClient.java             # HTTP client for SonarQube Web API
├── config/
│   ├── SonarQubeProperties.java         # Reads sonarqube.url / sonarqube.token
│   ├── McpServerConfig.java             # Registers tools with the MCP server
│   └── SecurityConfig.java             # Minimal Spring Security config
└── tools/
    └── SonarQubeMcpTools.java           # The MCP tool definitions (@Tool methods)
```

---

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `listProjects` | List all SonarQube projects accessible with the configured token |
| `getProjectMetrics` | Get bugs, vulnerabilities, code smells, coverage, duplications, and ratings for a project |
| `getQualityGateStatus` | Get Quality Gate PASSED/FAILED status with individual conditions |
| `searchIssues` | Search issues filtered by project, severity, and type |
| `getSecurityHotspots` | Get security hotspots that need manual review |
| `getServerInfo` | Get SonarQube server version |

---

## How the MCP Server Works

### 1. Tool Registration (`McpServerConfig`)

```java
@Bean
public ToolCallbackProvider sonarQubeToolCallbackProvider(SonarQubeMcpTools tools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
}
```

Spring AI's MCP server auto-configuration picks up all `ToolCallbackProvider` beans and publishes the tools over the MCP protocol. No manual wiring is needed.

### 2. Tool Definition (`SonarQubeMcpTools`)

```java
@Tool(description = "Get the Quality Gate status for a SonarQube project.")
public String getQualityGateStatus(
        @ToolParam(description = "The SonarQube project key") String projectKey) {
    return client.get("/api/qualitygates/project_status?projectKey=" + projectKey);
}
```

Each `@Tool` method becomes a callable tool. The `description` fields are sent to the AI so it knows when and how to use each tool.

### 3. Transport

Two transport modes are supported by Spring AI MCP:

| Mode | Use case | Config |
|------|----------|--------|
| **STDIO** | Claude Desktop, local CLI | `spring.ai.mcp.server.stdio: true` |
| **SSE** | Web-based clients, remote access | `spring.ai.mcp.server.stdio: false` |

---

## Setup & Configuration

### Prerequisites

- Java 17+
- A running SonarQube instance (Community Edition is free)
- A SonarQube **User Token** (generated from *My Account → Security*)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SONARQUBE_URL` | `http://localhost:9000` | Base URL of your SonarQube instance |
| `SONARQUBE_TOKEN` | *(empty)* | SonarQube user token for authentication |

### Running the Server

```bash
# Build
./gradlew build

# Run with environment variables
SONARQUBE_URL=http://localhost:9000 \
SONARQUBE_TOKEN=squ_yourtoken \
java -jar build/libs/SonarqubeMcpDemo-0.0.1-SNAPSHOT.jar
```

On Windows:
```cmd
set SONARQUBE_URL=http://localhost:9000
set SONARQUBE_TOKEN=squ_yourtoken
java -jar build\libs\SonarqubeMcpDemo-0.0.1-SNAPSHOT.jar
```

---

## Integration with Claude Desktop

Add the following to your Claude Desktop config file:

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "java",
      "args": ["-jar", "/path/to/SonarqubeMcpDemo-0.0.1-SNAPSHOT.jar"],
      "env": {
        "SONARQUBE_URL": "http://localhost:9000",
        "SONARQUBE_TOKEN": "squ_yourtoken"
      }
    }
  }
}
```

After restarting Claude Desktop, you can ask questions like:
- *"List all my SonarQube projects"*
- *"What is the quality gate status of my-service?"*
- *"Show me all BLOCKER bugs in the payment-api project"*
- *"What security hotspots need review in my-app?"*

---

## Key Dependencies

```groovy
// Spring AI MCP Server — provides @Tool, ToolCallbackProvider, STDIO/SSE transport
implementation 'org.springframework.ai:spring-ai-starter-mcp-server'

// Spring AI MCP Client — for acting as an MCP client to other servers
implementation 'org.springframework.ai:spring-ai-starter-mcp-client'

// MCP security extensions from the Spring AI Community
implementation 'org.springaicommunity:mcp-server-security:0.1.1'
implementation 'org.springaicommunity:mcp-client-security:0.1.1'
```

---

## Example AI Conversation

```
User: What's the quality gate status of the checkout-service?

Claude: [calls getQualityGateStatus("checkout-service")]
        → SonarQube returns: FAILED
          Conditions:
          - Coverage < 80%  (actual: 61.3%)  ❌
          - Blocker Issues = 0  (actual: 0)   ✓

        The quality gate for checkout-service is currently FAILED.
        The main issue is test coverage at 61.3%, which is below the
        required 80% threshold.

User: Show me the blocker bugs.

Claude: [calls searchIssues("checkout-service", "BLOCKER", "BUG")]
        → Returns list of blocker-level bugs with file locations and messages
```

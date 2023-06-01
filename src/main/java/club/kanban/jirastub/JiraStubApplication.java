package club.kanban.jirastub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

@SpringBootApplication
@RestController
public class JiraStubApplication {
    private final static String API_PREFIX = "/rest/agile/{apiVersion}";
    private final String ERROR_MESSAGE_ISSUE = "{\"errorMessages\":[\"The issue no longer exists.\"],\"errors\":{}}";
    private final String ERROR_MESSAGE_BOARD = "{\"errorMessages\":[],\"errors\":{\"rapidViewId\":\"The requested board cannot be viewed because it either does not exist or you do not have permission to view it.\"}}";
    private final String FILE_BOARD = "board.json";
    private final String FILE_BOARD_CONFIGURATION = "boardconfig.json";
    private final String FILE_BOARD_ISSUES = "issueset.json";
    private final static String DEFAULT_MAX_RESULTS = "50";

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.ssl.key-store:}")
    private String keyStore;

    @Value("${profiles}")
    private String[] profiles;
    private final Map<Long, LinkedHashMap> boards = new LinkedHashMap<>();
    private final Map<Long, LinkedHashMap> boardConfigurations = new LinkedHashMap<>();
    private final Map<Long, List> boardIssues = new LinkedHashMap<>();
    private final Logger logger;

    public JiraStubApplication() {
        logger = LoggerFactory.getLogger("JiraStubApplication");
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(JiraStubApplication.class).headless(false).run(args);

        EventQueue.invokeLater(() -> {
            JiraStubApplication app = ctx.getBean(JiraStubApplication.class);

            for (String profile : app.profiles) {
                app.loadProfile(profile);
            }
        });
    }

    public void loadProfile(String profile) {
        try {
            LinkedHashMap json;

            logger.info("Loading profile '{}' ...", profile);

            json = getJSONObjectFromResource(profile + "/" + FILE_BOARD);

            Long boardId;
            Object o = json.get("id");
            if (o instanceof Number)
                boardId = ((Number) (o)).longValue();
            else
                boardId = Long.parseLong((String) o);

            boards.put(boardId, json);

            json = getJSONObjectFromResource(profile + "/" + FILE_BOARD_CONFIGURATION);
            boardConfigurations.put(boardId, json);

            json = getJSONObjectFromResource(profile + "/" + FILE_BOARD_ISSUES);
            List issues = (List) json.get("issues");
            boardIssues.put(boardId, issues);

            logger.info("SUCCESS: {} issues loaded for profile '{}'", issues.size(), profile);
        } catch (Exception e) {
            logger.error("ERROR: no issues loaded for profile '{}'", profile);
        }

        String scheme = (keyStore != null && !keyStore.isBlank()) ? "https" : "http";
        logger.info("USAGE {}://localhost:{}/rest/agile/latest/board/1/issue", scheme, serverPort);
    }

    @PostMapping("/rest/auth/1/session")
    public ResponseEntity<String> login(@RequestBody String json) {
        ResponseEntity<String> response;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> parsedJson = mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            String username = Objects.requireNonNull(parsedJson.get("username"));
            String password = Objects.requireNonNull(parsedJson.get("password"));

            logger.info("Запрос на авторизацию пользователя: {}", username);
            if (!(username.equalsIgnoreCase("username") && password.equals("password"))) {
                throw new RuntimeException("Unauthorized");
            }
            String sessionId = UUID.randomUUID().toString();
            HttpCookie cookie = ResponseCookie.from("JSESSIONID", sessionId).build();
            response = ResponseEntity
                    .ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(String.format("{ \"session\": {\"name\": \"JSESSIONID\", \"value\": \"%s\"}}", sessionId));

            logger.info("Отправлен JSESSIONID: {}", sessionId);
        } catch (NullPointerException | JsonProcessingException e) {
            logger.info("Неверный формат запроса на авторизацию");
            response = ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.info("Неверное имя пользователя или пароль");
            response = new ResponseEntity<String>(e.getMessage(), HttpStatus.UNAUTHORIZED);
        }

        return response;
    }
    @DeleteMapping("/rest/auth/1/session")
    public ResponseEntity<String> logout(@CookieValue("JSESSIONID") String sessionId) {
        ResponseEntity<String> response = ResponseEntity.noContent().build();
        logger.info("Сессия {} закрыта", sessionId);
        return response;
    }

    public static LinkedHashMap getJSONObjectFromResource(String file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(file);
        return mapper.readValue(inputStream.readAllBytes(), new TypeReference<LinkedHashMap>() {
        });
    }

    @GetMapping(API_PREFIX + "/board/{boardId}")
    public String getBoard(@PathVariable("boardId") long boardId) throws JsonProcessingException {
        LinkedHashMap board = boards.get(boardId);
        return board != null ? (new ObjectMapper()).writeValueAsString(board) : ERROR_MESSAGE_BOARD;
    }

    @GetMapping(API_PREFIX + "/board/{boardId}/configuration")
    public String getBoardConfiguration(@PathVariable("boardId") long boardId) throws JsonProcessingException {
        LinkedHashMap boardConfiguration = boardConfigurations.get(boardId);
        return boardConfiguration != null ? (new ObjectMapper()).writeValueAsString(boardConfiguration) : ERROR_MESSAGE_BOARD;
    }

/*    @GetMapping(API_PREFIX + "/issue/{issueId}")
    public String getIssue(@PathVariable("issueId") long issueId) throws JsonProcessingException {
        List issue = boardIssues.get(issueId);
        return issue != null ? (new ObjectMapper()).writeValueAsString(issue.stream().findAny().orElseThrow()) : ERROR_MESSAGE_BOARD;
    }*/

    @GetMapping(API_PREFIX + "/board/{boardId}/issue")
    public String getAllIssues(@PathVariable("boardId") long boardId,
                               @RequestParam(name = "startAt", defaultValue = "0") long startAt,
                               @RequestParam(name = "maxResults", defaultValue = DEFAULT_MAX_RESULTS) long maxResults
    ) throws JsonProcessingException {

        long start = startAt >= 0 ? startAt : 0;
        long stop = start + maxResults;

        List<LinkedHashMap> subList = null;

        List<LinkedHashMap> issues = boardIssues.get(boardId);

        if (issues != null) {

            if (start < issues.size() && stop >= 0) {
                if (stop > issues.size()) {
                    stop = issues.size();
                }
                subList = issues.subList((int) start, (int) stop);
            } else
                subList = new ArrayList<>();
            LinkedHashMap issueSet = new LinkedHashMap() {
            };
            issueSet.put("startAt", start);
            issueSet.put("maxResults", maxResults);
            issueSet.put("total", issues.size());
            issueSet.put("issues", subList);
            return (new ObjectMapper()).writeValueAsString(issueSet);
        } else {
            return String.format("The board '%d' is not found", boardId);
        }
    }
}

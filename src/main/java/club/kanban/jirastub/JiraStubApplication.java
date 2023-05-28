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
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

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
    String serverPort;

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

        logger.info("USAGE http://localhost:{}/rest/agile/latest/board/00000/issue", serverPort);
    }

    @PostMapping("/login.jsp")
    public ResponseEntity<String> login() {
        HttpCookie cookie = ResponseCookie.from("JSESSIONID", UUID.randomUUID().toString()).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body("");
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

    @GetMapping(API_PREFIX + "/issue/{issueId}")
    public String getIssue(@PathVariable("issueId") long issueId) throws JsonProcessingException {
        List issue = boardIssues.get(issueId);
        return issue != null ? (new ObjectMapper()).writeValueAsString(issue.stream().findAny().orElseThrow()) : ERROR_MESSAGE_BOARD;
//        return "asca";
    }

    @GetMapping(API_PREFIX + "/board/{boardId}/issue")
    public String getAllIssues(@PathVariable("boardId") long boardId, @RequestParam(name = "startAt", defaultValue = "0") long startAt, @RequestParam(name = "maxResults", defaultValue = DEFAULT_MAX_RESULTS) long maxResults) throws JsonProcessingException {
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
            return String.format("The board '%d' not found", boardId);
        }
    }
}

package club.kanban.jirastub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

//@PropertySource(value = "classpath:config.properties")
@SpringBootApplication
@RestController
public class JiraStubApplication {

    private final static String API_PREFIX = "/rest/agile/{apiVersion}";
    private final String ERROR_MESSAGE_ISSUE = "{\"errorMessages\":[\"The issue no longer exists.\"],\"errors\":{}}";
    private final String ERROR_MESSAGE_BOARD = "{\"errorMessages\":[],\"errors\":{\"rapidViewId\":\"The requested board cannot be viewed because it either does not exist or you do not have permission to view it.\"}}";
    private final String FILE_BOARD = "board.json";
    private final String FILE_BOARD_CONFIGURATION = "boardconfig.json";
    private final String FILE_ISSUE = "issue.json";
    private final String FILE_BOARD_ISSUES = "issueset.json";
    private final static String DEFAULT_MAX_RESULTS = "50";

    @Value("${profile}")
    private String profile;
    private JSONObject jsonBoard;
    private JSONObject jsonBoardConfiguration;
    private JSONObject jsonIssue;
    private List<LinkedHashMap> jsonBoardIssues;

    public static void main(String[] args) {
//		SpringApplication.run(JiraStubApplication.class, args);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(JiraStubApplication.class).headless(false).run(args);

        EventQueue.invokeLater(() -> {
            JiraStubApplication app = ctx.getBean(JiraStubApplication.class);

            try {
                System.out.print("Loading data files ...");
                app.jsonBoard = getJSONObjectFromResource(app.profile + "/" + app.FILE_BOARD);
                app.jsonBoardConfiguration = getJSONObjectFromResource(app.profile + "/" + app.FILE_BOARD_CONFIGURATION);
                app.jsonIssue = getJSONObjectFromResource(app.profile + "/" + app.FILE_ISSUE);
                ObjectMapper mapper = new ObjectMapper();

//                File file = new File(app.profile + "/" + app.FILE_BOARD_ISSUES);
                InputStream inputStream = ClassLoader.getSystemResourceAsStream(app.profile + "/" + app.FILE_BOARD_ISSUES);
                LinkedHashMap json = mapper.readValue(inputStream.readAllBytes(), new TypeReference<LinkedHashMap>() {});
                app.jsonBoardIssues = (List<LinkedHashMap>) json.get("issues");

                System.out.printf(" done. %d issues loaded.\n", app.jsonBoardIssues.size());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
    }

    public static JSONObject getJSONObjectFromResource(String file) throws IOException {
        JSONObject jsonObject = null;
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(file);
        String s = IOUtils.toString(inputStream, "UTF-8");
        jsonObject = (JSONObject) JSONSerializer.toJSON(s);
        return jsonObject;
    }

    @GetMapping(API_PREFIX + "/board/{boardId}")
    public String getBoard(@PathVariable("boardId") long boardId) {
        long id = jsonBoard.getLong("id");
        return boardId == id ? jsonBoard.toString() : ERROR_MESSAGE_BOARD;
    }

    @GetMapping(API_PREFIX + "/board/{boardId}/configuration")
    public String getBoardConfiguration(@PathVariable("boardId") int boardId) {
        long id = jsonBoard.getLong("id");
        return boardId == id ? jsonBoardConfiguration.toString() : ERROR_MESSAGE_BOARD;
    }

    @GetMapping(API_PREFIX + "/issue/{issueId}")
    public String getIssue(@PathVariable("issueId") int issueId) {
        long id = jsonIssue.getLong("id");
        return issueId == id ? jsonIssue.toString() : ERROR_MESSAGE_ISSUE;
    }

    @GetMapping(API_PREFIX + "/board/{boardId}/issue")
    public IssuesSet getAllIssues(@PathVariable("boardId") int boardId, @RequestParam(name = "startAt", defaultValue = "0") long startAt, @RequestParam(name = "maxResults", defaultValue = DEFAULT_MAX_RESULTS) long maxResults) {
        long start = startAt >= 0 ? startAt : 0;
        long stop = start + maxResults;

        List<LinkedHashMap> subList = null;

        long id = jsonBoard.getLong("id");
        if (boardId == id) {

            if (start < jsonBoardIssues.size() && stop >= 0) {
                if (stop > jsonBoardIssues.size()) {
                    stop = jsonBoardIssues.size();
                }
                subList = jsonBoardIssues.subList((int) start, (int) stop);
            } else
                subList = new ArrayList<>();
        } else
            subList = new ArrayList<>();

        return new IssuesSet(subList, start, maxResults, jsonBoardIssues.size());
    }
}

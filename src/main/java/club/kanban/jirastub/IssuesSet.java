package club.kanban.jirastub;

import net.sf.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IssuesSet {
    public long startAt;
    public long maxResults;
    public long total;
    public List<LinkedHashMap> issues;
    public IssuesSet(List<LinkedHashMap> issues, long startAt, long maxResults, long total) {
        this.startAt = startAt;
        this.maxResults = maxResults;
        this.total = total;
        this.issues = issues;
    }
}

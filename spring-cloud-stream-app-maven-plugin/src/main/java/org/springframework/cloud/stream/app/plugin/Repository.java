package org.springframework.cloud.stream.app.plugin;

/**
 * @author Soby Chacko
 */
public class Repository {

    private String name;
    private String url;
    private boolean snapshotEnabled;
    private String id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    public void setSnapshotEnabled(boolean snapshotEnabled) {
        this.snapshotEnabled = snapshotEnabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

import java.util.ArrayList;
import java.util.List;

public class Host {
    private String ip;
    private List<CountStatistic> counts;

    public Host() {
        counts = new ArrayList<>();
    }

    public Host(String ip, List<CountStatistic> counts) {
        this.ip = ip;
        this.counts = counts;
    }

    public void incrementCount(String name) {
        Boolean found = false;
        for (CountStatistic s : counts) {
            if (s.getName().equals(name)) {
                s.increment(1);
                found = true;
            }
        }
        if (!found) counts.add(new CountStatistic(name, 1));
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<CountStatistic> getCounts() {
        return counts;
    }

    public void setCounts(List<CountStatistic> counts) {
        this.counts = counts;
    }
}

